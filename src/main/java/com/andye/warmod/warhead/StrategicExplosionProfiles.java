package com.andye.warmod.warhead;

import java.util.Objects;

/** Tuned gameplay profiles for the custom strategic crater engine. */
public final class StrategicExplosionProfiles {
	/*
	 * Conventional warheads retain roughly the existing battlefield footprint,
	 * but penetrate stone more consistently and use a rounded ray distribution.
	 */
	private static final StrategicExplosionProfile CONVENTIONAL = new StrategicExplosionProfile(
		WarheadPayloadType.CONVENTIONAL,
		24.0,
		18.0,
		17.0,
		3_584,
		0.55,
		72.0F,
		0.62F,
		0.18F,
		95.0F,
		0.34,
		28.0F
	);

	/*
	 * Nuclear cratering is deliberately independent from the old vanilla
	 * strength=160 call. A 72-block horizontal / 50-block downward envelope is
	 * large enough to read as strategic while remaining bounded and staged.
	 */
	private static final StrategicExplosionProfile NUCLEAR = new StrategicExplosionProfile(
		WarheadPayloadType.NUCLEAR,
		72.0,
		54.0,
		50.0,
		12_288,
		0.65,
		320.0F,
		0.82F,
		0.095F,
		650.0F,
		0.32,
		112.0F
	);

	private StrategicExplosionProfiles() {
	}

	public static StrategicExplosionProfile get(final WarheadPayloadType payloadType) {
		return Objects.requireNonNull(payloadType, "payloadType") == WarheadPayloadType.NUCLEAR
			? NUCLEAR
			: CONVENTIONAL;
	}

	public static StrategicExplosionProfile fromLegacyStrength(final float strength) {
		return strength >= 100.0F ? NUCLEAR : CONVENTIONAL;
	}
}
