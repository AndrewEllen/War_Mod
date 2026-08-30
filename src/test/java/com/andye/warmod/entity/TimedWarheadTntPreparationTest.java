package com.andye.warmod.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class TimedWarheadTntPreparationTest {
    static { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void verticalFallRetargetsThePreparedImpact() {
        Vec3 armed = new Vec3(12.0, 96.0, -4.0);
        assertFalse(TimedWarheadTntEntity.preparationTargetMoved(
            armed, armed.add(0.0, -2.0, 0.0)));
        assertTrue(TimedWarheadTntEntity.preparationTargetMoved(
            armed, armed.add(0.0, -2.01, 0.0)));
    }

    @Test
    void horizontalDriftUsesTheSameTwoBlockThreshold() {
        Vec3 armed = new Vec3(12.0, 64.0, -4.0);
        assertTrue(TimedWarheadTntEntity.preparationTargetMoved(
            armed, armed.add(2.01, 0.0, 0.0)));
    }

    @Test
    void nuclearFuseCannotPauseAtOneForPreparation() {
        assertTrue(TimedWarheadTntEntity.nextFuse(1) == 0);
        assertTrue(TimedWarheadTntEntity.nextFuse(0) == 0);
    }
}
