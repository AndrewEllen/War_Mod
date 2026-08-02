package com.andye.warmod.warhead;

import net.minecraft.world.phys.Vec3;

/** Pure timing and shaping helpers shared by the client visual systems. */
public final class WarheadVisualMath {
	private static final double CONE_ACTIVATION_START = 0.32;
	private static final double CONE_ACTIVATION_FULL = 0.55;
	private static final double CONE_ATTACK_TICKS = 4.0;
	private static final double PRESSURE_SHELL_LIFETIME = 24.0;
	private static final double GROUND_SHOCKWAVE_TRAVEL_TICKS = 32.0;
	private static final double GROUND_SHOCKWAVE_FADE_TICKS = 72.0;
	private static final double GROUND_SHOCKWAVE_MAX_RADIUS = 128.0;

	private WarheadVisualMath() {
	}

	public static double normalizedSpeed(final Vec3 velocity, final double expectedMaximumSpeed) {
		if (velocity == null || !velocity.isFinite() || !Double.isFinite(expectedMaximumSpeed) || expectedMaximumSpeed <= 0.0) {
			return 0.0;
		}
		return clamp(velocity.length() / expectedMaximumSpeed, 0.0, 1.0);
	}

	public static double coneActivation(final double normalizedSpeed) {
		if (!Double.isFinite(normalizedSpeed)) {
			return 0.0;
		}
		return smoothstep((normalizedSpeed - CONE_ACTIVATION_START) / (CONE_ACTIVATION_FULL - CONE_ACTIVATION_START));
	}

	public static double coneAttack(final double elapsedTicksSinceThreshold) {
		if (!Double.isFinite(elapsedTicksSinceThreshold)) {
			return 0.0;
		}
		return smoothstep(elapsedTicksSinceThreshold / CONE_ATTACK_TICKS);
	}

	public static double conePulse(final double elapsedTicks, final long visualSeed) {
		if (!Double.isFinite(elapsedTicks)) {
			return 1.0;
		}
		double seedPhase = (visualSeed & 0xFFFFL) / 65536.0 * Math.PI * 2.0;
		return 0.93 + 0.07 * (0.5 + 0.5 * Math.sin(elapsedTicks * 0.58 + seedPhase));
	}

	public static double coneFade(final double remainingTicks) {
		return smoothstep(remainingTicks / 4.0);
	}

	public static double vaporBandPhase(final double elapsedTicks, final int bandIndex, final int bandCount, final long visualSeed) {
		if (!Double.isFinite(elapsedTicks) || bandCount <= 0) {
			return 0.0;
		}
		double seedOffset = ((visualSeed >>> 16) & 0xFFFFL) / 65536.0;
		return fractionalPart(elapsedTicks * 0.10 + bandIndex / (double) bandCount + seedOffset);
	}

	public static double pressureSphereRadius(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0) {
			return 2.0;
		}
		double t = clamp(ageTicks / PRESSURE_SHELL_LIFETIME, 0.0, 1.0);
		return 2.0 + 54.0 * (1.0 - Math.pow(1.0 - t, 2.7));
	}

	public static double pressureSphereLeadingAlpha(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= PRESSURE_SHELL_LIFETIME) {
			return 0.0;
		}
		double t = clamp(ageTicks / PRESSURE_SHELL_LIFETIME, 0.0, 1.0);
		return 0.32 * Math.pow(1.0 - t, 0.82);
	}

	public static double pressureSphereTrailingAlpha(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= PRESSURE_SHELL_LIFETIME * 0.92) {
			return 0.0;
		}
		double t = clamp(ageTicks / (PRESSURE_SHELL_LIFETIME * 0.92), 0.0, 1.0);
		return 0.16 * Math.pow(1.0 - t, 0.72);
	}

	public static double groundShockwaveRadius(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0) {
			return 2.0;
		}
		double t = clamp(ageTicks / GROUND_SHOCKWAVE_TRAVEL_TICKS, 0.0, 1.0);
		return 2.0 + (GROUND_SHOCKWAVE_MAX_RADIUS - 2.0) * (1.0 - Math.pow(1.0 - t, 1.65));
	}

	public static double groundShockwaveAlpha(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= GROUND_SHOCKWAVE_FADE_TICKS) {
			return 0.0;
		}
		double t = clamp(ageTicks / GROUND_SHOCKWAVE_FADE_TICKS, 0.0, 1.0);
		return 0.78 * Math.pow(1.0 - t, 1.15);
	}

	public static double fireballRise(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks <= 10.0) {
			return 0.0;
		}
		double t = clamp((ageTicks - 10.0) / 55.0, 0.0, 1.0);
		return 16.0 * smoothstep(t);
	}

	public static double fireballAlpha(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= 55.0) {
			return 0.0;
		}
		if (ageTicks <= 3.0) {
			return 1.0 - 0.18 * smoothstep(ageTicks / 3.0);
		}
		return 0.82 * Math.pow(1.0 - clamp((ageTicks - 3.0) / 52.0, 0.0, 1.0), 0.72);
	}

	public static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double smoothstep(final double value) {
		double t = clamp(value, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double fractionalPart(final double value) {
		return value - Math.floor(value);
	}
}
