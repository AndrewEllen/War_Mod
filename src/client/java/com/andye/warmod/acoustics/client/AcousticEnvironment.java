package com.andye.warmod.acoustics.client;

public record AcousticEnvironment(
	double enclosure,
	double averageReflectionDistance,
	boolean openSky,
	double obstruction,
	double terrainRelief,
	double foliageAbsorption
) {
	public AcousticEnvironment {
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

	public double transmissionGain() {
		return Math.max(0.12, (1.0 - obstruction * 0.72)
			* (1.0 - foliageAbsorption * 0.38));
	}

	public float transmissionPitch() {
		return (float) Math.max(0.82, 1.0 - obstruction * 0.075
			- foliageAbsorption * 0.035);
	}

	public double reflectionStrength() {
		double indoor = enclosure * 0.78;
		double outdoorTerrain = openSky ? terrainRelief * 0.62 : terrainRelief * 0.30;
		return Math.max(0.0, Math.min(1.0,
			Math.max(indoor, outdoorTerrain) * (1.0 - foliageAbsorption * 0.62)));
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
