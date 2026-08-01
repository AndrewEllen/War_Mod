package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WarheadEffectTimingTest {
	@Test
	void pressureRingStartsNearZero() {
		assertEquals(0.0, WarheadEffectMath.pressureRingRadius(2.0, 1.0), 1.0E-9);
	}

	@Test
	void pressureRingIncreasesMonotonically() {
		double previous = 0.0;
		for (int age = 2; age <= 32; age++) {
			double current = WarheadEffectMath.pressureRingRadius(age, 1.0);
			assertTrue(current >= previous);
			previous = current;
		}
	}

	@Test
	void pressureRingReachesConfiguredMaximum() {
		assertEquals(WarheadConstants.PRESSURE_RING_MAX_RADIUS, WarheadEffectMath.pressureRingRadius(32.0, 1.0), 1.0E-9);
	}

	@Test
	void impactExpiresAfterConfiguredLifetime() {
		assertFalse(WarheadEffectMath.impactExpired(WarheadConstants.IMPACT_VISUAL_LIFETIME_TICKS - 0.01));
		assertTrue(WarheadEffectMath.impactExpired(WarheadConstants.IMPACT_VISUAL_LIFETIME_TICKS));
	}
}