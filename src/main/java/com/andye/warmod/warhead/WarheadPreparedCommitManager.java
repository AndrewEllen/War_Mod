package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.diagnostics.WarheadLifecycleDiagnostics;
import com.andye.warmod.warhead.network.ClientboundWarheadTerrainCommitPayload;
import com.andye.warmod.warhead.obscuration.NuclearTerrainObscurationEmitter;
import com.andye.warmod.worldgen.ModBiomes;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Main-thread streaming bulk commit with bounded 0..15 radial buckets and tracked sync. */
public final class WarheadPreparedCommitManager {
    private static final byte IMMEDIATE_APPLIED = 1;
    private static final byte RADIAL_APPLIED = 2;
    private static final int FINAL_APPLICATION_TICK = 15;
    private static final int MAX_FALLBACK_COMPILES_PER_TICK = 2;
    private static final long FALLBACK_COMPILE_BUDGET_NANOS = 3_000_000L;
    private static final int CLIENT_ACK_TIMEOUT_TICKS = 40;
    private static final int MAX_CRATER_FIRE = 2_048;
    private static final int MAX_TREE_FIRE = 2_048;
    private static final int MAX_GROUND_FIRE = 2_048;
    /* ThreadedLevelLightEngine exposes exact block checks but no safe live
     * section-data reset in 26.2. Keep sparse surface damage section-local and
     * retain the cheaper full rebuild for densely rewritten crater chunks. */
    private static final int MAX_EXACT_LIGHT_CHECKS = 512;
    private static final int UPDATE_FLAGS = Block.UPDATE_KNOWN_SHAPE
        | Block.UPDATE_SUPPRESS_DROPS;
    private static final EnumSet<Heightmap.Types> LIVE_HEIGHTMAPS = EnumSet.of(
        Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR,
        Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    private static final Map<ServerLevel, ArrayDeque<Commit>> COMMITS =
        new IdentityHashMap<>();
    private static boolean registered;

    private WarheadPreparedCommitManager() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(WarheadPreparedCommitManager::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> clearLevel(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
        registered = true;
    }

    public static synchronized boolean begin(final ServerLevel level,
        final UUID preparationId, final PreparedImpactPlan plan,
        final @Nullable ServerPlayer source, final WarheadYield yield,
        final long visualSeed) {
        return begin(level, preparationId, plan, source, yield, visualSeed, false);
    }

    public static synchronized boolean begin(final ServerLevel level,
        final UUID preparationId, final PreparedImpactPlan plan,
        final @Nullable ServerPlayer source, final WarheadYield yield,
        final long visualSeed, final boolean customFire) {
        return begin(level, preparationId, plan, source, yield, visualSeed,
            customFire, false);
    }

    /**
     * Last-resort entry when no detachable preparation survived until impact. The
     * commit still uses the detached snapshot/compiler policy and the normal
     * radial/category application path; only ownership and snapshot capture move
     * to the bounded server-thread fallback.
     */
    public static synchronized boolean beginLiveFallback(final ServerLevel level,
        final UUID impactId, final Vec3 center, final @Nullable ServerPlayer source,
        final WarheadYield yield, final long visualSeed, final boolean customFire) {
        if (level == null || impactId == null || center == null || !center.isFinite()
            || yield == null || !yield.nuclear()) return false;
        if (active(level, impactId)) return true;
        WarheadFootprint footprint = WarheadFootprintCalculator.calculate(
            yield.payloadType(), yield, center);
        PreparedImpactPlan plan = new PreparedImpactPlan(impactId, center, footprint,
            new Long2ObjectOpenHashMap<>(), FINAL_APPLICATION_TICK,
            PlanStatistics.empty());
        return begin(level, UUID.randomUUID(), plan, source, yield, visualSeed,
            customFire, true);
    }

    public static synchronized boolean active(final ServerLevel level,
        final UUID impactId) {
        ArrayDeque<Commit> commits = COMMITS.get(level);
        if (commits == null || impactId == null) return false;
        for (Commit commit : commits) {
            if (commit.plan.impactId().equals(impactId)) return true;
        }
        return false;
    }

    private static boolean begin(final ServerLevel level,
        final UUID preparationId, final PreparedImpactPlan plan,
        final @Nullable ServerPlayer source, final WarheadYield yield,
        final long visualSeed, final boolean customFire,
        final boolean detachedFallback) {
        if (level == null || preparationId == null || plan == null || yield == null) return false;
        registerLifecycle();
        ArrayDeque<Commit> commits = COMMITS.computeIfAbsent(level,
            ignored -> new ArrayDeque<>());
        for (Commit active : commits) {
            if (active.plan.impactId().equals(plan.impactId())) return false;
        }
        Commit commit = new Commit(preparationId, plan,
            source == null ? null : source.getUUID(), yield, visualSeed,
            customFire, level.getGameTime());
        commit.addChunks(plan.chunks().values(), false);
        commits.addLast(commit);
        WarheadLifecycleDiagnostics.commitStarted(level, plan.impactId());
        if (detachedFallback) commit.startFallback(level, "preparation_unavailable");
        /* ImpactService emits the flash/sound and schedules the entity blast before
         * entering here. Apply any tick-zero terrain only after those observable
         * effects have been dispatched. */
        commit.applyAvailable(level, 0);
        return true;
    }

    public static synchronized void ack(final ServerPlayer player, final UUID impactId,
        final long sequence) {
        if (player == null || impactId == null) return;
        ArrayDeque<Commit> commits = COMMITS.get(player.level());
        if (commits == null) return;
        for (Commit commit : commits) {
            if (commit.plan.impactId().equals(impactId)
                && commit.markerSequence == sequence) {
                commit.pendingAcknowledgements.remove(player.getUUID());
                WarheadLifecycleDiagnostics.acknowledged(player.level(), impactId);
                return;
            }
        }
    }

    public static synchronized CommitSnapshot snapshot(final ServerLevel level,
        final UUID impactId) {
        ArrayDeque<Commit> commits = COMMITS.get(level);
        if (commits != null) {
            for (Commit commit : commits) {
                if (commit.plan.impactId().equals(impactId)) return commit.snapshot(level);
            }
        }
        return new CommitSnapshot(false, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0);
    }

    private static synchronized void tick(final ServerLevel level) {
        ArrayDeque<Commit> commits = COMMITS.get(level);
        if (commits == null || commits.isEmpty()) return;
        int scheduled = commits.size();
        for (int index = 0; index < scheduled; index++) {
            Commit commit = commits.removeFirst();
            commit.advance(level);
            if (commit.complete(level)) {
                commit.releaseFallbackLease(level);
                WarheadLifecycleDiagnostics.completed(level, commit.plan.impactId());
                WarheadPreparationCoordinator.completeCommit(level,
                    commit.preparationId, commit.plan.impactId());
            } else {
                commits.addLast(commit);
            }
        }
        updateGauges(commits);
        if (commits.isEmpty()) COMMITS.remove(level);
    }

    private static void updateGauges(final ArrayDeque<Commit> commits) {
        long pendingSync = 0L;
        long pendingAcks = 0L;
        long conflicts = 0L;
        for (Commit commit : commits) {
            pendingSync += commit.pendingSync;
            pendingAcks += commit.pendingAcknowledgements.size();
            conflicts += commit.conflictedCells;
        }
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.ACTIVE_PREPARED_COMMITS,
            commits.size());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.PENDING_WARHEAD_RELIGHT_SYNCS,
            pendingSync);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.PENDING_WARHEAD_ACKS,
            pendingAcks);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.WARHEAD_REVISION_CONFLICTS,
            conflicts);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.WARHEAD_SLA_VIOLATIONS,
            WarheadLifecycleDiagnostics.slaViolationCount());
    }

    private static void clearLevel(final ServerLevel level) {
        ArrayDeque<Commit> commits = COMMITS.remove(level);
        if (commits == null) return;
        for (Commit commit : commits) {
            commit.releaseFallbackLease(level);
            WarheadLifecycleDiagnostics.impactCancelled(level, commit.plan.impactId(),
                "dimension_unload_during_commit");
            WarheadPreparationCoordinator.completeCommit(level,
                commit.preparationId, commit.plan.impactId());
        }
        commits.clear();
    }

    private static synchronized void clearAll() {
        for (ServerLevel level : List.copyOf(COMMITS.keySet())) clearLevel(level);
        COMMITS.clear();
    }

    private static boolean hasImmediate(final PreparedChunkPlan plan) {
        if (plan.fireMutations().stream().anyMatch(PreparedFireMutation::crater)) return true;
        return plan.blockSections().stream()
            .anyMatch(section -> section.phase() == PreparedMutationPhase.IMMEDIATE_CRATER
                && section.mutationCount() > 0);
    }

    private static boolean hasRadial(final PreparedChunkPlan plan) {
        if (!plan.biomeSections().isEmpty()
            || plan.fireMutations().stream().anyMatch(fire -> !fire.crater())) return true;
        return plan.blockSections().stream()
            .anyMatch(section -> section.phase() == PreparedMutationPhase.RADIAL_AFTERMATH
                && section.mutationCount() > 0);
    }

    static int applicationTick(final int plannedTick) {
        return Math.min(FINAL_APPLICATION_TICK, Math.max(0, plannedTick));
    }

    static boolean usesExactLightChecks(final int changedBlocks,
        final int changedSections) {
        return changedBlocks > 0 && (changedSections == 1
            || changedBlocks <= MAX_EXACT_LIGHT_CHECKS);
    }

    /**
     * Neighbour updates may change a block property between capture and commit
     * without changing the physical crater material (snowy grass is the common
     * case). Crater-owned cells may accept that property-only drift; every other
     * mutation retains exact snapshot conflict protection.
     */
    static boolean matchesExpectedState(final BlockState live, final int expectedStateId,
        final byte categoryId) {
        if (live == null) return false;
        if (Block.getId(live) == expectedStateId) return true;
        WarheadMutationCategory category = WarheadMutationCategory.fromWireId(categoryId);
        if (category != WarheadMutationCategory.CRATER_EXCAVATION
            && category != WarheadMutationCategory.CRATER_SHELL
            && category != WarheadMutationCategory.CRATER_CLEANUP) return false;
        return live.getBlock() == Block.stateById(expectedStateId).getBlock();
    }

    public record CommitSnapshot(boolean active, int ageTicks, int plannedChunks,
        int appliedChunks, int pendingLightAndPackets, int pendingAcknowledgements,
        int plannedCells, int changedBlocks, int conflictedCells,
        int survivalRejections, int semanticRejections, int alreadyEqualCells) { }

    private static final class Commit {
        private final UUID preparationId;
        private final PreparedImpactPlan plan;
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<PreparedChunkPlan>
            chunks = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        private final UUID sourcePlayerId;
        private final WarheadYield yield;
        private final long visualSeed;
        private final boolean customFire;
        private final long startedAt;
        private final Long2ByteOpenHashMap appliedPhases = new Long2ByteOpenHashMap();
        private final Set<UUID> recipients = new HashSet<>();
        private final Set<UUID> pendingAcknowledgements = new HashSet<>();
        private final Set<Long> changedChunkIds = new HashSet<>();
        private final Set<Long> changedSectionIds = new HashSet<>();
        private int pendingSync;
        private int chunkSyncs;
        private int conflictedCells;
        private int plannedCells;
        private int survivalRejections;
        private int semanticRejections;
        private int alreadyEqualCells;
        private final long[] plannedByCategory =
            new long[WarheadMutationCategory.values().length];
        private final long[] appliedByCategory =
            new long[WarheadMutationCategory.values().length];
        private int changedBlocks;
        private int changedSections;
        private int changedBiomeQuarts;
        private int bulkSafeMutations;
        private int specialPathMutations;
        private int craterFire;
        private int treeFire;
        private int groundFire;
        private long markerSequence;
        private long markerSentAt = Long.MIN_VALUE;
        private boolean markerSent;
        private boolean deadlineWarning;
        private boolean fallbackStarted;
        private UUID fallbackLeaseId;
        private ArrayDeque<Long> fallbackQueue;
        private List<WarheadSnapshotRequirement> fallbackRequirements = List.of();
        private WarheadStateMetadata fallbackMetadata;
        private WarheadStatePalette fallbackPalette;
        private final Set<Long> preparedOwners = new HashSet<>();
        private final Set<Long> fallbackOwners = new HashSet<>();
        private PlanStatistics plannedStatistics = PlanStatistics.empty();

        private Commit(final UUID preparationId, final PreparedImpactPlan plan,
            final UUID sourcePlayerId, final WarheadYield yield, final long visualSeed,
            final boolean customFire, final long startedAt) {
            this.preparationId = preparationId;
            this.plan = plan;
            this.sourcePlayerId = sourcePlayerId;
            this.yield = yield;
            this.visualSeed = visualSeed;
            this.customFire = customFire;
            this.startedAt = startedAt;
        }

        private void addChunks(final Iterable<PreparedChunkPlan> additions,
            final boolean fallbackOwner) {
            boolean changed = false;
            for (PreparedChunkPlan chunkPlan : additions) {
                long packed = chunkPlan.chunk().pack();
                if (chunks.putIfAbsent(packed, chunkPlan) != null) continue;
                PlanStatistics statistics = WarheadPlanCompiler.statistics(chunkPlan);
                plannedStatistics = plannedStatistics.add(statistics);
                plannedCells += Math.toIntExact(Math.min(Integer.MAX_VALUE,
                    statistics.changedBlocks()));
                for (WarheadMutationCategory category : WarheadMutationCategory.values()) {
                    plannedByCategory[category.ordinal()] +=
                        statistics.categories().count(category);
                }
                byte initial = 0;
                if (!hasImmediate(chunkPlan)) initial |= IMMEDIATE_APPLIED;
                if (!hasRadial(chunkPlan)) initial |= RADIAL_APPLIED;
                appliedPhases.put(packed, initial);
                (fallbackOwner ? fallbackOwners : preparedOwners).add(packed);
                WarheadLifecycleDiagnostics.chunkOwnership(plan.impactId(), packed,
                    fallbackOwner);
                changed = true;
            }
            if (changed) WarheadLifecycleDiagnostics.planExtended(plan.impactId(),
                plannedStatistics);
        }

        private void advance(final ServerLevel level) {
            int age = (int)Math.max(0L, level.getGameTime() - startedAt);
            if (fallbackLeaseId != null) {
                WarheadPreparationLeaseManager.extend(level, fallbackLeaseId,
                    level.getGameTime() + 1_200L);
            }
            ImpactStreamState streamState = WarheadPreparationCoordinator.impactStreamState(
                level, preparationId, plan.impactId());
            if (streamState == ImpactStreamState.FAILED) {
                startFallback(level, "prepared_stream_detached_complete_fallback");
                compileDetachedFallback(level);
            } else {
                addChunks(WarheadPreparationCoordinator.drainPreparedChunks(level,
                    preparationId, plan.impactId()), false);
                addChunks(WarheadPreparationCoordinator.compileFallbackChunks(level,
                    preparationId, plan.impactId(), MAX_FALLBACK_COMPILES_PER_TICK,
                    FALLBACK_COMPILE_BUDGET_NANOS), true);
            }
            applyAvailable(level, age);
            double radius = plan.footprint().maximumMutationRadius()
                * Math.min(1.0, age / (double)Math.max(1, FINAL_APPLICATION_TICK));
            NuclearTerrainObscurationEmitter.mutationProgress(level, plan.impactId(),
                plan.center(), visualSeed,
                yield.visualScale(), plan.footprint().maximumMutationRadius(),
                radius, radius, allPhasesApplied(level));
            if (allPhasesApplied(level) && pendingSync == 0 && !markerSent) sendMarkers(level);
            if (age >= FINAL_APPLICATION_TICK + 1 && !allPhasesApplied(level)
                && !deadlineWarning) {
                deadlineWarning = true;
                WarheadLifecycleDiagnostics.deadlineViolation(plan.impactId());
                WarMod.LOGGER.warn("Prepared impact {} missed the {}-tick terrain target: "
                    + "applied={}/{}, received={}, pendingSync={}", plan.impactId(),
                    FINAL_APPLICATION_TICK + 1, appliedChunkCount(),
                    plan.footprint().requiredChunkCount(), chunks.size(), pendingSync);
            }
        }

        private void startFallback(final ServerLevel level, final String reason) {
            if (fallbackStarted) return;
            fallbackStarted = true;
            fallbackLeaseId = UUID.randomUUID();
            fallbackMetadata = WarheadStateMetadata.capture(level);
            fallbackPalette = WarheadStatePalette.capture();
            PreparedImpactSpec impact = new PreparedImpactSpec(plan.impactId(),
                plan.center(), yield.payloadType(), yield, visualSeed, customFire);
            fallbackRequirements = List.of(new WarheadSnapshotRequirement(impact,
                plan.footprint()));
            ArrayList<Long> missing = new ArrayList<>();
            for (long packed : plan.footprint().requiredChunks()) {
                if (!chunks.containsKey(packed)) missing.add(packed);
            }
            missing.sort(java.util.Comparator.comparingInt((Long packed) ->
                WarheadPlanCompiler.radialActivationTick(plan.center(),
                    plan.footprint().maximumMutationRadius(),
                    ChunkPos.unpack(packed.longValue())))
                .thenComparingLong(Long::longValue));
            fallbackQueue = new ArrayDeque<>(missing);
            WarheadPreparationLeaseManager.acquireOrReplace(level, fallbackLeaseId,
                plan.center(), plan.footprint(), level.getGameTime() + 1_200L);
            WarMod.LOGGER.error("Prepared terrain stream failed for {}; assigning {} "
                + "missing chunks to the complete bounded live compiler", plan.impactId(),
                fallbackQueue.size());
            WarheadLifecycleDiagnostics.fallbackStarted(plan.impactId(), reason);
            WarheadLifecycleDiagnostics.ownershipPlanned(plan.impactId(),
                preparedOwners.size(), fallbackQueue.size());
        }

        private void compileDetachedFallback(final ServerLevel level) {
            if (!fallbackStarted || fallbackQueue == null || fallbackQueue.isEmpty()) return;
            long deadline = System.nanoTime() + FALLBACK_COMPILE_BUDGET_NANOS;
            int inspected = 0;
            int limit = Math.min(fallbackQueue.size(), MAX_FALLBACK_COMPILES_PER_TICK);
            while (inspected < limit && System.nanoTime() < deadline) {
                long packed = fallbackQueue.removeFirst();
                inspected++;
                if (chunks.containsKey(packed)) continue;
                if (!WarheadPreparationLeaseManager.chunkReady(level, fallbackLeaseId,
                    packed)) {
                    fallbackQueue.addLast(packed);
                    continue;
                }
                WarheadChunkSnapshot snapshot = WarheadWorldSnapshotter.captureFallback(level,
                    ChunkPos.unpack(packed), fallbackRequirements, fallbackMetadata);
                if (snapshot == null) {
                    fallbackQueue.addLast(packed);
                    continue;
                }
                try {
                    snapshot.preflight();
                    PreparedChunkPlan compiled = WarheadPlanCompiler.compile(
                        fallbackRequirements.getFirst().impact(), plan.footprint(),
                        snapshot, fallbackPalette);
                    addChunks(List.of(compiled), true);
                    WarheadLifecycleDiagnostics.chunkPrepared(level, plan.impactId());
                } catch (Throwable failure) {
                    fallbackQueue.addLast(packed);
                    WarMod.LOGGER.error("Complete live-fallback compile failed for impact {} "
                        + "chunk {}; ownership retained for retry", plan.impactId(),
                        ChunkPos.unpack(packed), failure);
                }
            }
        }

        private void releaseFallbackLease(final ServerLevel level) {
            if (fallbackLeaseId == null) return;
            WarheadPreparationLeaseManager.release(level, fallbackLeaseId);
            fallbackLeaseId = null;
        }

        private void applyAvailable(final ServerLevel level, final int age) {
            for (PreparedChunkPlan chunkPlan : chunks.values()) {
                long packed = chunkPlan.chunk().pack();
                byte applied = appliedPhases.get(packed);
                int activation = applicationTick(chunkPlan.activationTick());
                boolean immediate = (applied & IMMEDIATE_APPLIED) == 0;
                boolean radial = (applied & RADIAL_APPLIED) == 0 && age >= activation;
                if (immediate || radial) {
                    /* Coalesce tick-zero crater and radial changes into one heightmap,
                     * light and packet transaction for the chunk. */
                    applyChunk(level, chunkPlan, immediate, radial);
                }
            }
        }

        private void applyChunk(final ServerLevel level, final PreparedChunkPlan chunkPlan,
            final boolean immediate, final boolean radial) {
            long commitStarted = WarModPerformanceDiagnostics.begin();
            long packed = chunkPlan.chunk().pack();
            byte previous = appliedPhases.get(packed);
            boolean applyImmediate = immediate && (previous & IMMEDIATE_APPLIED) == 0;
            boolean applyRadial = radial && (previous & RADIAL_APPLIED) == 0;
            if (!applyImmediate && !applyRadial) return;
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                chunkPlan.chunk().x(), chunkPlan.chunk().z());
            if (chunk == null) return;
            ServerPlayer source = sourcePlayer(level);
            ChangeBounds bounds = new ChangeBounds();
            Map<Integer, ShortOpenHashSet> sectionChanges = new HashMap<>();
            Map<Integer, ShortOpenHashSet> directLightingChanges = new HashMap<>();
            boolean blockChanged = false;
            int sectionsBefore = changedSections;
            int blocksBefore = changedBlocks;
            int biomeQuartsBefore = changedBiomeQuarts;
            int firesBefore = craterFire + treeFire + groundFire;
            int bulkBefore = bulkSafeMutations;
            int specialBefore = specialPathMutations;
            int conflictsBefore = conflictedCells;
            int survivalBefore = survivalRejections;
            int semanticRejectedBefore = semanticRejections;
            int equalBefore = alreadyEqualCells;
            long[] categoriesBefore = appliedByCategory.clone();
            for (PreparedSectionPlan sectionPlan : chunkPlan.blockSections()) {
                if ((sectionPlan.phase() == PreparedMutationPhase.IMMEDIATE_CRATER
                    && !applyImmediate) || (sectionPlan.phase()
                        == PreparedMutationPhase.RADIAL_AFTERMATH && !applyRadial)) continue;
                boolean sectionChanged = applySection(level, chunk, sectionPlan, source,
                    bounds, sectionChanges, directLightingChanges);
                blockChanged |= sectionChanged;
                if (sectionChanged && changedSectionIds.add(SectionPos.asLong(
                    chunk.getPos().x(), sectionPlan.sectionY(), chunk.getPos().z()))) {
                    changedSections++;
                }
            }
            boolean biomeChanged = applyRadial && applyBiomes(level, chunk,
                chunkPlan.biomeSections());
            boolean fireChanged = applyFires(level, chunkPlan.fireMutations(), source,
                applyImmediate, applyRadial, bounds);
            blockChanged |= fireChanged;
            if (blockChanged) {
                repairHeightmaps(chunk, bounds);
                chunk.getSkyLightSources().fillFrom(chunk);
                chunk.markUnsaved();
            }
            if (biomeChanged) chunk.markUnsaved();
            boolean specialMutation = specialPathMutations > specialBefore || fireChanged;
            if (blockChanged || biomeChanged) {
                queueSync(level, chunk, blockChanged, biomeChanged,
                    sectionChanges, directLightingChanges, specialMutation);
            }
            if (applyImmediate) previous |= IMMEDIATE_APPLIED;
            if (applyRadial) previous |= RADIAL_APPLIED;
            appliedPhases.put(packed, previous);
            if (blockChanged || biomeChanged) {
                WarheadLifecycleDiagnostics.chunkApplied(level, plan.impactId(), packed,
                    changedSections - sectionsBefore, changedBlocks - blocksBefore,
                    changedBiomeQuarts - biomeQuartsBefore,
                    bulkSafeMutations - bulkBefore, specialPathMutations - specialBefore,
                    conflictedCells - conflictsBefore,
                    categoryDifference(categoriesBefore,
                        craterFire + treeFire + groundFire - firesBefore,
                        changedBiomeQuarts - biomeQuartsBefore),
                    survivalRejections - survivalBefore,
                    semanticRejections - semanticRejectedBefore,
                    alreadyEqualCells - equalBefore, blockChanged, biomeChanged);
            }
            WarModPerformanceDiagnostics.record(
                WarModPerformanceDiagnostics.Subsystem.WARHEAD_BULK_COMMIT,
                commitStarted);
        }

        private boolean applySection(final ServerLevel level, final LevelChunk chunk,
            final PreparedSectionPlan plan, final @Nullable ServerPlayer source,
            final ChangeBounds bounds,
            final Map<Integer, ShortOpenHashSet> sectionChanges,
            final Map<Integer, ShortOpenHashSet> directLightingChanges) {
            int blockY = SectionPos.sectionToBlockCoord(plan.sectionY());
            if (blockY < level.dimensionType().minY()
                || blockY >= level.dimensionType().minY() + level.dimensionType().height()) {
                return false;
            }
            LevelChunkSection section = chunk.getSection(level.getSectionIndex(blockY));
            WarheadChunkRevisionAccess revisions = (WarheadChunkRevisionAccess)(Object)chunk;
            boolean validate = revisions.war_mod$getSectionRevision(plan.sectionY())
                != plan.sourceRevision();
            int[] indices = plan.localIndicesUnsafe();
            int[] expected = plan.expectedStateIdsUnsafe();
            int[] replacements = plan.finalStateIdsUnsafe();
            BitSet semantic = plan.semanticMaskUnsafe();
            BitSet survival = plan.survivalMaskUnsafe();
            BitSet supportCheck = plan.supportCheckMaskUnsafe();
            byte[] categories = plan.mutationCategoriesUnsafe();
            boolean directChanged = false;
            boolean changed = false;
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            for (int index = 0; index < indices.length; index++) {
                int local = indices[index];
                int localX = local & 15;
                int localZ = local >> 4 & 15;
                int localY = local >> 8 & 15;
                BlockState live = section.getBlockState(localX, localY, localZ);
                if ((validate || semantic.get(index) || survival.get(index)
                    || supportCheck.get(index))
                    && !matchesExpectedState(live, expected[index], categories[index])) {
                    conflictedCells++;
                    continue;
                }
                BlockState replacement = Block.stateById(replacements[index]);
                int worldX = chunk.getPos().getMinBlockX() + localX;
                int worldY = blockY + localY;
                int worldZ = chunk.getPos().getMinBlockZ() + localZ;
                position.set(worldX, worldY, worldZ);
                if (supportCheck.get(index) && live.canSurvive(level, position)) {
                    survivalRejections++;
                    continue;
                }
                if (survival.get(index) && !replacement.isAir()
                    && !replacement.canSurvive(level, position)) {
                    survivalRejections++;
                    replacement = Blocks.AIR.defaultBlockState();
                }
                boolean cellChanged;
                if (semantic.get(index)) {
                    cellChanged = WarheadExplosionWorkManager.applyPreparedSemanticMutation(
                        level, source, position.immutable(), planCenter(),
                        this.plan.footprint().terrainProfile().entityBlastRadius(),
                        replacement);
                } else {
                    if (live.equals(replacement)) {
                        alreadyEqualCells++;
                        continue;
                    }
                    section.getStates().set(localX, localY, localZ, replacement);
                    directChanged = true;
                    cellChanged = true;
                }
                if (cellChanged) {
                    changed = true;
                    changedBlocks++;
                    if (semantic.get(index)) specialPathMutations++;
                    else bulkSafeMutations++;
                    appliedByCategory[Byte.toUnsignedInt(categories[index])]++;
                    bounds.include(localX, worldY, localZ);
                    sectionChanges.computeIfAbsent(plan.sectionY(),
                        ignored -> new ShortOpenHashSet()).add((short)(localX << 8
                            | localZ << 4 | localY));
                    if (!semantic.get(index)) {
                        directLightingChanges.computeIfAbsent(plan.sectionY(),
                            ignored -> new ShortOpenHashSet()).add((short)(localX << 8
                                | localZ << 4 | localY));
                    }
                } else if (semantic.get(index)) {
                    semanticRejections++;
                }
            }
            if (directChanged) {
                section.recalcBlockCounts();
                revisions.war_mod$markBulkSectionChanged(plan.sectionY());
            }
            return changed;
        }

        @SuppressWarnings("unchecked")
        private boolean applyBiomes(final ServerLevel level, final LevelChunk chunk,
            final List<PreparedBiomeSectionPlan> plans) {
            if (plans.isEmpty()) return false;
            Holder<Biome> wasteland = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(ModBiomes.NUCLEAR_WASTELAND);
            boolean changed = false;
            for (PreparedBiomeSectionPlan plan : plans) {
                int blockY = SectionPos.sectionToBlockCoord(plan.sectionY());
                if (blockY < level.dimensionType().minY()
                    || blockY >= level.dimensionType().minY()
                        + level.dimensionType().height()) continue;
                LevelChunkSection section = chunk.getSection(level.getSectionIndex(blockY));
                PalettedContainer<Holder<Biome>> biomes =
                    (PalettedContainer<Holder<Biome>>)section.getBiomes();
                long mask = plan.quartMask();
                boolean sectionChanged = false;
                while (mask != 0L) {
                    int bit = Long.numberOfTrailingZeros(mask);
                    mask &= mask - 1L;
                    int localY = bit / 16;
                    int remainder = bit % 16;
                    int localZ = remainder / 4;
                    int localX = remainder % 4;
                    if (!section.getNoiseBiome(localX, localY, localZ)
                        .is(ModBiomes.NUCLEAR_WASTELAND)) {
                        biomes.set(localX, localY, localZ, wasteland);
                        changed = true;
                        sectionChanged = true;
                        changedBiomeQuarts++;
                    }
                }
                if (sectionChanged && changedSectionIds.add(SectionPos.asLong(
                    chunk.getPos().x(), plan.sectionY(), chunk.getPos().z()))) {
                    changedSections++;
                }
            }
            return changed;
        }

        private boolean applyFires(final ServerLevel level,
            final List<PreparedFireMutation> fires, final @Nullable ServerPlayer source,
            final boolean immediate, final boolean radial, final ChangeBounds bounds) {
            boolean changed = false;
            for (PreparedFireMutation fire : fires) {
                if ((fire.crater() && !immediate) || (!fire.crater() && !radial)) continue;
                if (fire.crater() && craterFire >= MAX_CRATER_FIRE) continue;
                if (fire.tree() && treeFire >= MAX_TREE_FIRE) continue;
                if (!fire.crater() && !fire.tree() && groundFire >= MAX_GROUND_FIRE) continue;
                BlockPos host = new BlockPos(fire.x(), fire.y(), fire.z());
                boolean placed = fire.tree()
                    ? WarheadFirePlacement.placeBlastFacing(level, host, plan.center(),
                        fire.customFire(), fire.intensity(), fire.seed(), UPDATE_FLAGS)
                    : WarheadFirePlacement.placeAbove(level, host, fire.customFire(),
                        fire.intensity(), fire.seed(), UPDATE_FLAGS);
                if (!placed) continue;
                if (fire.crater()) craterFire++;
                else if (fire.tree()) treeFire++;
                else groundFire++;
                changed = true;
                changedBlocks++;
                specialPathMutations++;
                bounds.include(fire.x() & 15, fire.y() + 1, fire.z() & 15);
            }
            return changed;
        }

        private void repairHeightmaps(final LevelChunk chunk, final ChangeBounds bounds) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int baseX = chunk.getPos().getMinBlockX();
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int column = bounds.columns.nextSetBit(0); column >= 0;
                column = bounds.columns.nextSetBit(column + 1)) {
                int localX = column & 15;
                int localZ = column >> 4;
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                for (Heightmap.Types type : LIVE_HEIGHTMAPS) {
                    Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
                    int oldTop = heightmap.getHighestTaken(localX, localZ);
                    if (oldTop >= chunk.getMinY() && oldTop < chunk.getMinY() + chunk.getHeight()) {
                        cursor.set(worldX, oldTop, worldZ);
                        heightmap.update(localX, oldTop, localZ, chunk.getBlockState(cursor));
                    }
                    int updatedTop = heightmap.getHighestTaken(localX, localZ);
                    for (int y = bounds.maximumY[column]; y > updatedTop; y--) {
                        cursor.set(worldX, y, worldZ);
                        BlockState state = chunk.getBlockState(cursor);
                        if (type.isOpaque().test(state)) {
                            heightmap.update(localX, y, localZ, state);
                            break;
                        }
                    }
                }
            }
        }

        private void queueSync(final ServerLevel level, final LevelChunk chunk,
            final boolean relight, final boolean biomeChanged,
            final Map<Integer, ShortOpenHashSet> sectionChanges,
            final Map<Integer, ShortOpenHashSet> directLightingChanges,
            final boolean forceFullChunk) {
            pendingSync++;
            chunkSyncs++;
            changedChunkIds.add(chunk.getPos().pack());
            long syncStarted = WarModPerformanceDiagnostics.begin();
            WarheadLifecycleDiagnostics.lightingQueued(level, plan.impactId());
            CompletableFuture<?> lightReady;
            ThreadedLevelLightEngine light = level.getChunkSource().getLightEngine();
            if (relight) {
                int directLightChecks = directLightingChanges.values().stream()
                    .mapToInt(ShortOpenHashSet::size).sum();
                if (usesExactLightChecks(directLightChecks,
                    directLightingChanges.size())) {
                    BlockPos.MutableBlockPos changed = new BlockPos.MutableBlockPos();
                    int baseX = chunk.getPos().getMinBlockX();
                    int baseZ = chunk.getPos().getMinBlockZ();
                    for (Map.Entry<Integer, ShortOpenHashSet> entry
                        : directLightingChanges.entrySet()) {
                        int baseY = SectionPos.sectionToBlockCoord(entry.getKey());
                        for (short packedPosition : entry.getValue()) {
                            int packed = Short.toUnsignedInt(packedPosition);
                            light.checkBlock(changed.set(baseX + (packed >> 8 & 15),
                                baseY + (packed & 15), baseZ + (packed >> 4 & 15)));
                        }
                    }
                } else if (directLightChecks > 0) {
                    ((WarheadLightEngineAccess)(Object)light)
                        .war_mod$resetChunkLighting(chunk.getPos());
                    for (int index = 0; index < chunk.getSectionsCount(); index++) {
                        LevelChunkSection section = chunk.getSection(index);
                        if (!section.hasOnlyAir()) {
                            light.updateSectionStatus(SectionPos.of(chunk.getPos(),
                                level.getSectionYFromSectionIndex(index)), false);
                        }
                    }
                    light.setLightEnabled(chunk.getPos(), true);
                    light.propagateLightSources(chunk.getPos());
                }
                light.tryScheduleUpdate();
                lightReady = light.waitForPendingTasks(chunk.getPos().x(), chunk.getPos().z());
            } else {
                lightReady = CompletableFuture.completedFuture(null);
            }
            lightReady.whenComplete((ignored, failure) -> level.getServer().execute(() -> {
                LevelChunk current = level.getChunkSource().getChunkNow(
                    chunk.getPos().x(), chunk.getPos().z());
                if (failure == null && current != null) {
                    int sent = 0;
                    for (ServerPlayer player : level.getChunkSource().chunkMap
                        .getPlayers(chunk.getPos(), false)) {
                        if (forceFullChunk) {
                            player.connection.send(new ClientboundLevelChunkWithLightPacket(
                                current, level.getChunkSource().getLightEngine(), null, null));
                            sent++;
                        } else {
                            for (Map.Entry<Integer, ShortOpenHashSet> entry
                                : sectionChanges.entrySet()) {
                                ShortSet changes = entry.getValue();
                                if (changes.isEmpty()) continue;
                                int sectionIndex = level.getSectionIndexFromSectionY(
                                    entry.getKey());
                                if (sectionIndex < 0
                                    || sectionIndex >= current.getSectionsCount()) continue;
                                player.connection.send(new ClientboundSectionBlocksUpdatePacket(
                                    SectionPos.of(current.getPos(), entry.getKey()), changes,
                                    current.getSection(sectionIndex)));
                                sent++;
                            }
                            if (relight) {
                                player.connection.send(new ClientboundLightUpdatePacket(
                                    current.getPos(), level.getChunkSource().getLightEngine(),
                                    null, null));
                                sent++;
                            }
                            if (biomeChanged) {
                                player.connection.send(ClientboundChunksBiomesPacket.forChunks(
                                    List.of(current)));
                                sent++;
                            }
                        }
                        recipients.add(player.getUUID());
                    }
                    WarheadLifecycleDiagnostics.lightingCompleted(level,
                        plan.impactId(), sent);
                    WarModPerformanceDiagnostics.add(
                        WarModPerformanceDiagnostics.Gauge.WARHEAD_SERVER_PACKETS_SENT,
                        sent);
                } else if (failure != null) {
                    WarMod.LOGGER.error("Prepared chunk relight failed for impact {} chunk {}",
                        plan.impactId(), chunk.getPos(), failure);
                }
                WarModPerformanceDiagnostics.record(
                    WarModPerformanceDiagnostics.Subsystem.WARHEAD_RELIGHT,
                    syncStarted);
                WarModPerformanceDiagnostics.record(
                    WarModPerformanceDiagnostics.Subsystem.WARHEAD_CHUNK_SYNC,
                    syncStarted);
                pendingSync = Math.max(0, pendingSync - 1);
            }));
        }

        private void sendMarkers(final ServerLevel level) {
            markerSent = true;
            markerSequence = 1L;
            markerSentAt = level.getGameTime();
            pendingAcknowledgements.clear();
            ClientboundWarheadTerrainCommitPayload payload =
                new ClientboundWarheadTerrainCommitPayload(plan.impactId(), markerSequence,
                    chunkSyncs, changedChunkIds.size(), changedSections, changedBlocks,
                    changedBiomeQuarts, markerSentAt);
            for (UUID playerId : recipients) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
                if (player == null || player.level() != level) continue;
                ServerPlayNetworking.send(player, payload);
                pendingAcknowledgements.add(playerId);
            }
        }

        private boolean complete(final ServerLevel level) {
            if (!markerSent || pendingSync > 0 || !allPhasesApplied(level)) return false;
            if (pendingAcknowledgements.isEmpty()) return true;
            if (level.getGameTime() - markerSentAt < CLIENT_ACK_TIMEOUT_TICKS) return false;
            WarMod.LOGGER.warn("Prepared impact {} client acknowledgement timeout: {} clients",
                plan.impactId(), pendingAcknowledgements.size());
            return true;
        }

        private boolean allPhasesApplied(final ServerLevel level) {
            if (fallbackStarted) {
                if (fallbackQueue == null || !fallbackQueue.isEmpty()) return false;
            } else if (!WarheadPreparationCoordinator.impactStreamClosed(level,
                preparationId, plan.impactId())) return false;
            for (long packed : plan.footprint().requiredChunks()) {
                if (!appliedPhases.containsKey(packed)) return false;
            }
            for (byte phases : appliedPhases.values()) {
                if ((phases & (IMMEDIATE_APPLIED | RADIAL_APPLIED))
                    != (IMMEDIATE_APPLIED | RADIAL_APPLIED)) return false;
            }
            return true;
        }

        private int appliedChunkCount() {
            int count = 0;
            for (byte phases : appliedPhases.values()) {
                if ((phases & (IMMEDIATE_APPLIED | RADIAL_APPLIED))
                    == (IMMEDIATE_APPLIED | RADIAL_APPLIED)) count++;
            }
            return count;
        }

        private MutationCategoryCounts categoryDifference(final long[] before,
            final long firesApplied, final long biomeQuartsApplied) {
            return new MutationCategoryCounts(
                appliedByCategory[WarheadMutationCategory.CRATER_EXCAVATION.ordinal()]
                    - before[WarheadMutationCategory.CRATER_EXCAVATION.ordinal()],
                appliedByCategory[WarheadMutationCategory.CRATER_SHELL.ordinal()]
                    - before[WarheadMutationCategory.CRATER_SHELL.ordinal()],
                appliedByCategory[WarheadMutationCategory.CRATER_CLEANUP.ordinal()]
                    - before[WarheadMutationCategory.CRATER_CLEANUP.ordinal()],
                appliedByCategory[WarheadMutationCategory.SURFACE.ordinal()]
                    - before[WarheadMutationCategory.SURFACE.ordinal()],
                appliedByCategory[WarheadMutationCategory.VEGETATION.ordinal()]
                    - before[WarheadMutationCategory.VEGETATION.ordinal()],
                appliedByCategory[WarheadMutationCategory.STRUCTURE.ordinal()]
                    - before[WarheadMutationCategory.STRUCTURE.ordinal()],
                appliedByCategory[WarheadMutationCategory.DECORATION.ordinal()]
                    - before[WarheadMutationCategory.DECORATION.ordinal()],
                firesApplied, biomeQuartsApplied,
                appliedByCategory[WarheadMutationCategory.OTHER.ordinal()]
                    - before[WarheadMutationCategory.OTHER.ordinal()]);
        }

        private ServerPlayer sourcePlayer(final ServerLevel level) {
            if (sourcePlayerId == null) return null;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(sourcePlayerId);
            return player != null && player.level() == level ? player : null;
        }

        private Vec3 planCenter() { return plan.center(); }

        private CommitSnapshot snapshot(final ServerLevel level) {
            return new CommitSnapshot(true,
                (int)Math.max(0L, level.getGameTime() - startedAt),
                plan.footprint().requiredChunkCount(),
                appliedChunkCount(), pendingSync, pendingAcknowledgements.size(),
                plannedCells, changedBlocks, conflictedCells, survivalRejections,
                semanticRejections, alreadyEqualCells);
        }
    }

    private static final class ChangeBounds {
        private final BitSet columns = new BitSet(256);
        private final int[] minimumY = new int[256];
        private final int[] maximumY = new int[256];

        private ChangeBounds() {
            java.util.Arrays.fill(minimumY, Integer.MAX_VALUE);
            java.util.Arrays.fill(maximumY, Integer.MIN_VALUE);
        }

        private void include(final int localX, final int y, final int localZ) {
            int column = (localZ & 15) * 16 + (localX & 15);
            columns.set(column);
            minimumY[column] = Math.min(minimumY[column], y);
            maximumY[column] = Math.max(maximumY[column], y);
        }
    }
}
