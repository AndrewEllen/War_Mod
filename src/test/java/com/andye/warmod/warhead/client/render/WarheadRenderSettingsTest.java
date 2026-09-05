package com.andye.warmod.warhead.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WarheadRenderSettingsTest {
    @AfterEach
    void restoreDefaults() {
        WarheadRenderSettings.resetQualityScale();
        WarheadRenderSettings.setParticleRenderer(
            WarheadRenderSettings.ParticleRenderer.PACKED);
    }

    @Test
    void qualityScaleIsBoundedAndScalesDetailCapacity() {
        WarheadRenderSettings.setQualityScale(4.0F);

        assertEquals(4.0F, WarheadRenderSettings.qualityScale());
        assertEquals(262_144,
            WarheadRenderSettings.conventionalParticleBudget());
        assertThrows(IllegalArgumentException.class,
            () -> WarheadRenderSettings.setQualityScale(0.24F));
        assertThrows(IllegalArgumentException.class,
            () -> WarheadRenderSettings.setQualityScale(4.01F));
    }

    @Test
    void resetRestoresNeutralGpuScaleWithoutChangingCpuMode() {
        WarheadRenderSettings.setParticleRenderer(
            WarheadRenderSettings.ParticleRenderer.LEGACY);
        WarheadRenderSettings.setQualityScale(0.25F);

        WarheadRenderSettings.resetQualityScale();

        assertEquals(1.0F, WarheadRenderSettings.qualityScale());
        assertEquals(WarheadRenderSettings.ParticleRenderer.LEGACY,
            WarheadRenderSettings.particleRenderer(),
            "Quality reset must not silently change the selected CPU renderer");
    }
}
