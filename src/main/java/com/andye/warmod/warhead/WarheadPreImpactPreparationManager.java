package com.andye.warmod.warhead;

import com.andye.warmod.entity.IncomingWarheadEntity;
import com.andye.warmod.icbm.IcbmChunkTicketRegistry;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.testtool.WarheadExplosionDropContext;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Uses the terminal-warhead approach window to move deterministic, read-only
 * debris source discovery off the impact tick. Prepared data is opportunistic:
 * impact falls back to the normal sampler whenever it is incomplete or stale.
 */
public final class WarheadPreImpactPreparationManager {
    private static final long LEVEL_WORK_BUDGET_NANOS = 2_000_000L;
    private static final int MAX_CHECKS_PER_LEVEL_TICK = 384;
    private static final int WORK_SLICE = 64;
    private static final double CENTER_EPSILON_SQR = 1.0E-6;
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
        levelWork.queue.addLast(warheadId);
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
        if (preparation == null || !preparation.complete() || preparation.yield != yield
            || preparation.seed != seed || preparation.effectiveCenter == null
            || preparation.effectiveCenter.distanceToSqr(effectiveCenter) > CENTER_EPSILON_SQR) {
            cleanupLevel(level, levelWork);
            return Optional.empty();
        }

        List<WarheadExplosionDropContext.DestroyedBlock> debris = preparation.sampler.result();
        for (WarheadExplosionDropContext.DestroyedBlock block : debris) {
            int chunkX = SectionPos.blockToSectionCoord(block.position().getX());
            int chunkZ = SectionPos.blockToSectionCoord(block.position().getZ());
            if (!level.getChunkSource().hasChunk(chunkX, chunkZ)
                || !level.getBlockState(block.position()).equals(block.originalState())) {
                cleanupLevel(level, levelWork);
                return Optional.empty();
            }
        }
        cleanupLevel(level, levelWork);
        return Optional.of(debris);
    }

    private static synchronized void ensureRegistered() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(WarheadPreImpactPreparationManager::tickLevel);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
        registered = true;
    }

    private static synchronized void tickLevel(final ServerLevel level) {
        LevelWork levelWork = LEVELS.get(level);
        if (levelWork == null || levelWork.queue.isEmpty()) return;
        long now = level.getGameTime();
        long deadline = System.nanoTime() + LEVEL_WORK_BUDGET_NANOS;
        int checksRemaining = MAX_CHECKS_PER_LEVEL_TICK;
        int scheduled = levelWork.queue.size();

        for (int index = 0; index < scheduled && checksRemaining > 0; index++) {
            if (index > 0 && System.nanoTime() >= deadline) break;
            UUID id = levelWork.queue.removeFirst();
            Preparation preparation = levelWork.byId.get(id);
            if (preparation == null) continue;
            if (now >= preparation.expiresAt) {
                levelWork.byId.remove(id);
                continue;
            }

            int used = preparation.advance(level, Math.min(WORK_SLICE, checksRemaining));
            checksRemaining -= Math.max(1, used);
            if (!preparation.complete()) levelWork.queue.addLast(id);
        }
        cleanupLevel(level, levelWork);
    }

    private static void cleanupLevel(final ServerLevel level, final LevelWork levelWork) {
        if (levelWork != null && levelWork.byId.isEmpty()) LEVELS.remove(level);
    }

    private static synchronized void clear() {
        LEVELS.clear();
    }

    private static final class LevelWork {
        private final Map<UUID, Preparation> byId = new HashMap<>();
        private final ArrayDeque<UUID> queue = new ArrayDeque<>();
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

        private Preparation(final UUID warheadId, final Vec3 intendedTarget, final long expiresAt) {
            this.warheadId = warheadId;
            this.intendedTarget = intendedTarget;
            this.expiresAt = expiresAt;
            this.impactWindow = IcbmChunkTicketRegistry.window(
                IcbmChunkTicketRegistry.chunk(intendedTarget),
                IcbmConstants.IMPACT_CHUNK_RADIUS
            );
        }

        private int advance(final ServerLevel level, final int budget) {
            if (sampler == null) {
                if (!IcbmChunkTicketRegistry.allLoaded(level, impactWindow)) return 0;
                IncomingWarheadEntity entity = IncomingWarheadRegistry.getByWarheadId(level, warheadId).orElse(null);
                if (entity == null || entity.intendedTarget().distanceToSqr(intendedTarget) > CENTER_EPSILON_SQR) return 0;
                yield = WarheadYieldRegistry.resolve(
                    level,
                    entity.warheadId(),
                    entity.radarRootTrackId(),
                    entity.payloadType()
                );
                seed = entity.visualSeed();
                effectiveCenter = WarheadExplosionWorkManager.resolveDetonationCenter(level, intendedTarget, yield);
                sampler = WarheadDebrisSourceSampler.begin(effectiveCenter, yield, seed);
            }
            return sampler.advance(level, budget);
        }

        private boolean complete() {
            return sampler != null && sampler.complete();
        }
    }
}
