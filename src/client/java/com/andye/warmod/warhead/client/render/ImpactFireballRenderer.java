package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Deterministic three-dimensional fireball lobes using an eight-frame atlas. */
public final class ImpactFireballRenderer {
	private ImpactFireballRenderer() {
	}

	public static void renderHot(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double ageTicks,
		final float visualScale,
		final List<FireballLobe> lobes,
		final WarheadMesh.Lod lod
	) {
		renderLobes(pose, buffer, ageTicks, visualScale, lobes, lod, true);
	}

	public static void renderCooling(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double ageTicks,
		final float visualScale,
		final List<FireballLobe> lobes,
		final WarheadMesh.Lod lod
	) {
		renderLobes(pose, buffer, ageTicks, visualScale, lobes, lod, false);
	}

	private static void renderLobes(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double ageTicks,
		final float visualScale,
		final List<FireballLobe> lobes,
		final WarheadMesh.Lod lod,
		final boolean hot
	) {
		if (!Double.isFinite(ageTicks) || lobes == null || lobes.isEmpty()) {
			return;
		}

		float scale = Mth.clamp(visualScale, 0.45F, 1.5F);
		int lobeLimit = lod == WarheadMesh.Lod.NEAR ? Math.min(48, lobes.size())
			: lod == WarheadMesh.Lod.MEDIUM ? Math.min(28, lobes.size()) : Math.min(14, lobes.size());
		for (int index = 0; index < lobeLimit; index++) {
			FireballLobe lobe = lobes.get(index);
			double localAge = ageTicks - lobe.spawnDelayTicks();
			if (localAge < 0.0 || localAge >= 75.0 || (hot && localAge > 40.0) || (!hot && localAge < 12.0)) {
				continue;
			}

			double growth = WarheadVisualMath.clamp(localAge / 24.0, 0.0, 1.0);
			double cooling = WarheadVisualMath.clamp((localAge - 30.0) / 45.0, 0.0, 1.0);
			double alpha = WarheadVisualMath.fireballAlpha(localAge) * (0.48 + 0.52 * (1.0 - index / (double) Math.max(1, lobeLimit)));
			if (hot) {
				alpha *= 0.88 + 0.12 * (1.0 - cooling);
			} else {
				alpha *= 0.86 * Math.pow(1.0 - cooling, 0.45);
			}
			if (alpha <= 0.0) {
				continue;
			}

			float radius = (float) (scale * lobe.baseRadius() * (0.62 + 0.58 * easeOut(growth)));
			Vec3 driftDirection = horizontalDirection(lobe.baseOffset(), lobe.rotation());
			double drift = lobe.horizontalDrift() * easeOut(growth);
			double rise = lobe.riseSpeed() * WarheadVisualMath.fireballRise(localAge) / 16.0;
			Vec3 center = lobe.baseOffset().scale(scale).add(driftDirection.scale(drift)).add(0.0, rise, 0.0);
			int frame = frameFor(localAge, lobe.animationOffset(), hot);
			float frameU0 = frame / 8.0F;
			float frameU1 = (frame + 1) / 8.0F;
			int red = hot ? 255 : Mth.lerpInt((float) cooling, 244, 132);
			int green = hot ? Mth.lerpInt((float) cooling, 244, 104) : Mth.lerpInt((float) cooling, 148, 92);
			int blue = hot ? Mth.lerpInt((float) cooling, 170, 34) : Mth.lerpInt((float) cooling, 52, 30);
			int light = hot ? 0xF000F0 : 0xA000A0;
			addBillboard(pose, buffer, center, radius, lobe.rotation(), frameU0, frameU1, red, green, blue, alpha, light);
		}
	}

	private static int frameFor(final double localAge, final int animationOffset, final boolean hot) {
		double duration = hot ? 40.0 : 63.0;
		int frame = (int) Math.floor(localAge / duration * 8.0) + animationOffset;
		return Math.floorMod(frame, 8);
	}

	private static Vec3 horizontalDirection(final Vec3 offset, final double rotation) {
		double x = offset.x;
		double z = offset.z;
		double length = Math.sqrt(x * x + z * z);
		if (length < 1.0E-5) {
			return new Vec3(Math.cos(rotation), 0.0, Math.sin(rotation));
		}
		return new Vec3(x / length, 0.0, z / length);
	}

	private static void addBillboard(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final Vec3 center,
		final float radius,
		final double rotation,
		final float u0,
		final float u1,
		final int red,
		final int green,
		final int blue,
		final double alpha,
		final int light
	) {
		float cos = Mth.cos((float) rotation);
		float sin = Mth.sin((float) rotation);
		float ux = cos * radius;
		float uy = sin * radius;
		float vx = -sin * radius;
		float vy = cos * radius;
		int alphaByte = Mth.clamp((int) (alpha * 255.0), 0, 255);
		vertex(pose, buffer, center, -ux - vx, -uy - vy, u0, 1.0F, red, green, blue, alphaByte, light);
		vertex(pose, buffer, center, -ux + vx, -uy + vy, u0, 0.0F, red, green, blue, alphaByte, light);
		vertex(pose, buffer, center, ux + vx, uy + vy, u1, 0.0F, red, green, blue, alphaByte, light);
		vertex(pose, buffer, center, ux - vx, uy - vy, u1, 1.0F, red, green, blue, alphaByte, light);
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
		final int alpha,
		final int light
	) {
		buffer.addVertex(pose, (float) center.x + x, (float) center.y + y, (float) center.z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(light)
			.setNormal(pose, 0.0F, 0.0F, 1.0F);
	}

	private static double easeOut(final double value) {
		double t = WarheadVisualMath.clamp(value, 0.0, 1.0);
		return 1.0 - (1.0 - t) * (1.0 - t);
	}
}
