package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

/** Three-layer translucent atmospheric pressure shell. */
public final class PressureWaveSphereRenderer {
	private PressureWaveSphereRenderer() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final double ageTicks,
		final float visualScale, final WarheadMesh.Lod lod) {
		double alpha = WarheadVisualMath.airShockwaveAlpha(ageTicks);
		if (alpha <= 0.0) return;
		float radius = (float) WarheadVisualMath.airShockwaveRadius(ageTicks, visualScale);
		float thickness = (float) WarheadVisualMath.airShockwaveThickness(ageTicks, visualScale);
		int longitudes = lod == WarheadMesh.Lod.NEAR ? 28 : lod == WarheadMesh.Lod.MEDIUM ? 18 : 12;
		int latitudes = lod == WarheadMesh.Lod.NEAR ? 14 : lod == WarheadMesh.Lod.MEDIUM ? 9 : 6;
		renderShell(pose, buffer, radius + thickness * 0.18F, longitudes, latitudes, 230, 244, 255, (float) alpha, 0.0F, 1.0F);
		renderShell(pose, buffer, radius * 0.94F, longitudes, latitudes, 214, 234, 250, (float) (alpha * 0.56), 0.08F, 0.90F);
		renderShell(pose, buffer, radius * 0.87F, longitudes, latitudes, 195, 220, 242, (float) (alpha * 0.28), 0.18F, 0.72F);
	}

	private static void renderShell(final PoseStack.Pose pose, final VertexConsumer buffer, final float radius,
		final int longitudes, final int latitudes, final int red, final int green, final int blue,
		final float alpha, final float vOffset, final float vScale) {
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		if (alphaByte <= 0) return;
		for (int latitude = 0; latitude < latitudes; latitude++) {
			float theta0 = (float) (Math.PI * latitude / latitudes);
			float theta1 = (float) (Math.PI * (latitude + 1) / latitudes);
			for (int longitude = 0; longitude < longitudes; longitude++) {
				float phi0 = Mth.TWO_PI * longitude / longitudes;
				float phi1 = Mth.TWO_PI * (longitude + 1) / longitudes;
				vertex(pose, buffer, radius, theta0, phi0, red, green, blue, alphaByte, (float) longitude / longitudes, vOffset + vScale * latitude / latitudes);
				vertex(pose, buffer, radius, theta1, phi0, red, green, blue, alphaByte, (float) longitude / longitudes, vOffset + vScale * (latitude + 1) / latitudes);
				vertex(pose, buffer, radius, theta1, phi1, red, green, blue, alphaByte, (float) (longitude + 1) / longitudes, vOffset + vScale * (latitude + 1) / latitudes);
				vertex(pose, buffer, radius, theta0, phi1, red, green, blue, alphaByte, (float) (longitude + 1) / longitudes, vOffset + vScale * latitude / latitudes);
			}
		}
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final float radius,
		final float theta, final float phi, final int red, final int green, final int blue, final int alpha,
		final float u, final float v) {
		float sinTheta = Mth.sin(theta);
		float nx = sinTheta * Mth.cos(phi);
		float ny = Mth.cos(theta);
		float nz = sinTheta * Mth.sin(phi);
		buffer.addVertex(pose, radius * nx, radius * ny, radius * nz).setColor(red, green, blue, alpha)
			.setUv(u, v).setOverlay(0).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
	}
}