package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WarheadImpactCenterTest {
    @Test
    void fractionalContactOnExpectedSupportReusesPreparedCentre() {
        Vec3 prepared = new Vec3(120.45, 71.0, -30.35);
        Vec3 collision = new Vec3(120.47, 71.0, -30.32);

        assertTrue(WarheadExplosionWorkManager.sameSupportBlock(prepared, collision));
    }

    @Test
    void earlierTerrainOrBuildingCollisionKeepsItsOwnCentre() {
        Vec3 prepared = new Vec3(120.45, 71.0, -30.35);

        assertFalse(WarheadExplosionWorkManager.sameSupportBlock(
            prepared, new Vec3(119.99, 71.0, -30.35)));
        assertFalse(WarheadExplosionWorkManager.sameSupportBlock(
            prepared, new Vec3(120.45, 83.0, -30.35)));
    }
}
