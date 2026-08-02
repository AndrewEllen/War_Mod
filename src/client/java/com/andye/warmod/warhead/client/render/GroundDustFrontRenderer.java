package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Persistent terrain-following dust lobes carried outward with the pressure front. */
public final class GroundDustFrontRenderer {
	private GroundDustFrontRenderer() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final List<TerrainShockfrontNode> nodes,
		final Vec3 impactPosition, final long gameTime, final WarheadMesh.Lod lod, final Quaternionf cameraOrientation) {
		if (nodes == null || nodes.isEmpty()) return;
		int limit = lod == WarheadMesh.Lod.NEAR ? 2_000 : lod == WarheadMesh.Lod.MEDIUM ? 1_000 : 400;
		for (int index = 0; index < Math.min(limit, nodes.size()); index++) {
			TerrainShockfrontNode node = nodes.get(index);
			long seed = mix(node.surfaceBlock().asLong());
			long start = node.emittedGameTime() == Long.MIN_VALUE ? node.readyGameTime() : node.emittedGameTime();
			double age = Math.max(0.0, gameTime - start);
			double lifetime = 50.0 + ((seed >>> 8) & 50L);
			if (age > lifetime) continue;
			double progress = age / lifetime;
			double dx = node.position().x - impactPosition.x;
			double dz = node.position().z - impactPosition.z;
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length < 1.0E-4) continue;
			double outward = (2.0 + ((seed >>> 18) & 31L) / 5.0) * progress;
			double rise = (2.0 + ((seed >>> 27) & 31L) / 5.2) * Math.sin(progress * Math.PI * 0.75);
			Vec3 center = node.position().subtract(impactPosition).add(dx / length * outward, rise, dz / length * outward);
			float radius = (float) ((1.5 + ((seed >>> 37) & 15L) / 5.0) * (0.75 + progress * 0.75));
			float rotation = (float) (((seed >>> 45) & 1023L) / 1024.0 * Mth.TWO_PI);
			float alpha = (float) ((0.62 + ((seed >>> 12) & 7L) * 0.025) * Math.pow(1.0 - progress, 0.62));
			int tint = node.tintColor();
			int red = (tint >>> 16) & 255;
			int green = (tint >>> 8) & 255;
			int blue = tint & 255;
			addBillboard(pose, buffer, center, radius, rotation, red, green, blue, alpha, cameraOrientation);
		}
	}

	private static void addBillboard(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float radius, final float rotation, final int red, final int green, final int blue, final float alpha, final Quaternionf cameraOrientation) {
		float cos = Mth.cos(rotation), sin = Mth.sin(rotation);
		float ux = cos * radius, uy = sin * radius, vx = -sin * radius, vy = cos * radius;
		int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		vertex(pose, buffer, center, -ux - vx, -uy - vy, 0, 1, red, green, blue, a, cameraOrientation);
		vertex(pose, buffer, center, -ux + vx, -uy + vy, 0, 0, red, green, blue, a, cameraOrientation);
		vertex(pose, buffer, center, ux + vx, uy + vy, 1, 0, red, green, blue, a, cameraOrientation);
		vertex(pose, buffer, center, ux - vx, uy - vy, 1, 1, red, green, blue, a, cameraOrientation);
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float x, final float y, final float u, final float v, final int red, final int green, final int blue,
		final int alpha, final Quaternionf cameraOrientation) {
		Vector3f offset = new Vector3f(x, y, 0.0F).rotate(cameraOrientation);
		Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F).rotate(cameraOrientation);
		buffer.addVertex(pose, (float) center.x + offset.x, (float) center.y + offset.y, (float) center.z + offset.z)
			.setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0).setLight(0xB000B0)
			.setNormal(pose, normal.x, normal.y, normal.z);
	}
	private static long mix(long value) {
		value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27; value *= 0x94D049BB133111EBL; return value ^ (value >>> 31);
	}
}