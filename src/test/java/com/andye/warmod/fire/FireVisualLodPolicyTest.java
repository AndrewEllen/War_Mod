package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.fire.network.FireVisualBand;
import org.junit.jupiter.api.Test;

final class FireVisualLodPolicyTest {
    @Test
    void projectedSizeSelectsIncreasingDetail() {
        assertEquals(4, FireVisualLodPolicy.level(1.0));
        assertEquals(3, FireVisualLodPolicy.level(2.0));
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
        assertTrue(FireVisualLodPolicy.density(3) > FireVisualLodPolicy.density(4));
        assertTrue(FireVisualLodPolicy.particleScale(1.0) <= 1.38F);
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
        assertEquals(3, FireVisualLodPolicy.level(1.0, 3));
        assertEquals(4, FireVisualLodPolicy.level(1.0, 4));
    }

    @Test
    void projectedDetailSelectsAndNormalizesParentChildRepresentations() {
        float zoomedHost = FireVisualLodPolicy.representationWeight(
            FireVisualBand.HOST, 170.0, 0);
        float normalHost = FireVisualLodPolicy.representationWeight(
            FireVisualBand.HOST, 170.0, 2);
        assertTrue(zoomedHost > normalHost,
            "zoom must prefer the finer available host representation");

        double sum = 0.0;
        for (FireVisualBand band : FireVisualBand.values()) {
            sum += FireVisualLodPolicy.representationWeight(band, 350.0, 3);
        }
        assertEquals(1.0, sum, 0.0001);
        assertTrue(FireVisualLodPolicy.representationWeight(
            FireVisualBand.HORIZON, 1_520.0, 4) < 1.0F);
    }
}
