package com.andye.warmod.warhead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.andye.warmod.warhead.WarheadEffectMath;
import net.minecraft.util.Mth;

public final class ImpactFireballRenderer {
	private ImpactFireballRenderer() {
	}

	public static void render(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double ageTicks,
		final float visualScale,
		final long visualSeed,
		final WarheadMesh.Lod lod
	) {
		float scale = Mth.clamp(visualScale, 0.05F, 8.0F);
		float seedAngle = (float) ((visualSeed & 0xFFFFL) / 65536.0 * Mth.TWO_PI);

		if (ageTicks >= 0.0 && ageTicks < 4.0) {
			float t = smoothstep((float) (ageTicks / 4.0));
			float radius = scale * Mth.lerp(t, 2.0F, 8.0F);
			float alpha = Mth.lerp(t, 1.0F, 0.15F);
			addDisc(pose, buffer, radius, 0.0F, seedAngle, 255, 246, 184, alpha);
			addDisc(pose, buffer, radius * 0.82F, 0.02F, seedAngle + 0.78F, 255, 222, 120, alpha * 0.85F);
		}

		if (ageTicks >= 0.0 && ageTicks < 20.0) {
			float t = smoothstep((float) (ageTicks / 20.0));
			float radius = scale * Mth.lerp(t, 3.0F, 12.0F);
			float alpha = Mth.lerp(t, 0.90F, 0.18F);
			int green = Mth.clamp((int) Mth.lerp(t, 238.0F, 84.0F), 0, 255);
			int blue = Mth.clamp((int) Mth.lerp(t, 104.0F, 30.0F), 0, 255);
			addDisc(pose, buffer, radius, 0.0F, seedAngle + 0.20F, 255, green, blue, alpha);
			addDisc(pose, buffer, radius * 0.84F, 0.08F, seedAngle + 1.35F, 255, green, blue, alpha * 0.82F);
			addDisc(pose, buffer, radius * 0.72F, -0.08F, seedAngle + 2.35F, 255, green, blue, alpha * 0.68F);
		}

		if (ageTicks >= 12.0 && ageTicks < 90.0) {
			float t = (float) WarheadEffectMath.clamp((ageTicks - 12.0) / 78.0, 0.0, 1.0);
			float radius = scale * (float) Mth.lerp((float) smoothstep((float) t), 4.0F, 7.0F);
			float alpha = (float) (0.34 * Math.pow(1.0 - t, 0.8));
			float rise = scale * (float) Mth.lerp((float) smoothstep((float) t), 0.0F, 14.0F);
			addDisc(pose, buffer, radius, rise, seedAngle + 0.55F, 116, 119, 116, alpha);
			if (lod != WarheadMesh.Lod.FAR) {
				addDisc(pose, buffer, radius * 0.75F, rise * 0.62F, seedAngle + 1.90F, 145, 143, 136, alpha * 0.72F);
			}
		}
	}

	private static void addDisc(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final float radius,
		final float yOffset,
		final float angle,
		final int red,
		final int green,
		final int blue,
		final float alpha
	) {
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		float cos = Mth.cos(angle);
		float sin = Mth.sin(angle);
		float ux = cos * radius;
		float uy = sin * radius;
		float vx = -sin * radius;
		float vy = cos * radius;
		discVertex(pose, buffer, -ux - vx, yOffset - uy - vy, 0.0F, red, green, blue, alphaByte, 0.0F, 1.0F);
		discVertex(pose, buffer, -ux + vx, yOffset - uy + vy, 0.0F, red, green, blue, alphaByte, 0.0F, 0.0F);
		discVertex(pose, buffer, ux + vx, yOffset + uy + vy, 0.0F, red, green, blue, alphaByte, 1.0F, 0.0F);
		discVertex(pose, buffer, ux - vx, yOffset + uy - vy, 0.0F, red, green, blue, alphaByte, 1.0F, 1.0F);
	}

	private static void discVertex(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final float x,
		final float y,
		final float z,
		final int red,
		final int green,
		final int blue,
		final int alpha,
		final float u,
		final float v
	) {
		buffer.addVertex(pose, x, y, z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(0xF000F0)
			.setNormal(pose, 0.0F, 0.0F, 1.0F);
	}

	private static float smoothstep(final float value) {
		float t = Mth.clamp(value, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}
}