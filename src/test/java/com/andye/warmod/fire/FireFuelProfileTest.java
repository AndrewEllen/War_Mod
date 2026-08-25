package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class FireFuelProfileTest {
    @Test
    void grassBlockIsShortLivedNonConsumableGroundFuel() {
        FireFuelProfile profile = FireFuelProfile.fallbackForPath("grass_block");
        assertEquals(FireFuelProfile.LOW, profile);
        assertFalse(profile.consumable());
        assertEquals(260, profile.burnTicks());
    }

    @Test
    void plantAndStructuralWoodRetainTheirExistingProfiles() {
        assertEquals(FireFuelProfile.HIGH,
            FireFuelProfile.fallbackForPath("short_grass"));
        assertEquals(FireFuelProfile.MEDIUM,
            FireFuelProfile.fallbackForPath("oak_log"));
    }
}
