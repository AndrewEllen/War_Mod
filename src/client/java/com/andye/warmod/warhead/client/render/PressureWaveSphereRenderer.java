package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

/** Camera-relative translucent spherical pressure shell. */
public final class PressureWaveSphereRenderer {
	private PressureWaveSphereRenderer() {
	}

	public static void render(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double ageTicks,
		final float visualScale,
		final WarheadMesh.Lod lod
	) {
		double leadingAlpha = WarheadVisualMath.pressureSphereLeadingAlpha(ageTicks);
		double trailingAlpha = WarheadVisualMath.pressureSphereTrailingAlpha(ageTicks);
		if (leadingAlpha <= 0.0 && trailingAlpha <= 0.0) {
			return;
		}

		float scale = Mth.clamp(visualScale, 0.75F, 1.35F);
		float leadingRadius = (float) (WarheadVisualMath.pressureSphereRadius(ageTicks) * scale);
		float trailingRadius = leadingRadius * 0.94F;
		int longitudes = lod == WarheadMesh.Lod.NEAR ? 24 : lod == WarheadMesh.Lod.MEDIUM ? 16 : 10;
		int latitudes = lod == WarheadMesh.Lod.NEAR ? 12 : lod == WarheadMesh.Lod.MEDIUM ? 8 : 6;

		renderShell(pose, buffer, leadingRadius, longitudes, latitudes, 238, 248, 255, (float) leadingAlpha, 0.0F, 1.0F);
		renderShell(pose, buffer, trailingRadius, longitudes, latitudes, 218, 232, 248, (float) trailingAlpha, 0.12F, 0.86F);
	}

	private static void renderShell(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final float radius,
		final int longitudes,
		final int latitudes,
		final int red,
		final int green,
		final int blue,
		final float alpha,
		final float vOffset,
		final float vScale
	) {
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		if (alphaByte <= 0) {
			return;
		}

		for (int latitude = 0; latitude < latitudes; latitude++) {
			float theta0 = (float) (Math.PI * latitude / latitudes);
			float theta1 = (float) (Math.PI * (latitude + 1) / latitudes);
			for (int longitude = 0; longitude < longitudes; longitude++) {
				float phi0 = (float) (Math.PI * 2.0 * longitude / longitudes);
				float phi1 = (float) (Math.PI * 2.0 * (longitude + 1) / longitudes);
				vertex(pose, buffer, radius, theta0, phi0, red, green, blue, alphaByte, (float) longitude / longitudes, vOffset + vScale * latitude / latitudes);
				vertex(pose, buffer, radius, theta1, phi0, red, green, blue, alphaByte, (float) longitude / longitudes, vOffset + vScale * (latitude + 1) / latitudes);
				vertex(pose, buffer, radius, theta1, phi1, red, green, blue, alphaByte, (float) (longitude + 1) / longitudes, vOffset + vScale * (latitude + 1) / latitudes);
				vertex(pose, buffer, radius, theta0, phi1, red, green, blue, alphaByte, (float) (longitude + 1) / longitudes, vOffset + vScale * latitude / latitudes);
			}
		}
	}

	private static void vertex(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final float radius,
		final float theta,
		final float phi,
		final int red,
		final int green,
		final int blue,
		final int alpha,
		final float u,
		final float v
	) {
		float sinTheta = Mth.sin(theta);
		float x = radius * sinTheta * Mth.cos(phi);
		float y = radius * Mth.cos(theta);
		float z = radius * sinTheta * Mth.sin(phi);
		float normalX = sinTheta * Mth.cos(phi);
		float normalY = Mth.cos(theta);
		float normalZ = sinTheta * Mth.sin(phi);
		buffer.addVertex(pose, x, y, z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(0xF000F0)
			.setNormal(pose, normalX, normalY, normalZ);
	}
}
