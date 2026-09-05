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
        assertTrue(defence.isHostile((UUID)null, false));
    }

    @Test
    void claimedDefenceExcludesOwnerAndAlliesOnly() {
        DefenceOwnershipSnapshot defence = new DefenceOwnershipSnapshot(
            OWNER, "Owner", List.of(new DefenceAlly(ALLY, "Ally")));
        assertFalse(defence.isHostile(OWNER, false));
        assertFalse(defence.isHostile(ALLY, false));
        assertTrue(defence.isHostile(STRANGER, false));
        assertTrue(defence.isHostile((UUID)null, false));
        assertTrue(defence.isHostile(new UUID(0L, 0L), false));
    }

    @Test
    void fallbackMissileIsAlwaysHostileEvenWhenOwned() {
        DefenceOwnershipSnapshot defence = new DefenceOwnershipSnapshot(OWNER, "Owner", List.of());
        assertTrue(defence.isHostile(OWNER, true));
    }

    @Test
    void capturedSiloAffiliationIncludesOwnerAndEveryLaunchTimeAlly() {
        DefenceOwnershipSnapshot silo = new DefenceOwnershipSnapshot(
            OWNER, "Owner", List.of(new DefenceAlly(ALLY, "Ally")));
        MissileAffiliation missile = MissileAffiliation.ofDefence(silo);

        DefenceOwnershipSnapshot ownersDefence = new DefenceOwnershipSnapshot(
            OWNER, "Owner", List.of());
        DefenceOwnershipSnapshot alliesDefence = new DefenceOwnershipSnapshot(
            ALLY, "Ally", List.of());
        DefenceOwnershipSnapshot strangerDefence = new DefenceOwnershipSnapshot(
            STRANGER, "Stranger", List.of());
        DefenceOwnershipSnapshot ownersDefenceWithAlly = new DefenceOwnershipSnapshot(
            OWNER, "Owner", List.of(new DefenceAlly(ALLY, "Ally")));
        DefenceOwnershipSnapshot crossWhitelistedDefence = new DefenceOwnershipSnapshot(
            STRANGER, "Stranger", List.of(
                new DefenceAlly(OWNER, "Owner"),
                new DefenceAlly(ALLY, "Ally")));

        assertTrue(ownersDefence.isHostile(missile, false));
        assertTrue(alliesDefence.isHostile(missile, false));
        assertFalse(ownersDefenceWithAlly.isHostile(missile, false));
        assertFalse(crossWhitelistedDefence.isHostile(missile, false));
        assertTrue(strangerDefence.isHostile(missile, false));
    }

    @Test
    void unclaimedLaunchStaysHostileAndFallbackOverridesAffiliation() {
        DefenceOwnershipSnapshot defence = new DefenceOwnershipSnapshot(
            OWNER, "Owner", List.of(new DefenceAlly(ALLY, "Ally")));

        assertTrue(defence.isHostile(MissileAffiliation.unowned(), false));
        assertTrue(defence.isHostile(
            new MissileAffiliation(java.util.Set.of(OWNER, ALLY)), true));
    }
}
