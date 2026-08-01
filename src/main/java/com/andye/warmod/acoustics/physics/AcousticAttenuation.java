package com.andye.warmod.acoustics.physics;

import com.andye.warmod.acoustics.model.AcousticLayer;

public final class AcousticAttenuation {
	private AcousticAttenuation() {
	}

	public static double gain(final double distanceBlocks, final AcousticLayer layer, final float eventVolume) {
		if (!Double.isFinite(distanceBlocks) || distanceBlocks < 0.0) {
			throw new IllegalArgumentException("distanceBlocks must be finite and non-negative");
		}
		if (!Float.isFinite(eventVolume) || eventVolume < 0.0F) {
			throw new IllegalArgumentException("eventVolume must be finite and non-negative");
		}
		if (distanceBlocks > layer.maximumDistanceBlocks()) {
			return 0.0;
		}

		double effectiveDistance = Math.max(distanceBlocks, layer.referenceDistanceBlocks());
		double geometricGain = Math.pow(
			layer.referenceDistanceBlocks() / effectiveDistance,
			layer.attenuationExponent()
		);
		double airDistance = Math.max(0.0, distanceBlocks - layer.referenceDistanceBlocks());
		double airGain = Math.exp(-layer.airAbsorptionPerBlock() * airDistance);
		double result = eventVolume * layer.volumeMultiplier() * geometricGain * airGain;
		return Double.isFinite(result) && result >= 0.0 ? result : 0.0;
	}
}
