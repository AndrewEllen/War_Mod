package com.andye.warmod.particle.gpu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GpuReadinessGateTest {
    @Test
    void rawBillboardsAloneCannotEnableGpuLayers() {
        assertFalse(GpuParticleEngine.readinessProofComplete(
            true, true, false, 0, 7));
    }

    @Test
    void worldOcclusionIsRequired() {
        assertFalse(GpuParticleEngine.readinessProofComplete(
            true, true, false, 7, 7));
    }

    @Test
    void everyDirectEmitterTypeIsRequired() {
        assertFalse(GpuParticleEngine.readinessProofComplete(
            true, true, true, 6, 7));
    }

    @Test
    void completeDepthOcclusionAndEmitterProofCanEnableGpuLayers() {
        assertTrue(GpuParticleEngine.readinessProofComplete(
            true, true, true, 7, 7));
    }
}
