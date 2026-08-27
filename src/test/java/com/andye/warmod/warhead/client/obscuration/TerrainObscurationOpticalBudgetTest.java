package com.andye.warmod.warhead.client.obscuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TerrainObscurationOpticalBudgetTest {
    @Test
    void repeatedCardsNeverExceedScreenBinCap() {
        float accumulated = 0.0F;
        for (int index = 0; index < 20; index++) {
            float admitted = TerrainObscurationOpticalBudget.admittedOpacity(
                accumulated, 0.38F, 0.78F);
            accumulated = TerrainObscurationOpticalBudget.accumulate(accumulated, admitted);
            assertTrue(accumulated <= 0.780_001F);
        }
        assertEquals(0.78F, accumulated, 0.000_01F);
    }

    @Test
    void stableCellBandsCrossfadeWithoutCoverageGap() {
        float innerTransition = ClientNuclearTerrainObscurationManager
            .cellBandWeight(8, 192.0)
            + ClientNuclearTerrainObscurationManager.cellBandWeight(16, 192.0);
        float outerTransition = ClientNuclearTerrainObscurationManager
            .cellBandWeight(16, 512.0)
            + ClientNuclearTerrainObscurationManager.cellBandWeight(32, 512.0);
        assertEquals(1.0F, innerTransition, 0.000_1F);
        assertEquals(1.0F, outerTransition, 0.000_1F);
    }
}
