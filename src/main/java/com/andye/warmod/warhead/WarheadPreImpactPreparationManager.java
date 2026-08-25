package com.andye.warmod.warhead;

import com.andye.warmod.entity.IncomingWarheadEntity;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.scheduler.WarModServerWorkScheduler;
import com.andye.warmod.scheduler.WarModServerWorkScheduler.WorkClass;
import com.andye.warmod.scheduler.WarModServerWorkScheduler.WorkPermit;
import com.andye.warmod.testtool.WarheadExplosionDropContext;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Uses the terminal-warhead approach window to move deterministic, read-only
 * debris source discovery off the impact tick. Preparations are per-warhead,
 * but their terrain observations are shared so overlapping salvos do not keep
 * rereading the same blocks and depth layers.
 */
public final class WarheadPreImpactPreparationManager {
    private static final int MAX_CHECKS_PER_LEVEL_TICK = 384;
    private static final int WORK_SLICE = 64;
    private static final int IMPACT_FINISH_CHECK_BUDGET = 768;
    private static final double CENTER_EPSILON_SQR = 1.0E-6;
    private static final double CRATER_DEPTH_INVALIDATION_MARGIN = 12.0;
    private static final Map<ServerLevel, LevelWork> LEVELS = new WeakHashMap<>();
    private static boolean registered;

    private WarheadPreImpactPreparationManager() { }

    public static synchronized void schedule(
        final ServerLevel level,
        final UUID warheadId,
        final Vec3 intendedTarget,
        final int lifetimeTicks
    ) {
        if (level == null || warheadId == null || intendedTarget == null || !intendedTarget.isFinite()) return;
        ensureRegistered();
        LevelWork levelWork = LEVELS.computeIfAbsent(level, ignored -> new LevelWork());
        long expiresAt = level.getGameTime() + Math.max(1, lifetimeTicks);
        Preparation existing = levelWork.byId.get(warheadId);
        if (existing != null) {
            existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
            return;
        }
        Preparation preparation = new Preparation(warheadId, intendedTarget, expiresAt);
        levelWork.byId.put(warheadId, preparation);
        levelWork.enqueue(preparation);
    }

    /**
     * Starts the nuclear surface discovery as soon as a caller already knows
     * its yield, seed and intended impact.  ICBMs get those values only once
     * their terminal entity is observed, while artillery and timed charges
     * know them at launch/fuse time.  The scan is deliberately read-only and
     * ignores unloaded chunks; it never expands the caller's chunk lease.
     */
    public static void scheduleKnownNuclearTerrain(
        final ServerLevel level,
        final UUID warheadId,
        final Vec3 intendedTarget,
        final WarheadYield yield,
        final long seed,
        final int lifetimeTicks
    ) {
        if (level == null || warheadId == null || intendedTarget == null
            || !intendedTarget.isFinite() || yield == null || !yield.nuclear()) return;
        WarheadExplosionWorkManager.prepareCraterMutationPlan(level, warheadId,
            intendedTarget, yield, seed, lifetimeTicks);
        WarheadGlassShockwaveManager.prepareNuclearTerrain(
            level, warheadId, intendedTarget, yield, seed, lifetimeTicks);
    }

    public static synchronized Optional<List<WarheadExplosionDropContext.DestroyedBlock>> consume(
        final ServerLevel level,
        final UUID warheadId,
        final Vec3 effectiveCenter,
        final WarheadYield yield,
        final long seed
    ) {
        LevelWork levelWork = LEVELS.get(level);
        Preparation preparation = levelWork == null ? null : levelWork.byId.remove(warheadId);
        if (preparation == null) {
            cleanupLevel(level, levelWork);
            return Optional.empty();
        }
        preparation.queued = false;

        /*
         * A miss, an earlier overlapping crater, or simply running out of
         * approach ticks can make the old per-warhead sample unusable. Rebuild
         * it against the shared read-through cache, then spend only a bounded
         * final slice here rather than moving the entire scan onto impact.
         */
        if (!preparation.compatible(effectiveCenter, yield, seed)) {
            preparation.prepareForImpact(level, effectiveCenter, yield, seed,
                levelWork.terrainCache);
        }
        if (!preparation.complete()) {
            preparation.sampler.advance(level, IMPACT_FINISH_CHECK_BUDGET);
        }

        List<WarheadExplosionDropContext.DestroyedBlock> debris = preparation.complete()
            ? preparation.sampler.result()
            : preparation.sampler.partialResult();
        for (WarheadExplosionDropContext.DestroyedBlock block : debris) {
            int chunkX = SectionPos.blockToSectionCoord(block.position().getX());
            int chunkZ = SectionPos.blockToSectionCoord(block.position().getZ());
            if (!level.getChunkSource().hasChunk(chunkX, chunkZ)
                || !level.getBlockState(block.position()).equals(block.originalState())) {
                /* Do not let a detected arbitrary world edit stay shared. */
                levelWork.terrainCache.invalidate(block.position());
                cleanupLevel(level, levelWork);
                return Optional.empty();
            }
        }
        cleanupLevel(level, levelWork);
        return Optional.of(debris);
    }

    /**
     * Marks terrain touched by a live explosion as changed without discarding
     * the reusable observations outside that changed volume. Overlapping
     * preparations are reset and re-queued; on their next pass they reuse the
     * untouched cache and reread only invalidated positions.
     */
    public static synchronized void invalidateAround(
        final ServerLevel level,
        final UUID exceptWarheadId,
        final Vec3 center,
        final WarheadYield yield,
        final double radius
    ) {
        if (level == null || center == null || yield == null || !center.isFinite()
            || !Double.isFinite(radius) || radius <= 0.0) return;
        LevelWork levelWork = LEVELS.get(level);
        if (levelWork == null) return;

        StrategicExplosionProfile profile = StrategicExplosionProfiles.get(yield);
        double deepCraterRadius = profile.horizontalRadius() * 1.12;
        int minimumCraterY = Mth.floor(
            center.y - profile.downwardRadius() - CRATER_DEPTH_INVALIDATION_MARGIN);
        levelWork.terrainCache.invalidateAround(center, radius, deepCraterRadius, minimumCraterY);
        WarheadExplosionWorkManager.invalidatePreparedCraterPlans(
            level, exceptWarheadId, center, radius);

        double radiusSqr;
        for (Preparation preparation : levelWork.byId.values()) {
            if (preparation.warheadId.equals(exceptWarheadId) || preparation.sampler == null) continue;
            double reach = radius + preparation.sampleRadius();
            radiusSqr = reach * reach;
            double dx = preparation.intendedTarget.x - center.x;
            double dz = preparation.intendedTarget.z - center.z;
            if (dx * dx + dz * dz > radiusSqr) continue;
            preparation.resetAfterTerrainChange();
            levelWork.enqueue(preparation);
        }
        cleanupLevel(level, levelWork);
    }

    private static synchronized void ensureRegistered() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(WarheadPreImpactPreparationManager::tickLevel);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
        registered = true;
    }

    private static synchronized void tickLevel(final ServerLevel level) {
        LevelWork levelWork = LEVELS.get(level);
        if (levelWork == null) return;
        long now = level.getGameTime();

        /*
         * Once an entity has been observed, disappearance means interception,
         * cancellation or another non-impact removal. Its private sampler can
         * be dropped while observations shared with other missiles remain in
         * the level cache until the last preparation is gone.
         */
        Iterator<Map.Entry<UUID, Preparation>> cleanup = levelWork.byId.entrySet().iterator();
        while (cleanup.hasNext()) {
            Map.Entry<UUID, Preparation> entry = cleanup.next();
            Preparation preparation = entry.getValue();
            if (now >= preparation.expiresAt
                || (preparation.observedEntity
                    && IncomingWarheadRegistry.getByWarheadId(level, entry.getKey()).isEmpty())) {
                cleanup.remove();
            }
        }
        if (levelWork.byId.isEmpty()) {
            LEVELS.remove(level);
            return;
        }
        if (levelWork.queue.isEmpty()) return;

        try (WorkPermit permit = WarModServerWorkScheduler.acquire(level,
            WorkClass.BACKGROUND_PREP, 2_000_000L)) {
            if (!permit.available()) return;
            long deadline = permit.deadlineNanos();
            int checksRemaining = MAX_CHECKS_PER_LEVEL_TICK;
            int scheduled = levelWork.queue.size();

            for (int index = 0; index < scheduled && checksRemaining > 0; index++) {
                if (index > 0 && System.nanoTime() >= deadline) break;
                UUID id = levelWork.queue.removeFirst();
                Preparation preparation = levelWork.byId.get(id);
                if (preparation == null) continue;
                preparation.queued = false;

                int used = preparation.advance(level, levelWork,
                    Math.min(WORK_SLICE, checksRemaining));
                checksRemaining -= Math.max(1, used);
                if (!preparation.complete()) levelWork.enqueue(preparation);
            }
        }
        cleanupLevel(level, levelWork);
    }

    private static void cleanupLevel(final ServerLevel level, final LevelWork levelWork) {
        if (levelWork != null && levelWork.byId.isEmpty()) LEVELS.remove(level);
    }

    private static synchronized void clear() {
        LEVELS.clear();
    }

    private static long chunkKey(final int chunkX, final int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static final class LevelWork {
        private final Map<UUID, Preparation> byId = new HashMap<>();
        private final ArrayDeque<UUID> queue = new ArrayDeque<>();
        private final RegionalTerrainCache terrainCache = new RegionalTerrainCache();

        private void enqueue(final Preparation preparation) {
            if (preparation.queued) return;
            preparation.queued = true;
            queue.addLast(preparation.warheadId);
        }
    }

    private static final class Preparation {
        private final UUID warheadId;
        private final Vec3 intendedTarget;
        private final Set<ChunkPos> impactWindow;
        private long expiresAt;
        private Vec3 effectiveCenter;
        private WarheadYield yield;
        private long seed;
        private WarheadDebrisSourceSampler.IncrementalSample sampler;
        private boolean queued;
        private boolean observedEntity;

        private Preparation(final UUID warheadId, final Vec3 intendedTarget, final long expiresAt) {
            this.warheadId = warheadId;
            this.intendedTarget = intendedTarget;
            this.expiresAt = expiresAt;
            this.impactWindow = IcbmChunkTicketRegistry.window(
                IcbmChunkTicketRegistry.chunk(intendedTarget),
                IcbmConstants.IMPACT_CHUNK_RADIUS
            );
        }

        private int advance(final ServerLevel level, final LevelWork levelWork, final int budget) {
            if (sampler == null) {
                if (!IcbmChunkTicketRegistry.allLoaded(level, impactWindow)) return 0;
                IncomingWarheadEntity entity = IncomingWarheadRegistry.getByWarheadId(level, warheadId).orElse(null);
                if (entity == null || entity.intendedTarget().distanceToSqr(intendedTarget) > CENTER_EPSILON_SQR) return 0;
                observedEntity = true;
                yield = WarheadYieldRegistry.resolve(
                    level,
                    entity.warheadId(),
                    entity.radarRootTrackId(),
                    entity.payloadType()
                );
                seed = entity.visualSeed();
                effectiveCenter = WarheadExplosionWorkManager.resolveDetonationCenter(level, intendedTarget, yield);
                WarheadExplosionWorkManager.prepareCraterMutationPlan(level, warheadId,
                    effectiveCenter, yield, seed,
                    Math.max(1, (int) (expiresAt - level.getGameTime())));
                scheduleKnownNuclearTerrain(
                    level, warheadId, effectiveCenter, yield, seed,
                    Math.max(1, (int) (expiresAt - level.getGameTime()))
                );
                sampler = WarheadDebrisSourceSampler.begin(effectiveCenter, yield, seed, levelWork.terrainCache);
            }
            return sampler.advance(level, budget);
        }

        private void prepareForImpact(
            final ServerLevel level,
            final Vec3 center,
            final WarheadYield actualYield,
            final long actualSeed,
            final RegionalTerrainCache terrainCache
        ) {
            effectiveCenter = center;
            yield = actualYield;
            seed = actualSeed;
            WarheadExplosionWorkManager.prepareCraterMutationPlan(
                level, warheadId, center, actualYield, actualSeed, 1);
            sampler = WarheadDebrisSourceSampler.begin(center, actualYield, actualSeed, terrainCache);
        }

        private boolean compatible(final Vec3 center, final WarheadYield actualYield, final long actualSeed) {
            return sampler != null && yield == actualYield && seed == actualSeed
                && effectiveCenter != null
                && effectiveCenter.distanceToSqr(center) <= CENTER_EPSILON_SQR;
        }

        private void resetAfterTerrainChange() {
            sampler = null;
            effectiveCenter = null;
        }

        private double sampleRadius() {
            return yield == null
                ? 12.0
                : StrategicExplosionProfiles.get(yield).horizontalRadius() * 0.68 + 4.0;
        }

        private boolean complete() {
            return sampler != null && sampler.complete();
        }
    }

    /**
     * Per-level, short-lived read-through terrain cache shared by every active
     * terminal warhead. Entries are bucketed by chunk and keep their Y value,
     * so a shallow first crater does not erase observations of deeper strata
     * that a directly overlapping follow-up crater may reach.
     */
    private static final class RegionalTerrainCache implements WarheadDebrisSourceSampler.TerrainReadCache {
        private final Map<Long, CachedChunk> chunks = new HashMap<>();

        @Override
        public BlockState blockState(final ServerLevel level, final BlockPos position) {
            int chunkX = position.getX() >> 4;
            int chunkZ = position.getZ() >> 4;
            CachedChunk chunk = chunks.computeIfAbsent(
                chunkKey(chunkX, chunkZ), ignored -> new CachedChunk(chunkX, chunkZ));
            int key = localStateKey(position.getX(), position.getY(), position.getZ());
            BlockState cached = chunk.states.get(key);
            if (cached != null) return cached;
            BlockState state = level.getBlockState(position);
            chunk.states.put(key, state);
            return state;
        }

        private void invalidate(final BlockPos position) {
            CachedChunk chunk = chunks.get(chunkKey(position.getX() >> 4, position.getZ() >> 4));
            if (chunk == null) return;
            chunk.states.remove(localStateKey(position.getX(), position.getY(), position.getZ()));
            if (chunk.states.isEmpty()) chunks.remove(chunkKey(chunk.chunkX, chunk.chunkZ));
        }

        private void invalidateAround(
            final Vec3 center,
            final double outerRadius,
            final double deepCraterRadius,
            final int minimumCraterY
        ) {
            double outerRadiusSqr = outerRadius * outerRadius;
            double deepRadiusSqr = deepCraterRadius * deepCraterRadius;
            Iterator<Map.Entry<Long, CachedChunk>> chunkIterator = chunks.entrySet().iterator();
            while (chunkIterator.hasNext()) {
                CachedChunk chunk = chunkIterator.next().getValue();
                Iterator<Map.Entry<Integer, BlockState>> stateIterator = chunk.states.entrySet().iterator();
                while (stateIterator.hasNext()) {
                    int packed = stateIterator.next().getKey();
                    int localX = packed & 15;
                    int localZ = (packed >>> 4) & 15;
                    int y = packed >> 8;
                    double dx = (chunk.chunkX << 4) + localX + 0.5 - center.x;
                    double dz = (chunk.chunkZ << 4) + localZ + 0.5 - center.z;
                    double distanceSqr = dx * dx + dz * dz;
                    if (distanceSqr > outerRadiusSqr) continue;

                    /*
                     * The outer shockwave/aftermath can change the local
                     * surface at an unknown Y, so outer-ring observations are
                     * discarded. Inside the actual crater footprint we retain
                     * strata safely below the first crater's maximum depth.
                     */
                    if (distanceSqr > deepRadiusSqr || y >= minimumCraterY) {
                        stateIterator.remove();
                    }
                }
                if (chunk.states.isEmpty()) chunkIterator.remove();
            }
        }

        private static int localStateKey(final int x, final int y, final int z) {
            return (y << 8) | ((z & 15) << 4) | (x & 15);
        }
    }

    private static final class CachedChunk {
        private final int chunkX;
        private final int chunkZ;
        private final Map<Integer, BlockState> states = new HashMap<>();

        private CachedChunk(final int chunkX, final int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
