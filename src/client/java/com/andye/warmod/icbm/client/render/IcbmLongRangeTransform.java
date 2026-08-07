package com.andye.warmod.icbm.client.render;

import net.minecraft.world.phys.Vec3;

/** One carrier-centred compression factor shared by every associated visual in a frame. */
public record IcbmLongRangeTransform(Vec3 cameraPosition, Vec3 actualCenter, Vec3 renderedCenter,
	double actualDistance, double renderedDistance, double compression) {
	private static final double SAFE_FAR_PLANE_FRACTION = 0.68;

	public static IcbmLongRangeTransform create(final Vec3 camera, final Vec3 center, final double currentWorldFarPlane) {
		Vec3 relative = center.subtract(camera);
		double actualDistance = relative.length();
		double usableFarPlane = Double.isFinite(currentWorldFarPlane) && currentWorldFarPlane > 64.0
			? currentWorldFarPlane : 1024.0;
		/* Keep compressed carriers in front of the heavy fog band without changing angular size. */
		double safeDistance = Math.min(actualDistance, usableFarPlane * SAFE_FAR_PLANE_FRACTION);
		double compression = actualDistance <= safeDistance || actualDistance < 1.0E-8
			? 1.0 : safeDistance / actualDistance;
		Vec3 renderedCenter = camera.add(relative.scale(compression));
		return new IcbmLongRangeTransform(camera, center, renderedCenter, actualDistance,
			actualDistance * compression, compression);
	}

	public Vec3 renderPosition(final Vec3 actualWorldPosition) {
		return this.cameraPosition.add(actualWorldPosition.subtract(this.cameraPosition).scale(this.compression));
	}
	public boolean compressed() { return this.compression < 0.999999; }
}
