package com.andye.warmod.acoustics.model;

import java.util.Objects;
import net.minecraft.resources.Identifier;

public record AcousticLayer(
	Identifier soundEventId,
	AcousticFrequencyBand band,
	float volumeMultiplier,
	float pitchMultiplier,
	double referenceDistanceBlocks,
	double attenuationExponent,
	double airAbsorptionPerBlock,
	double maximumDistanceBlocks,
	int additionalDelayTicks,
	boolean echoable
) {
	public AcousticLayer {
		Objects.requireNonNull(soundEventId, "soundEventId");
		Objects.requireNonNull(band, "band");
		if (!Float.isFinite(volumeMultiplier) || volumeMultiplier < 0.0F) {
			throw new IllegalArgumentException("volumeMultiplier must be finite and non-negative");
		}
		if (!Float.isFinite(pitchMultiplier) || pitchMultiplier <= 0.0F) {
			throw new IllegalArgumentException("pitchMultiplier must be finite and greater than zero");
		}
		if (!Double.isFinite(referenceDistanceBlocks) || referenceDistanceBlocks <= 0.0) {
			throw new IllegalArgumentException("referenceDistanceBlocks must be finite and greater than zero");
		}
		if (!Double.isFinite(attenuationExponent) || attenuationExponent < 0.0) {
			throw new IllegalArgumentException("attenuationExponent must be finite and non-negative");
		}
		if (!Double.isFinite(airAbsorptionPerBlock) || airAbsorptionPerBlock < 0.0) {
			throw new IllegalArgumentException("airAbsorptionPerBlock must be finite and non-negative");
		}
		if (!Double.isFinite(maximumDistanceBlocks) || maximumDistanceBlocks <= 0.0) {
			throw new IllegalArgumentException("maximumDistanceBlocks must be finite and greater than zero");
		}
		if (additionalDelayTicks < 0) {
			throw new IllegalArgumentException("additionalDelayTicks cannot be negative");
		}
	}
}
