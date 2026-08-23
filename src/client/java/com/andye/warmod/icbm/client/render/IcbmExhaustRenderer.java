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
		plume(pose, buffer, -2.55F, 0.46F, (float) (4.65 * flicker), 255, 255, 238, 255, 3);
		if (lod != IcbmLongRangeRenderContext.Lod.EXTREME) {
			plume(pose, buffer, -2.58F, 0.63F, (float) (6.35 * flicker), 255, 232, 146, 228, 3);
		}
	}

	public static void renderFringe(final PoseStack.Pose pose, final VertexConsumer buffer,
		final long seed, final double elapsed, final IcbmLongRangeRenderContext.Lod lod) {
		double flicker = flicker(seed, elapsed) * ignitionBuildup(elapsed);
		plume(pose, buffer, -2.60F, 0.82F, (float) (8.55 * flicker), 255, 174, 54, 216, 4);
		if (lod != IcbmLongRangeRenderContext.Lod.EXTREME) {
			plume(pose, buffer, -2.64F, 1.08F, (float) (11.25 * flicker), 255, 108, 24, 150, 4);
		}
		if (lod == IcbmLongRangeRenderContext.Lod.NEAR || lod == IcbmLongRangeRenderContext.Lod.MEDIUM) {
			plume(pose, buffer, -2.68F, 1.38F, (float) (14.0 * flicker), 238, 76, 18, 82, 5);
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
