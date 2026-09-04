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
    private long highestGeneration = Long.MIN_VALUE;

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
        long receivedAt = level.getGameTime();
        acceptCells(accepted, receivedAt);

        HashSet<Long> receivedEmbers = new HashSet<>(accepted.embers().size());
        for (ClientboundFireStatePayload.EmberEntry entry : accepted.embers()) {
            receivedEmbers.add(entry.id());
            Vec3 incoming = new Vec3(entry.x(), entry.y(), entry.z());
            Vec3 velocity = new Vec3(entry.velocityX(), entry.velocityY(), entry.velocityZ());
            Vec3 wind = new Vec3(entry.windX(), entry.windY(), entry.windZ());
            EmberVisual visual = embers.get(entry.id());
            if (visual == null) embers.put(entry.id(), new EmberVisual(entry.id(), incoming,
                velocity, wind, entry.intensity(), entry.seed(), entry.startGameTime(),
                entry.lifetime(), accepted.serverGameTime(), receivedAt));
            else visual.accept(incoming, velocity, wind, entry.intensity(), entry.seed(),
                entry.startGameTime(), entry.lifetime(), accepted.serverGameTime(), receivedAt);
        }
        if (accepted.emberComplete()) embers.keySet().removeIf(id -> !receivedEmbers.contains(id));
        GpuParticleEngine.recordFirePacket(true, false,
            accepted.cells().size(), storedCellCount());
    }

    private void acceptCells(final ClientboundFireStatePayload payload,
        final long receivedAt) {
        for (long removedId : payload.removedCellIds()) {
            for (FireVisualBand band : FireVisualBand.values()) {
                CellVisual removed = cells.get(band).remove(removedId);
                if (removed == null) continue;
                BandCellKey key = new BandCellKey(band, removedId);
                CellVisual smoke = removed.retireSmoke(receivedAt);
                if (smoke == null) retiringCells.remove(key);
                else retiringCells.put(key, smoke);
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
                    retiringCells.remove(key);
                    CellVisual previous = current.get(cell.id());
                    current.put(cell.id(), previous == null
                        ? relatedRepresentation(cell)
                            ? CellVisual.fadeIn(cell, receivedAt)
                            : CellVisual.immediate(cell, receivedAt)
                        : previous.accept(cell, receivedAt));
                }
                continue;
            }

            LinkedHashMap<Long, CellVisual> replacement = new LinkedHashMap<>();
            HashSet<Long> incomingIds = new HashSet<>(bandCells.size());
            for (FireVisualCell cell : bandCells) incomingIds.add(cell.id());
            for (CellVisual previous : current.values()) {
                if (incomingIds.contains(previous.cell.id())) continue;
                BandCellKey key = new BandCellKey(band, previous.cell.id());
                CellVisual smoke = previous.retireSmoke(receivedAt);
                if (smoke == null) retiringCells.remove(key);
                else retiringCells.put(key, smoke);
            }
            for (FireVisualCell cell : bandCells) {
                BandCellKey key = new BandCellKey(band, cell.id());
                retiringCells.remove(key);
                CellVisual previous = current.get(cell.id());
                replacement.put(cell.id(), previous != null
                    ? previous.accept(cell, receivedAt)
                    : relatedRepresentation(cell) ? CellVisual.fadeIn(cell, receivedAt)
                        : CellVisual.immediate(cell, receivedAt));
            }
            current.clear();
            current.putAll(replacement);
        }
    }

    private boolean relatedRepresentation(final FireVisualCell incoming) {
        for (LinkedHashMap<Long, CellVisual> bandCells : cells.values()) {
            for (CellVisual existing : bandCells.values()) {
                if (related(incoming, existing.cell)) return true;
            }
        }
        for (CellVisual existing : retiringCells.values()) {
            if (related(incoming, existing.cell)) return true;
        }
        return false;
    }

    private static boolean related(final FireVisualCell left, final FireVisualCell right) {
        return left.parentId() == right.id() || right.parentId() == left.id()
            || left.parentId() > 0L && left.parentId() == right.parentId();
    }

    public synchronized void acceptImpulse(final ClientboundFireWindImpulsePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (payload == null || !payload.isWellFormed() || !ensureCurrentLevel(level)) return;
        while (windImpulses.size() >= 32) windImpulses.removeFirst();
        windImpulses.addLast(payload.impulse());
    }

    public synchronized void tick(final Minecraft client) {
        if (!ensureCurrentLevel(client.level)) return;
        ClientSmokeFlowField.INSTANCE.tick(client.level);
        long now = client.level.getGameTime();
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
        Iterator<EmberVisual> emberIterator = embers.values().iterator();
        while (emberIterator.hasNext()) {
            EmberVisual ember = emberIterator.next();
            if (now - ember.lastSeenClientTick > 24
                || now - ember.startGameTime > ember.lifetime + 4L) emberIterator.remove();
            else ember.simulate(now, effectiveWind(ember.position, ember.wind, now));
        }
    }

    public synchronized List<VisualCell> snapshot(final ClientLevel level) {
        if (level == null || activeLevel != level) return List.of();
        long now = level.getGameTime();
        ArrayList<VisualCell> result = new ArrayList<>(storedCellCount()
            + retiringCells.size());
        for (LinkedHashMap<Long, CellVisual> bandCells : cells.values()) {
            for (CellVisual cell : bandCells.values())
                result.add(new VisualCell(cell.displayCell(now), cell.transitionWeight(now),
                    cell.lastSeenClientTick));
        }
        for (CellVisual cell : retiringCells.values()) {
            float weight = cell.transitionWeight(now);
            if (weight > 0.0F) result.add(new VisualCell(cell.displayCell(now), weight,
                cell.lastSeenClientTick));
        }
        return List.copyOf(result);
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
        for (LinkedHashMap<Long, CellVisual> bandCells : cells.values()) bandCells.clear();
        retiringCells.clear(); embers.clear(); windImpulses.clear(); lodLevels.clear();
        pendingPages = null;
        ClientSmokeFlowField.INSTANCE.clear();
        highestGeneration = Long.MIN_VALUE;
        activeLevel = null;
    }

    private boolean ensureCurrentLevel(final ClientLevel level) {
        if (level == null) { clear(); return false; }
        if (activeLevel != level) {
            for (LinkedHashMap<Long, CellVisual> bandCells : cells.values()) bandCells.clear();
            retiringCells.clear(); embers.clear(); windImpulses.clear(); lodLevels.clear();
            pendingPages = null;
            ClientSmokeFlowField.INSTANCE.clear();
            highestGeneration = Long.MIN_VALUE;
            activeLevel = level;
        }
        return true;
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
        long lastSeenClientTick) { }
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

    private static final class CellVisual {
        private final FireVisualCell cell;
        private final long transitionStartTick;
        private final float transitionStartWeight;
        private final float transitionTargetWeight;
        private final long lastSeenClientTick;
        private final int transitionTicks;

        private CellVisual(final FireVisualCell cell, final long transitionStartTick,
            final float transitionStartWeight, final float transitionTargetWeight,
            final long lastSeenClientTick, final int transitionTicks) {
            this.cell = cell;
            this.transitionStartTick = transitionStartTick;
            this.transitionStartWeight = transitionStartWeight;
            this.transitionTargetWeight = transitionTargetWeight;
            this.lastSeenClientTick = lastSeenClientTick;
            this.transitionTicks = Math.max(1, transitionTicks);
        }

        private static CellVisual immediate(final FireVisualCell cell, final long now) {
            return new CellVisual(cell, now, 1.0F, 1.0F, now, 1);
        }

        private static CellVisual fadeIn(final FireVisualCell cell, final long now) {
            return new CellVisual(cell, now, 0.0F, 1.0F, now,
                REPRESENTATION_TRANSITION_TICKS);
        }

        private CellVisual accept(final FireVisualCell incoming, final long now) {
            float currentWeight = transitionWeight(now);
            FireVisualCell smoothed = new FireVisualCell(incoming.id(), incoming.parentId(),
                incoming.band(),
                incoming.cellSize(), incoming.cellX(), incoming.cellY(), incoming.cellZ(),
                cell.centroid().lerp(incoming.centroid(), 0.44), incoming.extents(),
                incoming.occupancyMask(), lerp(cell.flameEnergy(), incoming.flameEnergy(), 0.44F),
                lerp(cell.flameEnvelopeHeight(), incoming.flameEnvelopeHeight(), 0.44F),
                lerp(cell.smokeMass(), incoming.smokeMass(), 0.40F),
                lerp(cell.maximumHeat(), incoming.maximumHeat(), 0.44F),
                lerp(cell.averageIntensity(), incoming.averageIntensity(), 0.44F),
                lerp(cell.coveredArea(), incoming.coveredArea(), 0.40F),
                lerp(cell.clumpStrength(), incoming.clumpStrength(), 0.40F),
                cell.wind().lerp(incoming.wind(), 0.34), incoming.hostCount(),
                cell.seed(), incoming.dominantFace(), incoming.phase(),
                incoming.ignitionGameTime());
            return new CellVisual(smoothed, now, currentWeight, 1.0F, now,
                REPRESENTATION_TRANSITION_TICKS);
        }

        private CellVisual retireSmoke(final long now) {
            if (cell.smokeMass() <= 0.012F && cell.flameEnergy() <= 0.012F) return null;
            int lifetime = cell.smokeMass() > 0.012F
                ? SMOKE_RETIRE_TICKS : REPRESENTATION_TRANSITION_TICKS;
            return new CellVisual(cell, now, transitionWeight(now), 0.0F,
                lastSeenClientTick, lifetime);
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

    /** Eight history samples make the renderer follow the real local path. */
    private static final class EmberVisual {
        private static final int MAX_TRAIL_SAMPLES = 8;
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
        private final ArrayDeque<EmberTrailSample> trail = new ArrayDeque<>();

        private EmberVisual(final long id, final Vec3 position, final Vec3 velocity,
            final Vec3 wind, final float intensity, final long seed, final long startGameTime,
            final int lifetime, final long serverSampleGameTime, final long receivedAt) {
            this.id = id; this.position = position; this.velocity = velocity; this.wind = wind;
            this.intensity = intensity; this.seed = seed; this.startGameTime = startGameTime;
            this.lifetime = lifetime; this.serverSampleGameTime = serverSampleGameTime;
            this.lastSeenClientTick = receivedAt; this.simulatedGameTime = receivedAt;
            appendTrail(receivedAt);
        }

        private void accept(final Vec3 incomingPosition, final Vec3 incomingVelocity,
            final Vec3 incomingWind, final float incomingIntensity, final long incomingSeed,
            final long incomingStart, final int incomingLifetime, final long serverTime,
            final long receivedAt) {
            if (position.distanceToSqr(incomingPosition) > 2.25) {
                position = incomingPosition; velocity = incomingVelocity; trail.clear();
            } else {
                position = position.lerp(incomingPosition, 0.46);
                velocity = velocity.lerp(incomingVelocity, 0.42);
            }
            wind = wind.lerp(incomingWind, 0.48); intensity = incomingIntensity;
            seed = incomingSeed; startGameTime = incomingStart; lifetime = incomingLifetime;
            serverSampleGameTime = serverTime; lastSeenClientTick = receivedAt;
            simulatedGameTime = Math.max(simulatedGameTime, receivedAt);
            if (trail.isEmpty()) appendTrail(receivedAt);
        }

        private void simulate(final long now, final Vec3 effectiveWind) {
            while (simulatedGameTime < now) {
                simulatedGameTime++;
                double progress = Math.min(1.0, Math.max(0.0,
                    (simulatedGameTime - startGameTime) / (double) Math.max(1, lifetime)));
                velocity = FireSimulationManager.stepEmberVelocity(velocity, effectiveWind, seed,
                    startGameTime, simulatedGameTime, progress);
                position = position.add(velocity);
                appendTrail(simulatedGameTime);
            }
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
