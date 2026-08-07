package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ShockConeMesh {
	private ShockConeMesh() {
	}

	public static void render(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final WarheadMesh.Lod lod,
		final double progress,
		final double elapsedTicks,
		final double remainingTicks,
		final Vec3 velocity,
		final long visualSeed,
		final int flightTicks
	) {
		double speed = WarheadVisualMath.normalizedSpeed(velocity, WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK * 1.65);
		double speedActivation = WarheadVisualMath.coneActivation(speed);
		double attack = WarheadVisualMath.coneAttack(elapsedTicks - flightTicks * 0.20);
		double fade = WarheadVisualMath.coneFade(remainingTicks);
		double activation = speedActivation * attack * fade;
		if (activation <= 0.001) {
			return;
		}

		double pulse = WarheadVisualMath.conePulse(elapsedTicks, visualSeed);
		float compression=(float)WarheadVisualMath.terminalConeCompression(progress);
		float length = (float) (Mth.lerp((float) WarheadVisualMath.clamp(activation, 0.0, 1.0), 3.0F, 25.0F) * pulse * Mth.lerp(compression,1.0F,.52F));
		float rearRadius = (float) (Mth.lerp((float) WarheadVisualMath.clamp(activation, 0.0, 1.0), 0.5F, 5.2F) * pulse * Mth.lerp(compression,1.0F,1.20F));
		float frontRadius = Mth.lerp(compression,.22F,.44F);
		int segments = lod == WarheadMesh.Lod.NEAR ? 16 : lod == WarheadMesh.Lod.MEDIUM ? 10 : 6;
		float alpha = (float) (0.22 * activation * (0.96 + 0.04 * pulse) * Mth.lerp(compression,1.0F,1.22F));
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
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		buffer.addVertex(pose, x, y, z)
			.setColor(232, 242, 249, alphaByte)
			.setUv(u, y * 0.045F)
			.setOverlay(0)
			.setLight(0xF000F0)
			.setNormal(pose, Mth.cos(angle), 0.20F, Mth.sin(angle));
	}
}