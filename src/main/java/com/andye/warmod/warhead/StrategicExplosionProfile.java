package com.andye.warmod.warhead;

/**
 * Geometry and resistance model for War Mod's strategic crater engine.
 *
 * <p>The radii describe an asymmetric ellipsoid around the impact point. The
 * calculation is independent from Minecraft's vanilla explosion-strength
 * value, allowing nuclear craters to grow without running the vanilla
 * 16x16x16 boundary-ray algorithm at extreme strengths.</p>
 */
public record StrategicExplosionProfile(
	WarheadPayloadType payloadType,
	double horizontalRadius,
	double upwardRadius,
	double downwardRadius,
	int rayCount,
	double rayStep,
	float initialEnergy,
	float airEnergyLossPerBlock,
	float resistanceScale,
	float maximumResistanceCost,
	double guaranteedCoreScale,
	float entityBlastRadius
) {
	public StrategicExplosionProfile {
		if (payloadType == null
			|| !Double.isFinite(horizontalRadius) || horizontalRadius <= 0.0
			|| !Double.isFinite(upwardRadius) || upwardRadius <= 0.0
			|| !Double.isFinite(downwardRadius) || downwardRadius <= 0.0
			|| rayCount < 128
			|| !Double.isFinite(rayStep) || rayStep <= 0.05
			|| !Float.isFinite(initialEnergy) || initialEnergy <= 0.0F
			|| !Float.isFinite(airEnergyLossPerBlock) || airEnergyLossPerBlock <= 0.0F
			|| !Float.isFinite(resistanceScale) || resistanceScale < 0.0F
			|| !Float.isFinite(maximumResistanceCost) || maximumResistanceCost <= 0.0F
			|| !Double.isFinite(guaranteedCoreScale) || guaranteedCoreScale <= 0.0 || guaranteedCoreScale > 1.0
			|| !Float.isFinite(entityBlastRadius) || entityBlastRadius <= 0.0F) {
			throw new IllegalArgumentException("Invalid strategic explosion profile");
		}
	}

	public double maximumRadius() {
		return Math.max(horizontalRadius, Math.max(upwardRadius, downwardRadius));
	}

	public double coreHorizontalRadius() {
		return Math.max(2.0, horizontalRadius * guaranteedCoreScale);
	}

	public double coreUpwardRadius() {
		return Math.max(1.5, upwardRadius * guaranteedCoreScale);
	}

	public double coreDownwardRadius() {
		return Math.max(2.0, downwardRadius * guaranteedCoreScale);
	}
}
