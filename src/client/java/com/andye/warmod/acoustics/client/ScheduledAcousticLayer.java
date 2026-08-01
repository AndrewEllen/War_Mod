package com.andye.warmod.acoustics.client;

import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public record ScheduledAcousticLayer(
	long playbackClientTick,
	Vec3 sourcePosition,
	Identifier soundEventId,
	SoundSource soundSource,
	float volume,
	float pitch,
	long seed,
	boolean echo
) {
	public ScheduledAcousticLayer {
		Objects.requireNonNull(sourcePosition, "sourcePosition");
		Objects.requireNonNull(soundEventId, "soundEventId");
		Objects.requireNonNull(soundSource, "soundSource");
		if (!sourcePosition.isFinite()) {
			throw new IllegalArgumentException("sourcePosition must be finite");
		}
		if (!Float.isFinite(volume) || volume < 0.0F) {
			throw new IllegalArgumentException("volume must be finite and non-negative");
		}
		if (!Float.isFinite(pitch) || pitch <= 0.0F) {
			throw new IllegalArgumentException("pitch must be finite and greater than zero");
		}
	}
}
