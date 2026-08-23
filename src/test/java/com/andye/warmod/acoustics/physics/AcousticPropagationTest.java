package com.andye.warmod.acoustics.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class AcousticPropagationTest {
	@Test
	void convertsPropagationDistanceToCeiledTicks() {
		assertEquals(20L, AcousticPropagation.delayTicks(343.0, 343.0));
		assertEquals(40L, AcousticPropagation.delayTicks(686.0, 343.0));
	}

	@Test
	void negativeElapsedTimeDoesNotProduceNegativeWaveRadius() {
		assertEquals(0.0, AcousticPropagation.waveRadiusBlocks(-1L, 343.0));
	}

	@Test
	void reflectionDelayUsesOnlyTheExtraTravelPath() {
		assertEquals(20L, AcousticPropagation.reflectedDelayTicks(343.0, 686.0, 343.0));
		assertEquals(0L, AcousticPropagation.reflectedDelayTicks(343.0, 343.0, 343.0));
	}
}
