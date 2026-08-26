package com.andye.warmod.warhead.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WarheadRenderSettingsTest {
    @AfterEach
    void restoreDefaults() {
        WarheadRenderSettings.resetParticleBudget();
        WarheadRenderSettings.setParticleRenderer(
            WarheadRenderSettings.ParticleRenderer.PACKED);
    }

    @Test
    void oneBudgetControlScalesCpuAndGpuPathsExplicitly() {
        WarheadRenderSettings.setParticleBudgetMultiplier(20.0F);

        assertEquals(20.0F,
            WarheadRenderSettings.particleBudgetMultiplier());
        assertEquals(1_310_720,
            WarheadRenderSettings.conventionalParticleBudget());
        assertEquals(81_920,
            WarheadRenderSettings.nuclearSupplementBudget());
        assertEquals(2.0,
            WarheadRenderSettings.gpuBudgetScale(), 0.000_001);
    }

    @Test
    void resetRestoresNeutralGpuScaleWithoutChangingCpuMode() {
        WarheadRenderSettings.setParticleRenderer(
            WarheadRenderSettings.ParticleRenderer.LEGACY);
        WarheadRenderSettings.setParticleBudgetMultiplier(1.0F);

        WarheadRenderSettings.resetParticleBudget();

        assertEquals(10.0F,
            WarheadRenderSettings.particleBudgetMultiplier());
        assertEquals(1.0,
            WarheadRenderSettings.gpuBudgetScale(), 0.000_001);
        assertEquals(WarheadRenderSettings.ParticleRenderer.LEGACY,
            WarheadRenderSettings.particleRenderer(),
            "Budget reset must not silently change the selected CPU renderer");
    }
}
