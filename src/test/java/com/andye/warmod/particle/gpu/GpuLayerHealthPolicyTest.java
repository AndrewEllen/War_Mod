package com.andye.warmod.particle.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.particle.gpu.GpuLayerHealthPolicy.MatchedFrame;
import com.andye.warmod.particle.gpu.GpuParticleEngine.LayerHealth;
import com.andye.warmod.particle.gpu.GpuParticleEngine.VisualLayer;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GpuLayerHealthPolicyTest {
    @Test
    void unprobedLayerCannotBeDegradedByUnrelatedRealWorkStatistics() {
        GpuLayerHealthPolicy policy = policy();
        for (int frame = 1; frame <= 20; frame++) {
            assertEquals(GpuLayerHealthPolicy.Evaluation.IGNORED,
                policy.evaluate(VisualLayer.FLAMES, matched(frame, 0L)));
        }
        assertEquals(LayerHealth.UNPROBED, policy.health(VisualLayer.FLAMES));
    }

    @Test
    void syntheticProbeNeverGrantsAuthority() {
        GpuLayerHealthPolicy policy = policy();
        policy.startProbe(VisualLayer.FLAMES, 1L);

        policy.recordSyntheticProbe(VisualLayer.FLAMES, 2L, true);

        assertEquals(LayerHealth.VERIFYING, policy.health(VisualLayer.FLAMES));
        assertFalse(policy.gpuAuthoritative(VisualLayer.FLAMES));
        assertTrue(policy.shouldSubmitRealWork(VisualLayer.FLAMES));
    }

    @Test
    void staleAndMismatchedStatisticsDoNotAffectHealth() {
        GpuLayerHealthPolicy policy = verifyingPolicy();
        for (int frame = 3; frame < 30; frame++) {
            policy.evaluate(VisualLayer.FLAMES, new MatchedFrame(frame, 41L, 42L,
                false, true, true, true, 0L));
        }
        assertEquals(LayerHealth.VERIFYING, policy.health(VisualLayer.FLAMES));

        policy.evaluate(VisualLayer.FLAMES, matched(30L, 0L));
        policy.evaluate(VisualLayer.FLAMES, matched(29L, 0L));
        assertEquals(LayerHealth.VERIFYING, policy.health(VisualLayer.FLAMES));
    }

    @Test
    void offCameraAndZeroDemandFramesAreIgnored() {
        GpuLayerHealthPolicy policy = verifyingPolicy();
        for (int frame = 3; frame < 40; frame++) {
            policy.evaluate(VisualLayer.FLAMES, new MatchedFrame(frame, 7L, 7L,
                false, frame % 2 == 0, false, true, 0L));
        }
        assertEquals(LayerHealth.VERIFYING, policy.health(VisualLayer.FLAMES));
    }

    @Test
    void eightRealSuccessesAndTwelveCrossfadeFramesGrantAuthority() {
        GpuLayerHealthPolicy policy = verifyingPolicy();
        long frame = 3L;
        for (int index = 0; index < GpuLayerHealthPolicy.REQUIRED_REAL_SUCCESSES;
            index++) policy.evaluate(VisualLayer.FLAMES, matched(frame++, 4L));
        assertEquals(LayerHealth.CROSSFADE, policy.health(VisualLayer.FLAMES));
        assertTrue(policy.cpuOpticalWeight(VisualLayer.FLAMES) > 0.0F);

        for (int index = 0; index < GpuLayerHealthPolicy.CROSSFADE_FRAMES;
            index++) policy.evaluate(VisualLayer.FLAMES, matched(frame++, 4L));
        assertEquals(LayerHealth.HEALTHY, policy.health(VisualLayer.FLAMES));
        assertEquals(1.0F, policy.gpuOpticalWeight(VisualLayer.FLAMES));
        assertEquals(0.0F, policy.cpuOpticalWeight(VisualLayer.FLAMES));
    }

    @Test
    void eightMatchedZeroVisibleFramesDegradeAHealthyLayer() {
        GpuLayerHealthPolicy policy = verifyingPolicy();
        long frame = 3L;
        for (int index = 0; index < GpuLayerHealthPolicy.REQUIRED_REAL_SUCCESSES
            + GpuLayerHealthPolicy.CROSSFADE_FRAMES; index++)
            policy.evaluate(VisualLayer.FLAMES, matched(frame++, 3L));
        for (int index = 0; index < GpuLayerHealthPolicy.REQUIRED_ZERO_VISIBLE_FAILURES;
            index++) policy.evaluate(VisualLayer.FLAMES, matched(frame++, 0L));

        assertEquals(LayerHealth.DEGRADED, policy.health(VisualLayer.FLAMES));
        assertFalse(policy.gpuAuthoritative(VisualLayer.FLAMES));
        assertEquals(1.0F, policy.cpuOpticalWeight(VisualLayer.FLAMES));
    }

    private static GpuLayerHealthPolicy policy() {
        return new GpuLayerHealthPolicy(Set.of(VisualLayer.FLAMES));
    }

    @Test
    void visibleParticlesCannotGrantAuthorityWithMissingFireLocations() {
        GpuLayerHealthPolicy policy = verifyingPolicy();
        assertEquals(GpuLayerHealthPolicy.Evaluation.COVERAGE_LOST,
            policy.evaluate(VisualLayer.FLAMES, new MatchedFrame(3L, 9L, 9L,
                false, true, true, true, 500L, false)));
        assertEquals(LayerHealth.DEGRADED, policy.health(VisualLayer.FLAMES));
        assertEquals(1.0F, policy.cpuOpticalWeight(VisualLayer.FLAMES));
        assertFalse(policy.canStartProbe(VisualLayer.FLAMES, 4L));
    }

    @Test
    void zeroAdmissionStillFallsBackWhenRequestedFireLocationsExist() {
        GpuLayerHealthPolicy policy = verifyingPolicy();
        assertEquals(GpuLayerHealthPolicy.Evaluation.COVERAGE_LOST,
            policy.evaluate(VisualLayer.FLAMES, new MatchedFrame(3L, 9L, 9L,
                false, true, false, false, 0L, false)));
    }

    private static GpuLayerHealthPolicy verifyingPolicy() {
        GpuLayerHealthPolicy policy = policy();
        policy.startProbe(VisualLayer.FLAMES, 1L);
        policy.recordSyntheticProbe(VisualLayer.FLAMES, 2L, true);
        return policy;
    }

    private static MatchedFrame matched(final long frame, final long visible) {
        return new MatchedFrame(frame, 9L, 9L, false,
            true, true, true, visible);
    }
}
