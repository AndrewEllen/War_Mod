package com.andye.warmod.particle.gpu;

import com.andye.warmod.particle.gpu.GpuParticleEngine.LayerHealth;
import com.andye.warmod.particle.gpu.GpuParticleEngine.VisualLayer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure authority policy for a semantic GPU layer. Synthetic probes can prove
 * that the shader path is callable, but only matched frames containing real
 * scheduled work can grant or retain rendering authority.
 */
final class GpuLayerHealthPolicy {
    static final int REQUIRED_REAL_SUCCESSES = 8;
    static final int REQUIRED_ZERO_VISIBLE_FAILURES = 8;
    static final int CROSSFADE_FRAMES = 12;
    static final long RETRY_DELAY_FRAMES = 120L;
    static final long PROBE_TIMEOUT_FRAMES = 120L;

    private final Set<VisualLayer> capableLayers;
    private final EnumMap<VisualLayer, LayerHealth> health =
        new EnumMap<>(VisualLayer.class);
    private final int[] zeroVisibleStreak = new int[VisualLayer.values().length];
    private final int[] realSuccessStreak = new int[VisualLayer.values().length];
    private final int[] crossfadeProgress = new int[VisualLayer.values().length];
    private final long[] retryAfterFrame = new long[VisualLayer.values().length];
    private final long[] probeStartedFrame = new long[VisualLayer.values().length];
    private final long[] lastEvaluatedFrame = new long[VisualLayer.values().length];

    GpuLayerHealthPolicy(final Set<VisualLayer> capableLayers) {
        this.capableLayers = Set.copyOf(capableLayers);
        reset();
    }

    void reset() {
        health.clear();
        for (VisualLayer layer : VisualLayer.values()) health.put(layer,
            capableLayers.contains(layer) ? LayerHealth.UNPROBED : LayerHealth.FAILED);
        Arrays.fill(zeroVisibleStreak, 0);
        Arrays.fill(realSuccessStreak, 0);
        Arrays.fill(crossfadeProgress, 0);
        Arrays.fill(retryAfterFrame, 0L);
        Arrays.fill(probeStartedFrame, Long.MIN_VALUE);
        Arrays.fill(lastEvaluatedFrame, Long.MIN_VALUE);
    }

    LayerHealth health(final VisualLayer layer) {
        return layer == null ? LayerHealth.FAILED
            : health.getOrDefault(layer, LayerHealth.FAILED);
    }

    Map<VisualLayer, LayerHealth> snapshot() {
        return Map.copyOf(health);
    }

    boolean canStartProbe(final VisualLayer layer, final long frameSequence) {
        LayerHealth state = health(layer);
        return state == LayerHealth.UNPROBED
            || state == LayerHealth.DEGRADED
                && frameSequence >= retryAfterFrame[layer.ordinal()];
    }

    void startProbe(final VisualLayer layer, final long frameSequence) {
        if (!canStartProbe(layer, frameSequence)) return;
        int index = layer.ordinal();
        health.put(layer, LayerHealth.PROBING);
        probeStartedFrame[index] = frameSequence;
        zeroVisibleStreak[index] = 0;
        realSuccessStreak[index] = 0;
        crossfadeProgress[index] = 0;
    }

    ProbeResult recordSyntheticProbe(final VisualLayer layer, final long frameSequence,
        final boolean producedVisibleOutput) {
        if (health(layer) != LayerHealth.PROBING) return ProbeResult.IGNORED;
        int index = layer.ordinal();
        if (producedVisibleOutput) {
            health.put(layer, LayerHealth.VERIFYING);
            realSuccessStreak[index] = 0;
            return ProbeResult.PASSED_DIAGNOSTIC;
        }
        if (frameSequence - probeStartedFrame[index] <= PROBE_TIMEOUT_FRAMES)
            return ProbeResult.PENDING;
        health.put(layer, LayerHealth.FAILED);
        return ProbeResult.FAILED;
    }

    Evaluation evaluate(final VisualLayer layer, final MatchedFrame sample) {
        if (layer == null || sample == null || !capableLayers.contains(layer))
            return Evaluation.IGNORED;
        int index = layer.ordinal();
        if (sample.frameSequence() <= lastEvaluatedFrame[index]
            || sample.scheduleIdentity() != sample.statsScheduleIdentity())
            return Evaluation.IGNORED;
        lastEvaluatedFrame[index] = sample.frameSequence();
        if (sample.syntheticProbe() || !sample.realDemand())
            return Evaluation.IGNORED;

        LayerHealth before = health(layer);
        if (before != LayerHealth.VERIFYING && before != LayerHealth.CROSSFADE
            && before != LayerHealth.HEALTHY) return Evaluation.IGNORED;
        // A few visible particles do not prove that all burning locations were
        // admitted. Revert to CPU on a matched coverage failure, even if the GPU
        // produced other output or accepted no work for the omitted locations.
        if ((layer == VisualLayer.FLAMES || layer == VisualLayer.SMOKE)
            && !sample.fireCoverageComplete()) {
            realSuccessStreak[index] = 0;
            zeroVisibleStreak[index] = 0;
            crossfadeProgress[index] = 0;
            health.put(layer, LayerHealth.DEGRADED);
            retryAfterFrame[index] = sample.frameSequence() + RETRY_DELAY_FRAMES;
            return Evaluation.COVERAGE_LOST;
        }
        if (!sample.expectedVisibility() || !sample.expectedOutput())
            return Evaluation.IGNORED;
        if (sample.visibleInstances() <= 0L) {
            realSuccessStreak[index] = 0;
            if (++zeroVisibleStreak[index] < REQUIRED_ZERO_VISIBLE_FAILURES)
                return Evaluation.REAL_FAILURE;
            zeroVisibleStreak[index] = 0;
            crossfadeProgress[index] = 0;
            health.put(layer, LayerHealth.DEGRADED);
            retryAfterFrame[index] = sample.frameSequence() + RETRY_DELAY_FRAMES;
            return before == LayerHealth.DEGRADED
                ? Evaluation.REAL_FAILURE : Evaluation.DEGRADED;
        }

        zeroVisibleStreak[index] = 0;
        LayerHealth state = health(layer);
        if (state == LayerHealth.VERIFYING) {
            if (++realSuccessStreak[index] >= REQUIRED_REAL_SUCCESSES) {
                health.put(layer, LayerHealth.CROSSFADE);
                crossfadeProgress[index] = 0;
                return Evaluation.CROSSFADE_STARTED;
            }
            return Evaluation.REAL_SUCCESS;
        }
        if (state == LayerHealth.CROSSFADE) {
            if (++crossfadeProgress[index] >= CROSSFADE_FRAMES) {
                health.put(layer, LayerHealth.HEALTHY);
                return Evaluation.AUTHORITY_GRANTED;
            }
            return Evaluation.CROSSFADE_ADVANCED;
        }
        return Evaluation.REAL_SUCCESS;
    }

    boolean shouldSubmitRealWork(final VisualLayer layer) {
        LayerHealth state = health(layer);
        return state == LayerHealth.VERIFYING || state == LayerHealth.CROSSFADE
            || state == LayerHealth.HEALTHY;
    }

    boolean gpuAuthoritative(final VisualLayer layer) {
        return health(layer) == LayerHealth.HEALTHY;
    }

    float gpuOpticalWeight(final VisualLayer layer) {
        LayerHealth state = health(layer);
        if (state == LayerHealth.HEALTHY) return 1.0F;
        if (state == LayerHealth.CROSSFADE) {
            int progress = crossfadeProgress[layer.ordinal()];
            return Math.min(1.0F, (progress + 1.0F) / CROSSFADE_FRAMES);
        }
        /* A low diagnostic contribution lets real raster work be observed while
           the established CPU path remains visually authoritative. */
        return state == LayerHealth.VERIFYING ? 0.08F : 0.0F;
    }

    float cpuOpticalWeight(final VisualLayer layer) {
        LayerHealth state = health(layer);
        if (state == LayerHealth.HEALTHY) return 0.0F;
        if (state == LayerHealth.CROSSFADE) return 1.0F - gpuOpticalWeight(layer);
        return 1.0F;
    }

    String actualRoute(final VisualLayer layer) {
        return switch (health(layer)) {
            case HEALTHY -> "gpu";
            case CROSSFADE -> "cpu+gpu-crossfade";
            case VERIFYING -> "cpu+gpu-verify";
            default -> "cpu";
        };
    }

    enum ProbeResult { IGNORED, PENDING, PASSED_DIAGNOSTIC, FAILED }
    enum Evaluation {
        IGNORED, REAL_SUCCESS, REAL_FAILURE, CROSSFADE_STARTED,
        CROSSFADE_ADVANCED, AUTHORITY_GRANTED, DEGRADED, COVERAGE_LOST
    }

    record MatchedFrame(long frameSequence, long scheduleIdentity,
        long statsScheduleIdentity, boolean syntheticProbe,
        boolean realDemand, boolean expectedVisibility,
        boolean expectedOutput, long visibleInstances, boolean fireCoverageComplete) {
        MatchedFrame(final long frameSequence, final long scheduleIdentity,
            final long statsScheduleIdentity, final boolean syntheticProbe,
            final boolean realDemand, final boolean expectedVisibility,
            final boolean expectedOutput, final long visibleInstances) {
            this(frameSequence, scheduleIdentity, statsScheduleIdentity, syntheticProbe,
                realDemand, expectedVisibility, expectedOutput, visibleInstances, true);
        }
    }
}
