package com.andye.warmod.icbm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

/** Full-bright layered rocket exhaust. Geometry is intentionally independent of world lighting. */
public final class IcbmExhaustRenderer {
	private IcbmExhaustRenderer() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
		final long seed, final double elapsed, final IcbmLongRangeRenderContext.Lod lod) {
		double phase = elapsed * 2.15 + (seed & 255L) * 0.03125;
		double flicker = 0.91 + 0.065 * Math.sin(phase) + 0.025 * Math.sin(phase * 2.37 + 1.4);
		/* A wide opaque-white core keeps the engine visibly hot even at midnight and under shaders. */
		plume(pose, buffer, -2.56F, 0.43F, (float) (4.25 * flicker), 255, 255, 235, 255);
		plume(pose, buffer, -2.59F, 0.66F, (float) (6.35 * flicker), 255, 211, 92, 232);
		if (lod == IcbmLongRangeRenderContext.Lod.EXTREME) return;
		plume(pose, buffer, -2.63F, 0.94F, (float) (8.8 * flicker), 255, 126, 28, 188);
		if (lod != IcbmLongRangeRenderContext.Lod.FAR) {
			plume(pose, buffer, -2.68F, 1.30F, (float) (12.5 * flicker), 220, 108, 24, 104);
		}
	}

	private static void plume(final PoseStack.Pose pose, final VertexConsumer buffer,
		final float top, final float radius, final float length,
		final int red, final int green, final int blue, final int alpha) {
		for (int quad = 0; quad < 3; quad++) {
			float angle = quad * Mth.PI / 3.0F;
			float cosine = Mth.cos(angle);
			float sine = Mth.sin(angle);
			vertex(pose, buffer, -cosine * radius, top, -sine * radius, 0.0F, 0.0F, red, green, blue, alpha);
			vertex(pose, buffer, cosine * radius, top, sine * radius, 1.0F, 0.0F, red, green, blue, alpha);
			vertex(pose, buffer, cosine * 0.04F, top - length, sine * 0.04F, 1.0F, 1.0F, red, green, blue, 0);
			vertex(pose, buffer, -cosine * 0.04F, top - length, -sine * 0.04F, 0.0F, 1.0F, red, green, blue, 0);
		}
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
		final float x, final float y, final float z, final float u, final float v,
		final int red, final int green, final int blue, final int alpha) {
		buffer.addVertex(pose, x, y, z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(0xF000F0)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
