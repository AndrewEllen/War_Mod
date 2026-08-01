package com.andye.warmod.acoustics.model;

import java.util.Objects;
import net.minecraft.resources.Identifier;

public record AcousticDistanceSound(
	AcousticDistanceProfile profile,
	Identifier soundEventId,
	double minimumListenerDistance,
	double maximumListenerDistance,
	float volumeMultiplier,
	float pitchMultiplier
) {
	public AcousticDistanceSound {
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(soundEventId, "soundEventId");
		if (!Double.isFinite(minimumListenerDistance) || minimumListenerDistance < 0.0) {
			throw new IllegalArgumentException("minimumListenerDistance must be finite and non-negative");
		}
		if (!Double.isFinite(maximumListenerDistance) || maximumListenerDistance <= minimumListenerDistance) {
			throw new IllegalArgumentException("maximumListenerDistance must be finite and exceed the minimum");
		}
		if (!Float.isFinite(volumeMultiplier) || volumeMultiplier < 0.0F) {
			throw new IllegalArgumentException("volumeMultiplier must be finite and non-negative");
		}
		if (!Float.isFinite(pitchMultiplier) || pitchMultiplier <= 0.0F) {
			throw new IllegalArgumentException("pitchMultiplier must be finite and greater than zero");
		}
	}

	public boolean contains(final double listenerDistance) {
		return listenerDistance >= minimumListenerDistance
			&& (listenerDistance < maximumListenerDistance
				|| profile == AcousticDistanceProfile.EXTREME && listenerDistance <= maximumListenerDistance);
	}
}