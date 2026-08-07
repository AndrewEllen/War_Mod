package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Three-layer translucent atmospheric pressure shell. */
public final class PressureWaveSphereRenderer {
	private PressureWaveSphereRenderer() {
	}

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double physicalRadius, final double ageTicks, final float thicknessScale,
		final float alphaScale, final WarheadMesh.Lod lod) {
		render(pose, buffer, physicalRadius, ageTicks, thicknessScale,
			alphaScale, 1.0F, lod);
	}

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double physicalRadius, final double ageTicks, final float thicknessScale,
		final float alphaScale, final float radiusScale, final WarheadMesh.Lod lod) {
		double alpha = Mth.clamp(
			WarheadVisualMath.airShockwaveAlpha(ageTicks, radiusScale) * alphaScale,
			0.0,
			1.0
		);
		if (alpha <= 0.0 || !Double.isFinite(physicalRadius)) return;
		float radius = (float) physicalRadius;
		float thickness = (float) WarheadVisualMath.airShockwaveThickness(
			ageTicks,
			thicknessScale,
			radiusScale
		);
		int longitudes = lod == WarheadMesh.Lod.NEAR ? 28
			: lod == WarheadMesh.Lod.MEDIUM ? 18 : 12;
		int latitudes = lod == WarheadMesh.Lod.NEAR ? 14
			: lod == WarheadMesh.Lod.MEDIUM ? 9 : 6;
		renderShell(pose, buffer, radius + thickness * 0.18F,
			longitudes, latitudes, 230, 244, 255, (float) alpha, 0.0F, 1.0F);
		renderShell(pose, buffer, Math.max(0.0F, radius - thickness * 1.6F),
			longitudes, latitudes, 214, 234, 250, (float) (alpha * 0.56),
			0.08F, 0.90F);
		renderShell(pose, buffer, Math.max(0.0F, radius - thickness * 3.2F),
			longitudes, latitudes, 195, 220, 242, (float) (alpha * 0.28),
			0.18F, 0.72F);
	}

	public static void renderReturn(final PoseStack.Pose pose,
		final VertexConsumer buffer, final double physicalRadius,
		final double ageTicks, final float radiusScale, final WarheadMesh.Lod lod) {
		double alpha = WarheadVisualMath.nuclearReturnWaveAlpha(ageTicks, radiusScale);
		if (alpha <= 0.0 || !Double.isFinite(physicalRadius) || physicalRadius <= 0.0) return;
		float radius = (float) physicalRadius;
		float thickness = (float) (1.6
			+ 3.8 * Math.sqrt(Math.max(0.25F, radiusScale)));
		int longitudes = lod == WarheadMesh.Lod.NEAR ? 24
			: lod == WarheadMesh.Lod.MEDIUM ? 16 : 10;
		int latitudes = lod == WarheadMesh.Lod.NEAR ? 12
			: lod == WarheadMesh.Lod.MEDIUM ? 8 : 5;
		renderShell(pose, buffer, radius + thickness * 0.20F,
			longitudes, latitudes, 210, 228, 242, (float) alpha, 0.04F, 0.90F);
		renderShell(pose, buffer, Math.max(0.0F, radius - thickness * 1.7F),
			longitudes, latitudes, 184, 208, 230, (float) (alpha * 0.46),
			0.16F, 0.70F);
	}

	private static void renderShell(final PoseStack.Pose pose,
		final VertexConsumer buffer, final float radius, final int longitudes,
		final int latitudes, final int red, final int green, final int blue,
		final float alpha, final float vOffset, final float vScale) {
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		if (alphaByte <= 0) return;
		for (int latitude = 0; latitude < latitudes; latitude++) {
			float theta0 = (float) (Math.PI * latitude / latitudes);
			float theta1 = (float) (Math.PI * (latitude + 1) / latitudes);
			for (int longitude = 0; longitude < longitudes; longitude++) {
				float phi0 = Mth.TWO_PI * longitude / longitudes;
				float phi1 = Mth.TWO_PI * (longitude + 1) / longitudes;
				vertex(pose, buffer, radius, theta0, phi0, red, green, blue,
					alphaByte, (float) longitude / longitudes,
					vOffset + vScale * latitude / latitudes);
				vertex(pose, buffer, radius, theta1, phi0, red, green, blue,
					alphaByte, (float) longitude / longitudes,
					vOffset + vScale * (latitude + 1) / latitudes);
				vertex(pose, buffer, radius, theta1, phi1, red, green, blue,
					alphaByte, (float) (longitude + 1) / longitudes,
					vOffset + vScale * (latitude + 1) / latitudes);
				vertex(pose, buffer, radius, theta0, phi1, red, green, blue,
					alphaByte, (float) (longitude + 1) / longitudes,
					vOffset + vScale * latitude / latitudes);
			}
		}
	}

	private static void vertex(final PoseStack.Pose pose,
		final VertexConsumer buffer, final float radius, final float theta,
		final float phi, final int red, final int green, final int blue,
		final int alpha, final float u, final float v) {
		float sinTheta = Mth.sin(theta);
		float nx = sinTheta * Mth.cos(phi);
		float ny = Mth.cos(theta);
		float nz = sinTheta * Mth.sin(phi);
		buffer.addVertex(pose, radius * nx, radius * ny, radius * nz)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0)
			.setNormal(pose, nx, ny, nz);
	}
}
