package com.andye.warmod.acoustics.client;

import com.andye.warmod.acoustics.model.AcousticResponseProfile;
import java.util.List;
import java.util.Objects;

public record AcousticEnvironment(
	double enclosure,
	double averageReflectionDistance,
	boolean openSky,
	double obstruction,
	double terrainRelief,
	double foliageAbsorption,
	List<AcousticReflection> terrainReflections
) {
	public AcousticEnvironment {
		Objects.requireNonNull(terrainReflections, "terrainReflections");
		terrainReflections = List.copyOf(terrainReflections);
		if (terrainReflections.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("terrainReflections cannot contain null");
		}
		if (!Double.isFinite(enclosure) || enclosure < 0.0 || enclosure > 1.0) {
			throw new IllegalArgumentException("enclosure must be between zero and one");
		}
		if (!Double.isFinite(averageReflectionDistance) || averageReflectionDistance < 0.0) {
			throw new IllegalArgumentException("averageReflectionDistance must be finite and non-negative");
		}
		if (!unit(obstruction) || !unit(terrainRelief) || !unit(foliageAbsorption)) {
			throw new IllegalArgumentException("environment factors must be between zero and one");
		}
	}

	public double transmissionGain(final AcousticResponseProfile response) {
		Objects.requireNonNull(response, "response");
		return Math.max(response.minimumTransmissionGain(),
			(1.0 - obstruction * response.obstructionAbsorption())
				* (1.0 - foliageAbsorption * response.foliageAbsorption()));
	}

	public float transmissionPitch(final AcousticResponseProfile response) {
		Objects.requireNonNull(response, "response");
		return (float) Math.max(0.92, 1.0
			- obstruction * response.obstructionPitchDamping()
			- foliageAbsorption * response.foliagePitchDamping());
	}

	/**
	 * Coarse high-frequency loss used to select the next, naturally low-passed
	 * distance recording when a genuinely solid path blocks most of the ray fan.
	 * Foliage contributes much less: a forest canopy is not an acoustic wall.
	 */
	public double highFrequencyLoss() {
		return Math.min(1.0, obstruction * 0.86 + foliageAbsorption * 0.28);
	}

	public double reflectionStrength(final AcousticResponseProfile response) {
		Objects.requireNonNull(response, "response");
		double indoor = enclosure * 0.78;
		double outdoorTerrain = openSky ? terrainRelief * 0.75 : terrainRelief * 0.34;
		return Math.max(0.0, Math.min(1.0,
			Math.max(indoor, outdoorTerrain)
				* (1.0 - foliageAbsorption * response.foliageAbsorption() * 0.35)));
	}

	public double effectiveReflectionDistance() {
		if (enclosure >= 0.30 && averageReflectionDistance > 0.0)
			return averageReflectionDistance;
		return 18.0 + terrainRelief * 76.0;
	}

	private static boolean unit(final double value) {
		return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
	}
}
