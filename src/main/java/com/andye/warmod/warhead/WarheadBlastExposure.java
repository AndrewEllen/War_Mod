package com.andye.warmod.warhead;

import net.minecraft.util.Mth;

/** Bounded cover transmission used by strategic blast damage and knockback. */
public final class WarheadBlastExposure {
	private static final float FULL_COVER_TRANSMISSION = 0.06F;

	private WarheadBlastExposure() {
	}

	public static float transmission(final int clearRays, final int totalRays) {
		if (totalRays <= 0 || clearRays < 0 || clearRays > totalRays) {
			throw new IllegalArgumentException("Invalid blast exposure ray counts");
		}
		float visible = clearRays / (float) totalRays;
		return Mth.lerp(visible * visible, FULL_COVER_TRANSMISSION, 1.0F);
	}
}
