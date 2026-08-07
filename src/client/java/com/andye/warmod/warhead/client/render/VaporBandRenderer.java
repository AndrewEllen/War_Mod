package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

/** Repeating, deterministic compression rings in the projectile wake. */
public final class VaporBandRenderer {
	private VaporBandRenderer() {
	}

	public static void render(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final WarheadMesh.Lod lod,
		final double elapsedTicks,
		final long visualSeed,
		final double progress,
		final float activation,
		final float fade
	) {
		double compression=WarheadVisualMath.terminalConeCompression(progress);
		int bandCount = lod == WarheadMesh.Lod.NEAR ? 4 : lod == WarheadMesh.Lod.MEDIUM ? 3 : 2;
		for (int band = 0; band < bandCount; band++) {
			double phase = WarheadVisualMath.vaporBandPhase(elapsedTicks*(1.0+.18*compression), band, bandCount, visualSeed);
			float distance = (float) (2.0 + phase * Mth.lerp((float)compression,16.0F,13.0F));
			float radius = (float) ((0.4 + phase * 4.0)*Mth.lerp((float)compression,1.0F,1.30F));
			float alpha = (float) (activation * fade * Math.sin(phase * Math.PI) * Mth.lerp((float)compression,.18F,.27F));
			renderBand(pose, buffer, distance, radius, alpha, (float) (phase * 2.0));
		}
	}

	private static void renderBand(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final float distance,
		final float radius,
		final float alpha,
		final float uOffset
	) {
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		if (alphaByte <= 0) {
			return;
		}
		int segments = 12;
		float innerRadius = Math.max(0.05F, radius - 0.22F);
		for (int index = 0; index < segments; index++) {
			int next = (index + 1) % segments;
			float angle = Mth.TWO_PI * index / segments;
			float nextAngle = Mth.TWO_PI * next / segments;
			vertex(pose, buffer, innerRadius * Mth.cos(angle), -distance, innerRadius * Mth.sin(angle), 0.0F + uOffset, 0.0F, alphaByte);
			vertex(pose, buffer, radius * Mth.cos(angle), -distance, radius * Mth.sin(angle), 0.5F + uOffset, 0.0F, alphaByte);
			vertex(pose, buffer, radius * Mth.cos(nextAngle), -distance, radius * Mth.sin(nextAngle), 0.5F + uOffset, 1.0F, alphaByte);
			vertex(pose, buffer, innerRadius * Mth.cos(nextAngle), -distance, innerRadius * Mth.sin(nextAngle), 0.0F + uOffset, 1.0F, alphaByte);
		}
	}

	private static void vertex(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final float x,
		final float y,
		final float z,
		final float u,
		final float v,
		final int alpha
	) {
		float normalX = x == 0.0F && z == 0.0F ? 0.0F : x;
		float normalZ = x == 0.0F && z == 0.0F ? 1.0F : z;
		float normalLength = Mth.sqrt(normalX * normalX + normalZ * normalZ);
		buffer.addVertex(pose, x, y, z)
			.setColor(230, 242, 250, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(0xF000F0)
			.setNormal(pose, normalX / normalLength, 0.12F, normalZ / normalLength);
	}
}
