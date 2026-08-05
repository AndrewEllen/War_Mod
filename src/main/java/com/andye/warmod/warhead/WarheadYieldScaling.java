package com.andye.warmod.warhead;

import net.minecraft.util.Mth;

/** Shared conversion from network visual scale to physical effect radius scale. */
public final class WarheadYieldScaling {
	private WarheadYieldScaling() {
	}

	public static float radiusScale(final WarheadPayloadType payloadType, final float visualScale) {
		if (!Float.isFinite(visualScale)) return 1.0F;
		return payloadType == WarheadPayloadType.NUCLEAR
			? Mth.clamp(visualScale / WarheadYield.STRATEGIC_NUCLEAR.visualScale(), 0.48F, 1.55F)
			: Mth.clamp(visualScale, 0.28F, 1.75F);
	}
}
