package com.andye.warmod.fire.client;

import com.andye.warmod.fire.FireSimulationManager;
import com.andye.warmod.fire.network.ClientboundFireStatePayload;
import com.andye.warmod.fire.network.ClientboundFireWindImpulsePayload;
import com.andye.warmod.fire.network.FireVisualBand;
import com.andye.warmod.fire.network.FireVisualCell;
import com.andye.warmod.fire.wind.FireWindImpulse;
import com.andye.warmod.particle.gpu.GpuParticleEngine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Persistent client cache for explicit fire-cell deltas and authoritative firebrands. */
public final class ClientFireVisualManager {
    public static final ClientFireVisualManager INSTANCE = new ClientFireVisualManager();
    private static final int EXPIRY_TICKS = 160;
    private static final int REPRESENTATION_TRANSITION_TICKS = 16;
    private static final int TOPOLOGY_TRANSITION_TICKS = 8;
    private static final int NEW_CELL_FADE_TICKS = 6;
    private static final int SMOKE_RETIRE_TICKS = 48;

    private final EnumMap<FireVisualBand, LinkedHashMap<Long, CellVisual>> cells =
        new EnumMap<>(FireVisualBand.class);
    private final LinkedHashMap<BandCellKey, CellVisual> retiringCells =
        new LinkedHashMap<>();
    private final Map<Long, EmberVisual> embers = new LinkedHashMap<>();
    private final Map<HierarchyLodKey, Integer> lodLevels = new LinkedHashMap<>();
    private final ArrayDeque<FireWindImpulse> windImpulses = new ArrayDeque<>();
    private PendingPageTransaction pendingPages;
    private ClientLevel activeLevel;
    /**
     * Fire animation uses a client-tick timeline rather than ClientLevel's
     * mutable world clock. Time-sync correction may rewind the latter while
     * this level is still active.
     */
    private final VisualClock visualClock = new VisualClock();
    private long highestGeneration = Long.MIN_VALUE;
    private long snapshotTick = Long.MIN_VALUE;
    private List<VisualCell> cachedSnapshot = List.of();

    private ClientFireVisualManager() {
        for (FireVisualBand band : FireVisualBand.values())
            cells.put(band, new LinkedHashMap<>());
    }

    public synchronized void accept(final ClientboundFireStatePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (payload == null || !payload.isWellFormed() || !ensureCurrentLevel(level)) {
            GpuParticleEngine.recordFirePacket(false, false, 0, storedCellCount());
            return;
        }
        ClientboundFireStatePayload accepted = payload;
        if (payload.pageCount() > 1) {
            if (payload.generation() <= highestGeneration) {
                GpuParticleEngine.recordFirePacket(false, true, 0, storedCellCount());
                return;
            }
            if (pendingPages == null || !pendingPages.matches(payload)) {
                pendingPages = new PendingPageTransaction(payload);
            }
            pendingPages.accept(payload);
            if (!pendingPages.complete()) {
                GpuParticleEngine.recordFirePacket(true, false,
                    payload.cells().size(), storedCellCount());
                return;
            }
            accepted = pendingPages.merge();
            pendingPages = null;
        } else if (payload.generation() <= highestGeneration) {
            GpuParticleEngine.recordFirePacket(false, true, 0, storedCellCount());
            return;
        } else if (pendingPages != null
            && payload.generation() >= pendingPages.generation) {
            pendingPages = null;
        }
        highestGeneration = accepted.generation();
        snapshotTick = Long.MIN_VALUE;
        long receivedAt = visualClock.now();
        visualClock.synchronizeServer(accepted.serverGameTime());
        long visualServerTime = visualClock.mapServerTime(accepted.serverGameTime());
        acceptCells(accepted, receivedAt);

        HashSet<Long> receivedEmbers = new HashSet<>(accepted.embers().size());
        for (ClientboundFireStatePayload.EmberEntry entry : accepted.embers()) {
            receivedEmbers.add(entry.id());
            Vec3 incoming = new Vec3(entry.x(), entry.y(), entry.z());
            Vec3 velocity = new Vec3(entry.velocityX(), entry.velocityY(), entry.velocityZ());
            Vec3 wind = new Vec3(entry.windX(), entry.windY(), entry.windZ());
            EmberVisual visual = embers.get(entry.id());
            if (visual == null) embers.put(entry.id(), new EmberVisual(entry.id(), incoming,
                velocity, wind, entry.intensity(), entry.seed(),
                visualClock.mapServerTime(entry.startGameTime()), entry.lifetime(),
                visualServerTime, receivedAt));
            else visual.accept(incoming, velocity, wind, entry.intensity(), entry.seed(),
                entry.lifetime(), visualServerTime, receivedAt);
        }
        if (accepted.emberComplete()) embers.keySet().removeIf(id -> !receivedEmbers.contains(id));
        GpuParticleEngine.recordFirePacket(true, false,
            accepted.cells().size(), storedCellCount());
    }

    private void acceptCells(final ClientboundFireStatePayload payload,
        final long receivedAt) {
        RepresentationRelationIndex relationships = RepresentationRelationIndex.from(cells,
            retiringCells);
        for (long removedId : payload.removedCellIds()) {
            for (FireVisualBand band : FireVisualBand.values()) {
                CellVisual removed = cells.get(band).remove(removedId);
                if (removed == null) continue;
                BandCellKey key = new BandCellKey(band, removedId);
                relationships.remove(removed.cell);
                CellVisual previousRetirement = retiringCells.remove(key);
                if (previousRetirement != null) relationships.remove(previousRetirement.cell);
                CellVisual smoke = removed.retireSmoke(receivedAt);
                if (smoke != null) {
                    retiringCells.put(key, smoke);
                    relationships.add(smoke.cell);
                }
            }
        }
        EnumMap<FireVisualBand, List<FireVisualCell>> incoming =
            new EnumMap<>(FireVisualBand.class);
        for (FireVisualBand band : FireVisualBand.values()) incoming.put(band,
            new ArrayList<>());
        for (ClientboundFireStatePayload.CellEntry entry : payload.cells()) {
            FireVisualCell cell = entry.toCell();
            incoming.get(cell.band()).add(cell);
        }

        for (FireVisualBand band : FireVisualBand.values()) {
            LinkedHashMap<Long, CellVisual> current = cells.get(band);
            List<FireVisualCell> bandCells = incoming.get(band);
            boolean complete = (payload.completeBandMask() & band.mask()) != 0;
            if (!complete) {
                for (FireVisualCell cell : bandCells) {
                    BandCellKey key = new BandCellKey(band, cell.id());
                    CellVisual previousRetirement = retiringCells.remove(key);
                    if (previousRetirement != null) relationships.remove(previousRetirement.cell);
                    CellVisual previous = current.get(cell.id());
                    CellVisual accepted = previous == null
                        ? relatedRepresentation(relationships, cell)
                            ? CellVisual.fadeIn(withVisualIgnition(cell), receivedAt)
                            : CellVisual.fadeIn(withVisualIgnition(cell), receivedAt,
                                NEW_CELL_FADE_TICKS)
                        : previous.accept(cell, receivedAt);
                    current.put(cell.id(), accepted);
                    if (previous != null) relationships.remove(previous.cell);
                    relationships.add(accepted.cell);
                }
                continue;
            }

            LinkedHashMap<Long, CellVisual> replacement = new LinkedHashMap<>();
            HashSet<Long> incomingIds = new HashSet<>(bandCells.size());
            for (FireVisualCell cell : bandCells) incomingIds.add(cell.id());
            for (CellVisual previous : current.values()) {
                if (incomingIds.contains(previous.cell.id())) continue;
                BandCellKey key = new BandCellKey(band, previous.cell.id());
                CellVisual previousRetirement = retiringCells.remove(key);
                if (previousRetirement != null) relationships.remove(previousRetirement.cell);
                CellVisual smoke = previous.retireSmoke(receivedAt);
                if (smoke != null) {
                    retiringCells.put(key, smoke);
                    relationships.add(smoke.cell);
                }
            }
            for (FireVisualCell cell : bandCells) {
                BandCellKey key = new BandCellKey(band, cell.id());
                CellVisual previousRetirement = retiringCells.remove(key);
                if (previousRetirement != null) relationships.remove(previousRetirement.cell);
                CellVisual previous = current.get(cell.id());
                replacement.put(cell.id(), previous != null
                    ? previous.accept(cell, receivedAt)
                    : relatedRepresentation(relationships, cell)
                        ? CellVisual.fadeIn(withVisualIgnition(cell), receivedAt)
                        : CellVisual.fadeIn(withVisualIgnition(cell), receivedAt,
                            NEW_CELL_FADE_TICKS));
            }
            for (CellVisual previous : current.values()) relationships.remove(previous.cell);
            current.clear();
            current.putAll(replacement);
            for (CellVisual accepted : replacement.values()) relationships.add(accepted.cell);
        }
    }

    private static boolean relatedRepresentation(final RepresentationRelationIndex relationships,
        final FireVisualCell incoming) {
        return relationships.relatedTo(incoming);
    }

    static boolean related(final FireVisualCell left, final FireVisualCell right) {
        return left.parentId() == right.id() || right.parentId() == left.id()
            || left.parentId() > 0L && left.parentId() == right.parentId();
    }

    /**
     * Tracks the same relationship predicate as the live cell maps while one payload is
     * accepted. Counts are required because a complete-band replacement temporarily keeps
     * the outgoing active cell and its retiring smoke representation at the same time.
     */
    static final class RepresentationRelationIndex {
        private final Map<Long, Integer> ids = new HashMap<>();
        private final Map<Long, Integer> parentIds = new HashMap<>();

        static RepresentationRelationIndex from(
            final EnumMap<FireVisualBand, LinkedHashMap<Long, CellVisual>> active,
            final Map<BandCellKey, CellVisual> retiring) {
            RepresentationRelationIndex result = new RepresentationRelationIndex();
            for (LinkedHashMap<Long, CellVisual> bandCells : active.values()) {
                for (CellVisual visual : bandCells.values()) result.add(visual.cell);
            }
            for (CellVisual visual : retiring.values()) result.add(visual.cell);
            return result;
        }

        void add(final FireVisualCell cell) {
            increment(ids, cell.id());
            increment(parentIds, cell.parentId());
        }

        void remove(final FireVisualCell cell) {
            decrement(ids, cell.id());
            decrement(parentIds, cell.parentId());
        }

        boolean relatedTo(final FireVisualCell incoming) {
            long parentId = incoming.parentId();
            return ids.containsKey(parentId) || parentIds.containsKey(incoming.id())
                || parentId > 0L && parentIds.containsKey(parentId);
        }

        private static void increment(final Map<Long, Integer> counts, final long key) {
            counts.merge(key, 1, Integer::sum);
        }

        private static void decrement(final Map<Long, Integer> counts, final long key) {
            Integer count = counts.get(key);
            if (count == null) throw new IllegalStateException("Missing fire relation index key");
            if (count == 1) counts.remove(key);
            else counts.put(key, count - 1);
        }
    }

    public synchronized void acceptImpulse(final ClientboundFireWindImpulsePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (payload == null || !payload.isWellFormed() || !ensureCurrentLevel(level)) return;
        while (windImpulses.size() >= 32) windImpulses.removeFirst();
        FireWindImpulse incoming = payload.impulse();
        windImpulses.addLast(new FireWindImpulse(incoming.center(), incoming.radius(),
            incoming.strength(), visualClock.mapServerTime(incoming.startTick()),
            incoming.durationTicks(), incoming.nuclear()));
    }

    public synchronized void tick(final Minecraft client) {
        if (!ensureCurrentLevel(client.level)) return;
        snapshotTick = Long.MIN_VALUE;
        ClientSmokeFlowField.INSTANCE.tick(client.level);
        long now = visualClock.advance(client.level.getGameTime());
        windImpulses.removeIf(impulse -> impulse.expired(now));
        for (LinkedHashMap<Long, CellVisual> bandCells : cells.values())
            bandCells.values().removeIf(cell -> now - cell.lastSeenClientTick > EXPIRY_TICKS);
        retiringCells.values().removeIf(cell -> cell.transitionWeight(now) <= 0.0F);
        if ((now & 15L) == 0L) {
            HashSet<HierarchyLodKey> active = new HashSet<>();
            for (LinkedHashMap<Long, CellVisual> bandCells : cells.values()) {
                for (CellVisual visual : bandCells.values()) {
                    active.add(HierarchyLodKey.of(visual.cell));
                }
            }
            for (CellVisual visual : retiringCells.values()) {
                active.add(HierarchyLodKey.of(visual.cell));
            }
            lodLevels.keySet().removeIf(key -> !active.contains(key));
        }
        for (Map.Entry<FireVisualBand, LinkedHashMap<Long, CellVisual>> entry : cells.entrySet()) {
            LinkedHashMap<Long, CellVisual> bandCells = entry.getValue();
            for (Map.Entry<Long, CellVisual> visual : bandCells.entrySet()) {
                CellVisual cell = visual.getValue();
                visual.setValue(cell.advanceWind(now, effectiveWind(cell.cell.centroid(),
                    cell.baseWind.windAt(now), now)));
            }
        }
        for (Map.Entry<BandCellKey, CellVisual> entry : retiringCells.entrySet()) {
            CellVisual cell = entry.getValue();
            entry.setValue(cell.advanceWind(now, effectiveWind(cell.cell.centroid(),
                cell.baseWind.windAt(now), now)));
        }
        Iterator<EmberVisual> emberIterator = embers.values().iterator();
        while (emberIterator.hasNext()) {
            EmberVisual ember = emberIterator.next();
            if (now - ember.lastSeenClientTick > 24
                || now - ember.startGameTime > ember.lifetime + 4L) emberIterator.remove();
            else ember.simulate(now);
        }
    }

    public synchronized List<VisualCell> snapshot(final ClientLevel level) {
        if (level == null || activeLevel != level) return List.of();
        long now = visualClock.now();
        // Simulation interpolation and transition weights use integer game ticks.
        // Reuse their immutable result between ticks, including high-FPS frames.
        if (snapshotTick == now) return cachedSnapshot;
        ArrayList<VisualCell> result = new ArrayList<>(storedCellCount()
            + retiringCells.size());
        for (LinkedHashMap<Long, CellVisual> bandCells : cells.values()) {
            for (CellVisual cell : bandCells.values()) cell.appendSnapshots(result, now);
        }
        for (CellVisual cell : retiringCells.values()) {
            float weight = cell.transitionWeight(now);
            if (weight > 0.0F) cell.appendSnapshots(result, now);
        }
        cachedSnapshot = List.copyOf(result);
        snapshotTick = now;
        return cachedSnapshot;
    }

    public synchronized Map<FireVisualBand, Integer> cellCounts(final ClientLevel level) {
        if (level == null || activeLevel != level) return Map.of();
        EnumMap<FireVisualBand, Integer> counts = new EnumMap<>(FireVisualBand.class);
        for (FireVisualBand band : FireVisualBand.values()) counts.put(band,
            cells.get(band).size());
        return Map.copyOf(counts);
    }

    public synchronized List<VisualEmber> emberSnapshot(final ClientLevel level) {
        if (level == null || activeLevel != level) return List.of();
        return embers.values().stream().map(EmberVisual::snapshot).toList();
    }

    /** Monotonic fire-render time; partial ticks only interpolate its next tick. */
    public synchronized double renderTime(final ClientLevel level, final float partialTick) {
        if (level == null || activeLevel != level) return 0.0;
        float partial = Float.isFinite(partialTick) ? Math.max(0.0F, Math.min(1.0F, partialTick))
            : 0.0F;
        return visualClock.now() + partial;
    }

    /** Monotonic tick used for fire renderer cache expiry. */
    public synchronized long visualTick(final ClientLevel level) {
        return level != null && activeLevel == level ? visualClock.now() : Long.MIN_VALUE;
    }

    public synchronized Vec3 effectiveWind(final Vec3 position, final Vec3 baseWind,
        final double gameTime) {
        Vec3 result = baseWind == null ? Vec3.ZERO : baseWind;
        if (position == null || !position.isFinite()) return result;
        for (FireWindImpulse impulse : windImpulses)
            result = result.add(impulse.sample(position, gameTime));
        double length = result.length();
        return length > 2.5 ? result.scale(2.5 / length) : result;
    }

    public synchronized int lodLevel(final ClientLevel level, final FireVisualCell cell,
        final double projectedDiameter) {
        if (level == null || activeLevel != level) {
            return com.andye.warmod.fire.FireVisualLodPolicy.level(projectedDiameter);
        }
        HierarchyLodKey key = HierarchyLodKey.of(cell);
        int previous = lodLevels.getOrDefault(key,
            com.andye.warmod.fire.FireVisualLodPolicy.level(projectedDiameter));
        int next = com.andye.warmod.fire.FireVisualLodPolicy.level(
            projectedDiameter, previous);
        lodLevels.put(key, next);
        if (next != previous) {
            com.andye.warmod.diagnostics.WarModPerformanceDiagnostics.add(
                com.andye.warmod.diagnostics.WarModPerformanceDiagnostics.Gauge
                    .FIRE_LOD_TRANSITIONS, 1L);
        }
        return next;
    }

    public synchronized void clear() {
        snapshotTick = Long.MIN_VALUE;
        cachedSnapshot = List.of();
        for (LinkedHashMap<Long, CellVisual> bandCells : cells.values()) bandCells.clear();
        retiringCells.clear(); embers.clear(); windImpulses.clear(); lodLevels.clear();
        pendingPages = null;
        ClientSmokeFlowField.INSTANCE.clear();
        highestGeneration = Long.MIN_VALUE;
        activeLevel = null;
        visualClock.reset();
    }

    private boolean ensureCurrentLevel(final ClientLevel level) {
        if (level == null) { clear(); return false; }
        if (activeLevel != level) {
            snapshotTick = Long.MIN_VALUE;
            cachedSnapshot = List.of();
            for (LinkedHashMap<Long, CellVisual> bandCells : cells.values()) bandCells.clear();
            retiringCells.clear(); embers.clear(); windImpulses.clear(); lodLevels.clear();
            pendingPages = null;
            ClientSmokeFlowField.INSTANCE.clear();
            highestGeneration = Long.MIN_VALUE;
            activeLevel = level;
            visualClock.reset(level.getGameTime());
        }
        return true;
    }

    /**
     * Server timestamps are mapped once at birth into a monotonically advancing
     * presentation timeline. Existing visuals retain their original mapped
     * origins, so a time-sync correction cannot restart their particle cycles.
     */
    static final class VisualClock {
        private static final long MAX_SERVER_OFFSET_STEP = 1L;
        private boolean initialized;
        private boolean synchronizedServer;
        private long visualTime;
        private long serverOffset;

        void reset() {
            initialized = false;
            synchronizedServer = false;
            visualTime = 0L;
            serverOffset = 0L;
        }

        void reset(final long initialWorldTime) {
            reset();
            initialized = true;
            // Preserve an intelligible diagnostic epoch, then stop following
            // world time. Client time-sync is permitted to move backwards.
            visualTime = initialWorldTime;
        }

        long now() { return visualTime; }

        long advance(final long observedWorldTime) {
            if (!initialized) reset(observedWorldTime);
            if (visualTime < Long.MAX_VALUE) visualTime++;
            return visualTime;
        }

        void synchronizeServer(final long serverTime) {
            long desiredOffset = saturatedSubtract(visualTime, serverTime);
            if (!synchronizedServer) {
                serverOffset = desiredOffset;
                synchronizedServer = true;
                return;
            }
            long difference = saturatedSubtract(desiredOffset, serverOffset);
            if (difference > 0L) serverOffset = saturatedAdd(serverOffset,
                Math.min(MAX_SERVER_OFFSET_STEP, difference));
            else if (difference < 0L) serverOffset = saturatedSubtract(serverOffset,
                Math.min(MAX_SERVER_OFFSET_STEP, -difference));
        }

        long mapServerTime(final long serverTime) {
            if (!synchronizedServer) synchronizeServer(serverTime);
            return saturatedAdd(serverTime, serverOffset);
        }

        private static long saturatedAdd(final long left, final long right) {
            if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
            if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
            return left + right;
        }

        private static long saturatedSubtract(final long left, final long right) {
            if (right == Long.MIN_VALUE) return left >= 0L ? Long.MAX_VALUE : left - right;
            return saturatedAdd(left, -right);
        }
    }

    private FireVisualCell withVisualIgnition(final FireVisualCell cell) {
        long visualIgnition = visualClock.mapServerTime(cell.ignitionGameTime());
        return new FireVisualCell(cell.id(), cell.parentId(), cell.band(), cell.cellSize(),
            cell.cellX(), cell.cellY(), cell.cellZ(), cell.centroid(), cell.extents(),
            cell.occupancyMask(), cell.flameEnergy(), cell.flameEnvelopeHeight(),
            cell.smokeMass(), cell.maximumHeat(), cell.averageIntensity(), cell.coveredArea(),
            cell.clumpStrength(), cell.wind(), cell.hostCount(), cell.seed(),
            cell.dominantFace(), cell.phase(), visualIgnition);
    }

    private int storedCellCount() {
        int total = 0;
        for (LinkedHashMap<Long, CellVisual> bandCells : cells.values())
            total += bandCells.size();
        return total;
    }

    private static float lerp(final float from, final float to, final float amount) {
        return from + (to - from) * amount;
    }

    public record VisualCell(FireVisualCell cell, float transitionWeight,
        long lastSeenClientTick, WindHistory windHistory) { }
    public record VisualEmber(long id, Vec3 position, Vec3 velocity, Vec3 wind,
        float intensity, long seed, long startGameTime, int lifetime,
        long serverSampleGameTime, long lastSeenClientTick, List<EmberTrailSample> trail) { }
    public record EmberTrailSample(Vec3 position, Vec3 wind, long gameTime) { }

    private record BandCellKey(FireVisualBand band, long id) { }
    private record HierarchyLodKey(int x, int y, int z) {
        private static HierarchyLodKey of(final FireVisualCell cell) {
            int rootSize = FireVisualBand.HORIZON.preferredCellSize();
            int cellVerticalSize = 1;
            int rootVerticalSize = 1;
            int worldX = cell.cellX() * cell.cellSize();
            int worldY = cell.cellY() * cellVerticalSize;
            int worldZ = cell.cellZ() * cell.cellSize();
            return new HierarchyLodKey(
                Math.floorDiv(worldX, rootSize),
                Math.floorDiv(worldY, rootVerticalSize),
                Math.floorDiv(worldZ, rootSize));
        }
    }

    static final class PendingPageTransaction {
        private final long generation;
        private final long transactionId;
        private final int pageCount;
        private final ClientboundFireStatePayload[] pages;
        private int received;

        PendingPageTransaction(final ClientboundFireStatePayload first) {
            generation = first.generation();
            transactionId = first.pageTransactionId();
            pageCount = first.pageCount();
            pages = new ClientboundFireStatePayload[pageCount];
        }

        boolean matches(final ClientboundFireStatePayload page) {
            return page.generation() == generation
                && page.pageTransactionId() == transactionId
                && page.pageCount() == pageCount;
        }

        void accept(final ClientboundFireStatePayload page) {
            if (!matches(page) || pages[page.pageIndex()] != null) return;
            pages[page.pageIndex()] = page;
            received++;
        }

        boolean complete() { return received == pageCount; }

        ClientboundFireStatePayload merge() {
            ArrayList<ClientboundFireStatePayload.CellEntry> cells = new ArrayList<>();
            ArrayList<Long> removed = new ArrayList<>();
            ArrayList<ClientboundFireStatePayload.EmberEntry> embers = new ArrayList<>();
            int completeMask = 0;
            boolean emberComplete = false;
            long serverGameTime = 0L;
            for (ClientboundFireStatePayload page : pages) {
                if (page == null) throw new IllegalStateException("Incomplete fire page transaction");
                serverGameTime = Math.max(serverGameTime, page.serverGameTime());
                completeMask |= page.completeBandMask();
                emberComplete |= page.emberComplete();
                cells.addAll(page.cells());
                removed.addAll(page.removedCellIds());
                embers.addAll(page.embers());
            }
            return new ClientboundFireStatePayload(serverGameTime, generation,
                transactionId, 0, 1, completeMask, List.copyOf(cells),
                List.copyOf(removed), emberComplete, List.copyOf(embers));
        }
    }

    /**
     * Fixed, client-thread-owned wind timeline. Prefix integrals make each
     * particle query two binary searches, rather than walking its full life
     * through every sampled wind value. No per-tick list copying is required.
     */
    public static final class WindHistory {
        private static final int HISTORY_TICKS = 192;
        private static final int RETARGET_TICKS = 6;
        private static final int MOTION_CAPACITY = HISTORY_TICKS + 8;
        private static final int BASE_CAPACITY = 8;
        private final int capacity;
        private final long[] ticks;
        private final double[] x, y, z;
        private final double[] integralX, integralY, integralZ;
        private int first;
        private int count;

        static WindHistory initial(final long now, final Vec3 wind) {
            WindHistory history = new WindHistory(MOTION_CAPACITY);
            history.append(now, safeWind(wind));
            return history;
        }

        private static WindHistory base(final long now, final Vec3 wind) {
            WindHistory history = new WindHistory(BASE_CAPACITY);
            history.append(now, safeWind(wind));
            return history;
        }

        private WindHistory(final int capacity) {
            this.capacity = capacity;
            ticks = new long[capacity];
            x = new double[capacity]; y = new double[capacity]; z = new double[capacity];
            integralX = new double[capacity]; integralY = new double[capacity];
            integralZ = new double[capacity];
        }

        void retarget(final long now, final Vec3 target) {
            if (count > 0 && now < ticks[first]) return;
            Vec3 current = windAt(now);
            truncateAfter(now);
            append(now, current);
            append(now + RETARGET_TICKS, safeWind(target));
            prune(now);
        }

        void sample(final long now, final Vec3 sampled) {
            if (count == 0) {
                append(now, safeWind(sampled));
                return;
            }
            // Motion samples may be repeated or delivered after a client clock
            // correction. They are observations of a future velocity, never a
            // licence to rewrite a path that has already been rendered.
            if (now < ticks[physical(count - 1)]) return;
            Vec3 current = windAt(now);
            truncateAfter(now);
            append(now, current);
            // Sampling is also forward-only: a tick's newly observed impulse
            // becomes the next tick's velocity, never a correction to the
            // already rendered interval ending at {@code now}.
            append(now + 1L, safeWind(sampled));
            prune(now);
        }

        /** Integral in block displacement units, not a current wind times age. */
        public Vec3 displacement(final double from, final double to) {
            if (!Double.isFinite(from) || !Double.isFinite(to) || to <= from)
                return Vec3.ZERO;
            return new Vec3(displacementX(from, to), displacementY(from, to),
                displacementZ(from, to));
        }

        public double displacementX(final double from, final double to) {
            return displacement(from, to, integralX, x);
        }

        public double displacementY(final double from, final double to) {
            return displacement(from, to, integralY, y);
        }

        public double displacementZ(final double from, final double to) {
            return displacement(from, to, integralZ, z);
        }

        public Vec3 windAt(final double time) {
            return new Vec3(valueAt(time, x), valueAt(time, y), valueAt(time, z));
        }

        private double displacement(final double from, final double to,
            final double[] integral, final double[] values) {
            if (!Double.isFinite(from) || !Double.isFinite(to) || to <= from) return 0.0;
            return integralAt(to, integral, values) - integralAt(from, integral, values);
        }

        private double integralAt(final double time, final double[] integral,
            final double[] values) {
            int index = floorIndex(time);
            int physical = physical(index);
            long tick = ticks[physical];
            if (index == count - 1 || time <= tick) {
                return integral[physical] + values[physical] * (time - tick);
            }
            int next = physical(index + 1);
            double span = ticks[next] - tick;
            double elapsed = time - tick;
            double startValue = values[physical];
            double value = startValue + (values[next] - startValue) * elapsed / span;
            return integral[physical] + (startValue + value) * elapsed * 0.5;
        }

        private double valueAt(final double time, final double[] values) {
            int index = floorIndex(time);
            int physical = physical(index);
            if (index == count - 1 || time <= ticks[physical]) return values[physical];
            int next = physical(index + 1);
            double span = ticks[next] - ticks[physical];
            return values[physical] + (values[next] - values[physical])
                * (time - ticks[physical]) / span;
        }

        private int floorIndex(final double time) {
            if (count == 0) throw new IllegalStateException("Empty wind history");
            if (time <= ticks[first]) return 0;
            int low = 0, high = count - 1;
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (ticks[physical(middle)] <= time) low = middle;
                else high = middle - 1;
            }
            return low;
        }

        private void truncateAfter(final long tick) {
            while (count > 1 && ticks[physical(count - 1)] > tick) count--;
        }

        private void append(final long tick, final Vec3 wind) {
            if (count == 0) {
                ticks[0] = tick;
                x[0] = wind.x; y[0] = wind.y; z[0] = wind.z;
                integralX[0] = integralY[0] = integralZ[0] = 0.0;
                count = 1;
                return;
            }
            int last = physical(count - 1);
            if (tick < ticks[last]) return;
            if (tick == ticks[last]) {
                x[last] = wind.x; y[last] = wind.y; z[last] = wind.z;
                if (count > 1) recomputeIntegral(last, physical(count - 2));
                return;
            }
            if (count == capacity) {
                first = physical(1);
                count--;
                last = physical(count - 1);
            }
            int next = physical(count);
            ticks[next] = tick;
            x[next] = wind.x; y[next] = wind.y; z[next] = wind.z;
            recomputeIntegral(next, last);
            count++;
        }

        private void recomputeIntegral(final int current, final int previous) {
            double elapsed = ticks[current] - ticks[previous];
            integralX[current] = integralX[previous] + (x[previous] + x[current]) * elapsed * 0.5;
            integralY[current] = integralY[previous] + (y[previous] + y[current]) * elapsed * 0.5;
            integralZ[current] = integralZ[previous] + (z[previous] + z[current]) * elapsed * 0.5;
        }

        private void prune(final long now) {
            long cutoff = now - HISTORY_TICKS;
            while (count > 1 && ticks[physical(1)] <= cutoff) {
                first = physical(1);
                count--;
            }
        }

        private int physical(final int logical) { return (first + logical) % capacity; }

        private static Vec3 safeWind(final Vec3 wind) {
            return wind == null || !wind.isFinite() ? Vec3.ZERO : wind;
        }
    }

    private static final class CellVisual {
        private final FireVisualCell cell;
        private final WindHistory baseWind;
        private final WindHistory motionWind;
        private final GeometryFade geometryFade;
        private final long transitionStartTick;
        private final float transitionStartWeight;
        private final float transitionTargetWeight;
        private final long lastSeenClientTick;
        private final int transitionTicks;

        private CellVisual(final FireVisualCell cell, final WindHistory baseWind,
            final WindHistory motionWind, final GeometryFade geometryFade,
            final long transitionStartTick,
            final float transitionStartWeight, final float transitionTargetWeight,
            final long lastSeenClientTick, final int transitionTicks) {
            this.cell = cell;
            this.baseWind = baseWind;
            this.motionWind = motionWind;
            this.geometryFade = geometryFade;
            this.transitionStartTick = transitionStartTick;
            this.transitionStartWeight = transitionStartWeight;
            this.transitionTargetWeight = transitionTargetWeight;
            this.lastSeenClientTick = lastSeenClientTick;
            this.transitionTicks = Math.max(1, transitionTicks);
        }

        private static CellVisual fadeIn(final FireVisualCell cell, final long now) {
            return fadeIn(cell, now, REPRESENTATION_TRANSITION_TICKS);
        }

        private static CellVisual fadeIn(final FireVisualCell cell, final long now,
            final int transitionTicks) {
            WindHistory baseWind = WindHistory.base(now, cell.wind());
            WindHistory motionWind = WindHistory.initial(now, cell.wind());
            return new CellVisual(cell, baseWind, motionWind, null, now, 0.0F, 1.0F, now,
                transitionTicks);
        }

        private CellVisual accept(final FireVisualCell incoming, final long now) {
            float currentWeight = transitionWeight(now);
            baseWind.retarget(now, incoming.wind());
            boolean changedTopology = cell.occupancyMask() != incoming.occupancyMask()
                || cell.dominantFace() != incoming.dominantFace()
                || cell.cellSize() != incoming.cellSize();
            GeometryFade fade = changedTopology
                ? new GeometryFade(cell, now) : geometryFade;
            FireVisualCell smoothed = new FireVisualCell(incoming.id(), incoming.parentId(),
                incoming.band(),
                incoming.cellSize(), incoming.cellX(), incoming.cellY(), incoming.cellZ(),
                cell.centroid().lerp(incoming.centroid(), 0.44),
                cell.extents().lerp(incoming.extents(), 0.44),
                incoming.occupancyMask(), lerp(cell.flameEnergy(), incoming.flameEnergy(), 0.44F),
                lerp(cell.flameEnvelopeHeight(), incoming.flameEnvelopeHeight(), 0.44F),
                lerp(cell.smokeMass(), incoming.smokeMass(), 0.40F),
                lerp(cell.maximumHeat(), incoming.maximumHeat(), 0.44F),
                lerp(cell.averageIntensity(), incoming.averageIntensity(), 0.44F),
                lerp(cell.coveredArea(), incoming.coveredArea(), 0.40F),
                lerp(cell.clumpStrength(), incoming.clumpStrength(), 0.40F),
                baseWind.windAt(now), incoming.hostCount(),
                cell.seed(), incoming.dominantFace(), incoming.phase(),
                // This ID already has a mapped visual birth time. Keep it:
                // incoming server timestamps can be rebased by a time-sync.
                cell.ignitionGameTime());
            return new CellVisual(smoothed, baseWind, motionWind, fade,
                now, currentWeight, 1.0F, now,
                REPRESENTATION_TRANSITION_TICKS);
        }

        private CellVisual retireSmoke(final long now) {
            if (cell.smokeMass() <= 0.012F && cell.flameEnergy() <= 0.012F) return null;
            int lifetime = cell.smokeMass() > 0.012F
                ? SMOKE_RETIRE_TICKS : REPRESENTATION_TRANSITION_TICKS;
            return new CellVisual(cell, baseWind, motionWind, geometryFade,
                now, transitionWeight(now), 0.0F,
                lastSeenClientTick, lifetime);
        }

        private CellVisual advanceWind(final long now, final Vec3 effectiveWind) {
            motionWind.sample(now, effectiveWind);
            return this;
        }

        private void appendSnapshots(final List<VisualCell> result, final long now) {
            float weight = transitionWeight(now);
            if (weight <= 0.0F) return;
            GeometryFade fade = geometryFade;
            float incomingWeight = 1.0F;
            if (fade != null) {
                float previousWeight = fade.weight(now);
                incomingWeight -= previousWeight;
                if (previousWeight > 0.001F) result.add(new VisualCell(fade.cell,
                    weight * previousWeight, lastSeenClientTick, motionWind));
            }
            if (incomingWeight > 0.001F) result.add(new VisualCell(displayCell(now),
                weight * incomingWeight, lastSeenClientTick, motionWind));
        }

        private FireVisualCell displayCell(final long now) {
            if (transitionTargetWeight != 0.0F) return cell;
            float flameProgress = Math.max(0.0F, Math.min(1.0F,
                (now - transitionStartTick) / (float)REPRESENTATION_TRANSITION_TICKS));
            float flameWeight = 1.0F - smoothStep(flameProgress);
            return new FireVisualCell(cell.id(), cell.parentId(), cell.band(), cell.cellSize(),
                cell.cellX(), cell.cellY(), cell.cellZ(), cell.centroid(), cell.extents(),
                cell.occupancyMask(), cell.flameEnergy() * flameWeight,
                cell.flameEnvelopeHeight(), cell.smokeMass(),
                cell.maximumHeat() * flameWeight,
                cell.averageIntensity() * flameWeight, cell.coveredArea(),
                cell.clumpStrength() * flameWeight, cell.wind(), cell.hostCount(),
                cell.seed(), cell.dominantFace(), flameWeight > 0.0F
                    ? com.andye.warmod.fire.FirePhase.DECAYING
                    : com.andye.warmod.fire.FirePhase.SMOLDERING,
                cell.ignitionGameTime());
        }

        private float transitionWeight(final long now) {
            if (transitionStartWeight == transitionTargetWeight) return transitionTargetWeight;
            float progress = Math.max(0.0F, Math.min(1.0F,
                (now - transitionStartTick) / (float) transitionTicks));
            return lerp(transitionStartWeight, transitionTargetWeight,
                smoothStep(progress));
        }

        private static float smoothStep(final float value) {
            return value * value * (3.0F - 2.0F * value);
        }
    }

    private record GeometryFade(FireVisualCell cell, long startedAt) {
        private float weight(final long now) {
            float progress = Math.max(0.0F, Math.min(1.0F,
                (now - startedAt) / (float) TOPOLOGY_TRANSITION_TICKS));
            return 1.0F - CellVisual.smoothStep(progress);
        }
    }

    /** Eight history samples make the renderer follow the real local path. */
    private static final class EmberVisual {
        private static final int MAX_TRAIL_SAMPLES = 8;
        private static final double MAX_CORRECTION_PER_TICK = 0.22;
        private final long id;
        private Vec3 position;
        private Vec3 velocity;
        private Vec3 wind;
        private float intensity;
        private long seed;
        private long startGameTime;
        private int lifetime;
        private long serverSampleGameTime;
        private long lastSeenClientTick;
        private long simulatedGameTime;
        private Vec3 pendingCorrection = Vec3.ZERO;
        private final ArrayDeque<EmberTrailSample> trail = new ArrayDeque<>();

        private EmberVisual(final long id, final Vec3 position, final Vec3 velocity,
            final Vec3 wind, final float intensity, final long seed, final long visualStartGameTime,
            final int lifetime,
            final long serverSampleGameTime, final long receivedAt) {
            this.id = id; this.position = position; this.velocity = velocity; this.wind = wind;
            this.intensity = intensity; this.seed = seed; this.startGameTime = visualStartGameTime;
            this.lifetime = lifetime; this.serverSampleGameTime = serverSampleGameTime;
            this.lastSeenClientTick = receivedAt;
            // The received position belongs to the server sample, not the local
            // receive tick. Advance it on the next client tick without first
            // discarding packet latency from the authoritative trajectory.
            this.simulatedGameTime = serverSampleGameTime;
            appendTrail(receivedAt);
        }

        private void accept(final Vec3 incomingPosition, final Vec3 incomingVelocity,
            final Vec3 incomingWind, final float incomingIntensity, final long incomingSeed,
            final int incomingLifetime, final long serverTime,
            final long receivedAt) {
            Vec3 authoritativeNow = predictFromSample(incomingPosition, incomingVelocity,
                incomingWind, incomingSeed, startGameTime, incomingLifetime, serverTime,
                receivedAt);
            Vec3 error = authoritativeNow.subtract(position);
            // A delayed sample may be wrong by more than a block after a collision
            // or a busy client. Correct at a bounded speed instead of teleporting
            // the ember and clearing its smoke trail every network refresh.
            pendingCorrection = pendingCorrection.add(error).scale(0.55);
            double correctionLength = pendingCorrection.length();
            if (correctionLength > 3.0)
                pendingCorrection = pendingCorrection.scale(3.0 / correctionLength);
            velocity = velocity.lerp(incomingVelocity, 0.42);
            // Ember wind is the exact server simulation input at serverTime;
            // unlike cell ambience it must not receive client impulses again.
            wind = incomingWind; intensity = incomingIntensity;
            // A stable ember keeps its mapped birth origin for the same reason
            // as a stable cell: a changed server offset must not restart it.
            seed = incomingSeed; lifetime = incomingLifetime;
            serverSampleGameTime = serverTime; lastSeenClientTick = receivedAt;
            simulatedGameTime = Math.max(simulatedGameTime, serverTime);
            if (trail.isEmpty()) appendTrail(receivedAt);
        }

        private void simulate(final long now) {
            while (simulatedGameTime < now) {
                simulatedGameTime++;
                double progress = Math.min(1.0, Math.max(0.0,
                    (simulatedGameTime - startGameTime) / (double) Math.max(1, lifetime)));
                velocity = FireSimulationManager.stepEmberVelocity(velocity, wind, seed,
                    startGameTime, simulatedGameTime, progress);
                position = position.add(velocity);
                double correctionLength = pendingCorrection.length();
                if (correctionLength > 1.0E-6) {
                    Vec3 correction = pendingCorrection.scale(Math.min(1.0,
                        MAX_CORRECTION_PER_TICK / correctionLength));
                    position = position.add(correction);
                    pendingCorrection = pendingCorrection.subtract(correction);
                }
                appendTrail(simulatedGameTime);
            }
        }

        private static Vec3 predictFromSample(final Vec3 position, final Vec3 velocity,
            final Vec3 wind, final long seed, final long startTime, final int lifetime,
            final long sampleTime, final long targetTime) {
            Vec3 predictedPosition = position;
            Vec3 predictedVelocity = velocity;
            long cursor = sampleTime;
            // Packet latency normally spans only a few ticks. Bound prediction so
            // a bad clock sample cannot make receive work proportional to a stall.
            long end = Math.min(targetTime, sampleTime + 12L);
            while (cursor < end) {
                cursor++;
                double progress = Math.min(1.0, Math.max(0.0,
                    (cursor - startTime) / (double) Math.max(1, lifetime)));
                predictedVelocity = FireSimulationManager.stepEmberVelocity(predictedVelocity,
                    wind, seed, startTime, cursor, progress);
                predictedPosition = predictedPosition.add(predictedVelocity);
            }
            return predictedPosition;
        }

        private void appendTrail(final long gameTime) {
            trail.addLast(new EmberTrailSample(position, wind, gameTime));
            while (trail.size() > MAX_TRAIL_SAMPLES) trail.removeFirst();
        }

        private VisualEmber snapshot() {
            return new VisualEmber(id, position, velocity, wind, intensity, seed,
                startGameTime, lifetime, serverSampleGameTime, lastSeenClientTick,
                List.copyOf(trail));
        }
    }
}
