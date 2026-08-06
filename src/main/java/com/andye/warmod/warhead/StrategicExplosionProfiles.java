package com.andye.warmod.warhead;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Tuned crater and extended surface-damage profiles for each yield. */
public final class StrategicExplosionProfiles {
	private static final Map<WarheadYield, StrategicExplosionProfile> PROFILES = new EnumMap<>(WarheadYield.class);

	static {
		/* Deep excavation has deliberately been reduced from Stage 5. The aftermath radius remains broader. */
		put(WarheadYield.HIGH_EXPLOSIVE,
			6.5, 3.5, 4.0, 0.68, 0.10, 34.0F, 0.58F, 10.0F, 1.55, 0.07);
		put(WarheadYield.HIGH_CAPACITY_HE,
			10.5, 5.5, 7.0, 0.69, 0.10, 64.0F, 0.55F, 17.0F, 1.60, 0.08);
		put(WarheadYield.CONVENTIONAL,
			17.0, 9.0, 12.0, 0.71, 0.095, 122.0F, 0.51F, 27.0F, 1.65, 0.10);
		put(WarheadYield.HEAVY_CONVENTIONAL,
			24.0, 13.0, 18.0, 0.72, 0.09, 210.0F, 0.47F, 42.0F, 1.72, 0.12);
		put(WarheadYield.TACTICAL_NUCLEAR,
			32.0, 18.0, 24.0, 0.74, 0.085, 390.0F, 0.43F, 72.0F, 2.15, 0.18);
		put(WarheadYield.STRATEGIC_NUCLEAR,
			48.0, 28.0, 36.0, 0.76, 0.078, 820.0F, 0.38F, 116.0F, 2.35, 0.22);
		put(WarheadYield.HEAVY_NUCLEAR,
			64.0, 36.0, 48.0, 0.78, 0.072, 1_450.0F, 0.34F, 168.0F, 2.55, 0.26);
	}

	private StrategicExplosionProfiles() { }

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
			yield, horizontalRadius, upwardRadius, downwardRadius, guaranteedVoidScale,
			boundaryRoughness, maximumDestroyResistance, edgeResistanceScale,
			entityBlastRadius, aftermathRadiusScale, aftermathDensity
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
		if (strength >= 55.0F) return get(WarheadYield.TACTICAL_NUCLEAR);
		if (strength >= 28.0F) return get(WarheadYield.HEAVY_CONVENTIONAL);
		if (strength >= 13.0F) return get(WarheadYield.CONVENTIONAL);
		if (strength > 7.0F) return get(WarheadYield.HIGH_CAPACITY_HE);
		return get(WarheadYield.HIGH_EXPLOSIVE);
	}
}
