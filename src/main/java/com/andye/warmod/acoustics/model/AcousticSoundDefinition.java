package com.andye.warmod.acoustics.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

public record AcousticSoundDefinition(
	Identifier id,
	List<AcousticDistanceSound> distanceSounds,
	double propagationSpeedBlocksPerSecond,
	double maximumDistanceBlocks,
	double minimumAudibleGain,
	boolean environmentEchoesEnabled
) {
	public AcousticSoundDefinition {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(distanceSounds, "distanceSounds");
		if (distanceSounds.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("Acoustic distance sounds cannot be null");
		}
		distanceSounds = List.copyOf(distanceSounds);
		if (!Double.isFinite(propagationSpeedBlocksPerSecond) || propagationSpeedBlocksPerSecond <= 0.0) {
			throw new IllegalArgumentException("propagationSpeedBlocksPerSecond must be finite and greater than zero");
		}
		if (!Double.isFinite(maximumDistanceBlocks) || maximumDistanceBlocks <= 0.0) {
			throw new IllegalArgumentException("maximumDistanceBlocks must be finite and greater than zero");
		}
		if (!Double.isFinite(minimumAudibleGain) || minimumAudibleGain < 0.0 || minimumAudibleGain > 1.0) {
			throw new IllegalArgumentException("minimumAudibleGain must be finite and between zero and one");
		}

		AcousticDistanceProfile[] profiles = AcousticDistanceProfile.values();
		if (distanceSounds.size() != profiles.length) {
			throw new IllegalArgumentException("An acoustic definition needs exactly one sound for every distance profile");
		}
		double expectedMinimum = 0.0;
		for (int index = 0; index < profiles.length; index++) {
			AcousticDistanceSound sound = distanceSounds.get(index);
			if (sound.profile() != profiles[index]) {
				throw new IllegalArgumentException("Distance sounds must be ordered by acoustic distance profile");
			}
			if (Double.compare(sound.minimumListenerDistance(), expectedMinimum) != 0) {
				throw new IllegalArgumentException("Distance profiles must be contiguous and non-overlapping");
			}
			expectedMinimum = sound.maximumListenerDistance();
		}
		if (Double.compare(expectedMinimum, maximumDistanceBlocks) != 0) {
			throw new IllegalArgumentException("Distance profiles must cover the definition maximum distance exactly");
		}
	}

	public Optional<AcousticDistanceSound> soundForDistance(final double listenerDistance) {
		if (!Double.isFinite(listenerDistance) || listenerDistance < 0.0 || listenerDistance > maximumDistanceBlocks) {
			return Optional.empty();
		}
		return distanceSounds.stream().filter(sound -> sound.contains(listenerDistance)).findFirst();
	}
}