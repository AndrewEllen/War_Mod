package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

final class CombustionPolicyTest {
    static { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void intensityUsesTheExistingSmallMediumInfernoCadences() {
        assertEquals(18, CombustionPolicy.spreadCadenceTicks(0.42F));
        assertEquals(11, CombustionPolicy.spreadCadenceTicks(0.70F));
        assertEquals(6, CombustionPolicy.spreadCadenceTicks(1.00F));
    }

    @Test
    void missedUpdatesDoNotMultiplyHeatAndIndirectIgnitionNeedsTwoCadences() {
        assertEquals(0.31F, CombustionPolicy.cadenceDose(0.31F));
        int first = CombustionPolicy.distinctExposureCadences(0, Long.MIN_VALUE, 100L);
        int same = CombustionPolicy.distinctExposureCadences(first, 100L, 100L);
        int second = CombustionPolicy.distinctExposureCadences(same, 100L, 111L);
        assertEquals(1, first);
        assertEquals(1, same);
        assertFalse(CombustionPolicy.mayIgnite(false, first));
        assertTrue(CombustionPolicy.mayIgnite(false, second));
        assertTrue(CombustionPolicy.mayIgnite(true, first));
    }

    @Test
    void organicGroundScorchesWithoutExcavation() {
        assertSame(FireFuelProfile.LOW, FireFuelProfile.fallbackForPath("dirt"));
        assertSame(FireFuelProfile.LOW, FireFuelProfile.fallbackForPath("rooted_dirt"));
        assertSame(Blocks.COARSE_DIRT,
            CombustionPolicy.scorchedState(Blocks.GRASS_BLOCK.defaultBlockState()).getBlock());
        assertSame(Blocks.COARSE_DIRT,
            CombustionPolicy.scorchedState(Blocks.DIRT.defaultBlockState()).getBlock());
        assertSame(Blocks.COARSE_DIRT,
            CombustionPolicy.scorchedState(Blocks.ROOTED_DIRT.defaultBlockState()).getBlock());
        assertSame(Blocks.COARSE_DIRT,
            CombustionPolicy.scorchedState(Blocks.MOSS_BLOCK.defaultBlockState()).getBlock());
        assertSame(Blocks.COARSE_DIRT,
            CombustionPolicy.scorchedState(Blocks.MYCELIUM.defaultBlockState()).getBlock());
        assertSame(Blocks.COARSE_DIRT.defaultBlockState(),
            CombustionPolicy.scorchedState(Blocks.PODZOL.defaultBlockState()));
        assertFalse(FireFuelProfile.of(CombustionPolicy.scorchedState(
            Blocks.GRASS_BLOCK.defaultBlockState())).flammable(),
            "spent surface fuel must not restart a grass-fire loop");
        assertFalse(FireFuelProfile.fallbackForPath("coarse_dirt").flammable());
    }
}
