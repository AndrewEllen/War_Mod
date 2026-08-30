package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FireVisualLodPolicyTest {
    @Test
    void projectedSizeSelectsIncreasingDetail() {
        assertEquals(3, FireVisualLodPolicy.level(1.0));
        assertEquals(2, FireVisualLodPolicy.level(8.0));
        assertEquals(1, FireVisualLodPolicy.level(20.0));
        assertEquals(0, FireVisualLodPolicy.level(48.0));
    }

    @Test
    void farBlocksHaveOnlyABoundedStableRepresentation() {
        assertEquals(1, FireVisualLodPolicy.representativesPerHost(16, 2.0));
        assertEquals(3, FireVisualLodPolicy.representativesPerHost(16, 8.0));
        assertEquals(6, FireVisualLodPolicy.representativesPerHost(16, 20.0));
        assertEquals(16, FireVisualLodPolicy.representativesPerHost(16, 48.0));
    }

    @Test
    void zoomedProjectionRaisesDetailWithoutDistanceSpecialCases() {
        int normal = FireVisualLodPolicy.representativesPerHost(16, 8.0);
        int zoomed = FireVisualLodPolicy.representativesPerHost(16, 56.0);
        assertEquals(3, normal);
        assertEquals(16, zoomed);
    }

    @Test
    void distantParticleAndEmberCompensationRemainBounded() {
        assertTrue(FireVisualLodPolicy.density(0) > FireVisualLodPolicy.density(1));
        assertTrue(FireVisualLodPolicy.density(1) > FireVisualLodPolicy.density(2));
        assertTrue(FireVisualLodPolicy.density(2) > FireVisualLodPolicy.density(3));
        assertTrue(FireVisualLodPolicy.particleScale(1.0) <= 1.32F);
        assertTrue(FireVisualLodPolicy.emberScale(0.2) <= 1.75F);
        assertTrue(FireVisualLodPolicy.emberRetention(0.2) < 0.10);
    }

    @Test
    void hysteresisPreventsBoundaryChatter() {
        assertEquals(0, FireVisualLodPolicy.level(29.0, 0));
        assertEquals(1, FireVisualLodPolicy.level(29.0, 1));
        assertEquals(1, FireVisualLodPolicy.level(12.0, 1));
        assertEquals(2, FireVisualLodPolicy.level(12.0, 2));
        assertEquals(2, FireVisualLodPolicy.level(3.5, 2));
        assertEquals(3, FireVisualLodPolicy.level(3.5, 3));
    }
}
