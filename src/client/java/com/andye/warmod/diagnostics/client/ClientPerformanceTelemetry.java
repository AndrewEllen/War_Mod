package com.andye.warmod.diagnostics.client;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.andye.warmod.warhead.network.ClientboundWarheadTerrainCommitPayload;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/** Bounded client frame/extraction timing with tail-latency percentiles. */
public final class ClientPerformanceTelemetry {
    private static final int WINDOW = 240;
    private static final Samples FRAME = new Samples();
    private static final Samples EXPLOSION = new Samples();
    private static final Samples IMPACT_PAYLOAD_ACCEPT = new Samples();
    private static final Samples IMPACT_VISUAL_STATE_CONSTRUCTION = new Samples();
    private static final Samples FIREBALL_LOBE_PREPARATION = new Samples();
    private static final Samples CLOUD_LOBE_PREPARATION = new Samples();
    private static final Samples DUST_NODE_SELECTION = new Samples();
    private static final Samples DEBRIS_SNAPSHOT = new Samples();
    private static final Samples MOVING_BLOCK_STATE_CONSTRUCTION = new Samples();
    private static final Samples FIRE = new Samples();
    private static final Samples GPU_ENGINE_CPU = new Samples();
    private static final Samples GPU_EXTRACTION_CPU = new Samples();
    private static final Samples GPU_SCHEDULER_CPU = new Samples();
    private static final Samples GPU_EMITTER_UPLOAD_CPU = new Samples();
    private static final Samples GPU_STATS_READBACK_CPU = new Samples();
    private static final Samples TERRAIN_SHOCKFRONT_CPU = new Samples();
    private static final Samples TERRAIN_MARKER_CALLBACK = new Samples();
    private static final Samples TERRAIN_MARKER_PROCESSING = new Samples();
    private static final Samples IMPACT_TO_TERRAIN_FINAL = new Samples();
    private static final Samples VANILLA_PARTICLE_EXTRACTION_CPU = new Samples();
    private static final Samples VANILLA_PARTICLE_RENDER_CPU = new Samples();
    private static long vanillaParticleCount;
    private static long terrainChangedChunks;
    private static long terrainChangedSections;
    private static long terrainChangedCells;
    private static long terrainChangedBiomeQuarts;
    private static long terrainMaximumQueueDepth;
    private static long terrainSequenceGaps;
    private static final Map<UUID, Long> IMPACT_ACCEPTED_NANOS = new HashMap<>();
    private static final Map<UUID, Long> TERRAIN_SEQUENCES = new HashMap<>();
    private static long frameStarted;
    private static boolean registered;

    private ClientPerformanceTelemetry() { }

    public static synchronized void register() {
        if (registered) return;
        LevelRenderEvents.START_MAIN.register(context -> frameStarted = System.nanoTime());
        LevelRenderEvents.END_MAIN.register(context -> {
            long started = frameStarted;
            frameStarted = 0L;
            if (started > 0L) FRAME.add(System.nanoTime() - started);
        });
        registered = true;
    }

    public static synchronized void recordExplosionNanos(final long nanos) {
        EXPLOSION.add(nanos);
    }
    public static synchronized void recordImpactPayloadAcceptNanos(final long nanos) {
        IMPACT_PAYLOAD_ACCEPT.add(nanos);
    }
    public static synchronized void recordImpactVisualStateConstructionNanos(
        final long nanos) {
        IMPACT_VISUAL_STATE_CONSTRUCTION.add(nanos);
    }
    public static synchronized void recordFireballLobePreparationNanos(final long nanos) {
        FIREBALL_LOBE_PREPARATION.add(nanos);
    }
    public static synchronized void recordCloudLobePreparationNanos(final long nanos) {
        CLOUD_LOBE_PREPARATION.add(nanos);
    }
    public static synchronized void recordDustNodeSelectionNanos(final long nanos) {
        DUST_NODE_SELECTION.add(nanos);
    }
    public static synchronized void recordDebrisSnapshotNanos(final long nanos) {
        DEBRIS_SNAPSHOT.add(nanos);
    }
    public static synchronized void recordMovingBlockStateConstructionNanos(
        final long nanos) {
        MOVING_BLOCK_STATE_CONSTRUCTION.add(nanos);
    }
    public static synchronized void recordFireNanos(final long nanos) { FIRE.add(nanos); }
    public static synchronized void recordGpuEngineCpuNanos(final long nanos) {
        GPU_ENGINE_CPU.add(nanos);
    }
    public static synchronized void recordGpuExtractionNanos(final long nanos) {
        GPU_EXTRACTION_CPU.add(nanos);
    }
    public static synchronized void recordGpuSchedulerNanos(final long nanos) {
        GPU_SCHEDULER_CPU.add(nanos);
    }
    public static synchronized void recordGpuEmitterUploadNanos(final long nanos) {
        GPU_EMITTER_UPLOAD_CPU.add(nanos);
    }
    public static synchronized void recordGpuStatsReadbackNanos(final long nanos) {
        GPU_STATS_READBACK_CPU.add(nanos);
    }
    public static synchronized void recordTerrainShockfrontNanos(final long nanos) {
        TERRAIN_SHOCKFRONT_CPU.add(nanos);
    }
    public static synchronized void markImpactAccepted(final UUID impactId) {
        if (impactId == null) return;
        IMPACT_ACCEPTED_NANOS.put(impactId, System.nanoTime());
        terrainMaximumQueueDepth = Math.max(terrainMaximumQueueDepth,
            IMPACT_ACCEPTED_NANOS.size());
    }
    public static synchronized void acceptTerrainCommit(
        final ClientboundWarheadTerrainCommitPayload payload, final long callbackStarted) {
        if (payload == null) return;
        long now = System.nanoTime();
        /* Fabric decodes before invoking this render-thread callback, so this is the
         * observable callback/dispatch cost, not a fabricated codec-only duration. */
        TERRAIN_MARKER_CALLBACK.add(Math.max(0L, now - callbackStarted));
        long previous = TERRAIN_SEQUENCES.getOrDefault(payload.impactId(), 0L);
        if (payload.sequence() != previous + 1L) terrainSequenceGaps++;
        TERRAIN_SEQUENCES.put(payload.impactId(), payload.sequence());
        terrainChangedChunks += payload.changedChunks();
        terrainChangedSections += payload.changedSections();
        terrainChangedCells += payload.changedCells();
        terrainChangedBiomeQuarts += payload.changedBiomeQuarts();
        Long accepted = IMPACT_ACCEPTED_NANOS.remove(payload.impactId());
        if (accepted != null) IMPACT_TO_TERRAIN_FINAL.add(Math.max(0L, now - accepted));
        /* Vanilla full-chunk packets have already installed blocks, biomes, light and
         * render invalidations when this ordered marker reaches the client thread. */
        TERRAIN_MARKER_PROCESSING.add(Math.max(0L, System.nanoTime() - now));
    }
    public static synchronized void clearTerrainLifecycle() {
        IMPACT_ACCEPTED_NANOS.clear();
        TERRAIN_SEQUENCES.clear();
    }
    public static synchronized void recordVanillaParticleExtractionNanos(final long nanos,
        final long particleCount) {
        VANILLA_PARTICLE_EXTRACTION_CPU.add(nanos);
        vanillaParticleCount = Math.max(0L, particleCount);
    }
    public static synchronized void recordVanillaParticleRenderNanos(final long nanos) {
        VANILLA_PARTICLE_RENDER_CPU.add(nanos);
    }

    public static synchronized DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(FRAME.snapshot(), EXPLOSION.snapshot(),
            IMPACT_PAYLOAD_ACCEPT.snapshot(), IMPACT_VISUAL_STATE_CONSTRUCTION.snapshot(),
            FIREBALL_LOBE_PREPARATION.snapshot(), CLOUD_LOBE_PREPARATION.snapshot(),
            DUST_NODE_SELECTION.snapshot(), DEBRIS_SNAPSHOT.snapshot(),
            MOVING_BLOCK_STATE_CONSTRUCTION.snapshot(), FIRE.snapshot(),
            GPU_ENGINE_CPU.snapshot(), GPU_EXTRACTION_CPU.snapshot(),
            GPU_SCHEDULER_CPU.snapshot(), GPU_EMITTER_UPLOAD_CPU.snapshot(),
            GPU_STATS_READBACK_CPU.snapshot(), TERRAIN_SHOCKFRONT_CPU.snapshot(),
            vanillaParticleCount, VANILLA_PARTICLE_EXTRACTION_CPU.snapshot(),
            VANILLA_PARTICLE_RENDER_CPU.snapshot(), TERRAIN_MARKER_CALLBACK.snapshot(),
            TERRAIN_MARKER_PROCESSING.snapshot(), IMPACT_TO_TERRAIN_FINAL.snapshot(),
            terrainChangedChunks, terrainChangedSections, terrainChangedCells,
            terrainChangedBiomeQuarts, terrainMaximumQueueDepth, terrainSequenceGaps);
    }

    public record Percentiles(double p50Millis, double p95Millis,
        double p99Millis, double maximumMillis) { }
    public record DebugSnapshot(Percentiles frame, Percentiles explosionExtraction,
        Percentiles impactPayloadAccept, Percentiles impactVisualStateConstruction,
        Percentiles fireballLobePreparation, Percentiles cloudLobePreparation,
        Percentiles dustNodeSelection, Percentiles debrisSnapshot,
        Percentiles movingBlockStateConstruction,
        Percentiles fireExtraction, Percentiles gpuEngineCpu,
        Percentiles gpuExtractionCpu, Percentiles gpuSchedulerCpu,
        Percentiles gpuEmitterUploadCpu, Percentiles gpuStatsReadbackCpu,
        Percentiles terrainShockfrontCpu, long vanillaParticleCount,
        Percentiles vanillaParticleExtractionCpu,
        Percentiles vanillaParticleRenderCpu, Percentiles terrainMarkerCallback,
        Percentiles terrainMarkerProcessing, Percentiles impactToTerrainFinal,
        long terrainChangedChunks, long terrainChangedSections, long terrainChangedCells,
        long terrainChangedBiomeQuarts, long terrainMaximumQueueDepth,
        long terrainSequenceGaps) { }

    private static final class Samples {
        private final ArrayDeque<Long> values = new ArrayDeque<>(WINDOW);
        private void add(final long nanos) {
            if (nanos < 0L) return;
            if (values.size() == WINDOW) values.removeFirst();
            values.addLast(nanos);
        }
        private Percentiles snapshot() {
            long[] sorted = new long[values.size()];
            int index = 0;
            for (long value : values) sorted[index++] = value;
            Arrays.sort(sorted);
            return new Percentiles(percentile(sorted, 0.50), percentile(sorted, 0.95),
                percentile(sorted, 0.99), sorted.length == 0 ? 0.0
                    : sorted[sorted.length - 1] / 1_000_000.0);
        }
        private static double percentile(final long[] sorted, final double fraction) {
            if (sorted.length == 0) return 0.0;
            int selected = Math.min(sorted.length - 1,
                Math.max(0, (int) Math.ceil(fraction * sorted.length) - 1));
            return sorted[selected] / 1_000_000.0;
        }
    }
}
