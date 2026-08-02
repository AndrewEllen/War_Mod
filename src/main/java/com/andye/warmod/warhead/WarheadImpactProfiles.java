package com.andye.warmod.warhead;

import java.util.Objects;

public final class WarheadImpactProfiles {
	private static final WarheadImpactProfile CONVENTIONAL = new WarheadImpactProfile(
		WarheadPayloadType.CONVENTIONAL, WarheadConstants.EXPLOSION_STRENGTH, 1.0F, 1.0F, 1.0F, 1.0, 320, 112, 1.0);
	private static final WarheadImpactProfile NUCLEAR = new WarheadImpactProfile(
		WarheadPayloadType.NUCLEAR, 128.0F, 2.4F, 0.96F, 3.0F, 1.25, 960, 320, 1.15);
	private WarheadImpactProfiles() { }
	public static WarheadImpactProfile get(final WarheadPayloadType payloadType) {
		return Objects.requireNonNull(payloadType, "payloadType") == WarheadPayloadType.NUCLEAR ? NUCLEAR : CONVENTIONAL;
	}
}
