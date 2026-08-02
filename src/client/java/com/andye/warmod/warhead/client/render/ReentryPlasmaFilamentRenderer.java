package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

/** A bounded set of deterministic luminous flows attached to the curved bow-shock surface. */
public final class ReentryPlasmaFilamentRenderer {
	private ReentryPlasmaFilamentRenderer() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final WarheadMesh.Lod lod,
		final double progress, final double heat, final double elapsed, final long seed) {
		if (heat < 0.08) return;
		int count = lod == WarheadMesh.Lod.NEAR ? 7 : lod == WarheadMesh.Lod.MEDIUM ? 4 : 2;
		double radius = Mth.lerp(WarheadVisualMath.reentryWidthProgress(progress), 0.78, 4.47);
		double depth = Mth.lerp(WarheadVisualMath.reentryElongationProgress(progress), 1.2, 3.45);
		double seedPhase = (seed & 0xffffL) / 65536.0 * Mth.TWO_PI;
		for (int filament = 0; filament < count; filament++) {
			double phase = seedPhase + filament * 2.3999632297;
			double life = 0.5 + 0.5 * Math.sin(elapsed * (0.11 + filament * 0.006) + phase * 1.7);
			double alpha = heat * Mth.clamp((life - 0.12) / 0.88, 0.0, 1.0);
			if (alpha < 0.035) continue;
			double drift = elapsed * (0.035 + filament * 0.004) + phase;
			double start = 0.18 + 0.06 * Math.sin(phase * 2.1);
			double end = Math.min(0.94, start + 0.48 + 0.10 * Math.sin(elapsed * 0.07 + phase));
			Point previous = point(start, radius, depth, drift, filament, elapsed);
			for (int step = 1; step <= 7; step++) {
				double t = Mth.lerp(step / 7.0, start, end);
				Point current = point(t, radius, depth, drift, filament, elapsed);
				quad(pose, buffer, previous, current, (float)(0.045 + 0.035 * heat),
					Mth.clamp((int)(230 * alpha * Math.sin(Math.PI * step / 8.0)), 0, 230));
				previous = current;
			}
		}
	}

	private static Point point(final double t, final double radius, final double depth, final double drift,
		final int filament, final double elapsed) {
		double radiusPath = t < 0.62 ? Mth.lerp(t / 0.62, 0.14, 1.0) : Mth.lerp((t - 0.62) / 0.38, 1.0, 0.48);
		double axialPath = t < 0.62 ? Mth.lerp(t / 0.62, 0.62, 0.03) : Mth.lerp((t - 0.62) / 0.38, 0.03, -0.52);
		double angle = drift + t * (1.45 + filament * 0.09) + 0.10 * Math.sin(elapsed * 0.28 + t * 9.0 + filament);
		double r = radius * radiusPath * (1.02 + 0.04 * Math.sin(elapsed * 0.41 + t * 13.0 + filament));
		return new Point((float)(r * Math.cos(angle)), (float)(0.55 + depth * axialPath), (float)(r * Math.sin(angle)));
	}

	private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer, final Point a, final Point b,
		final float width, final int alpha) {
		float ax = a.x * width / Math.max(0.001F, Mth.sqrt(a.x * a.x + a.z * a.z));
		float az = a.z * width / Math.max(0.001F, Mth.sqrt(a.x * a.x + a.z * a.z));
		float bx = b.x * width / Math.max(0.001F, Mth.sqrt(b.x * b.x + b.z * b.z));
		float bz = b.z * width / Math.max(0.001F, Mth.sqrt(b.x * b.x + b.z * b.z));
		put(pose, buffer, a.x - az, a.y, a.z + ax, 0.0F, 0.0F, alpha);
		put(pose, buffer, b.x - bz, b.y, b.z + bx, 0.0F, 1.0F, alpha);
		put(pose, buffer, b.x + bz, b.y, b.z - bx, 1.0F, 1.0F, alpha);
		put(pose, buffer, a.x + az, a.y, a.z - ax, 1.0F, 0.0F, alpha);
	}

	private static void put(final PoseStack.Pose pose, final VertexConsumer buffer, final float x, final float y,
		final float z, final float u, final float v, final int alpha) {
		buffer.addVertex(pose, x, y, z).setColor(232, 248, 255, alpha).setUv(u, v)
			.setOverlay(0).setLight(0xF000F0).setNormal(pose, x, 0.25F, z);
	}

	private record Point(float x, float y, float z) { }
}