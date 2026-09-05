package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class WarheadPreparationSchedulingPolicyTest {
    @Test
    void sealedImpactRunsBeforeAnOlderLaunchTimePreparation() {
        int launchTime = WarheadPreparationSchedulingPolicy.priority(
            PreparationState.SNAPSHOTTING, 0);
        int sealed = WarheadPreparationSchedulingPolicy.priority(
            PreparationState.IMPACT_SEALED, 1);

        assertTrue(sealed < launchTime);
    }

    @Test
    void activeCommitRemainsUrgentAcrossIntermediateStateChanges() {
        int active = WarheadPreparationSchedulingPolicy.priority(
            PreparationState.SNAPSHOTTING, 1);
        int preImpact = WarheadPreparationSchedulingPolicy.priority(
            PreparationState.COMPILING, 0);

        assertTrue(active < preImpact);
    }

    @Test
    void equalPriorityImpactsRotateTheirFirstBudgetOwner() {
        assertEquals(0, WarheadPreparationSchedulingPolicy.startIndex(2, 0));
        assertEquals(1, WarheadPreparationSchedulingPolicy.startIndex(2, 1));
        assertEquals(0, WarheadPreparationSchedulingPolicy.startIndex(2, 2));
    }
}
