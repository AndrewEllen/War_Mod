package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A tall, dark, fading smoke column layered behind the initial fireball. */
public final class AftermathSmokeColumnRenderer {
	private AftermathSmokeColumnRenderer() {
	}

	public static void render(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double ageTicks,
		final float visualScale,
		final List<FireballLobe> lobes,
		final WarheadMesh.Lod lod
	) {
		if (!Double.isFinite(ageTicks) || ageTicks < 12.0 || ageTicks >= 140.0 || lobes == null || lobes.isEmpty()) {
			return;
		}

		float scale = Mth.clamp(visualScale, 0.45F, 1.5F);
		double progress = WarheadVisualMath.clamp((ageTicks - 12.0) / 128.0, 0.0, 1.0);
		int lobeCount = lod == WarheadMesh.Lod.NEAR ? 14 : lod == WarheadMesh.Lod.MEDIUM ? 9 : 5;
		double columnHeight = 52.0 * scale;
		float baseAlpha = (float) (0.34 * Math.pow(1.0 - progress, 0.62));

		for (int index = 0; index < lobeCount; index++) {
			FireballLobe lobe = lobes.get((index * 3 + 2) % lobes.size());
			double layer = (index + 0.5) / lobeCount;
			double angle = lobe.rotation() + ageTicks * 0.004 * (index % 2 == 0 ? 1.0 : -1.0);
			double radius = 1.0 + layer * (2.5 + progress * 3.5) + Math.sin(ageTicks * 0.045 + index * 1.7) * 0.65;
			double y = 2.0 + layer * (5.0 + columnHeight * progress) + Math.sin(ageTicks * 0.06 + lobe.rotation()) * 0.7;
			Vec3 center = new Vec3(
				Math.cos(angle) * radius + lobe.baseOffset().x * 0.16,
				y,
				Math.sin(angle) * radius + lobe.baseOffset().z * 0.16
			);
			float lobeRadius = (float) (scale * (2.0 + progress * 3.8) * (0.78 + layer * 0.24));
			float alpha = baseAlpha * (0.92F - (float) layer * 0.30F);
			int gray = Mth.clamp((int) (42.0 + layer * 24.0 + progress * 8.0), 24, 86);
			addBillboard(pose, buffer, center, lobeRadius, lobe.rotation(), gray, gray + 2, gray + 5, alpha);
		}
	}

	private static void addBillboard(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final Vec3 center,
		final float radius,
		final double rotation,
		final int red,
		final int green,
		final int blue,
		final float alpha
	) {
		float cos = Mth.cos((float) rotation);
		float sin = Mth.sin((float) rotation);
		float ux = cos * radius;
		float uy = sin * radius;
		float vx = -sin * radius;
		float vy = cos * radius;
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F, red, green, blue, alphaByte);
		vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F, red, green, blue, alphaByte);
		vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F, red, green, blue, alphaByte);
		vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F, red, green, blue, alphaByte);
	}

	private static void vertex(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final Vec3 center,
		final float x,
		final float y,
		final float u,
		final float v,
		final int red,
		final int green,
		final int blue,
		final int alpha
	) {
		buffer.addVertex(pose, (float) center.x + x, (float) center.y + y, (float) center.z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(0x700070)
			.setNormal(pose, 0.0F, 0.0F, 1.0F);
	}
}
