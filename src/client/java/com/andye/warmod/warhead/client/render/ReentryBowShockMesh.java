package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Curved velocity-aligned plasma envelope; local +Y is the warhead nose. */
public final class ReentryBowShockMesh {
	private ReentryBowShockMesh() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
		final WarheadMesh.Lod lod, final double progress, final double heat,
		final double shimmer, final double elapsed, final long seed) {
		if (heat <= 0.002) return;
		int segments = lod == WarheadMesh.Lod.NEAR ? 28
			: lod == WarheadMesh.Lod.MEDIUM ? 18 : 10;
		int rings = lod == WarheadMesh.Lod.NEAR ? 9
			: lod == WarheadMesh.Lod.MEDIUM ? 7 : 5;
		layer(pose, buffer, segments, rings, progress, heat, shimmer,
			elapsed, seed, 1.00, 0.42);
		layer(pose, buffer, segments, rings, progress, heat, shimmer,
			elapsed, seed ^ 0x6A09E667F3BCC909L, 1.12, 0.23);
		layer(pose, buffer, segments, rings, progress, heat, shimmer,
			elapsed, seed ^ 0xBB67AE8584CAA73BL, 1.23, 0.14);
	}

	private static void layer(final PoseStack.Pose pose, final VertexConsumer buffer,
		final int segments, final int rings, final double progress, final double heat,
		final double shimmer, final double elapsed, final long seed,
		final double layerScale, final double alphaScale) {
		double widthProgress = WarheadVisualMath.reentryWidthProgress(progress);
		double elongationProgress = WarheadVisualMath.reentryElongationProgress(progress);
		double radius = Mth.lerp(widthProgress, 0.78, 4.45) * layerScale;
		double depth = Mth.lerp(elongationProgress, 1.2, 3.4);
		double basePhase = (seed & 0xffffL) / 65536.0 * Mth.TWO_PI;
		for (int ring = 0; ring < rings; ring++) {
			double t0 = ring / (double) rings;
			double t1 = (ring + 1) / (double) rings;
			for (int segment = 0; segment < segments; segment++) {
				int next = (segment + 1) % segments;
				VertexData a = vertex(t0, ring, segment, segments, radius,
					depth, heat, elapsed, basePhase);
				VertexData b = vertex(t1, ring + 1, segment, segments, radius,
					depth, heat, elapsed, basePhase);
				VertexData c = vertex(t1, ring + 1, next, segments, radius,
					depth, heat, elapsed, basePhase);
				VertexData d = vertex(t0, ring, next, segments, radius,
					depth, heat, elapsed, basePhase);
				put(pose, buffer, a, 0.0F, (float) t0, heat, shimmer, alphaScale);
				put(pose, buffer, b, 1.0F, (float) t1, heat, shimmer, alphaScale);
				put(pose, buffer, c, 1.0F, (float) t1, heat, shimmer, alphaScale);
				put(pose, buffer, d, 0.0F, (float) t0, heat, shimmer, alphaScale);
			}
		}
	}

	private static VertexData vertex(final double t, final int ring,
		final int segment, final int segments, final double radius, final double depth,
		final double heat, final double elapsed, final double basePhase) {
		double angle = Mth.TWO_PI * segment / segments;
		double segmentPhase = basePhase + segment * 0.73 + ring * 1.17;
		double noise = Math.sin(elapsed * 0.18 + segmentPhase)
			+ 0.55 * Math.sin(elapsed * 0.37 - segmentPhase * 1.9)
			+ 0.30 * Math.sin(elapsed * 0.71 + segmentPhase * 0.43);
		double radialVariation = 1.0 + noise * (0.030 + 0.040 * heat);
		double axialVariation = noise * (0.07 + 0.11 * heat);
		double ringRadius = radius * radiusPath(t) * radialVariation;
		double y = 0.55 + depth * axialPath(t) + axialVariation;
		double alphaVariation = Mth.clamp(1.0 + noise * 0.13, 0.70, 1.30);
		double temperature = Mth.clamp(0.5 + 0.5
			* Math.sin(elapsed * 0.24 + segmentPhase * 0.61), 0.0, 1.0);
		return new VertexData((float) (ringRadius * Math.cos(angle)), (float) y,
			(float) (ringRadius * Math.sin(angle)), (float) alphaVariation,
			(float) temperature);
	}

	private static double radiusPath(final double t) {
		if (t < 0.18) return Mth.lerp(t / 0.18, 0.10, 0.47);
		if (t < 0.62) return Mth.lerp((t - 0.18) / 0.44, 0.47, 1.0);
		return Mth.lerp((t - 0.62) / 0.38, 1.0, 0.46);
	}

	private static double axialPath(final double t) {
		if (t < 0.62) return Mth.lerp(t / 0.62, 0.62, 0.03);
		return Mth.lerp((t - 0.62) / 0.38, 0.03, -0.48);
	}

	private static void put(final PoseStack.Pose pose, final VertexConsumer buffer,
		final VertexData vertex, final float u, final float v, final double heat,
		final double shimmer, final double alphaScale) {
		int red = 255;
		int green = Mth.clamp((int) Mth.lerp(vertex.temperature, 232.0, 250.0), 0, 255);
		int blue = Mth.clamp((int) Mth.lerp(vertex.temperature, 174.0, 255.0), 0, 255);
		int alpha = Mth.clamp((int) (255.0 * alphaScale * heat * shimmer
			* vertex.alpha), 0, 255);
		float length = Math.max(0.001F,
			Mth.sqrt(vertex.x * vertex.x + vertex.z * vertex.z));
		buffer.addVertex(pose, vertex.x, vertex.y, vertex.z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0)
			.setNormal(pose, vertex.x / length, 0.28F, vertex.z / length);
	}

	private record VertexData(float x, float y, float z,
		float alpha, float temperature) { }
}
