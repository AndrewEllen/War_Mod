package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadEffectMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

public final class ShockConeMesh {
	private ShockConeMesh() {
	}

	public static void render(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final WarheadMesh.Lod lod,
		final double progress,
		final double remainingTicks
	) {
		double fade = WarheadEffectMath.clamp(remainingTicks / 4.0, 0.0, 1.0);
		if (fade <= 0.0) {
			return;
		}

		double clampedProgress = WarheadEffectMath.clamp(progress, 0.0, 1.0);
		float length = (float) Mth.lerp((float) clampedProgress, 7.0F, 24.0F);
		float rearRadius = (float) Mth.lerp((float) clampedProgress, 1.2F, 4.8F);
		float frontRadius = 0.20F;
		int segments = lod == WarheadMesh.Lod.NEAR ? 16 : lod == WarheadMesh.Lod.MEDIUM ? 10 : 6;
		float alpha = (float) (Mth.lerp((float) clampedProgress, 0.07F, 0.16F) * fade);
		float rearY = -length;
		float frontY = -0.55F;

		for (int index = 0; index < segments; index++) {
			int next = (index + 1) % segments;
			float angle = Mth.TWO_PI * index / segments;
			float nextAngle = Mth.TWO_PI * next / segments;
			float rearX = rearRadius * Mth.cos(angle);
			float rearZ = rearRadius * Mth.sin(angle);
			float nextRearX = rearRadius * Mth.cos(nextAngle);
			float nextRearZ = rearRadius * Mth.sin(nextAngle);
			float frontX = frontRadius * Mth.cos(angle);
			float frontZ = frontRadius * Mth.sin(angle);
			float nextFrontX = frontRadius * Mth.cos(nextAngle);
			float nextFrontZ = frontRadius * Mth.sin(nextAngle);
			coneVertex(pose, buffer, rearX, rearY, rearZ, angle, 0.0F, alpha);
			coneVertex(pose, buffer, frontX, frontY, frontZ, angle, 1.0F, alpha);
			coneVertex(pose, buffer, nextFrontX, frontY, nextFrontZ, nextAngle, 1.0F, alpha);
			coneVertex(pose, buffer, nextRearX, rearY, nextRearZ, nextAngle, 0.0F, alpha);
		}

		renderBand(pose, buffer, segments, rearY * 0.48F, rearRadius * 0.52F, alpha * 0.30F);
		renderBand(pose, buffer, segments, rearY * 0.78F, rearRadius * 0.80F, alpha * 0.22F);
	}

	private static void renderBand(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final int segments,
		final float y,
		final float radius,
		final float alpha
	) {
		float innerRadius = Math.max(0.01F, radius - 0.10F);
		for (int index = 0; index < segments; index++) {
			int next = (index + 1) % segments;
			float angle = Mth.TWO_PI * index / segments;
			float nextAngle = Mth.TWO_PI * next / segments;
			coneVertex(pose, buffer, innerRadius * Mth.cos(angle), y, innerRadius * Mth.sin(angle), angle, 0.0F, alpha);
			coneVertex(pose, buffer, radius * Mth.cos(angle), y, radius * Mth.sin(angle), angle, 1.0F, alpha);
			coneVertex(pose, buffer, radius * Mth.cos(nextAngle), y, radius * Mth.sin(nextAngle), nextAngle, 1.0F, alpha);
			coneVertex(pose, buffer, innerRadius * Mth.cos(nextAngle), y, innerRadius * Mth.sin(nextAngle), nextAngle, 0.0F, alpha);
		}
	}

	private static void coneVertex(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final float x,
		final float y,
		final float z,
		final float angle,
		final float u,
		final float alpha
	) {
		float normalX = Mth.cos(angle);
		float normalZ = Mth.sin(angle);
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		buffer.addVertex(pose, x, y, z)
			.setColor(215, 225, 230, alphaByte)
			.setUv(u, y * 0.05F)
			.setOverlay(0)
			.setLight(0xF000F0)
			.setNormal(pose, normalX, 0.20F, normalZ);
	}
}