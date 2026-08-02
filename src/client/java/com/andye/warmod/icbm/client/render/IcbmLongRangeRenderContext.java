package com.andye.warmod.icbm.client.render;

import net.minecraft.world.phys.Vec3;

public record IcbmLongRangeRenderContext(IcbmLongRangeTransform transform, Lod lod) {
	public static IcbmLongRangeRenderContext create(final Vec3 camera, final Vec3 center, final double farPlane) {
		IcbmLongRangeTransform transform = IcbmLongRangeTransform.create(camera, center, farPlane);
		double distance = transform.actualDistance();
		Lod lod = distance < 600.0 ? Lod.NEAR : distance < 1500.0 ? Lod.MEDIUM
			: distance < 4000.0 ? Lod.FAR : Lod.EXTREME;
		return new IcbmLongRangeRenderContext(transform, lod);
	}

	public enum Lod { NEAR, MEDIUM, FAR, EXTREME }
}