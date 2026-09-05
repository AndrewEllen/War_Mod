package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WarheadPreparationTicketTypeTest {
    @Test
    void preparationTicketLoadsFullChunksWithoutSimulation() {
        assertTrue(WarheadPreparationTicketType.WARHEAD_PREPARATION.doesLoad());
        assertFalse(WarheadPreparationTicketType.WARHEAD_PREPARATION.doesSimulate());
        assertFalse(WarheadPreparationTicketType.WARHEAD_PREPARATION.persist());
    }
}
