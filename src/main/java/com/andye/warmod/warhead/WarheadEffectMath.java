package com.andye.warmod.warhead;

public final class WarheadEffectMath {
	private WarheadEffectMath() {
	}

	public static double pressureRingRadius(final double ageTicks, final double visualScale) {
		if (!Double.isFinite(ageTicks) || !Double.isFinite(visualScale) || visualScale <= 0.0) {
			return 0.0;
		}
		double t = clamp((ageTicks - 2.0) / 30.0, 0.0, 1.0);
		return WarheadConstants.PRESSURE_RING_MAX_RADIUS * visualScale * (1.0 - Math.pow(1.0 - t, 3.0));
	}

	public static double dustRingRadius(final double ageTicks, final double visualScale) {
		if (!Double.isFinite(ageTicks) || !Double.isFinite(visualScale) || visualScale <= 0.0) {
			return 0.0;
		}
		double t = clamp((ageTicks - 5.0) / 47.0, 0.0, 1.0);
		return WarheadConstants.DUST_RING_MAX_RADIUS * visualScale * (1.0 - Math.pow(1.0 - t, 2.5));
	}

	public static boolean impactExpired(final double ageTicks) {
		return !Double.isFinite(ageTicks) || ageTicks >= WarheadConstants.IMPACT_VISUAL_LIFETIME_TICKS;
	}

	public static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}