package com.andye.warmod.acoustics.client;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** A loaded-terrain reflection point and its complete source-reflector-listener path. */
public record AcousticReflection(
	Vec3 position,
	double reflectedPathLength,
	double strength,
	double pathTransmission
) {
	public AcousticReflection {
		Objects.requireNonNull(position, "position");
		if (!position.isFinite()) throw new IllegalArgumentException("position must be finite");
		if (!Double.isFinite(reflectedPathLength) || reflectedPathLength < 0.0) {
			throw new IllegalArgumentException("reflectedPathLength must be finite and non-negative");
		}
		if (!Double.isFinite(strength) || strength < 0.0 || strength > 1.0) {
			throw new IllegalArgumentException("strength must be between zero and one");
		}
		if (!Double.isFinite(pathTransmission) || pathTransmission < 0.0 || pathTransmission > 1.0) {
			throw new IllegalArgumentException("pathTransmission must be between zero and one");
		}
	}
}
