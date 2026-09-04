package com.andye.warmod.defence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DefenceOwnershipSnapshotTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ALLY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID STRANGER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void unclaimedDefenceTreatsEveryMissileAsHostile() {
        DefenceOwnershipSnapshot defence = DefenceOwnershipSnapshot.unclaimed();
        assertTrue(defence.isHostile(OWNER, false));
        assertTrue(defence.isHostile(null, false));
    }

    @Test
    void claimedDefenceExcludesOwnerAndAlliesOnly() {
        DefenceOwnershipSnapshot defence = new DefenceOwnershipSnapshot(
            OWNER, "Owner", List.of(new DefenceAlly(ALLY, "Ally")));
        assertFalse(defence.isHostile(OWNER, false));
        assertFalse(defence.isHostile(ALLY, false));
        assertTrue(defence.isHostile(STRANGER, false));
        assertTrue(defence.isHostile(null, false));
        assertTrue(defence.isHostile(new UUID(0L, 0L), false));
    }

    @Test
    void fallbackMissileIsAlwaysHostileEvenWhenOwned() {
        DefenceOwnershipSnapshot defence = new DefenceOwnershipSnapshot(OWNER, "Owner", List.of());
        assertTrue(defence.isHostile(OWNER, true));
    }
}
