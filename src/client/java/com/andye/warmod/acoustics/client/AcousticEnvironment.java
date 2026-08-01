package com.andye.warmod.acoustics.client;

public record AcousticEnvironment(
	double enclosure,
	double averageReflectionDistance,
	boolean openSky
) {
	public AcousticEnvironment {
		if (!Double.isFinite(enclosure) || enclosure < 0.0 || enclosure > 1.0) {
			throw new IllegalArgumentException("enclosure must be between zero and one");
		}
		if (!Double.isFinite(averageReflectionDistance) || averageReflectionDistance < 0.0) {
			throw new IllegalArgumentException("averageReflectionDistance must be finite and non-negative");
		}
	}
}
