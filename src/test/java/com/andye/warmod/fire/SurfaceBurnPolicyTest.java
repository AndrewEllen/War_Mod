package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SurfaceBurnPolicyTest {
    @Test
    void grassSurfaceHasARealDeadlineEvenUnderRepeatedExternalHeat() {
        assertFalse(FireFuelProfile.LOW.consumable());
        int lifetime = SurfaceBurnPolicy.initialLifetimeTicks(
            FireFuelProfile.LOW.burnTicks());
        long originalDeadline = 1_000L + lifetime;
        float originalBudget = SurfaceBurnPolicy.initialExternalHeatBudget(lifetime);
        SurfaceBurnPolicy.Extension state = new SurfaceBurnPolicy.Extension(
            originalDeadline, originalBudget, Long.MIN_VALUE);

        for (int index = 0; index < 40; index++) {
            long now = 1_000L + index * SurfaceBurnPolicy.EXTERNAL_HEAT_INTERVAL_TICKS;
            state = SurfaceBurnPolicy.extend(state.hardBurnEndTick(),
                state.remainingBudgetTicks(), state.lastExternalHeatTick(), 4.0F, now);
        }

        assertTrue(state.hardBurnEndTick()
            <= originalDeadline + (long) Math.floor(originalBudget));
        assertTrue(state.remainingBudgetTicks() >= 0.0F);
        assertEquals(state.hardBurnEndTick(), SurfaceBurnPolicy.extend(
            state.hardBurnEndTick(), state.remainingBudgetTicks(),
            state.lastExternalHeatTick(), 4.0F, state.hardBurnEndTick())
            .hardBurnEndTick());
    }

    @Test
    void woodRetainsItsFuelDrivenClassification() {
        assertTrue(FireFuelProfile.MEDIUM.flammable());
        assertTrue(FireFuelProfile.MEDIUM.consumable());
        assertTrue(FireFuelProfile.MEDIUM.burnTicks()
            > FireFuelProfile.LOW.burnTicks());
    }

    @Test
    void stoneSurfaceFlameIsBriefAndFollowedByCooldown() {
        assertFalse(FireFuelProfile.NONE.flammable());
        assertFalse(FireFuelProfile.NONE.consumable());
        int lifetime = SurfaceBurnPolicy.initialLifetimeTicks(250);
        long deadline = 5_000L + lifetime;

        assertTrue(SurfaceBurnPolicy.tailExpired(deadline,
            deadline + SurfaceBurnPolicy.SMOULDER_TAIL_TICKS));
        assertTrue(SurfaceBurnPolicy.cooldownExpiry(deadline)
            > deadline + SurfaceBurnPolicy.SMOULDER_TAIL_TICKS);
    }

    @Test
    void nuclearStyleNonFuelIgnitionCannotCreateAnUnlimitedLifetime() {
        int generatedBurnTicks = 120 + Math.round(0.90F * 260.0F);
        int lifetime = SurfaceBurnPolicy.initialLifetimeTicks(generatedBurnTicks);

        assertTrue(lifetime <= 520);
        assertEquals(lifetime * 0.25F,
            SurfaceBurnPolicy.initialExternalHeatBudget(lifetime), 0.001F);
    }
}
