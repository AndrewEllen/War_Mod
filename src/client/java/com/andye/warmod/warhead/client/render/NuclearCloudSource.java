package com.andye.warmod.warhead.client.render;

import net.minecraft.world.phys.Vec3;

/** One local ignition source contributing to a merged nuclear cloud field. */
public record NuclearCloudSource(Vec3 offset, double ageTicks, float visualScale, long seed) {
	public NuclearCloudSource {
		if (offset == null || !offset.isFinite()) offset = Vec3.ZERO;
	}
}
