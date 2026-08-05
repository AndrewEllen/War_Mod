package com.andye.warmod.warhead;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Tuned yield profiles for the custom strategic crater engine. */
public final class StrategicExplosionProfiles {
	private static final Map<WarheadYield, StrategicExplosionProfile> PROFILES = new EnumMap<>(WarheadYield.class);

	static {
		put(WarheadYield.HIGH_EXPLOSIVE,
			8.0, 5.5, 5.5, 0.70, 0.10, 40.0F, 0.55F, 12.0F, 1.35, 0.05);
		put(WarheadYield.CONVENTIONAL,
			24.0, 16.0, 18.0, 0.72, 0.095, 145.0F, 0.50F, 28.0F, 1.42, 0.08);
		put(WarheadYield.HEAVY_CONVENTIONAL,
			36.0, 24.0, 28.0, 0.73, 0.09, 230.0F, 0.46F, 44.0F, 1.46, 0.10);
		put(WarheadYield.TACTICAL_NUCLEAR,
			48.0, 34.0, 36.0, 0.76, 0.085, 420.0F, 0.42F, 72.0F, 1.52, 0.12);
		put(WarheadYield.STRATEGIC_NUCLEAR,
			72.0, 54.0, 52.0, 0.78, 0.078, 900.0F, 0.36F, 112.0F, 1.58, 0.15);
		put(WarheadYield.HEAVY_NUCLEAR,
			104.0, 76.0, 74.0, 0.80, 0.070, 1_600.0F, 0.32F, 164.0F, 1.62, 0.18);
	}

	private StrategicExplosionProfiles() {
	}

	private static void put(
		final WarheadYield yield,
		final double horizontalRadius,
		final double upwardRadius,
		final double downwardRadius,
		final double guaranteedVoidScale,
		final double boundaryRoughness,
		final float maximumDestroyResistance,
		final float edgeResistanceScale,
		final float entityBlastRadius,
		final double aftermathRadiusScale,
		final double aftermathDensity
	) {
		PROFILES.put(yield, new StrategicExplosionProfile(
			yield,
			horizontalRadius,
			upwardRadius,
			downwardRadius,
			guaranteedVoidScale,
			boundaryRoughness,
			maximumDestroyResistance,
			edgeResistanceScale,
			entityBlastRadius,
			aftermathRadiusScale,
			aftermathDensity
		));
	}

	public static StrategicExplosionProfile get(final WarheadYield yield) {
		return Objects.requireNonNull(PROFILES.get(Objects.requireNonNull(yield, "yield")), "profile");
	}

	public static StrategicExplosionProfile get(final WarheadPayloadType payloadType) {
		return get(WarheadYield.defaultFor(payloadType));
	}

	public static StrategicExplosionProfile fromLegacyStrength(final float strength) {
		if (strength >= 220.0F) return get(WarheadYield.HEAVY_NUCLEAR);
		if (strength >= 100.0F) return get(WarheadYield.STRATEGIC_NUCLEAR);
		if (strength >= 28.0F) return get(WarheadYield.HEAVY_CONVENTIONAL);
		if (strength <= 7.0F) return get(WarheadYield.HIGH_EXPLOSIVE);
		return get(WarheadYield.CONVENTIONAL);
	}
}
