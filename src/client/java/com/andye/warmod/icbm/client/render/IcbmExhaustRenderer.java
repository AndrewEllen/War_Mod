package com.andye.warmod.icbm.client.render;

import com.andye.warmod.icbm.IcbmConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Full-bright layered rocket exhaust using neutral alpha-mask textures. */
public final class IcbmExhaustRenderer {
	private IcbmExhaustRenderer() { }

	public static void renderCore(final PoseStack.Pose pose, final VertexConsumer buffer,
		final long seed, final double elapsed, final IcbmLongRangeRenderContext.Lod lod) {
		double flicker = flicker(seed, elapsed) * ignitionBuildup(elapsed);
		float boost = distanceBoost(lod);
		plume(pose, buffer, -2.55F, 0.42F * boost, (float) (7.20 * flicker * boost), 255, 255, 250, 255, 4);
		plume(pose, buffer, -2.57F, 0.64F * boost, (float) (8.85 * flicker * boost), 255, 246, 205, 248, 4);
		if (lod != IcbmLongRangeRenderContext.Lod.EXTREME) {
			plume(pose, buffer, -2.58F, 0.84F * boost, (float) (10.60 * flicker * boost), 255, 220, 122, 226, 4);
		} else {
			/* The world transform preserves angular size at extreme distance, but a
			 * physically tiny nozzle flame still vanishes into fog. This full-bright
			 * pilot flare is the long-range signature of a live booster. */
			plume(pose, buffer, -2.50F, 1.12F * boost, (float) (4.2 * flicker * boost),
				255, 250, 224, 248, 5);
		}
	}

	public static void renderFringe(final PoseStack.Pose pose, final VertexConsumer buffer,
		final long seed, final double elapsed, final IcbmLongRangeRenderContext.Lod lod) {
		double flicker = flicker(seed, elapsed) * ignitionBuildup(elapsed);
		float boost = distanceBoost(lod);
		plume(pose, buffer, -2.60F, 1.10F * boost, (float) (12.4 * flicker * boost), 255, 174, 54, 238, 4);
		if (lod != IcbmLongRangeRenderContext.Lod.EXTREME) {
			plume(pose, buffer, -2.64F, 1.46F * boost, (float) (17.2 * flicker * boost), 255, 108, 24, 194, 4);
		} else {
			plume(pose, buffer, -2.64F, 1.52F * boost, (float) (13.8 * flicker * boost),
				255, 116, 24, 196, 5);
		}
		if (lod == IcbmLongRangeRenderContext.Lod.NEAR || lod == IcbmLongRangeRenderContext.Lod.MEDIUM) {
			plume(pose, buffer, -2.68F, 1.82F, (float) (21.0 * flicker), 238, 76, 18, 104, 5);
		}
	}

	/** Retained for any external call sites; the world renderer submits the two passes separately. */
	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
		final long seed, final double elapsed, final IcbmLongRangeRenderContext.Lod lod) {
		renderCore(pose, buffer, seed, elapsed, lod);
		renderFringe(pose, buffer, seed, elapsed, lod);
	}

	private static double flicker(final long seed, final double elapsed) {
		double phase = elapsed * 2.15 + (seed & 255L) * 0.03125;
		return 0.91 + 0.065 * Math.sin(phase) + 0.025 * Math.sin(phase * 2.37 + 1.4);
	}

	private static float distanceBoost(final IcbmLongRangeRenderContext.Lod lod) {
		return switch (lod) {
			case NEAR -> 1.0F;
			case MEDIUM -> 1.12F;
			case FAR -> 1.75F;
			case EXTREME -> 2.70F;
		};
	}

	private static double ignitionBuildup(final double elapsed) {
		double progress = Mth.clamp(elapsed / IcbmConstants.IGNITION_TICKS, 0.0, 1.0);
		// Keep a short pilot flame at tick zero, then smoothly build chamber pressure.
		double smooth = progress * progress * (3.0 - 2.0 * progress);
		return 0.22 + 0.78 * smooth;
	}

	private static void plume(final PoseStack.Pose pose, final VertexConsumer buffer,
		final float top, final float radius, final float length,
		final int red, final int green, final int blue, final int alpha, final int quads) {
		for (int quad = 0; quad < quads; quad++) {
			float angle = quad * Mth.PI / quads;
			float cosine = Mth.cos(angle);
			float sine = Mth.sin(angle);
			vertex(pose, buffer, -cosine * radius, top, -sine * radius, 0.0F, 0.0F, red, green, blue, alpha);
			vertex(pose, buffer, cosine * radius, top, sine * radius, 1.0F, 0.0F, red, green, blue, alpha);
			vertex(pose, buffer, cosine * 0.035F, top - length, sine * 0.035F, 1.0F, 1.0F, red, green, blue, 0);
			vertex(pose, buffer, -cosine * 0.035F, top - length, -sine * 0.035F, 0.0F, 1.0F, red, green, blue, 0);
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
