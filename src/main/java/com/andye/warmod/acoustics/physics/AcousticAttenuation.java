package com.andye.warmod.acoustics.physics;

import com.andye.warmod.acoustics.model.AcousticDistanceSound;

public final class AcousticAttenuation {
	private static final double REFERENCE_DISTANCE_BLOCKS = 32.0;
	private static final double ATTENUATION_EXPONENT = 0.55;
	private static final double AIR_ABSORPTION_PER_BLOCK = 0.00008;

	private AcousticAttenuation() {
	}

	public static double gain(final double distanceBlocks, final AcousticDistanceSound sound, final float eventVolume) {
		if (!Double.isFinite(distanceBlocks) || distanceBlocks < 0.0) {
			throw new IllegalArgumentException("distanceBlocks must be finite and non-negative");
		}
		if (!Float.isFinite(eventVolume) || eventVolume < 0.0F) {
			throw new IllegalArgumentException("eventVolume must be finite and non-negative");
		}
		if (!sound.contains(distanceBlocks)) {
			return 0.0;
		}

		double effectiveDistance = Math.max(distanceBlocks, REFERENCE_DISTANCE_BLOCKS);
		double geometricGain = Math.pow(REFERENCE_DISTANCE_BLOCKS / effectiveDistance, ATTENUATION_EXPONENT);
		double airDistance = Math.max(0.0, distanceBlocks - REFERENCE_DISTANCE_BLOCKS);
		double airGain = Math.exp(-AIR_ABSORPTION_PER_BLOCK * airDistance);
		double result = eventVolume * sound.volumeMultiplier() * geometricGain * airGain;
		return Double.isFinite(result) && result >= 0.0 ? result : 0.0;
	}
}