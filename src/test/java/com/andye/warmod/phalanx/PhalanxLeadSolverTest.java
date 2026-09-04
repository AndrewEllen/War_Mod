package com.andye.warmod.phalanx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class PhalanxLeadSolverTest {
    @Test
    void stationaryTargetDirectlyOverheadHasVerticalSolution() {
        var solution = PhalanxLeadSolver.solve(Vec3.ZERO, new Vec3(0, 200, 0), Vec3.ZERO);
        assertTrue(solution.isPresent());
        assertEquals(90.0, solution.orElseThrow().elevationDegrees(), 1.0E-8);
        assertEquals(1.0, solution.orElseThrow().direction().y, 1.0E-8);
    }

    @Test
    void highIncomingTargetAboveFormerEightyDegreeLimitRemainsTrackable() {
        var solution = PhalanxLeadSolver.solve(Vec3.ZERO,
            new Vec3(10, 200, 0), new Vec3(0, -2, 0));
        assertTrue(solution.isPresent());
        assertTrue(solution.orElseThrow().elevationDegrees() > 80.0);
        assertTrue(solution.orElseThrow().direction().isFinite());
    }
}
