package com.andye.warmod.warhead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

/** Animated compact emissive envelope around the projectile nose and forward body. */
public final class ReentryGlowMesh {
	private ReentryGlowMesh() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final WarheadMesh.Lod lod,
		final double heat, final double shimmer, final double elapsed, final long seed) {
		if (heat <= 0.01) return;
		int segments = lod == WarheadMesh.Lod.FAR ? 8 : lod == WarheadMesh.Lod.MEDIUM ? 14 : 22;
		int rings = lod == WarheadMesh.Lod.FAR ? 4 : 7;
		double phase = (seed & 0xffffL) / 65536.0 * Mth.TWO_PI;
		float radius = (float)Mth.lerp(heat, 0.48, 1.78);
		float halfLength = (float)Mth.lerp(heat, 1.45, 2.12);
		float breathe = (float)(1.0 + (0.035 + 0.035 * heat) * Math.sin(elapsed * 0.52 + phase));
		float offsetX = (float)(Math.sin(elapsed * 0.31 + phase * 1.7) * 0.10 * heat);
		float offsetZ = (float)(Math.cos(elapsed * 0.27 - phase * 1.3) * 0.08 * heat);
		for (int ring = 0; ring < rings; ring++) {
			float v0 = ring / (float)rings, v1 = (ring + 1) / (float)rings;
			for (int segment = 0; segment < segments; segment++) {
				float u0 = segment / (float)segments, u1 = (segment + 1) / (float)segments;
				put(pose, buffer, vertex(u0, v0, ring, segment, radius, halfLength, breathe, offsetX, offsetZ, elapsed, phase), u0, v0, heat, shimmer);
				put(pose, buffer, vertex(u0, v1, ring + 1, segment, radius, halfLength, breathe, offsetX, offsetZ, elapsed, phase), u0, v1, heat, shimmer);
				put(pose, buffer, vertex(u1, v1, ring + 1, segment + 1, radius, halfLength, breathe, offsetX, offsetZ, elapsed, phase), u1, v1, heat, shimmer);
				put(pose, buffer, vertex(u1, v0, ring, segment + 1, radius, halfLength, breathe, offsetX, offsetZ, elapsed, phase), u1, v0, heat, shimmer);
			}
		}
	}

	private static VertexData vertex(final float u, final float v, final int ring, final int segment, final float radius,
		final float halfLength, final float breathe, final float offsetX, final float offsetZ,
		final double elapsed, final double phase) {
		float latitude = (v - 0.5F) * Mth.PI;
		float angle = u * Mth.TWO_PI;
		double localPhase = phase + ring * 1.17 + segment * 0.73;
		float asymmetry = (float)(1.0 + 0.065 * Math.sin(angle * 2.0 + localPhase)
			+ 0.035 * Math.sin(elapsed * 0.67 + localPhase));
		float ringPulse = (float)(1.0 + 0.055 * Math.sin(elapsed * 0.45 + localPhase));
		float r = Mth.cos(latitude) * radius * breathe * asymmetry * ringPulse;
		float y = 0.48F + Mth.sin(latitude) * halfLength
			+ (float)(0.10 * Math.sin(elapsed * 0.38 + ring * 0.91 + phase));
		float flicker = (float)Mth.clamp(1.0 + 0.20 * Math.sin(elapsed * 0.86 + localPhase)
			+ 0.10 * Math.sin(elapsed * 1.47 - localPhase), 0.65, 1.30);
		return new VertexData(offsetX + r * Mth.cos(angle), y, offsetZ + r * Mth.sin(angle), flicker);
	}

	private static void put(final PoseStack.Pose pose, final VertexConsumer buffer, final VertexData vertex,
		final float u, final float v, final double heat, final double shimmer) {
		int alpha = Mth.clamp((int)(255.0 * (0.30 + 0.27 * heat) * heat * shimmer * vertex.flicker), 0, 255);
		buffer.addVertex(pose, vertex.x, vertex.y, vertex.z).setColor(255, 250, 224, alpha)
			.setUv(u, v).setOverlay(0).setLight(0xF000F0).setNormal(pose, vertex.x, vertex.y - 0.48F, vertex.z);
	}

	private record VertexData(float x, float y, float z, float flicker) { }
}