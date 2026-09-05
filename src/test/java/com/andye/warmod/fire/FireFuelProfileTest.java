package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class FireFuelProfileTest {
    @Test
    void charredAftermathPlantsCannotReigniteAsFreshGrass() {
        assertEquals(FireFuelProfile.NONE, FireFuelProfile.fallbackForPath("charred_short_dry_grass"));
        assertEquals(FireFuelProfile.NONE, FireFuelProfile.fallbackForPath("charred_tall_dry_grass"));
    }

    @Test
    void regularDirtAndDirtPathsAreNotOrdinaryFireFuel() {
        assertEquals(FireFuelProfile.NONE, FireFuelProfile.fallbackForPath("dirt"));
        assertEquals(FireFuelProfile.NONE, FireFuelProfile.fallbackForPath("dirt_path"));
    }
    @Test
    void grassBlockIsShortLivedNonConsumableGroundFuel() {
        FireFuelProfile profile = FireFuelProfile.fallbackForPath("grass_block");
        assertEquals(FireFuelProfile.LOW, profile);
        assertFalse(profile.consumable());
        assertEquals(600, profile.burnTicks());
    }

    @Test
    void plantAndStructuralWoodRetainTheirExistingProfiles() {
        assertEquals(FireFuelProfile.HIGH,
            FireFuelProfile.fallbackForPath("short_grass"));
        assertEquals(FireFuelProfile.MEDIUM,
            FireFuelProfile.fallbackForPath("oak_log"));
    }
}
