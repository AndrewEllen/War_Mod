package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NuclearBiomeDomeTest {
    @Test
    void biomeExtendsTenPercentPastPhysicalAftermath() {
        assertEquals(220.0, NuclearBiomeDome.radius(200.0), 0.0001);
    }

    @Test
    void verticalVolumeFormsADomeWithAThirtyTwoBlockEdge() {
        double radius = NuclearBiomeDome.radius(200.0);
        int center = NuclearBiomeDome.verticalHeight(0.0, radius, 2.0F);
        int middle = NuclearBiomeDome.verticalHeight(radius * 0.65, radius, 2.0F);
        int edge = NuclearBiomeDome.verticalHeight(radius, radius, 2.0F);

        assertTrue(center > middle);
        assertTrue(middle > edge);
        assertEquals(NuclearBiomeDome.EDGE_HEIGHT, edge);
        assertEquals(-1, NuclearBiomeDome.verticalHeight(radius + 0.1,
            radius, 2.0F));
    }

    @Test
    void outerTwelvePercentUsesDeterministicFeathering() {
        double radius = 220.0;
        assertTrue(NuclearBiomeDome.survivesFeather(radius * 0.88,
            radius, 0.999));
        assertTrue(NuclearBiomeDome.survivesFeather(radius * 0.94,
            radius, 0.25));
        assertFalse(NuclearBiomeDome.survivesFeather(radius * 0.94,
            radius, 0.75));
    }
}
