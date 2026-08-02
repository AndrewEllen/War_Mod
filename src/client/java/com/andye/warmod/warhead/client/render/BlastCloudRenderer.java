package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** One coherent, deterministic, dense rising blast cloud. */
public final class BlastCloudRenderer {
	private BlastCloudRenderer() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final double ageTicks,
		final float visualScale, final List<BlastCloudLobe> lobes, final WarheadMesh.Lod lod) {
		if (!Double.isFinite(ageTicks) || ageTicks < 20.0 || ageTicks >= 260.0 || lobes == null || lobes.isEmpty()) return;
		int limit = lod == WarheadMesh.Lod.NEAR ? Math.min(64, lobes.size()) : lod == WarheadMesh.Lod.MEDIUM ? Math.min(38, lobes.size()) : Math.min(18, lobes.size());
		float scale = Mth.clamp(visualScale, 0.55F, 1.45F);
		double develop = smooth((ageTicks - 20.0) / 50.0);
		double rise = smooth((ageTicks - 35.0) / 165.0);
		double dissipate = WarheadVisualMath.clamp((ageTicks - 190.0) / 70.0, 0.0, 1.0);
		double fade = Math.pow(1.0 - dissipate, 0.72);
		for (int index = 0; index < limit; index++) {
			BlastCloudLobe lobe = lobes.get(index);
			Vec3 center = center(lobe, ageTicks, scale);
			double roll = 1.0 + 0.10 * Math.sin(lobe.phase() + ageTicks * 0.035);
			double separation = 1.0 + dissipate * (lobe.upperCap() ? 0.55 : 0.30);
			center = new Vec3(center.x * separation, center.y, center.z * separation);
			float radius = (float) (lobe.baseRadius() * scale * (0.62 + develop * (lobe.upperCap() ? 1.18 : 0.82)) * roll);
			float alpha = (float) (lobe.opacity() * (0.68 + 0.32 * develop) * fade);
			addBillboard(pose, buffer, center, radius, lobe.rotation() + ageTicks * 0.0025, lobe.red(), lobe.green(), lobe.blue(), alpha);
		}
	}

	public static Vec3 center(final BlastCloudLobe lobe, final double ageTicks, final float scale) {
		double rise = smooth((ageTicks - 35.0) / 165.0);
		double horizontalLength = Math.sqrt(lobe.baseOffset().x * lobe.baseOffset().x + lobe.baseOffset().z * lobe.baseOffset().z);
		double dx = horizontalLength < 1.0E-4 ? Math.cos(lobe.rotation()) : lobe.baseOffset().x / horizontalLength;
		double dz = horizontalLength < 1.0E-4 ? Math.sin(lobe.rotation()) : lobe.baseOffset().z / horizontalLength;
		double outward = lobe.outwardDrift() * rise;
		double lift = rise * (lobe.upperCap() ? 41.0 : 32.0) * lobe.riseFactor();
		return lobe.baseOffset().scale(scale).add(dx * outward, lift, dz * outward);
	}

	private static void addBillboard(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float radius, final double rotation, final int red, final int green, final int blue, final float alpha) {
		float cos = Mth.cos((float) rotation), sin = Mth.sin((float) rotation);
		float ux = cos * radius, uy = sin * radius, vx = -sin * radius, vy = cos * radius;
		int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		vertex(pose, buffer, center, -ux - vx, -uy - vy, 0, 1, red, green, blue, a);
		vertex(pose, buffer, center, -ux + vx, -uy + vy, 0, 0, red, green, blue, a);
		vertex(pose, buffer, center, ux + vx, uy + vy, 1, 0, red, green, blue, a);
		vertex(pose, buffer, center, ux - vx, uy - vy, 1, 1, red, green, blue, a);
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float x, final float y, final float u, final float v, final int red, final int green, final int blue, final int alpha) {
		buffer.addVertex(pose, (float) center.x + x, (float) center.y + y, (float) center.z).setColor(red, green, blue, alpha)
			.setUv(u, v).setOverlay(0).setLight(0xA000A0).setNormal(pose, 0.0F, 0.0F, 1.0F);
	}

	private static double smooth(final double value) {
		double t = WarheadVisualMath.clamp(value, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}