package com.andye.warmod.particle.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SurfaceFireCadenceTest {
    @Test
    void surfaceStartsImmediatelyAndMaintainsRegularCadence() {
        var cadence = new GpuParticleEngine.SurfaceFireCadence(16);
        assertEquals(1, cadence.sample(7L, 4, 0.05, 1L));
        int total = 1;
        int gap = 0;
        for (long frame = 2; frame <= 40; frame++) {
            int count = cadence.sample(7L, 4, 0.05, frame);
            total += count;
            gap = count > 0 ? 0 : gap + 1;
            assertTrue(gap <= 5, "Fire stream skipped more than its quarter-second cadence");
        }
        assertEquals(9, total);
    }

    @Test
    void cadenceStateIsBoundedAndExpires() {
        var cadence = new GpuParticleEngine.SurfaceFireCadence(4);
        for (int source = 0; source < 100; source++) cadence.sample(source, 2, 0.05, 1L);
        assertEquals(4, cadence.size());
        cadence.prune(192L);
        assertEquals(0, cadence.size());
    }

    @Test
    void sourceReappearingAfterGapSpawnsImmediately() {
        var cadence = new GpuParticleEngine.SurfaceFireCadence(4);
        cadence.sample(7L, 2, 0.05, 1L);
        assertEquals(1, cadence.sample(7L, 2, 0.05, 10L));
        cadence.clear();
        assertEquals(0, cadence.size());
    }
}
