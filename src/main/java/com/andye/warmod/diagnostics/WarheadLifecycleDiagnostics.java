package com.andye.warmod.diagnostics;

import com.andye.warmod.warhead.PlanStatistics;
import com.andye.warmod.warhead.MutationCategoryCounts;
import com.andye.warmod.warhead.PreparationProgress;
import com.andye.warmod.warhead.PreparedImpactSpec;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadFootprint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-impact evidence for the prepared nuclear terrain lifecycle. Values that
 * the mapped networking API does not expose (notably compressed packet bytes)
 * remain -1 rather than being presented as measurements.
 */
public final class WarheadLifecycleDiagnostics {
    private static final int RETAINED_IMPACTS = 256;
    private static final int IMPACT_TICK_SAMPLES = 80;
    private static final Map<UUID, Lifecycle> IMPACTS = new LinkedHashMap<>();
    private static final Map<UUID, List<UUID>> BY_PREPARATION = new LinkedHashMap<>();

    private WarheadLifecycleDiagnostics() { }

    public static synchronized void requested(final ServerLevel level,
        final UUID preparationId, final UUID rootTrackId,
        final List<PreparedImpactSpec> impacts, final List<WarheadFootprint> footprints,
        final WarheadDeliveryMode deliveryMode, final long expectedImpactTick,
        final int unionRequiredChunks) {
        if (level == null || preparationId == null || rootTrackId == null
            || impacts == null || footprints == null || impacts.size() != footprints.size()) {
            return;
        }
        List<UUID> previousIds = BY_PREPARATION.getOrDefault(preparationId, List.of());
        ArrayList<UUID> ids = new ArrayList<>(impacts.size());
        for (int index = 0; index < impacts.size(); index++) {
            PreparedImpactSpec impact = impacts.get(index);
            WarheadFootprint footprint = footprints.get(index);
            Lifecycle lifecycle = IMPACTS.computeIfAbsent(impact.impactId(), Lifecycle::new);
            lifecycle.preparationId = preparationId;
            lifecycle.rootTrackId = rootTrackId;
            lifecycle.yield = impact.yield().getSerializedName();
            lifecycle.deliveryMode = deliveryMode.name();
            if (lifecycle.requestTick < 0L) lifecycle.requestTick = level.getGameTime();
            lifecycle.expectedImpactTick = expectedImpactTick;
            lifecycle.requiredChunks = footprint.requiredChunkCount();
            lifecycle.unionRequiredChunks = unionRequiredChunks;
            lifecycle.ticketsRequested = unionRequiredChunks;
            lifecycle.leakStatus = "ACTIVE";
            ids.add(impact.impactId());
        }
        for (UUID previousId : previousIds) {
            if (ids.contains(previousId)) continue;
            Lifecycle transferred = IMPACTS.get(previousId);
            if (transferred != null && transferred.completionTick < 0L) {
                transferred.completionTick = level.getGameTime();
                transferred.fallbackReason = "retargeted_before_impact";
                transferred.leakStatus = "OWNERSHIP_TRANSFERRED";
            }
        }
        BY_PREPARATION.put(preparationId, List.copyOf(ids));
        trim();
    }

    public static synchronized void progress(final ServerLevel level,
        final UUID preparationId, final PreparationProgress progress,
        final long snapshotBytes) {
        if (level == null || preparationId == null || progress == null) return;
        long now = level.getGameTime();
        forEach(preparationId, lifecycle -> {
            lifecycle.ticketsAcquired = Math.max(lifecycle.ticketsAcquired,
                progress.ticketedChunks());
            if (progress.readyChunks() > 0 && lifecycle.firstChunkReadyTick < 0L) {
                lifecycle.firstChunkReadyTick = now;
            }
            if (progress.requiredChunks() > 0
                && progress.readyChunks() == progress.requiredChunks()
                && lifecycle.allChunksReadyTick < 0L) {
                lifecycle.allChunksReadyTick = now;
            }
            if (progress.requiredChunks() > 0
                && progress.snapshottedChunks() == progress.requiredChunks()
                && lifecycle.snapshotCompleteTick < 0L) {
                lifecycle.snapshotCompleteTick = now;
            }
            if (progress.requiredChunks() > 0
                && progress.compiledChunks() == progress.requiredChunks()
                && lifecycle.compileCompleteTick < 0L) {
                lifecycle.compileCompleteTick = now;
            }
            lifecycle.peakSnapshotMemoryEstimate = Math.max(
                lifecycle.peakSnapshotMemoryEstimate, snapshotBytes);
        });
    }

    public static synchronized void planReady(final ServerLevel level,
        final UUID preparationId, final UUID impactId, final PlanStatistics statistics,
        final long snapshotBytes) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (level == null || lifecycle == null || statistics == null) return;
        long now = level.getGameTime();
        lifecycle.snapshotCompleteTick = valueOr(lifecycle.snapshotCompleteTick, now);
        lifecycle.compileCompleteTick = valueOr(lifecycle.compileCompleteTick, now);
        lifecycle.planReadyTick = now;
        lifecycle.changedChunksPlanned = statistics.changedChunks();
        lifecycle.changedSectionsPlanned = statistics.changedSections();
        lifecycle.changedBlocksPlanned = statistics.changedBlocks();
        lifecycle.changedBiomeQuartsPlanned = statistics.changedBiomeQuarts();
        lifecycle.specialPathPlanned = statistics.semanticMutations();
        lifecycle.bulkSafePlanned = Math.max(0L,
            statistics.changedBlocks() - statistics.semanticMutations());
        lifecycle.plannedCategories = statistics.categories();
        lifecycle.replacementHistogram = statistics.replacementHistogram();
        lifecycle.peakPlanMemoryEstimate = Math.max(lifecycle.peakPlanMemoryEstimate,
            statistics.estimatedBytes());
        lifecycle.peakSnapshotMemoryEstimate = Math.max(
            lifecycle.peakSnapshotMemoryEstimate, snapshotBytes);
    }

    public static synchronized void recompiled(final UUID preparationId,
        final int sections) {
        forEach(preparationId, lifecycle -> lifecycle.recompiledSections +=
            Math.max(0, sections));
    }

    public static synchronized void impactAttempt(final ServerLevel level,
        final UUID impactId, final double readinessPercent, final String fallbackReason) {
        Lifecycle lifecycle = IMPACTS.computeIfAbsent(impactId, Lifecycle::new);
        lifecycle.actualImpactTick = level == null ? -1L : level.getGameTime();
        lifecycle.planReadinessPercentAtAttempt = Math.max(0.0,
            Math.min(100.0, readinessPercent));
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            lifecycle.fallbackReason = fallbackReason;
        }
        trim();
    }

    public static synchronized void chunkPrepared(final ServerLevel level,
        final UUID impactId) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (level != null && lifecycle != null) {
            lifecycle.firstPreparedChunkTick = valueOr(
                lifecycle.firstPreparedChunkTick, level.getGameTime());
        }
    }

    public static synchronized void impactSealed(final ServerLevel level,
        final UUID impactId, final int preparedChunks, final int compilingChunks,
        final int requiredChunks) {
        Lifecycle lifecycle = IMPACTS.computeIfAbsent(impactId, Lifecycle::new);
        if (level != null) lifecycle.impactSealedTick = valueOr(
            lifecycle.impactSealedTick, level.getGameTime());
        lifecycle.preparedChunksAtSeal = Math.max(lifecycle.preparedChunksAtSeal,
            Math.max(0, preparedChunks));
        lifecycle.compilingChunksAtSeal = Math.max(lifecycle.compilingChunksAtSeal,
            Math.max(0, compilingChunks));
        lifecycle.unsnapshottedChunksAtSeal = Math.max(0L,
            requiredChunks - preparedChunks - compilingChunks);
        lifecycle.requiredChunks = Math.max(lifecycle.requiredChunks,
            Math.max(0, requiredChunks));
    }

    public static synchronized void visualImpact(final ServerLevel level,
        final UUID impactId) {
        Lifecycle lifecycle = IMPACTS.computeIfAbsent(impactId, Lifecycle::new);
        if (level != null) lifecycle.visualImpactTick = valueOr(
            lifecycle.visualImpactTick, level.getGameTime());
    }

    public static synchronized void entityBlast(final ServerLevel level,
        final UUID impactId) {
        Lifecycle lifecycle = IMPACTS.computeIfAbsent(impactId, Lifecycle::new);
        if (level != null) lifecycle.entityBlastTick = valueOr(
            lifecycle.entityBlastTick, level.getGameTime());
    }

    public static synchronized void commitStarted(final ServerLevel level,
        final UUID impactId) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (level == null || lifecycle == null) return;
        lifecycle.firstAuthoritativeCommitTick = valueOr(
            lifecycle.firstAuthoritativeCommitTick, level.getGameTime());
    }

    public static synchronized void chunkApplied(final ServerLevel level,
        final UUID impactId, final long packedChunk, final int changedSections,
        final int changedBlocks,
        final int changedBiomeQuarts, final int bulkSafe, final int specialPath,
        final int conflicts, final MutationCategoryCounts appliedCategories,
        final int survivalRejections, final int semanticRejections,
        final int alreadyEqualCells, final boolean blocksApplied,
        final boolean biomesApplied) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (level == null || lifecycle == null) return;
        long now = level.getGameTime();
        lifecycle.changedChunkIds.add(packedChunk);
        lifecycle.changedChunks = lifecycle.changedChunkIds.size();
        lifecycle.changedSections += Math.max(0, changedSections);
        lifecycle.changedBlocks += Math.max(0, changedBlocks);
        lifecycle.changedBiomeQuarts += Math.max(0, changedBiomeQuarts);
        lifecycle.bulkSafeMutations += Math.max(0, bulkSafe);
        lifecycle.specialPathMutations += Math.max(0, specialPath);
        lifecycle.revisionConflicts += Math.max(0, conflicts);
        if (appliedCategories != null) {
            lifecycle.appliedCategories = lifecycle.appliedCategories.add(appliedCategories);
        }
        lifecycle.survivalRejections += Math.max(0, survivalRejections);
        lifecycle.semanticRejections += Math.max(0, semanticRejections);
        lifecycle.alreadyEqualCells += Math.max(0, alreadyEqualCells);
        if (blocksApplied) lifecycle.lastAuthoritativeBlockCommitTick = now;
        if (biomesApplied) lifecycle.lastBiomeCommitTick = now;
    }

    public static synchronized void lightingQueued(final ServerLevel level,
        final UUID impactId) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (level != null && lifecycle != null) {
            lifecycle.lightingQueuedTick = level.getGameTime();
        }
    }

    public static synchronized void lightingCompleted(final ServerLevel level,
        final UUID impactId, final int packetsSent) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (level == null || lifecycle == null) return;
        lifecycle.lightingCompletedTick = level.getGameTime();
        lifecycle.serverPacketsSent += Math.max(0, packetsSent);
    }

    public static synchronized void acknowledged(final ServerLevel level,
        final UUID impactId) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (level != null && lifecycle != null) {
            lifecycle.lastTrackedClientAckTick = level.getGameTime();
        }
    }

    public static synchronized void deadlineViolation(final UUID impactId) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (lifecycle != null) lifecycle.slaViolated = true;
    }

    public static synchronized void completed(final ServerLevel level,
        final UUID impactId) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (level == null || lifecycle == null) return;
        lifecycle.completionTick = level.getGameTime();
        lifecycle.leakStatus = lifecycle.ticketsReleased >= lifecycle.ticketsRequested
            ? "CLEAN" : "WAITING_FOR_SHARED_LEASE_RELEASE";
    }

    public static synchronized void cancelled(final ServerLevel level,
        final UUID preparationId, final String reason) {
        long now = level == null ? -1L : level.getGameTime();
        forEach(preparationId, lifecycle -> {
            lifecycle.completionTick = now;
            lifecycle.fallbackReason = reason == null ? "cancelled" : "cancelled:" + reason;
            lifecycle.leakStatus = lifecycle.ticketsReleased >= lifecycle.ticketsRequested
                ? "CLEAN" : "WAITING_FOR_LEASE_RELEASE";
        });
    }

    public static synchronized void impactCancelled(final ServerLevel level,
        final UUID impactId, final String reason) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (lifecycle == null) return;
        lifecycle.completionTick = level == null ? -1L : level.getGameTime();
        lifecycle.fallbackReason = reason == null ? "cancelled" : "cancelled:" + reason;
        lifecycle.leakStatus = "WAITING_FOR_SHARED_LEASE_RELEASE";
    }

    public static synchronized void fallbackStarted(final UUID impactId,
        final String reason) {
        Lifecycle lifecycle = IMPACTS.get(impactId);
        if (lifecycle != null) lifecycle.fallbackReason = reason == null
            ? "prepared_stream_failed" : reason;
    }

    public static synchronized void leaseReleased(final ServerLevel level,
        final UUID preparationId, final int releasedChunks) {
        long now = level == null ? -1L : level.getGameTime();
        forEach(preparationId, lifecycle -> {
            lifecycle.ticketsReleased = Math.max(lifecycle.ticketsReleased,
                Math.max(0, releasedChunks));
            lifecycle.leaseReleasedTick = now;
            lifecycle.leakStatus = "CLEAN";
        });
    }

    public static synchronized void observeServerTick(final long nanos) {
        for (Lifecycle lifecycle : IMPACTS.values()) {
            if (lifecycle.actualImpactTick < 0L || lifecycle.completionTick >= 0L
                || lifecycle.impactTickNanos.size() >= IMPACT_TICK_SAMPLES) continue;
            lifecycle.impactTickNanos.add(Math.max(0L, nanos));
        }
    }

    public static synchronized long slaViolationCount() {
        return IMPACTS.values().stream().filter(lifecycle -> lifecycle.slaViolated).count();
    }

    public static synchronized void reset() {
        IMPACTS.clear();
        BY_PREPARATION.clear();
    }

    public static synchronized void appendReport(final StringBuilder report) {
        report.append("\nPrepared nuclear impact lifecycles:\n");
        if (IMPACTS.isEmpty()) {
            report.append("- no prepared-impact samples have been recorded in this server run\n");
            return;
        }
        for (Lifecycle value : IMPACTS.values()) appendLifecycle(report, value);
        report.append("\nPrepared-impact summary by yield:\n");
        Map<String, List<Lifecycle>> grouped = new LinkedHashMap<>();
        for (Lifecycle lifecycle : IMPACTS.values()) {
            grouped.computeIfAbsent(lifecycle.yield, ignored -> new ArrayList<>()).add(lifecycle);
        }
        for (Map.Entry<String, List<Lifecycle>> entry : grouped.entrySet()) {
            appendSummary(report, entry.getKey(), entry.getValue());
        }
        report.append("- serverBytesSent=-1 means compressed vanilla full-chunk packet bytes "
            + "are not exposed by the mapped send API; no estimate is reported as measured data.\n");
    }

    private static void appendLifecycle(final StringBuilder report,
        final Lifecycle value) {
        report.append("- impactId=").append(value.impactId)
            .append(", preparationId=").append(value.preparationId)
            .append(", rootTrackId=").append(value.rootTrackId)
            .append(", yield=").append(value.yield)
            .append(", delivery=").append(value.deliveryMode)
            .append(", requestTick=").append(value.requestTick)
            .append(", expectedImpactTick=").append(value.expectedImpactTick)
            .append(", requiredChunks=").append(value.requiredChunks)
            .append(", unionRequiredChunks=").append(value.unionRequiredChunks)
            .append(", ticketsRequested=").append(value.ticketsRequested)
            .append(", ticketsAcquired=").append(value.ticketsAcquired)
            .append(", ticketsReleased=").append(value.ticketsReleased)
            .append(", firstChunkReadyTick=").append(value.firstChunkReadyTick)
            .append(", allChunksReadyTick=").append(value.allChunksReadyTick)
            .append(", snapshotCompleteTick=").append(value.snapshotCompleteTick)
            .append(", compileCompleteTick=").append(value.compileCompleteTick)
            .append(", readinessAtImpact=")
            .append(String.format(Locale.ROOT, "%.2f%%", value.planReadinessPercentAtAttempt))
            .append(", actualImpactTick=").append(value.actualImpactTick)
            .append(", visualImpactTick=").append(value.visualImpactTick)
            .append(", entityBlastTick=").append(value.entityBlastTick)
            .append(", impactSealedTick=").append(value.impactSealedTick)
            .append(", firstPreparedChunkTick=").append(value.firstPreparedChunkTick)
            .append(", preparedChunksAtSeal=").append(value.preparedChunksAtSeal)
            .append(", compilingChunksAtSeal=").append(value.compilingChunksAtSeal)
            .append(", unsnapshottedChunksAtSeal=").append(
                value.unsnapshottedChunksAtSeal)
            .append(", detonationGateWaitTicks=").append(
                delta(value.actualImpactTick, value.impactSealedTick))
            .append(", firstCommitTick=").append(value.firstAuthoritativeCommitTick)
            .append(", lastBlockTick=").append(value.lastAuthoritativeBlockCommitTick)
            .append(", lastBiomeTick=").append(value.lastBiomeCommitTick)
            .append(", lightingQueuedTick=").append(value.lightingQueuedTick)
            .append(", lightingCompletedTick=").append(value.lightingCompletedTick)
            .append(", lastClientAckTick=").append(value.lastTrackedClientAckTick)
            .append(", changedChunks=").append(value.changedChunks)
            .append(", changedSections=").append(value.changedSections)
            .append(", changedBlocks=").append(value.changedBlocks)
            .append(", changedBiomeQuarts=").append(value.changedBiomeQuarts)
            .append(", bulkSafe=").append(value.bulkSafeMutations)
            .append(", specialPath=").append(value.specialPathMutations)
            .append(", conflicts=").append(value.revisionConflicts)
            .append(", survivalRejections=").append(value.survivalRejections)
            .append(", semanticRejections=").append(value.semanticRejections)
            .append(", alreadyEqual=").append(value.alreadyEqualCells)
            .append(", plannedCategories=").append(value.plannedCategories)
            .append(", appliedCategories=").append(value.appliedCategories)
            .append(", replacementHistogram=").append(value.replacementHistogram)
            .append(", recompiledSections=").append(value.recompiledSections)
            .append(", fallbackReason=").append(value.fallbackReason)
            .append(", serverBytesSent=-1")
            .append(", serverPacketsSent=").append(value.serverPacketsSent)
            .append(", peakSnapshotBytesEstimate=")
            .append(value.peakSnapshotMemoryEstimate)
            .append(", peakPlanBytesEstimate=").append(value.peakPlanMemoryEstimate)
            .append(", leaseLifetimeTicks=").append(delta(value.requestTick,
                value.leaseReleasedTick))
            .append(", leakStatus=").append(value.leakStatus)
            .append(", slaViolated=").append(value.slaViolated).append('\n');
    }

    private static void appendSummary(final StringBuilder report, final String yield,
        final List<Lifecycle> values) {
        long[] load = deltas(values, value -> delta(value.requestTick,
            value.allChunksReadyTick));
        long[] snapshot = deltas(values, value -> delta(value.allChunksReadyTick,
            value.snapshotCompleteTick));
        long[] compile = deltas(values, value -> delta(value.snapshotCompleteTick,
            value.compileCompleteTick));
        long[] serverFinal = deltas(values, value -> delta(value.actualImpactTick,
            Math.max(value.lastAuthoritativeBlockCommitTick, value.lastBiomeCommitTick)));
        long[] clientFinal = deltas(values, value -> delta(value.actualImpactTick,
            value.lastTrackedClientAckTick));
        ArrayList<Long> ticks = new ArrayList<>();
        for (Lifecycle value : values) ticks.addAll(value.impactTickNanos);
        long[] tickNanos = ticks.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(tickNanos);
        report.append("- ").append(yield).append(": samples=").append(values.size())
            .append(", chunkLoadTicks[p50/p95/p99]=").append(triple(load))
            .append(", snapshotTicks=").append(triple(snapshot))
            .append(", compileTicks=").append(triple(compile))
            .append(", impactToServerFinalTicks=").append(triple(serverFinal))
            .append(", impactToClientAckTicks=").append(triple(clientFinal))
            .append(", impactWindowMSPT[p50/p95/p99/max]=")
            .append(millisQuartet(tickNanos))
            .append(", violations=")
            .append(values.stream().filter(value -> value.slaViolated).count())
            .append('\n');
    }

    private static long[] deltas(final List<Lifecycle> values,
        final java.util.function.ToLongFunction<Lifecycle> extractor) {
        long[] result = values.stream().mapToLong(extractor).filter(value -> value >= 0L)
            .sorted().toArray();
        return result;
    }

    private static String triple(final long[] sorted) {
        if (sorted.length == 0) return "unavailable";
        return percentile(sorted, 0.50) + "/" + percentile(sorted, 0.95)
            + "/" + percentile(sorted, 0.99);
    }

    private static String millisQuartet(final long[] sorted) {
        if (sorted.length == 0) return "unavailable";
        return String.format(Locale.ROOT, "%.2f/%.2f/%.2f/%.2f",
            percentile(sorted, 0.50) / 1_000_000.0,
            percentile(sorted, 0.95) / 1_000_000.0,
            percentile(sorted, 0.99) / 1_000_000.0,
            sorted[sorted.length - 1] / 1_000_000.0);
    }

    private static long percentile(final long[] sorted, final double fraction) {
        int index = Math.min(sorted.length - 1,
            Math.max(0, (int)Math.ceil(sorted.length * fraction) - 1));
        return sorted[index];
    }

    private static long delta(final long start, final long end) {
        return start < 0L || end < 0L ? -1L : Math.max(0L, end - start);
    }

    private static long valueOr(final long current, final long replacement) {
        return current < 0L ? replacement : current;
    }

    private static void forEach(final UUID preparationId,
        final java.util.function.Consumer<Lifecycle> action) {
        List<UUID> ids = BY_PREPARATION.get(preparationId);
        if (ids == null) return;
        for (UUID id : ids) {
            Lifecycle lifecycle = IMPACTS.get(id);
            if (lifecycle != null) action.accept(lifecycle);
        }
    }

    private static void trim() {
        while (IMPACTS.size() > RETAINED_IMPACTS) {
            UUID first = IMPACTS.keySet().iterator().next();
            IMPACTS.remove(first);
            BY_PREPARATION.replaceAll((ignored, ids) -> ids.stream()
                .filter(id -> !id.equals(first)).toList());
            BY_PREPARATION.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    private static final class Lifecycle {
        private final UUID impactId;
        private UUID preparationId;
        private UUID rootTrackId;
        private String yield = "unknown";
        private String deliveryMode = "unknown";
        private long requestTick = -1L;
        private long expectedImpactTick = -1L;
        private long requiredChunks;
        private long unionRequiredChunks;
        private long ticketsRequested;
        private long ticketsAcquired;
        private long ticketsReleased;
        private long firstChunkReadyTick = -1L;
        private long allChunksReadyTick = -1L;
        private long snapshotCompleteTick = -1L;
        private long compileCompleteTick = -1L;
        private long planReadyTick = -1L;
        private double planReadinessPercentAtAttempt;
        private long actualImpactTick = -1L;
        private long visualImpactTick = -1L;
        private long entityBlastTick = -1L;
        private long impactSealedTick = -1L;
        private long firstPreparedChunkTick = -1L;
        private long preparedChunksAtSeal;
        private long compilingChunksAtSeal;
        private long unsnapshottedChunksAtSeal;
        private long firstAuthoritativeCommitTick = -1L;
        private long lastAuthoritativeBlockCommitTick = -1L;
        private long lastBiomeCommitTick = -1L;
        private long lightingQueuedTick = -1L;
        private long lightingCompletedTick = -1L;
        private long lastTrackedClientAckTick = -1L;
        private long completionTick = -1L;
        private long leaseReleasedTick = -1L;
        private long changedChunksPlanned;
        private long changedSectionsPlanned;
        private long changedBlocksPlanned;
        private long changedBiomeQuartsPlanned;
        private long bulkSafePlanned;
        private long specialPathPlanned;
        private MutationCategoryCounts plannedCategories = MutationCategoryCounts.empty();
        private Map<Integer, Long> replacementHistogram = Map.of();
        private long changedChunks;
        private final HashSet<Long> changedChunkIds = new HashSet<>();
        private long changedSections;
        private long changedBlocks;
        private long changedBiomeQuarts;
        private long bulkSafeMutations;
        private long specialPathMutations;
        private long revisionConflicts;
        private long survivalRejections;
        private long semanticRejections;
        private long alreadyEqualCells;
        private MutationCategoryCounts appliedCategories = MutationCategoryCounts.empty();
        private long recompiledSections;
        private String fallbackReason = "none";
        private long serverPacketsSent;
        private long peakSnapshotMemoryEstimate;
        private long peakPlanMemoryEstimate;
        private String leakStatus = "UNOBSERVED";
        private boolean slaViolated;
        private final ArrayList<Long> impactTickNanos = new ArrayList<>();

        private Lifecycle(final UUID impactId) { this.impactId = impactId; }
    }
}
