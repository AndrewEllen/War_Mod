package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.diagnostics.WarheadLifecycleDiagnostics;
import com.andye.warmod.icbm.IcbmConstants;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Owns load-only preparation leases, server-thread snapshots, worker compilation,
 * immutable per-chunk publication, impact sealing and deterministic cleanup.
 */
public final class WarheadPreparationCoordinator {
    private static final int MAX_SNAPSHOTS_PER_LEVEL_TICK = 96;
    private static final long SNAPSHOT_BUDGET_NANOS = 4_000_000L;
    private static final int MAX_COMPILED_RESULTS_PER_LEVEL_TICK = 256;
    private static final int MAX_COMPILE_RETRIES = 3;
    private static final int FINAL_ACTIVATION_TICK = 15;
    private static final long MINIMUM_LIFETIME_TICKS = 1_200L;
    private static final long CACHE_LIFETIME_TICKS = 1_600L;
    private static final double TARGET_COMPATIBILITY_SQR = 16.0;
    private static final Map<ServerLevel, LevelState> LEVELS = new IdentityHashMap<>();
    private static final ExecutorService COMPILER = Executors.newFixedThreadPool(
        Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() / 2)),
        runnable -> {
            Thread thread = new Thread(runnable, "war-mod-impact-plan");
            thread.setDaemon(true);
            return thread;
        });
    private static boolean registered;

    private WarheadPreparationCoordinator() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        WarheadPreparationLeaseManager.registerLifecycle();
        ServerTickEvents.END_LEVEL_TICK.register(WarheadPreparationCoordinator::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> clearLevel(level,
            CancellationReason.DIMENSION_UNLOAD));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
        registered = true;
    }

    public static synchronized WarheadPreparationHandle request(final ServerLevel level,
        final WarheadPreparationRequest request) {
        validate(level, request);
        registerLifecycle();
        LevelState levelState = LEVELS.computeIfAbsent(level, LevelState::new);
        Preparation existing = levelState.preparations.get(request.preparationId());
        if (existing != null && existing.matches(request)) {
            existing.expectedImpactTick = Math.max(existing.expectedImpactTick,
                request.expectedImpactTick());
            existing.expiresAt = expiry(level, existing.expectedImpactTick);
            WarheadPreparationLeaseManager.extend(level, existing.id, existing.expiresAt);
            recordRequest(level, existing);
            return existing.handle;
        }

        for (PreparedImpactSpec impact : request.impacts()) {
            Preparation owner = levelState.byImpact.get(impact.impactId());
            if (owner != null && owner != existing) {
                cancelPreparation(level, levelState, owner, CancellationReason.RETARGETED);
            }
        }
        if (existing != null) {
            detachImpactMappings(levelState, existing);
            existing.retarget(request);
            attachImpactMappings(levelState, existing);
            acquireLease(level, existing);
            recordRequest(level, existing);
            return existing.handle;
        }

        Preparation preparation = new Preparation(level, request, levelState.metadata);
        levelState.preparations.put(preparation.id, preparation);
        attachImpactMappings(levelState, preparation);
        acquireLease(level, preparation);
        recordRequest(level, preparation);
        return preparation.handle;
    }

    public static synchronized boolean ensureImpact(final ServerLevel level,
        final UUID preparationId, final UUID impactId, final UUID radarRootTrackId,
        final Vec3 requestedCenter, final WarheadYield yield, final long seed,
        final boolean customFire, final long expectedImpactTick) {
        if (level == null || preparationId == null || impactId == null
            || radarRootTrackId == null || requestedCenter == null
            || !requestedCenter.isFinite() || yield == null) return false;
        LevelState state = LEVELS.get(level);
        Preparation owner = state == null ? null : state.byImpact.get(impactId);
        if (owner != null) {
            PreparedImpactSpec current = owner.spec(impactId);
            if (current != null && compatible(current, requestedCenter, yield, seed, customFire)) {
                owner.expectedImpactTick = Math.max(owner.expectedImpactTick, expectedImpactTick);
                owner.expiresAt = expiry(level, owner.expectedImpactTick);
                WarheadPreparationLeaseManager.extend(level, owner.id, owner.expiresAt);
                return owner.readyPlans.containsKey(impactId);
            }
            ImpactCompile currentCompile = owner.compiles.get(impactId);
            if (currentCompile != null && currentCompile.sealed) return true;
            /* A cluster impact may arrive after one of its siblings has already sealed.
             * Retargeting the shared preparation here would discard the sibling's live
             * compiler stream. Detach only this impact and prepare its corrected target
             * under its own id instead. */
            if (owner.activeCommits > 0) {
                state.byImpact.remove(impactId);
                owner.cancelledImpacts.add(impactId);
                owner.readyPlans.remove(impactId);
                request(level, new WarheadPreparationRequest(impactId, radarRootTrackId,
                    level.dimension(), List.of(new PreparedImpactSpec(impactId,
                        requestedCenter, yield.payloadType(), yield, seed, customFire)),
                    expectedImpactTick, WarheadDeliveryMode.SINGLE));
                return false;
            }
            ArrayList<PreparedImpactSpec> revised = new ArrayList<>(owner.request.impacts());
            revised.removeIf(spec -> spec.impactId().equals(impactId));
            revised.add(new PreparedImpactSpec(impactId, requestedCenter,
                yield.payloadType(), yield, seed, customFire));
            request(level, new WarheadPreparationRequest(owner.id,
                owner.request.radarRootTrackId(), level.dimension(), revised,
                expectedImpactTick, owner.request.deliveryMode()));
            return false;
        }
        request(level, new WarheadPreparationRequest(preparationId, radarRootTrackId,
            level.dimension(), List.of(new PreparedImpactSpec(impactId, requestedCenter,
                yield.payloadType(), yield, seed, customFire)), expectedImpactTick,
            WarheadDeliveryMode.SINGLE));
        return false;
    }

    public static synchronized double readinessPercent(final ServerLevel level,
        final UUID impactId) {
        LevelState state = LEVELS.get(level);
        Preparation preparation = state == null ? null : state.byImpact.get(impactId);
        if (preparation == null) return 0.0;
        if (preparation.readyPlans.containsKey(impactId)) return 100.0;
        PreparedImpactSpec spec = preparation.spec(impactId);
        ImpactCompile compile = preparation.compiles.get(impactId);
        WarheadSnapshotRequirement requirement = null;
        for (WarheadSnapshotRequirement candidate : preparation.requirements) {
            if (candidate.impact().impactId().equals(impactId)) {
                requirement = candidate;
                break;
            }
        }
        if (spec == null || compile == null || requirement == null) return 0.0;
        int required = Math.max(1, requirement.footprint().requiredChunkCount());
        WarheadPreparationLeaseManager.LeaseSnapshot lease =
            WarheadPreparationLeaseManager.snapshot(level, preparation.id);
        int impactSnapshots = 0;
        for (long packed : requirement.footprint().requiredChunks()) {
            if (preparation.snapshots.containsKey(packed)) impactSnapshots++;
        }
        double ready = Math.min(1.0, lease.readyChunks()
            / (double)Math.max(1, lease.requiredChunks()));
        double snapshotted = Math.min(1.0, impactSnapshots / (double)required);
        double compiled = Math.min(1.0, compile.chunks.size() / (double)required);
        return (ready * 0.30 + snapshotted * 0.30 + compiled * 0.40) * 100.0;
    }

    public static synchronized PreparationProgress progress(final ServerLevel level,
        final UUID preparationId) {
        LevelState state = LEVELS.get(level);
        Preparation preparation = state == null ? null : state.preparations.get(preparationId);
        return preparation == null ? new PreparationProgress(PreparationState.CANCELLED,
            0, 0, 0, 0, 0, 0) : preparation.progress(level);
    }

    public static synchronized ConsumedPreparedImpact consumeReadyImpact(
        final ServerLevel level, final UUID impactId, final Vec3 actualCenter,
        final WarheadYield yield, final long seed) {
        LevelState state = LEVELS.get(level);
        Preparation preparation = state == null ? null : state.byImpact.get(impactId);
        if (preparation == null) return null;
        PreparedImpactSpec spec = preparation.spec(impactId);
        if (spec == null || spec.yield() != yield || spec.seed() != seed
            || spec.target().distanceToSqr(actualCenter) > TARGET_COMPATIBILITY_SQR) return null;
        PreparedImpactPlan plan = preparation.readyPlans.remove(impactId);
        if (plan == null) return null;
        state.byImpact.remove(impactId);
        preparation.consumedImpacts.add(impactId);
        preparation.activeCommits++;
        preparation.state = PreparationState.COMMITTING;
        return new ConsumedPreparedImpact(preparation.id, plan);
    }

    /**
     * Seals the physical impact without waiting for terrain preparation. Any chunks
     * already compiled are handed to the committer immediately; remaining chunks
     * keep streaming through the same preparation after impact.
     */
    public static synchronized ConsumedPreparedImpact sealImpact(
        final ServerLevel level, final UUID preparationId, final UUID impactId,
        final UUID radarRootTrackId, final Vec3 actualCenter,
        final WarheadYield yield, final long seed, final boolean customFire) {
        if (level == null || preparationId == null || impactId == null
            || radarRootTrackId == null || actualCenter == null
            || !actualCenter.isFinite() || yield == null || !yield.nuclear()) return null;
        ensureImpact(level, preparationId, impactId, radarRootTrackId, actualCenter,
            yield, seed, customFire, level.getGameTime());
        LevelState state = LEVELS.get(level);
        Preparation preparation = state == null ? null : state.byImpact.get(impactId);
        if (preparation == null) return null;
        PreparedImpactSpec spec = preparation.spec(impactId);
        ImpactCompile compile = preparation.compiles.get(impactId);
        WarheadSnapshotRequirement requirement = preparation.requirement(impactId);
        if (spec == null || compile == null || requirement == null
            || spec.yield() != yield || spec.seed() != seed) return null;
        if (!compile.sealed) {
            compile.sealed = true;
            preparation.consumedImpacts.add(impactId);
            preparation.activeCommits++;
        }
        preparation.readyPlans.remove(impactId);
        preparation.state = PreparationState.IMPACT_SEALED;
        Long2ObjectOpenHashMap<PreparedChunkPlan> available =
            new Long2ObjectOpenHashMap<>();
        for (PreparedChunkPlan chunk : compile.chunks.values()) {
            long packed = chunk.chunk().pack();
            if (compile.deliveredChunks.add(packed)) available.put(packed, chunk);
        }
        WarheadLifecycleDiagnostics.impactSealed(level, impactId, compile.chunks.size(),
            compile.inFlightChunks.size(), requirement.footprint().requiredChunkCount());
        return new ConsumedPreparedImpact(preparation.id,
            new PreparedImpactPlan(impactId, spec.target(), requirement.footprint(),
                available, FINAL_ACTIVATION_TICK, compile.statistics));
    }

    /** Drains newly compiled immutable chunks for an impact-sealed commit. */
    public static synchronized List<PreparedChunkPlan> drainPreparedChunks(
        final ServerLevel level, final UUID preparationId, final UUID impactId) {
        LevelState state = LEVELS.get(level);
        Preparation preparation = state == null ? null
            : state.preparations.get(preparationId);
        ImpactCompile compile = preparation == null ? null
            : preparation.compiles.get(impactId);
        if (compile == null || !compile.sealed) return List.of();
        ArrayList<PreparedChunkPlan> drained = new ArrayList<>();
        for (PreparedChunkPlan chunk : compile.chunks.values()) {
            if (compile.deliveredChunks.add(chunk.chunk().pack())) drained.add(chunk);
        }
        drained.sort(Comparator.comparingInt(PreparedChunkPlan::activationTick)
            .thenComparingLong(chunk -> chunk.chunk().pack()));
        return List.copyOf(drained);
    }

    /** True when no further prepared chunks can arrive for this sealed impact. */
    public static synchronized boolean impactStreamClosed(final ServerLevel level,
        final UUID preparationId, final UUID impactId) {
        return impactStreamState(level, preparationId, impactId)
            == ImpactStreamState.COMPLETE;
    }

    static synchronized ImpactStreamState impactStreamState(final ServerLevel level,
        final UUID preparationId, final UUID impactId) {
        LevelState state = LEVELS.get(level);
        Preparation preparation = state == null ? null
            : state.preparations.get(preparationId);
        ImpactCompile compile = preparation == null ? null
            : preparation.compiles.get(impactId);
        WarheadSnapshotRequirement requirement = preparation == null ? null
            : preparation.requirement(impactId);
        return ImpactStreamPolicy.state(preparation != null,
            preparation != null && preparation.cancelled,
            compile != null && requirement != null,
            compile != null && compile.failed,
            requirement == null ? null : requirement.footprint().requiredChunks(),
            compile == null ? null : compile.chunks.keySet(),
            compile == null ? null : compile.inFlightChunks);
    }

    public static synchronized void completeCommit(final ServerLevel level,
        final UUID preparationId, final UUID impactId) {
        LevelState state = LEVELS.get(level);
        Preparation preparation = state == null ? null : state.preparations.get(preparationId);
        if (preparation == null) return;
        preparation.activeCommits = Math.max(0, preparation.activeCommits - 1);
        preparation.completedImpacts.add(impactId);
        cleanupIfTerminal(level, state, preparation);
    }

    public static synchronized void cancelImpact(final ServerLevel level,
        final UUID impactId, final CancellationReason reason) {
        LevelState state = LEVELS.get(level);
        Preparation preparation = state == null ? null : state.byImpact.get(impactId);
        if (preparation == null) return;
        state.byImpact.remove(impactId);
        preparation.cancelledImpacts.add(impactId);
        preparation.readyPlans.remove(impactId);
        WarheadLifecycleDiagnostics.impactCancelled(level, impactId, reason.name());
        cleanupIfTerminal(level, state, preparation);
    }

    public static synchronized void cancelPreparation(final ServerLevel level,
        final UUID preparationId, final CancellationReason reason) {
        LevelState state = LEVELS.get(level);
        Preparation preparation = state == null ? null : state.preparations.get(preparationId);
        if (preparation != null) cancelPreparation(level, state, preparation, reason);
    }

    static synchronized int activePreparationCount(final ServerLevel level) {
        LevelState state = LEVELS.get(level);
        return state == null ? 0 : state.preparations.size();
    }

    private static synchronized void tick(final ServerLevel level) {
        LevelState levelState = LEVELS.get(level);
        if (levelState == null) return;
        long now = level.getGameTime();
        drainCompiled(level, levelState);
        evictCache(level, levelState, now);
        int snapshotsRemaining = MAX_SNAPSHOTS_PER_LEVEL_TICK;
        long deadline = System.nanoTime() + SNAPSHOT_BUDGET_NANOS;
        for (Preparation preparation : List.copyOf(levelState.preparations.values())) {
            if (preparation.cancelled) continue;
            recordProgress(level, preparation);
            if (preparation.activeCommits == 0 && now >= preparation.expiresAt) {
                cancelPreparation(level, levelState, preparation, CancellationReason.TIMEOUT);
                continue;
            }
            if (preparation.state == PreparationState.READY) continue;
            if (snapshotsRemaining <= 0 || System.nanoTime() >= deadline) continue;
            if (preparation.activeCommits == 0) {
                preparation.state = preparation.snapshots.isEmpty()
                    ? PreparationState.ACQUIRING_CHUNKS : PreparationState.SNAPSHOTTING;
            }
            int used = captureReadySnapshots(level, levelState, preparation,
                snapshotsRemaining, deadline);
            snapshotsRemaining -= used;
            publishIfComplete(level, levelState, preparation);
        }
        if (levelState.preparations.isEmpty()) LEVELS.remove(level);
    }

    private static int captureReadySnapshots(final ServerLevel level,
        final LevelState levelState, final Preparation preparation,
        final int budget, final long deadline) {
        int captured = 0;
        int inspected = 0;
        while (captured < budget && inspected < preparation.snapshotOrder.size()
            && System.nanoTime() < deadline) {
            if (preparation.snapshotCursor >= preparation.snapshotOrder.size()) {
                preparation.snapshotCursor = 0;
            }
            long packed = preparation.snapshotOrder.getLong(preparation.snapshotCursor++);
            inspected++;
            if (preparation.snapshots.containsKey(packed)
                || preparation.publishedRevisions.containsKey(packed)
                || !WarheadPreparationLeaseManager.chunkReady(level, preparation.id, packed)) continue;
            WarheadChunkSnapshot snapshot = cachedSnapshot(level, levelState, preparation, packed);
            if (snapshot == null) {
                long snapshotStarted = WarModPerformanceDiagnostics.begin();
                snapshot = WarheadWorldSnapshotter.capture(level, ChunkPos.unpack(packed),
                    preparation.requirements, preparation.metadata);
                WarModPerformanceDiagnostics.record(
                    WarModPerformanceDiagnostics.Subsystem.WARHEAD_SNAPSHOT_CAPTURE,
                    snapshotStarted);
                if (snapshot == null) continue;
                levelState.snapshotCache.put(packed,
                    new CachedSnapshot(snapshot, level.getGameTime() + CACHE_LIFETIME_TICKS));
                WarModPerformanceDiagnostics.add(
                    WarModPerformanceDiagnostics.Gauge.WARHEAD_SNAPSHOTS_CAPTURED, 1L);
                WarModPerformanceDiagnostics.add(
                    WarModPerformanceDiagnostics.Gauge.WARHEAD_SNAPSHOT_BYTES_COPIED,
                    snapshot.estimatedBytes());
                WarModPerformanceDiagnostics.add(
                    WarModPerformanceDiagnostics.Gauge.WARHEAD_SNAPSHOT_SECTIONS_COPIED,
                    snapshot.copiedSectionCount());
                WarModPerformanceDiagnostics.add(
                    WarModPerformanceDiagnostics.Gauge.WARHEAD_SNAPSHOT_BLOCK_IDS_COPIED,
                    snapshot.copiedBlockStateIdCount());
                captured++;
            }
            preparation.snapshots.put(packed, snapshot);
            scheduleCompiles(preparation, snapshot);
        }
        if (preparation.activeCommits == 0
            && preparation.snapshots.size() + preparation.publishedRevisions.size()
                == preparation.snapshotOrder.size()
            && preparation.inFlight > 0) preparation.state = PreparationState.COMPILING;
        return captured;
    }

    private static WarheadChunkSnapshot cachedSnapshot(final ServerLevel level,
        final LevelState state, final Preparation preparation, final long packed) {
        CachedSnapshot cached = state.snapshotCache.get(packed);
        if (cached == null || cached.expiresAt < level.getGameTime()) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(
            ChunkPos.getX(packed), ChunkPos.getZ(packed));
        if (chunk == null || ((WarheadChunkRevisionAccess)(Object)chunk)
            .war_mod$getChunkRevision() != cached.snapshot.chunkRevision()) return null;
        ChunkPos chunkPosition = ChunkPos.unpack(packed);
        int[] band = requiredCraterBand(preparation, chunkPosition);
        int features = WarheadWorldSnapshotter.requiredFeatures(chunkPosition,
            preparation.requirements);
        if (!cached.snapshot.covers(features, band[0], band[1])) return null;
        return cached.snapshot;
    }

    private static void scheduleCompiles(final Preparation preparation,
        final WarheadChunkSnapshot snapshot) {
        for (WarheadSnapshotRequirement requirement : preparation.requirements) {
            if (!requirement.footprint().requiredChunks().contains(snapshot.chunk().pack())) continue;
            ImpactCompile compile = preparation.compiles.get(requirement.impact().impactId());
            if (compile.failed || compile.chunks.containsKey(snapshot.chunk().pack())
                || compile.inFlightChunks.contains(snapshot.chunk().pack())) continue;
            compile.inFlightChunks.add(snapshot.chunk().pack());
            preparation.inFlight++;
            long generation = preparation.generation;
            COMPILER.execute(() -> {
                long compileStarted = WarModPerformanceDiagnostics.begin();
                try {
                    PreparedChunkPlan plan = WarheadPlanCompiler.compile(requirement.impact(),
                        requirement.footprint(), snapshot, preparation.palette);
                    preparation.results.add(new CompileResult(generation,
                        requirement.impact().impactId(), snapshot.chunk().pack(), plan, null));
                } catch (Throwable failure) {
                    preparation.results.add(new CompileResult(generation,
                        requirement.impact().impactId(), snapshot.chunk().pack(), null, failure));
                } finally {
                    WarModPerformanceDiagnostics.record(
                        WarModPerformanceDiagnostics.Subsystem.WARHEAD_WORKER_COMPILE,
                        compileStarted);
                }
            });
        }
    }

    private static void drainCompiled(final ServerLevel level, final LevelState state) {
        int remaining = MAX_COMPILED_RESULTS_PER_LEVEL_TICK;
        for (Preparation preparation : List.copyOf(state.preparations.values())) {
            CompileResult result;
            while (remaining-- > 0 && (result = preparation.results.poll()) != null) {
                if (result.generation != preparation.generation) continue;
                preparation.inFlight = Math.max(0, preparation.inFlight - 1);
                ImpactCompile compile = preparation.compiles.get(result.impactId);
                if (compile == null) continue;
                compile.inFlightChunks.remove(result.packedChunk);
                if (result.failure != null) {
                    int retry = compile.compileRetries.addTo(result.packedChunk, 1) + 1;
                    if (retry <= MAX_COMPILE_RETRIES) {
                        preparation.snapshots.remove(result.packedChunk);
                        preparation.publishedRevisions.remove(result.packedChunk);
                        state.snapshotCache.remove(result.packedChunk);
                        preparation.state = PreparationState.SNAPSHOTTING;
                        WarMod.LOGGER.warn("Warhead preparation compiler rejected {} chunk {}; "
                            + "resnapshot retry {}/{}", result.impactId,
                            ChunkPos.unpack(result.packedChunk), retry,
                            MAX_COMPILE_RETRIES, result.failure);
                        continue;
                    }
                    compile.failed = true;
                    compile.failure = result.failure;
                    WarMod.LOGGER.error("Warhead preparation compiler terminal failure for {} "
                        + "chunk {} after {} retries", result.impactId,
                        ChunkPos.unpack(result.packedChunk), MAX_COMPILE_RETRIES,
                        result.failure);
                    continue;
                }
                compile.chunks.put(result.packedChunk, result.plan);
                WarheadLifecycleDiagnostics.chunkPrepared(level, result.impactId);
                compile.statistics = compile.statistics.add(
                    WarheadPlanCompiler.statistics(result.plan));
            }
            publishIfComplete(level, state, preparation);
        }
    }

    private static void publishIfComplete(final ServerLevel level,
        final LevelState state, final Preparation preparation) {
        if (preparation.cancelled) return;
        boolean allComplete = true;
        for (WarheadSnapshotRequirement requirement : preparation.requirements) {
            ImpactCompile compile = preparation.compiles.get(requirement.impact().impactId());
            boolean complete = !compile.failed && compile.inFlightChunks.isEmpty()
                && containsAllRequiredChunks(compile, requirement);
            allComplete &= complete;
            if (complete && !compile.readyReported) {
                compile.readyReported = true;
                WarheadLifecycleDiagnostics.planReady(level, preparation.id,
                    requirement.impact().impactId(), compile.statistics,
                    snapshotBytes(preparation));
            }
            if (!complete || compile.sealed
                || preparation.readyPlans.containsKey(requirement.impact().impactId())) continue;
            preparation.readyPlans.put(requirement.impact().impactId(), new PreparedImpactPlan(
                requirement.impact().impactId(), requirement.impact().target(),
                requirement.footprint(), compile.chunks, FINAL_ACTIVATION_TICK,
                compile.statistics));
        }
        if (!allComplete) return;
        for (long packed : preparation.snapshotOrder) {
            WarheadChunkSnapshot snapshot = preparation.snapshots.get(packed);
            if (snapshot != null) {
                preparation.publishedRevisions.put(packed, snapshot.chunkRevision());
            }
        }
        /* Plans own only primitive mutation arrays and source revisions. The large
         * per-preparation world snapshots are no longer retained after publication. */
        preparation.snapshots.clear();
        preparation.state = preparation.activeCommits > 0
            ? PreparationState.IMPACT_SEALED : PreparationState.READY;
    }

    private static int[] requiredCraterBand(final Preparation preparation,
        final ChunkPos chunk) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (WarheadSnapshotRequirement requirement : preparation.requirements) {
            PreparedImpactSpec impact = requirement.impact();
            NuclearTerrainProfile profile = requirement.footprint().terrainProfile();
            if (!WarheadFootprintCalculator.chunkIntersectsCircle(chunk.x(), chunk.z(),
                impact.target().x, impact.target().z, profile.horizontalRadius() + 1.0)) continue;
            int centerY = (int)Math.floor(impact.target().y);
            minimum = Math.min(minimum, centerY - Mth.ceil(profile.downwardRadius()) - 1);
            maximum = Math.max(maximum, centerY + Mth.ceil(profile.upwardRadius()) + 1);
        }
        return new int[] {minimum, maximum};
    }

    private static boolean containsAllRequiredChunks(final ImpactCompile compile,
        final WarheadSnapshotRequirement requirement) {
        return ImpactStreamPolicy.containsAllRequiredChunks(
            requirement.footprint().requiredChunks(), compile.chunks.keySet());
    }

    private static void acquireLease(final ServerLevel level,
        final Preparation preparation) {
        ArrayList<WarheadPreparationLeaseTarget> targets = new ArrayList<>();
        for (WarheadSnapshotRequirement requirement : preparation.requirements) {
            targets.add(new WarheadPreparationLeaseTarget(requirement.impact().target(),
                requirement.footprint()));
        }
        WarheadPreparationLeaseManager.acquireOrReplace(level, preparation.id,
            targets, preparation.expiresAt);
    }

    private static void recordRequest(final ServerLevel level,
        final Preparation preparation) {
        ArrayList<PreparedImpactSpec> impacts = new ArrayList<>();
        ArrayList<WarheadFootprint> footprints = new ArrayList<>();
        LongOpenHashSet union = new LongOpenHashSet();
        for (WarheadSnapshotRequirement requirement : preparation.requirements) {
            impacts.add(requirement.impact());
            footprints.add(requirement.footprint());
            union.addAll(requirement.footprint().requiredChunks());
        }
        WarheadLifecycleDiagnostics.requested(level, preparation.id,
            preparation.request.radarRootTrackId(), impacts, footprints,
            preparation.request.deliveryMode(), preparation.expectedImpactTick,
            union.size());
    }

    private static void recordProgress(final ServerLevel level,
        final Preparation preparation) {
        PreparationProgress progress = preparation.progress(level);
        WarheadLifecycleDiagnostics.progress(level, preparation.id, progress,
            snapshotBytes(preparation));
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.WARHEAD_REQUIRED_CHUNKS,
            progress.requiredChunks());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.WARHEAD_TICKETED_CHUNKS,
            progress.ticketedChunks());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.WARHEAD_READY_CHUNKS,
            progress.readyChunks());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.WARHEAD_SNAPSHOTTED_CHUNKS,
            progress.snapshottedChunks());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.WARHEAD_COMPILED_CHUNKS,
            progress.compiledChunks());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.WARHEAD_READY_PLANS,
            progress.publishedImpactPlans());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.WARHEAD_GLOBAL_PLAN_INVALIDATIONS, 0L);
    }

    private static long snapshotBytes(final Preparation preparation) {
        long total = 0L;
        for (WarheadChunkSnapshot snapshot : preparation.snapshots.values()) {
            total += snapshot.estimatedBytes();
        }
        return total;
    }

    private static void cleanupIfTerminal(final ServerLevel level, final LevelState state,
        final Preparation preparation) {
        int accounted = preparation.cancelledImpacts.size()
            + preparation.consumedImpacts.size();
        if (preparation.activeCommits > 0 || accounted < preparation.request.impacts().size()) return;
        preparation.state = PreparationState.COMPLETE;
        detachImpactMappings(state, preparation);
        state.preparations.remove(preparation.id);
        WarheadPreparationLeaseManager.release(level, preparation.id);
    }

    private static void cancelPreparation(final ServerLevel level, final LevelState state,
        final Preparation preparation, final CancellationReason reason) {
        if (preparation.cancelled) return;
        preparation.cancelled = true;
        preparation.state = PreparationState.CANCELLED;
        preparation.generation++;
        detachImpactMappings(state, preparation);
        state.preparations.remove(preparation.id);
        WarheadLifecycleDiagnostics.cancelled(level, preparation.id, reason.name());
        WarheadPreparationLeaseManager.release(level, preparation.id);
        WarMod.LOGGER.debug("Cancelled warhead preparation {}: {}", preparation.id, reason);
    }

    private static void attachImpactMappings(final LevelState state,
        final Preparation preparation) {
        for (PreparedImpactSpec impact : preparation.request.impacts()) {
            state.byImpact.put(impact.impactId(), preparation);
        }
    }

    private static void detachImpactMappings(final LevelState state,
        final Preparation preparation) {
        state.byImpact.entrySet().removeIf(entry -> entry.getValue() == preparation);
    }

    private static void evictCache(final ServerLevel level, final LevelState state,
        final long now) {
        Iterator<Long2ObjectOpenHashMap.Entry<CachedSnapshot>> iterator =
            state.snapshotCache.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().expiresAt < now) iterator.remove();
        }
    }

    private static void clearLevel(final ServerLevel level, final CancellationReason reason) {
        LevelState state = LEVELS.remove(level);
        if (state == null) return;
        for (Preparation preparation : List.copyOf(state.preparations.values())) {
            preparation.cancelled = true;
            preparation.state = PreparationState.CANCELLED;
            preparation.generation++;
            WarheadLifecycleDiagnostics.cancelled(level, preparation.id, reason.name());
            WarheadPreparationLeaseManager.release(level, preparation.id);
        }
        state.clear();
    }

    private static synchronized void clearAll() {
        for (ServerLevel level : List.copyOf(LEVELS.keySet())) {
            clearLevel(level, CancellationReason.SERVER_STOP);
        }
        LEVELS.clear();
    }

    private static void validate(final ServerLevel level,
        final WarheadPreparationRequest request) {
        if (level == null || request == null || request.dimension() != level.dimension()) {
            throw new IllegalArgumentException("Preparation dimension mismatch");
        }
        for (PreparedImpactSpec impact : request.impacts()) {
            if (!impact.yield().nuclear()) {
                throw new IllegalArgumentException("Prepared terrain pipeline is nuclear-only");
            }
        }
    }

    private static long expiry(final ServerLevel level, final long expectedImpactTick) {
        return Math.max(level.getGameTime() + MINIMUM_LIFETIME_TICKS,
            expectedImpactTick + IcbmConstants.IMPACT_CHUNK_TAIL_TICKS);
    }

    private static boolean compatible(final PreparedImpactSpec current,
        final Vec3 target, final WarheadYield yield, final long seed,
        final boolean customFire) {
        return current.yield() == yield && current.seed() == seed
            && current.customFire() == customFire
            && current.target().distanceToSqr(target) <= TARGET_COMPATIBILITY_SQR;
    }

    private static final class LevelState {
        private final Map<UUID, Preparation> preparations = new LinkedHashMap<>();
        private final Map<UUID, Preparation> byImpact = new HashMap<>();
        private final Long2ObjectOpenHashMap<CachedSnapshot> snapshotCache =
            new Long2ObjectOpenHashMap<>();
        private final WarheadStateMetadata metadata;

        private LevelState(final ServerLevel level) {
            metadata = WarheadStateMetadata.capture(level);
        }

        private void clear() {
            preparations.clear();
            byImpact.clear();
            snapshotCache.clear();
        }
    }

    private static final class Preparation {
        private final UUID id;
        private final Handle handle;
        private final WarheadStatePalette palette;
        private final WarheadStateMetadata metadata;
        private final ConcurrentLinkedQueue<CompileResult> results =
            new ConcurrentLinkedQueue<>();
        private final Long2ObjectOpenHashMap<WarheadChunkSnapshot> snapshots =
            new Long2ObjectOpenHashMap<>();
        private final Long2LongOpenHashMap publishedRevisions =
            new Long2LongOpenHashMap();
        private final Map<UUID, ImpactCompile> compiles = new HashMap<>();
        private final Map<UUID, PreparedImpactPlan> readyPlans = new HashMap<>();
        private final Set<UUID> consumedImpacts = new java.util.HashSet<>();
        private final Set<UUID> completedImpacts = new java.util.HashSet<>();
        private final Set<UUID> cancelledImpacts = new java.util.HashSet<>();
        private WarheadPreparationRequest request;
        private List<WarheadSnapshotRequirement> requirements;
        private LongArrayList snapshotOrder;
        private PreparationState state = PreparationState.REQUESTED;
        private long expectedImpactTick;
        private long expiresAt;
        private long generation;
        private int snapshotCursor;
        private int inFlight;
        private int activeCommits;
        private boolean cancelled;

        private Preparation(final ServerLevel level, final WarheadPreparationRequest request,
            final WarheadStateMetadata metadata) {
            this.id = request.preparationId();
            this.handle = new Handle(level, id);
            this.palette = WarheadStatePalette.capture();
            this.metadata = metadata;
            retarget(request);
        }

        private void retarget(final WarheadPreparationRequest revised) {
            generation++;
            request = revised;
            expectedImpactTick = revised.expectedImpactTick();
            expiresAt = expiry(handle.level, expectedImpactTick);
            requirements = revised.impacts().stream().map(impact ->
                new WarheadSnapshotRequirement(impact,
                    WarheadFootprintCalculator.calculate(impact.payload(),
                        impact.yield(), impact.target()))).toList();
            LongOpenHashSet union = new LongOpenHashSet();
            for (WarheadSnapshotRequirement requirement : requirements) {
                union.addAll(requirement.footprint().requiredChunks());
            }
            ArrayList<Long> ordered = new ArrayList<>(union.size());
            for (long packed : union) ordered.add(packed);
            ordered.sort(Comparator.comparingInt((Long packed) -> minimumActivationTick(
                requirements, ChunkPos.unpack(packed.longValue())))
                .thenComparingDouble(packed -> minimumDistanceSqr(
                    requirements, ChunkPos.unpack(packed.longValue())))
                .thenComparingLong(Long::longValue));
            snapshotOrder = new LongArrayList(ordered.size());
            for (long packed : ordered) snapshotOrder.add(packed);
            snapshots.keySet().removeIf(packed -> {
                if (!union.contains((long)packed)) return true;
                WarheadChunkSnapshot snapshot = snapshots.get((long)packed);
                int[] band = requiredCraterBand(this, ChunkPos.unpack((long)packed));
                return band[1] >= band[0] && (snapshot.craterMinimumY() > band[0]
                    || snapshot.craterMaximumY() < band[1]);
            });
            publishedRevisions.clear();
            compiles.clear();
            for (WarheadSnapshotRequirement requirement : requirements) {
                compiles.put(requirement.impact().impactId(), new ImpactCompile());
            }
            readyPlans.clear();
            results.clear();
            inFlight = 0;
            snapshotCursor = 0;
            cancelled = false;
            state = PreparationState.REQUESTED;
            for (WarheadChunkSnapshot snapshot : snapshots.values()) scheduleCompiles(this, snapshot);
        }

        private boolean matches(final WarheadPreparationRequest candidate) {
            return request.dimension() == candidate.dimension()
                && request.radarRootTrackId().equals(candidate.radarRootTrackId())
                && request.deliveryMode() == candidate.deliveryMode()
                && request.impacts().equals(candidate.impacts());
        }

        private PreparedImpactSpec spec(final UUID impactId) {
            for (PreparedImpactSpec spec : request.impacts()) {
                if (spec.impactId().equals(impactId)) return spec;
            }
            return null;
        }

        private WarheadSnapshotRequirement requirement(final UUID impactId) {
            for (WarheadSnapshotRequirement requirement : requirements) {
                if (requirement.impact().impactId().equals(impactId)) return requirement;
            }
            return null;
        }

        private PreparationProgress progress(final ServerLevel level) {
            WarheadPreparationLeaseManager.LeaseSnapshot lease =
                WarheadPreparationLeaseManager.snapshot(level, id);
            int compiled = 0;
            for (long packed : snapshotOrder) {
                boolean ready = true;
                for (WarheadSnapshotRequirement requirement : requirements) {
                    if (!requirement.footprint().requiredChunks().contains(packed)) continue;
                    if (!compiles.get(requirement.impact().impactId()).chunks.containsKey(packed)) {
                        ready = false;
                        break;
                    }
                }
                if (ready) compiled++;
            }
            int snapshotted = snapshots.size() + publishedRevisions.size();
            return new PreparationProgress(state, snapshotOrder.size(),
                lease.ticketedChunks(), lease.readyChunks(), snapshotted, compiled,
                readyPlans.size());
        }
    }

    private static double minimumDistanceSqr(
        final List<WarheadSnapshotRequirement> requirements, final ChunkPos chunk) {
        double minimum = Double.POSITIVE_INFINITY;
        for (WarheadSnapshotRequirement requirement : requirements) {
            Vec3 center = requirement.impact().target();
            double dx = chunk.getMiddleBlockX() + 0.5 - center.x;
            double dz = chunk.getMiddleBlockZ() + 0.5 - center.z;
            minimum = Math.min(minimum, dx * dx + dz * dz);
        }
        return minimum;
    }

    private static int minimumActivationTick(
        final List<WarheadSnapshotRequirement> requirements, final ChunkPos chunk) {
        int earliest = FINAL_ACTIVATION_TICK;
        for (WarheadSnapshotRequirement requirement : requirements) {
            if (!requirement.footprint().requiredChunks().contains(chunk.pack())) continue;
            PreparedImpactSpec impact = requirement.impact();
            if (WarheadFootprintCalculator.chunkIntersectsCircle(chunk.x(), chunk.z(),
                impact.target().x, impact.target().z,
                requirement.footprint().craterRadius() + 1.0)) return -1;
            earliest = Math.min(earliest, WarheadPlanCompiler.radialActivationTick(
                impact.target(), requirement.footprint().maximumMutationRadius(), chunk));
        }
        return earliest;
    }

    private static final class ImpactCompile {
        private final Long2ObjectOpenHashMap<PreparedChunkPlan> chunks =
            new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet inFlightChunks = new LongOpenHashSet();
        private final LongOpenHashSet deliveredChunks = new LongOpenHashSet();
        private final Long2IntOpenHashMap compileRetries = new Long2IntOpenHashMap();
        private PlanStatistics statistics = PlanStatistics.empty();
        private boolean sealed;
        private boolean readyReported;
        private boolean failed;
        private Throwable failure;

        private void recalculateStatistics() {
            statistics = PlanStatistics.empty();
            for (PreparedChunkPlan plan : chunks.values()) {
                statistics = statistics.add(WarheadPlanCompiler.statistics(plan));
            }
        }
    }

    private static final class Handle implements WarheadPreparationHandle {
        private final ServerLevel level;
        private final UUID id;

        private Handle(final ServerLevel level, final UUID id) {
            this.level = level;
            this.id = id;
        }

        @Override public UUID id() { return id; }
        @Override public PreparationState state() { return progress().state(); }
        @Override public PreparationProgress progress() {
            return WarheadPreparationCoordinator.progress(level, id);
        }
        @Override public boolean ready() { return state() == PreparationState.READY; }
        @Override public void retarget(final List<PreparedImpactSpec> impacts,
            final long expectedImpactTick) {
            synchronized (WarheadPreparationCoordinator.class) {
                LevelState state = LEVELS.get(level);
                Preparation preparation = state == null ? null : state.preparations.get(id);
                if (preparation == null) return;
                request(level, new WarheadPreparationRequest(id,
                    preparation.request.radarRootTrackId(), level.dimension(), impacts,
                    expectedImpactTick, preparation.request.deliveryMode()));
            }
        }
        @Override public PreparedImpactPlan consumeReadyPlan(final UUID impactId) {
            synchronized (WarheadPreparationCoordinator.class) {
                LevelState state = LEVELS.get(level);
                Preparation preparation = state == null ? null : state.preparations.get(id);
                if (preparation == null) return null;
                PreparedImpactPlan plan = preparation.readyPlans.remove(impactId);
                if (plan == null) return null;
                state.byImpact.remove(impactId);
                preparation.consumedImpacts.add(impactId);
                preparation.activeCommits++;
                preparation.state = PreparationState.COMMITTING;
                return plan;
            }
        }
        @Override public void cancel(final CancellationReason reason) {
            WarheadPreparationCoordinator.cancelPreparation(level, id, reason);
        }
    }

    private record CachedSnapshot(WarheadChunkSnapshot snapshot, long expiresAt) { }
    private record CompileResult(long generation, UUID impactId, long packedChunk,
        PreparedChunkPlan plan, Throwable failure) { }
}
