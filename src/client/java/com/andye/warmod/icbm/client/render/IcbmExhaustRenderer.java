package com.andye.warmod.icbm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Full-bright layered rocket exhaust using neutral alpha-mask textures. */
public final class IcbmExhaustRenderer {
	private IcbmExhaustRenderer() { }

	public static void renderCore(final PoseStack.Pose pose, final VertexConsumer buffer,
		final long seed, final double elapsed, final IcbmLongRangeRenderContext.Lod lod) {
		double flicker = flicker(seed, elapsed);
		float visibility = visibilityScale(lod);
		/*
		 * The orange layer is intentionally wider than the white kernel. It is
		 * full-bright and submitted through an additive emissive pipeline so the
		 * engine remains visibly lit even when the missile body is small in fog.
		 */
		plume(pose, buffer, -2.60F, 1.04F * visibility,
			(float) (9.35 * flicker * visibility), 255, 138, 24, 255, 6);
		plume(pose, buffer, -2.57F, 0.78F * visibility,
			(float) (7.70 * flicker * visibility), 255, 205, 76, 255, 5);
		plume(pose, buffer, -2.54F, 0.54F * visibility,
			(float) (5.65 * flicker * visibility), 255, 255, 238, 255, 5);
	}

	public static void renderFringe(final PoseStack.Pose pose, final VertexConsumer buffer,
		final long seed, final double elapsed, final IcbmLongRangeRenderContext.Lod lod) {
		double flicker = flicker(seed, elapsed);
		float visibility = visibilityScale(lod);
		plume(pose, buffer, -2.63F, 1.30F * visibility,
			(float) (12.8 * flicker * visibility), 255, 111, 20, 218, 6);
		if (lod != IcbmLongRangeRenderContext.Lod.EXTREME) {
			plume(pose, buffer, -2.67F, 1.55F * visibility,
				(float) (15.6 * flicker * visibility), 255, 72, 14, 154, 6);
		}
		if (lod == IcbmLongRangeRenderContext.Lod.NEAR
			|| lod == IcbmLongRangeRenderContext.Lod.MEDIUM) {
			plume(pose, buffer, -2.72F, 1.82F,
				(float) (18.4 * flicker), 236, 58, 12, 82, 7);
		}
	}

	/** Retained for external call sites; the world renderer submits both passes. */
	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
		final long seed, final double elapsed, final IcbmLongRangeRenderContext.Lod lod) {
		renderCore(pose, buffer, seed, elapsed, lod);
		renderFringe(pose, buffer, seed, elapsed, lod);
	}

	private static float visibilityScale(final IcbmLongRangeRenderContext.Lod lod) {
		return switch (lod) {
			case NEAR -> 1.0F;
			case MEDIUM -> 1.12F;
			case FAR -> 1.38F;
			case EXTREME -> 1.90F;
		};
	}

	private static double flicker(final long seed, final double elapsed) {
		double phase = elapsed * 2.15 + (seed & 255L) * 0.03125;
		return 0.91 + 0.065 * Math.sin(phase) + 0.025 * Math.sin(phase * 2.37 + 1.4);
	}

	private static void plume(final PoseStack.Pose pose, final VertexConsumer buffer,
		final float top, final float radius, final float length,
		final int red, final int green, final int blue, final int alpha, final int quads) {
		for (int quad = 0; quad < quads; quad++) {
			float angle = quad * Mth.PI / quads;
			float cosine = Mth.cos(angle);
			float sine = Mth.sin(angle);
			vertex(pose, buffer, -cosine * radius, top, -sine * radius,
				0.0F, 0.0F, red, green, blue, alpha);
			vertex(pose, buffer, cosine * radius, top, sine * radius,
				1.0F, 0.0F, red, green, blue, alpha);
			vertex(pose, buffer, cosine * 0.035F, top - length, sine * 0.035F,
				1.0F, 1.0F, red, green, blue, 0);
			vertex(pose, buffer, -cosine * 0.035F, top - length, -sine * 0.035F,
				0.0F, 1.0F, red, green, blue, 0);
		}
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
		final float x, final float y, final float z, final float u, final float v,
		final int red, final int green, final int blue, final int alpha) {
		buffer.addVertex(pose, x, y, z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
