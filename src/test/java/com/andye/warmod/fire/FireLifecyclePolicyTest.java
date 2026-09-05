package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FireLifecyclePolicyTest {
    @Test
    void descendantsAndNaturalMergesNeverRenewTheRootDeadline() {
        long root = FireLifecyclePolicy.newRootExpiry(1_000L);
        assertEquals(15_400L, root);
        assertEquals(root, FireLifecyclePolicy.inherit(Long.MAX_VALUE, root));
        assertEquals(root, FireLifecyclePolicy.inherit(root, root + 4_000L));
        assertEquals(root - 200L, FireLifecyclePolicy.inherit(root, root - 200L));
    }

    @Test
    void finalNinetySecondsTaperAndThenExpire() {
        long expiry = 20_000L;
        assertEquals(1.0F, FireLifecyclePolicy.strength(expiry,
            expiry - FireLifecyclePolicy.TAPER_TICKS));
        float halfway = FireLifecyclePolicy.strength(expiry,
            expiry - FireLifecyclePolicy.TAPER_TICKS / 2L);
        assertTrue(halfway > 0.0F && halfway < 1.0F);
        assertFalse(FireLifecyclePolicy.expired(expiry, expiry - 1L));
        assertTrue(FireLifecyclePolicy.expired(expiry, expiry));
        assertEquals(0.0F, FireLifecyclePolicy.strength(expiry, expiry));
    }

    @Test
    void legacyPatchesUseTheirOriginalIgnitionTime() {
        assertEquals(15_400L, FireLifecyclePolicy.restoredRootExpiry(
            Long.MAX_VALUE, 1_000L, 8_000L));
        assertEquals(9_500L, FireLifecyclePolicy.restoredRootExpiry(
            9_500L, 1_000L, 8_000L));
        assertTrue(FireLifecyclePolicy.expired(
            FireLifecyclePolicy.restoredRootExpiry(
                Long.MAX_VALUE, 1_000L, 20_000L), 20_000L));
    }
}
