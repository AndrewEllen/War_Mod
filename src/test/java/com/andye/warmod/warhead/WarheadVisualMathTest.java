package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WarheadVisualMathTest {
	@Test
	void coneActivationIsZeroBelowThreshold() {
		assertEquals(0.0, WarheadVisualMath.coneActivation(0.20), 1.0E-9);
	}

	@Test
	void coneActivationRisesMonotonicallyThroughAttackRange() {
		double previous = 0.0;
		for (int index = 0; index <= 10; index++) {
			double current = WarheadVisualMath.coneActivation(0.32 + index * 0.023);
			assertTrue(current >= previous);
			previous = current;
		}
	}

	@Test
	void vaporBandPhaseRemainsBetweenZeroAndOne() {
		for (int tick = 0; tick < 100; tick++) {
			double phase = WarheadVisualMath.vaporBandPhase(tick + 0.25, 2, 4, 1234L);
			assertTrue(phase >= 0.0 && phase < 1.0);
		}
	}

	@Test
	void coneFadeReachesZeroAtImpact() {
		assertEquals(0.0, WarheadVisualMath.coneFade(0.0), 1.0E-9);
	}

	@Test
	void normalizedSpeedHandlesFiniteVelocity() {
		assertEquals(0.5, WarheadVisualMath.normalizedSpeed(new Vec3(3.0, 4.0, 0.0), 10.0), 1.0E-9);
	}
}
