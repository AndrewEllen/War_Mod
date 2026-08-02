package com.andye.warmod.warhead;

import net.minecraft.world.phys.Vec3;

/** Pure timing and shaping helpers shared by the client visual systems. */
public final class WarheadVisualMath {
	private static final double CONE_ACTIVATION_START = 0.32;
	private static final double CONE_ACTIVATION_FULL = 0.55;
	private static final double CONE_ATTACK_TICKS = 4.0;
	public static final double AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK = 343.0 / 20.0;
	public static final double AIR_SHOCKWAVE_DURATION_TICKS = 72.0;

	private WarheadVisualMath() {
	}

	public static double normalizedSpeed(final Vec3 velocity, final double expectedMaximumSpeed) {
		if (velocity == null || !velocity.isFinite() || !Double.isFinite(expectedMaximumSpeed) || expectedMaximumSpeed <= 0.0) return 0.0;
		return clamp(velocity.length() / expectedMaximumSpeed, 0.0, 1.0);
	}

	public static double coneActivation(final double normalizedSpeed) {
		if (!Double.isFinite(normalizedSpeed)) return 0.0;
		return smoothstep((normalizedSpeed - CONE_ACTIVATION_START) / (CONE_ACTIVATION_FULL - CONE_ACTIVATION_START));
	}

	public static double coneAttack(final double elapsedTicksSinceThreshold) {
		if (!Double.isFinite(elapsedTicksSinceThreshold)) return 0.0;
		return smoothstep(elapsedTicksSinceThreshold / CONE_ATTACK_TICKS);
	}

	public static double conePulse(final double elapsedTicks, final long visualSeed) {
		if (!Double.isFinite(elapsedTicks)) return 1.0;
		double seedPhase = (visualSeed & 0xFFFFL) / 65536.0 * Math.PI * 2.0;
		return 0.93 + 0.07 * (0.5 + 0.5 * Math.sin(elapsedTicks * 0.58 + seedPhase));
	}

	public static double coneFade(final double remainingTicks) {
		return smoothstep(remainingTicks / 4.0);
	}

	public static double vaporBandPhase(final double elapsedTicks, final int bandIndex, final int bandCount, final long visualSeed) {
		if (!Double.isFinite(elapsedTicks) || bandCount <= 0) return 0.0;
		double seedOffset = ((visualSeed >>> 16) & 0xFFFFL) / 65536.0;
		return fractionalPart(elapsedTicks * 0.10 + bandIndex / (double) bandCount + seedOffset);
	}

	public static double airShockwaveRadius(final double ageTicks, final double visualScale) {
		double safeAge = Double.isFinite(ageTicks) ? ageTicks : 0.0;
		double safeScale = Double.isFinite(visualScale) ? Math.max(0.05, visualScale) : 1.0;
		double t = clamp(safeAge, 0.0, AIR_SHOCKWAVE_DURATION_TICKS);
		return safeScale * t * AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
	}

	public static double airShockwaveAlpha(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= AIR_SHOCKWAVE_DURATION_TICKS) return 0.0;
		double fade = ageTicks <= 30.0 ? 1.0 : 1.0 - smoothstep(30.0, AIR_SHOCKWAVE_DURATION_TICKS, ageTicks);
		return 0.38 * Math.pow(fade, 0.70);
	}

	public static double airShockwaveThickness(final double ageTicks, final double visualScale) {
		double progress = clamp(ageTicks / AIR_SHOCKWAVE_DURATION_TICKS, 0.0, 1.0);
		double safeScale = Double.isFinite(visualScale) ? Math.max(0.05, visualScale) : 1.0;
		return safeScale * (0.8 + 2.7 * smoothstep(progress));
	}

	public static double groundShockwaveDistance(final double ageTicks, final double visualScale) {
		double delayedAge = Math.max(0.0, ageTicks - 0.75);
		return airShockwaveRadius(delayedAge, visualScale) * 0.92;
	}

	public static double groundShockwaveRadius(final double ageTicks) {
		return groundShockwaveDistance(ageTicks, 1.0);
	}

	public static double groundShockwaveAlpha(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= AIR_SHOCKWAVE_DURATION_TICKS) return 0.0;
		return Math.min(0.78, airShockwaveAlpha(ageTicks) * 1.65);
	}

	public static double fireballRise(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks <= 10.0) return 0.0;
		double t = clamp((ageTicks - 10.0) / 65.0, 0.0, 1.0);
		return 18.0 * smoothstep(t);
	}

	public static double fireballAlpha(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= 75.0) return 0.0;
		if (ageTicks <= 3.0) return 1.0 - 0.12 * smoothstep(ageTicks / 3.0);
		return 0.88 * Math.pow(1.0 - clamp((ageTicks - 3.0) / 72.0, 0.0, 1.0), 0.64);
	}

	public static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double smoothstep(final double value) {
		double t = clamp(value, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double smoothstep(final double edge0, final double edge1, final double value) {
		if (edge1 <= edge0) return value < edge0 ? 0.0 : 1.0;
		return smoothstep((value - edge0) / (edge1 - edge0));
	}

	private static double fractionalPart(final double value) {
		return value - Math.floor(value);
	}
}