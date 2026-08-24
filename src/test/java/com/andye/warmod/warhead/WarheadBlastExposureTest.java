package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class WarheadBlastExposureTest {
	@Test
	void openAirTransmitsTheFullPressureWave() {
		assertEquals(1.0F, WarheadBlastExposure.transmission(7, 7), 1.0E-6F);
	}

	@Test
	void solidCoverLeavesOnlyResidualPressure() {
		assertEquals(0.06F, WarheadBlastExposure.transmission(0, 7), 1.0E-6F);
	}

	@Test
	void partialCoverIsNonLinear() {
		assertEquals(0.367F, WarheadBlastExposure.transmission(4, 7), 0.001F);
	}

	@Test
	void rejectsImpossibleRayCounts() {
		assertThrows(IllegalArgumentException.class,
			() -> WarheadBlastExposure.transmission(8, 7));
	}
}
