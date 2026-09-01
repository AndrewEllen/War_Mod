package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.diagnostics.WarheadLifecycleDiagnostics;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Reference-counted load-only footprint leases. Ticket insertion is bounded,
 * and readiness is based on a completed FULL-chunk load plus getChunkNow.
 */
public final class WarheadPreparationLeaseManager {
    static final int MAX_TICKET_REQUESTS_PER_LEVEL_TICK = 48;
    private static final int LOAD_RETRY_TICKS = 20;
    private static final Map<ServerLevel, LevelState> LEVELS = new IdentityHashMap<>();
    private static boolean registered;

    private WarheadPreparationLeaseManager() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(WarheadPreparationLeaseManager::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> clearLevel(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
        registered = true;
    }

    public static synchronized void acquireOrReplace(final ServerLevel level,
        final UUID leaseId, final Vec3 center, final WarheadFootprint footprint,
        final long expiresAt) {
        if (level == null || leaseId == null || center == null || !center.isFinite()
            || footprint == null) throw new IllegalArgumentException("Invalid preparation lease");
        acquireOrReplace(level, leaseId,
            List.of(new WarheadPreparationLeaseTarget(center, footprint)), expiresAt);
    }

    static synchronized void acquireOrReplace(final ServerLevel level,
        final UUID leaseId, final List<WarheadPreparationLeaseTarget> targets,
        final long expiresAt) {
        if (level == null || leaseId == null || targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("Invalid preparation lease group");
        }
        registerLifecycle();
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState());
        Lease previous = state.leases.get(leaseId);
        LongOpenHashSet owned = new LongOpenHashSet();
        for (WarheadPreparationLeaseTarget target : targets) {
            if (target == null) throw new IllegalArgumentException("Null preparation target");
            owned.addAll(target.footprint().requiredChunks());
        }
        LongOpenHashSet required = withSurfaceHalo(owned);
        if (previous != null) {
            WarheadLeaseDelta delta = WarheadLeaseDelta.between(previous.required, required);
            for (long packed : delta.released()) releaseReference(level, state, packed);
            for (long packed : delta.acquired()) state.references.acquire(packed);
        } else {
            for (long packed : required) state.references.acquire(packed);
        }
        state.leases.put(leaseId, new Lease(owned, required,
            prioritized(level, targets, required),
            Math.max(level.getGameTime() + 1L, expiresAt)));
    }

    public static synchronized void extend(final ServerLevel level,
        final UUID leaseId, final long expiresAt) {
        LevelState state = LEVELS.get(level);
        Lease lease = state == null ? null : state.leases.get(leaseId);
        if (lease != null) lease.expiresAt = Math.max(lease.expiresAt, expiresAt);
    }

    public static synchronized LeaseSnapshot snapshot(final ServerLevel level,
        final UUID leaseId) {
        LevelState state = LEVELS.get(level);
        Lease lease = state == null ? null : state.leases.get(leaseId);
        if (lease == null) return new LeaseSnapshot(0, 0, 0, false);
        int ticketed = 0;
        int ready = 0;
        for (long packed : lease.owned) {
            if (state.ticketed.contains(packed)) ticketed++;
            if (fullChunkReady(level, state, packed)) ready++;
        }
        boolean readDomainReady = true;
        for (long packed : lease.required) {
            if (!fullChunkReady(level, state, packed)) {
                readDomainReady = false;
                break;
            }
        }
        return new LeaseSnapshot(lease.owned.size(), ticketed, ready,
            ready == lease.owned.size() && ready > 0 && readDomainReady);
    }

    public static synchronized boolean ready(final ServerLevel level,
        final UUID leaseId) {
        return snapshot(level, leaseId).ready();
    }

    static synchronized boolean chunkReady(final ServerLevel level,
        final UUID leaseId, final long packedChunk) {
        LevelState state = LEVELS.get(level);
        Lease lease = state == null ? null : state.leases.get(leaseId);
        return lease != null && lease.owned.contains(packedChunk)
            && fullChunkReady(level, state, packedChunk);
    }

    public static synchronized void release(final ServerLevel level,
        final UUID leaseId) {
        LevelState state = LEVELS.get(level);
        if (state == null) return;
        Lease lease = state.leases.remove(leaseId);
        if (lease != null) {
            WarheadLifecycleDiagnostics.leaseReleased(level, leaseId,
                lease.owned.size());
            for (long packed : lease.required) releaseReference(level, state, packed);
        }
        if (state.leases.isEmpty()) LEVELS.remove(level);
    }

    public static synchronized int activeLeaseCount(final ServerLevel level) {
        LevelState state = LEVELS.get(level);
        return state == null ? 0 : state.leases.size();
    }

    public static synchronized int referenceCount(final ServerLevel level,
        final ChunkPos chunk) {
        LevelState state = LEVELS.get(level);
        return state == null ? 0 : state.references.count(chunk.pack());
    }

    private static synchronized void tick(final ServerLevel level) {
        LevelState state = LEVELS.get(level);
        if (state == null) return;
        long now = level.getGameTime();
        for (UUID leaseId : List.copyOf(state.leases.keySet())) {
            Lease lease = state.leases.get(leaseId);
            if (lease != null && now >= lease.expiresAt) {
                WarMod.LOGGER.warn("Warhead preparation lease {} expired with {} of {} chunks ready",
                    leaseId, snapshot(level, leaseId).readyChunks(), lease.owned.size());
                release(level, leaseId);
            }
        }
        state = LEVELS.get(level);
        if (state == null) return;

        int remaining = MAX_TICKET_REQUESTS_PER_LEVEL_TICK;
        for (Lease lease : state.leases.values()) {
            int inspected = 0;
            while (remaining > 0 && inspected < lease.priority.size()) {
                if (lease.nextPriority >= lease.priority.size()) lease.nextPriority = 0;
                long packed = lease.priority.getLong(lease.nextPriority++);
                inspected++;
                if (state.references.count(packed) <= 0 || state.ticketed.contains(packed)
                    || state.retryAfter.get(packed) > now) continue;
                ChunkPos chunk = ChunkPos.unpack(packed);
                try {
                    CompletableFuture<?> future = level.getChunkSource()
                        .addTicketAndLoadWithRadius(
                            WarheadPreparationTicketType.WARHEAD_PREPARATION, chunk, 0);
                    state.ticketed.add(packed);
                    state.loads.put(packed, future);
                    remaining--;
                } catch (RuntimeException failure) {
                    state.retryAfter.put(packed, now + LOAD_RETRY_TICKS);
                    WarMod.LOGGER.warn("Could not request preparation chunk {}", chunk,
                        failure);
                }
            }
        }

        for (long packed : new LongOpenHashSet(state.ticketed)) {
            CompletableFuture<?> future = state.loads.get(packed);
            if (future != null && future.isCompletedExceptionally()) {
                level.getChunkSource().removeTicketWithRadius(
                    WarheadPreparationTicketType.WARHEAD_PREPARATION,
                    ChunkPos.unpack(packed), 0);
                state.ticketed.remove(packed);
                state.loads.remove(packed);
                state.retryAfter.put(packed, now + LOAD_RETRY_TICKS);
            }
        }
    }

    private static boolean fullChunkReady(final ServerLevel level,
        final LevelState state, final long packed) {
        CompletableFuture<?> future = state.loads.get(packed);
        if (!state.ticketed.contains(packed) || future == null || !future.isDone()
            || future.isCompletedExceptionally()) return false;
        ChunkPos chunk = ChunkPos.unpack(packed);
        return level.getChunkSource().hasChunk(chunk.x(), chunk.z())
            && level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null;
    }

    private static void releaseReference(final ServerLevel level,
        final LevelState state, final long packed) {
        if (state.references.release(packed) > 0) return;
        state.retryAfter.remove(packed);
        state.loads.remove(packed);
        if (state.ticketed.remove(packed)) {
            level.getChunkSource().removeTicketWithRadius(
                WarheadPreparationTicketType.WARHEAD_PREPARATION,
                ChunkPos.unpack(packed), 0);
        }
    }

    private static LongArrayList prioritized(final ServerLevel level,
        final List<WarheadPreparationLeaseTarget> targets, final LongSet required) {
        ArrayList<Long> boxed = new ArrayList<>(required.size());
        for (long packed : required) boxed.add(packed);
        boxed.sort(Comparator
            .comparingInt((Long packed) -> priorityBand(level, targets,
                packed.longValue()))
            .thenComparingDouble(packed -> distanceSqr(targets,
                ChunkPos.unpack(packed.longValue())))
            .thenComparingLong(Long::longValue));
        LongArrayList result = new LongArrayList(boxed.size());
        for (long packed : boxed) result.add(packed);
        return result;
    }

    /** Adds the cardinal load-only neighbours required by the surface read halo.
     * These chunks receive tickets but never become mutation owners or inflate
     * the impact's required/prepared/fallback diagnostics. */
    private static LongOpenHashSet withSurfaceHalo(final LongSet owned) {
        LongOpenHashSet required = new LongOpenHashSet(owned);
        for (long packed : owned) {
            ChunkPos chunk = ChunkPos.unpack(packed);
            required.add(new ChunkPos(chunk.x() - 1, chunk.z()).pack());
            required.add(new ChunkPos(chunk.x() + 1, chunk.z()).pack());
            required.add(new ChunkPos(chunk.x(), chunk.z() - 1).pack());
            required.add(new ChunkPos(chunk.x(), chunk.z() + 1).pack());
        }
        return required;
    }

    private static int priorityBand(final ServerLevel level,
        final List<WarheadPreparationLeaseTarget> targets, final long packed) {
        ChunkPos chunk = ChunkPos.unpack(packed);
        for (WarheadPreparationLeaseTarget target : targets) {
            ChunkPos impact = new ChunkPos((int)Math.floor(target.center().x) >> 4,
                (int)Math.floor(target.center().z) >> 4);
            if (chunk.getChessboardDistance(impact)
                <= IcbmConstants.MINIMUM_PREPARATION_CHUNK_RADIUS) return 0;
        }
        for (WarheadPreparationLeaseTarget target : targets) {
            if (WarheadFootprintCalculator.chunkIntersectsCircle(chunk.x(), chunk.z(),
                target.center().x, target.center().z,
                target.footprint().craterRadius())) return 1;
        }
        if (!level.getChunkSource().chunkMap.getPlayers(chunk, false).isEmpty()) return 2;
        return 3;
    }

    private static double distanceSqr(final List<WarheadPreparationLeaseTarget> targets,
        final ChunkPos chunk) {
        double minimum = Double.POSITIVE_INFINITY;
        for (WarheadPreparationLeaseTarget target : targets) {
            double dx = chunk.getMiddleBlockX() + 0.5 - target.center().x;
            double dz = chunk.getMiddleBlockZ() + 0.5 - target.center().z;
            minimum = Math.min(minimum, dx * dx + dz * dz);
        }
        return minimum;
    }

    private static synchronized void clearLevel(final ServerLevel level) {
        LevelState state = LEVELS.remove(level);
        if (state == null) return;
        for (Map.Entry<UUID, Lease> entry : state.leases.entrySet()) {
            WarheadLifecycleDiagnostics.leaseReleased(level, entry.getKey(),
                entry.getValue().owned.size());
        }
        for (long packed : state.ticketed) {
            level.getChunkSource().removeTicketWithRadius(
                WarheadPreparationTicketType.WARHEAD_PREPARATION,
                ChunkPos.unpack(packed), 0);
        }
        state.clear();
    }

    private static synchronized void clearAll() {
        for (ServerLevel level : List.copyOf(LEVELS.keySet())) clearLevel(level);
        LEVELS.clear();
    }

    public record LeaseSnapshot(int requiredChunks, int ticketedChunks,
        int readyChunks, boolean ready) { }

    private static final class Lease {
        private final LongOpenHashSet owned;
        private final LongOpenHashSet required;
        private final LongArrayList priority;
        private long expiresAt;
        private int nextPriority;

        private Lease(final LongOpenHashSet owned, final LongOpenHashSet required,
            final LongArrayList priority, final long expiresAt) {
            this.owned = owned;
            this.required = required;
            this.priority = priority;
            this.expiresAt = expiresAt;
        }
    }

    private static final class LevelState {
        private final Map<UUID, Lease> leases = new java.util.LinkedHashMap<>();
        private final WarheadLeaseReferenceCounter references =
            new WarheadLeaseReferenceCounter();
        private final LongOpenHashSet ticketed = new LongOpenHashSet();
        private final Long2ObjectOpenHashMap<CompletableFuture<?>> loads =
            new Long2ObjectOpenHashMap<>();
        private final Long2LongOpenHashMap retryAfter = new Long2LongOpenHashMap();

        private void clear() {
            leases.clear();
            references.clear();
            ticketed.clear();
            loads.clear();
            retryAfter.clear();
        }
    }
}
