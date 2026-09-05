package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FireReplicationLifecycleTest {
    @Test
    void clearingCombustionDoesNotRewindTheConnectedClientsSequence() {
        long lastVisibleFire = FireSimulationManager.nextReplicationGeneration();
        FireSimulationManager.clearAll();
        long newIgnition = FireSimulationManager.nextReplicationGeneration();
        assertTrue(newIgnition > lastVisibleFire,
            "A newly ignited field must not be rejected as older than the extinct field");
    }
}
