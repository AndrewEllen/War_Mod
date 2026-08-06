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
		if (velocity == null || !velocity.isFinite() || !Double.isFinite(expectedMaximumSpeed)
			|| expectedMaximumSpeed <= 0.0) return 0.0;
		return clamp(velocity.length() / expectedMaximumSpeed, 0.0, 1.0);
	}

	public static double coneActivation(final double normalizedSpeed) {
		if (!Double.isFinite(normalizedSpeed)) return 0.0;
		return smoothstep((normalizedSpeed - CONE_ACTIVATION_START)
			/ (CONE_ACTIVATION_FULL - CONE_ACTIVATION_START));
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

	public static double reentryHeat(final double progress, final Vec3 velocity, final double remainingTicks) {
		double p = Double.isFinite(progress) ? clamp(progress, 0.0, 1.0) : 0.0;
		double speed = normalizedSpeed(velocity, WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK * 1.65);
		double progressHeat = smoothstep(0.45, 0.62, p) * 0.16
			+ smoothstep(0.62, 0.82, p) * 0.34
			+ smoothstep(0.82, 0.96, p) * 0.50;
		double speedGate = smoothstep(0.28, 0.62, speed);
		double finalTaper = remainingTicks < 1.0 ? 0.82 + 0.18 * smoothstep(remainingTicks) : 1.0;
		return clamp(progressHeat * speedGate * finalTaper, 0.0, 1.0);
	}

	public static double reentryWidthProgress(final double progress) {
		return smoothstep(0.52, 0.90, clamp(progress, 0.0, 1.0));
	}

	public static double reentryElongationProgress(final double progress) {
		return smoothstep(0.68, 0.98, clamp(progress, 0.0, 1.0));
	}

	public static double reentryShimmer(final double elapsedTicks, final long visualSeed) {
		if (!Double.isFinite(elapsedTicks)) return 1.0;
		double phase = (visualSeed & 0xFFFFL) / 65536.0 * Math.PI * 2.0;
		return clamp(1.0 + 0.075 * Math.sin(elapsedTicks * 0.43 + phase)
			+ 0.045 * Math.sin(elapsedTicks * 0.79 - phase * 1.7)
			+ 0.025 * Math.sin(elapsedTicks * 1.31 + phase * 0.4), 0.82, 1.18);
	}

	public static double terminalConeCompression(final double progress) {
		return smoothstep(0.72, 0.98, progress);
	}

	public static double vaporBandPhase(final double elapsedTicks, final int bandIndex,
		final int bandCount, final long visualSeed) {
		if (!Double.isFinite(elapsedTicks) || bandCount <= 0) return 0.0;
		double seedOffset = ((visualSeed >>> 16) & 0xFFFFL) / 65536.0;
		return fractionalPart(elapsedTicks * 0.10 + bandIndex / (double) bandCount + seedOffset);
	}

	/**
	 * The front always propagates at the physical speed of sound. Yield changes
	 * only how long the front remains energetic and therefore how far it travels.
	 */
	public static double airShockwaveDurationTicks(final double radiusScale) {
		double safeScale = Double.isFinite(radiusScale) ? clamp(radiusScale, 0.22, 1.75) : 1.0;
		return AIR_SHOCKWAVE_DURATION_TICKS * safeScale;
	}

	public static double airShockwaveRadius(final double ageTicks) {
		return airShockwaveRadius(ageTicks, 1.0);
	}

	public static double airShockwaveRadius(final double ageTicks, final double radiusScale) {
		double safeAge = Double.isFinite(ageTicks) ? Math.max(0.0, ageTicks) : 0.0;
		return Math.min(safeAge, airShockwaveDurationTicks(radiusScale))
			* AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
	}

	public static double airShockwaveAlpha(final double ageTicks) {
		return airShockwaveAlpha(ageTicks, 1.0);
	}

	public static double airShockwaveAlpha(final double ageTicks, final double radiusScale) {
		double duration = airShockwaveDurationTicks(radiusScale);
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= duration) return 0.0;
		double fadeStart = duration * 0.42;
		double fade = ageTicks <= fadeStart ? 1.0 : 1.0 - smoothstep(fadeStart, duration, ageTicks);
		return 0.38 * Math.pow(fade, 0.70);
	}

	public static double airShockwaveThickness(final double ageTicks, final double thicknessScale) {
		return airShockwaveThickness(ageTicks, thicknessScale, 1.0);
	}

	public static double airShockwaveThickness(final double ageTicks, final double thicknessScale,
		final double radiusScale) {
		double progress = clamp(ageTicks / airShockwaveDurationTicks(radiusScale), 0.0, 1.0);
		double safe = Double.isFinite(thicknessScale) ? Math.max(0.05, thicknessScale) : 1.0;
		return safe * (0.8 + 2.7 * smoothstep(progress));
	}

	public static double groundShockwaveDistance(final double ageTicks) {
		return groundShockwaveDistance(ageTicks, 1.0);
	}

	public static double groundShockwaveDistance(final double ageTicks, final double radiusScale) {
		return airShockwaveRadius(ageTicks, radiusScale);
	}

	public static double groundShockwaveRadius(final double ageTicks) {
		return groundShockwaveDistance(ageTicks);
	}

	public static double groundShockwaveAlpha(final double ageTicks) {
		return groundShockwaveAlpha(ageTicks, 1.0);
	}

	public static double groundShockwaveAlpha(final double ageTicks, final double radiusScale) {
		return Math.min(0.78, airShockwaveAlpha(ageTicks, radiusScale) * 1.65);
	}

	/**
	 * Nuclear rarefaction/return front. It begins after the primary front has
	 * substantially expanded, then contracts at the same acoustic propagation
	 * speed. This is a visual/gameplay approximation of the negative-pressure
	 * phase rather than a second faster shockwave.
	 */
	public static double nuclearReturnWaveStartTicks(final double radiusScale) {
		return airShockwaveDurationTicks(radiusScale) * 0.72;
	}

	public static double nuclearReturnWaveDurationTicks(final double radiusScale) {
		return airShockwaveDurationTicks(radiusScale) * 0.82;
	}

	public static double nuclearReturnWaveMaximumRadius(final double radiusScale) {
		return nuclearReturnWaveDurationTicks(radiusScale) * AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
	}

	public static double nuclearReturnWaveRadius(final double ageTicks, final double radiusScale) {
		double start = nuclearReturnWaveStartTicks(radiusScale);
		double duration = nuclearReturnWaveDurationTicks(radiusScale);
		if (!Double.isFinite(ageTicks) || ageTicks < start || ageTicks >= start + duration) return -1.0;
		double elapsed = ageTicks - start;
		return Math.max(0.0, nuclearReturnWaveMaximumRadius(radiusScale)
			- elapsed * AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK);
	}

	public static double nuclearReturnWaveAlpha(final double ageTicks, final double radiusScale) {
		double start = nuclearReturnWaveStartTicks(radiusScale);
		double duration = nuclearReturnWaveDurationTicks(radiusScale);
		if (!Double.isFinite(ageTicks) || ageTicks < start || ageTicks >= start + duration) return 0.0;
		double progress = clamp((ageTicks - start) / duration, 0.0, 1.0);
		return 0.24 * Math.pow(Math.sin(Math.PI * progress), 0.72);
	}

	public static double fireballRise(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks <= 10.0) return 0.0;
		return 18.0 * smoothstep(clamp((ageTicks - 10.0) / 65.0, 0.0, 1.0));
	}

	public static double fireballAlpha(final double ageTicks) {
		if (!Double.isFinite(ageTicks) || ageTicks < 0.0 || ageTicks >= 80.0) return 0.0;
		if (ageTicks <= 3.0) return 1.0 - 0.12 * smoothstep(ageTicks / 3.0);
		return 0.88 * Math.pow(1.0 - clamp((ageTicks - 3.0) / 77.0, 0.0, 1.0), 0.64);
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
