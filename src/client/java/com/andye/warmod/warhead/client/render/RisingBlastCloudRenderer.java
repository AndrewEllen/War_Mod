package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Cooling lobes that lift and disperse without forming a defined mushroom cloud. */
public final class RisingBlastCloudRenderer {
	private RisingBlastCloudRenderer() {
	}

	public static void render(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double ageTicks,
		final float visualScale,
		final List<FireballLobe> lobes,
		final WarheadMesh.Lod lod
	) {
		if (!Double.isFinite(ageTicks) || ageTicks < 10.0 || ageTicks >= 65.0 || lobes == null) {
			return;
		}

		float scale = Mth.clamp(visualScale, 0.45F, 1.5F);
		int lobeLimit = lod == WarheadMesh.Lod.NEAR ? Math.min(24, lobes.size())
			: lod == WarheadMesh.Lod.MEDIUM ? Math.min(13, lobes.size()) : Math.min(6, lobes.size());
		double cloudProgress = WarheadVisualMath.clamp((ageTicks - 10.0) / 55.0, 0.0, 1.0);
		float alpha = (float) (0.38 * Math.pow(1.0 - cloudProgress, 0.68));
		for (int index = 0; index < lobeLimit; index++) {
			FireballLobe lobe = lobes.get(index);
			double localAge = ageTicks - lobe.spawnDelayTicks();
			if (localAge < 8.0) {
				continue;
			}
			Vec3 direction = horizontalDirection(lobe.baseOffset(), lobe.rotation());
			double drift = lobe.horizontalDrift() * WarheadVisualMath.clamp((localAge - 8.0) / 42.0, 0.0, 1.0);
			Vec3 center = lobe.baseOffset().scale(scale)
				.add(direction.scale(drift))
				.add(0.0, WarheadVisualMath.fireballRise(localAge) * lobe.riseSpeed() / 16.0, 0.0);
			float radius = (float) (scale * lobe.baseRadius() * (1.1 + cloudProgress * 0.65));
			float lobeAlpha = alpha * (0.62F + 0.38F * (1.0F - index / (float) Math.max(1, lobeLimit)));
			addBillboard(pose, buffer, center, radius, lobe.rotation(), 128, 132, 128, lobeAlpha);
		}
	}

	private static Vec3 horizontalDirection(final Vec3 offset, final double rotation) {
		double length = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
		return length < 1.0E-5
			? new Vec3(Math.cos(rotation), 0.0, Math.sin(rotation))
			: new Vec3(offset.x / length, 0.0, offset.z / length);
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
			.setLight(0x900090)
			.setNormal(pose, 0.0F, 0.0F, 1.0F);
	}
}
