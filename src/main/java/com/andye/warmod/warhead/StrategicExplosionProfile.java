package com.andye.warmod.warhead;

/**
 * Parameters for the Stage 4 column/SDF crater engine.
 *
 * <p>Terrain destruction is represented as a noisy asymmetric ellipsoid made
 * from vertical column spans. This removes the visible spokes produced by
 * sparse explosion rays while keeping the amount of work predictable.</p>
 */
public record StrategicExplosionProfile(
	WarheadYield yield,
	double horizontalRadius,
	double upwardRadius,
	double downwardRadius,
	double guaranteedVoidScale,
	double boundaryRoughness,
	float maximumDestroyResistance,
	float edgeResistanceScale,
	float entityBlastRadius,
	double aftermathRadiusScale,
	double aftermathDensity
) {
	public StrategicExplosionProfile {
		if (yield == null
			|| !Double.isFinite(horizontalRadius) || horizontalRadius <= 1.0
			|| !Double.isFinite(upwardRadius) || upwardRadius <= 1.0
			|| !Double.isFinite(downwardRadius) || downwardRadius <= 1.0
			|| !Double.isFinite(guaranteedVoidScale) || guaranteedVoidScale <= 0.05 || guaranteedVoidScale > 1.0
			|| !Double.isFinite(boundaryRoughness) || boundaryRoughness < 0.0 || boundaryRoughness > 0.35
			|| !Float.isFinite(maximumDestroyResistance) || maximumDestroyResistance <= 0.0F
			|| !Float.isFinite(edgeResistanceScale) || edgeResistanceScale < 0.0F
			|| !Float.isFinite(entityBlastRadius) || entityBlastRadius <= 0.0F
			|| !Double.isFinite(aftermathRadiusScale) || aftermathRadiusScale < 1.0
			|| !Double.isFinite(aftermathDensity) || aftermathDensity < 0.0 || aftermathDensity > 1.0) {
			throw new IllegalArgumentException("Invalid strategic explosion profile");
		}
	}

	public WarheadPayloadType payloadType() {
		return yield.payloadType();
	}

	public double maximumRadius() {
		return Math.max(horizontalRadius, Math.max(upwardRadius, downwardRadius));
	}

	public double guaranteedHorizontalRadius() {
		return Math.max(2.0, horizontalRadius * guaranteedVoidScale);
	}

	public double guaranteedUpwardRadius() {
		return Math.max(1.5, upwardRadius * guaranteedVoidScale);
	}

	public double guaranteedDownwardRadius() {
		return Math.max(2.0, downwardRadius * guaranteedVoidScale);
	}
}
