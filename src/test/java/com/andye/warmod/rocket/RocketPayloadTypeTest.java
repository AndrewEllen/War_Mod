package com.andye.warmod.rocket;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketPayloadTypeTest {
    @Test
    void motorBuildsSpeedInsteadOfStartingAtCruiseVelocity() {
        for (RocketPayloadType payload : RocketPayloadType.values()) {
            assertTrue(payload.launchSpeed() > 0.0);
            assertTrue(payload.launchSpeed() < payload.speed());
        }
    }
}
