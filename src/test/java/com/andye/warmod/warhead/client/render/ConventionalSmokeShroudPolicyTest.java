package com.andye.warmod.warhead.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ConventionalSmokeShroudPolicyTest {
    @Test
    void shroudBuildsEarlyHoldsAndFadesLate() {
        assertFalse(ConventionalSmokeShroudPolicy.active(3.0));
        assertTrue(ConventionalSmokeShroudPolicy.active(4.0));
        assertEquals(1.0F, ConventionalSmokeShroudPolicy.coverage(60.0), 0.000_1F);
        assertEquals(1.0F, ConventionalSmokeShroudPolicy.systemFade(160.0), 0.000_1F);
        assertEquals(0.5F, ConventionalSmokeShroudPolicy.systemFade(500.0), 0.000_1F);
        assertFalse(ConventionalSmokeShroudPolicy.active(700.0));
    }

    @Test
    void individualLifeAndSettlingStayWithinContract() {
        assertEquals(240.0F, ConventionalSmokeShroudPolicy.individualLifetime(0.0F));
        assertEquals(520.0F, ConventionalSmokeShroudPolicy.individualLifetime(1.0F));
        assertEquals(180.0F, ConventionalSmokeShroudPolicy.settlingStart(0.0F));
        assertEquals(240.0F, ConventionalSmokeShroudPolicy.settlingStart(1.0F));
    }
}
