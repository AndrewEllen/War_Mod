package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class ConventionalDebrisBallisticsTest {
    @Test
    void alreadyFastHighExplosiveVelocityIsPreserved() {
        Vec3 original = new Vec3(1.2, 0.8, 0.15);
        Vec3 result = ConventionalDebrisBallistics.ensureClearance(original,
            3.0, 0.0, WarheadYield.HIGH_EXPLOSIVE.visualScale());

        assertEquals(original, result);
    }

    @Test
    void everyConventionalYieldClearsItsOpaqueCoreEnvelope() {
        for (WarheadYield yield : WarheadYield.values()) {
            if (yield.nuclear()) continue;
            double core = ConventionalDebrisBallistics.opaqueCoreRadius(yield.visualScale());
            for (double start : new double[] {0.0, core * 0.25, core * 0.72, core + 2.0}) {
                Vec3 velocity = ConventionalDebrisBallistics.ensureClearance(
                    new Vec3(0.02, 0.8, 0.0), start, 0.0, yield.visualScale());
                double finalRadius = start + ConventionalDebrisBallistics.outwardDisplacement(
                    velocity.x, ConventionalDebrisBallistics.OPAQUE_CORE_END_TICK);
                assertTrue(finalRadius + 1.0E-6 >= core + 1.0,
                    () -> yield + " debris remained inside core: " + finalRadius
                        + " < " + (core + 1.0));
            }
        }
    }

    @Test
    void clearanceAddsOnlyMissingRadialMomentum() {
        Vec3 input = new Vec3(0.10, 1.1, 0.42);
        Vec3 result = ConventionalDebrisBallistics.ensureClearance(input,
            4.0, 0.0, WarheadYield.HEAVY_CONVENTIONAL.visualScale());

        assertEquals(input.y, result.y, 1.0E-12);
        assertEquals(input.z, result.z, 1.0E-12);
        assertTrue(result.x >= input.x);
    }
}
