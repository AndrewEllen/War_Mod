package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Dense neutral terrain-following puffs carried outward with the pressure front. */
public final class GroundDustFrontRenderer {
	private GroundDustFrontRenderer() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
		final List<TerrainShockfrontNode> nodes, final Vec3 impactPosition, final long gameTime,
		final WarheadMesh.Lod lod, final float densityScale, final Quaternionf cameraOrientation) {
		if (nodes == null || nodes.isEmpty()) return;
		int limit = Math.round((lod == WarheadMesh.Lod.NEAR ? 2_600
			: lod == WarheadMesh.Lod.MEDIUM ? 1_300 : 480) * Mth.clamp(densityScale, 0.25F, 3.0F));
		int count = Math.min(limit, nodes.size());
		Basis basis = Basis.from(cameraOrientation);
		for (int index = 0; index < count; index++) {
			TerrainShockfrontNode node = nodes.get(index);
			long seed = mix(node.surfaceBlock().asLong());
			long start = node.emittedGameTime() == Long.MIN_VALUE ? node.readyGameTime() : node.emittedGameTime();
			double age = Math.max(0.0, gameTime - start);
			double lifetime = 24.0 + ((seed >>> 8) & 31L);
			if (age > lifetime) continue;
			double progress = age / lifetime;
			double dx = node.position().x - impactPosition.x;
			double dz = node.position().z - impactPosition.z;
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length < 1.0E-4) continue;
			double outward = (0.8 + ((seed >>> 18) & 31L) / 12.0) * progress;
			double rise = (0.35 + ((seed >>> 27) & 31L) / 20.0) * Math.sin(progress * Math.PI * 0.82);
			Vec3 base = node.position().subtract(impactPosition)
				.add(dx / length * outward, 0.10 + rise, dz / length * outward);
			float alpha = (float) ((0.46 + ((seed >>> 12) & 7L) * 0.025)
				* Math.pow(1.0 - progress, 0.68));
			int tone = 174 + (int) ((seed >>> 35) & 47L);
			boolean hot = progress < 0.30 && ((seed >>> 44) & 31L) == 0L;
			int red = hot ? 255 : tone;
			int green = hot ? 132 + (int) ((seed >>> 49) & 31L) : Math.min(226, tone + 3);
			int blue = hot ? 38 : Math.min(232, tone + 8);
			int puffs = lod == WarheadMesh.Lod.NEAR ? 3 : lod == WarheadMesh.Lod.MEDIUM ? 2 : 1;
			for (int puff = 0; puff < puffs; puff++) {
				long puffSeed = mix(seed + puff * 0x9E3779B97F4A7C15L);
				float radius = (0.18F + unit(puffSeed, 0) * 0.34F)
					* (0.82F + (float) progress * 0.62F);
				float rotation = unit(puffSeed, 1) * Mth.TWO_PI;
				Vec3 center = base.add(signed(puffSeed, 2) * radius * 0.75,
					unit(puffSeed, 3) * radius * 0.65, signed(puffSeed, 4) * radius * 0.75);
				addBillboard(pose, buffer, center, radius, rotation, red, green, blue,
					alpha * (0.72F + unit(puffSeed, 5) * 0.28F), basis);
			}
		}
	}

	private static void addBillboard(final PoseStack.Pose pose, final VertexConsumer buffer,
		final Vec3 center, final float radius, final float rotation, final int red, final int green,
		final int blue, final float alpha, final Basis basis) {
		float cosine = Mth.cos(rotation), sine = Mth.sin(rotation);
		float ux = cosine * radius, uy = sine * radius;
		float vx = -sine * radius, vy = cosine * radius;
		int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F, red, green, blue, a, basis);
		vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F, red, green, blue, a, basis);
		vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F, red, green, blue, a, basis);
		vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F, red, green, blue, a, basis);
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
		final float x, final float y, final float u, final float v, final int red, final int green,
		final int blue, final int alpha, final Basis basis) {
		float ox = basis.right.x * x + basis.up.x * y;
		float oy = basis.right.y * x + basis.up.y * y;
		float oz = basis.right.z * x + basis.up.z * y;
		buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy, (float) center.z + oz)
			.setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0).setLight(0xB000B0)
			.setNormal(pose, basis.normal.x, basis.normal.y, basis.normal.z);
	}

	private record Basis(Vector3f right, Vector3f up, Vector3f normal) {
		private static Basis from(final Quaternionf camera) {
			return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera),
				new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera),
				new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera));
		}
	}

	private static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private static float unit(final long value, final int lane) {
		long mixed = mix(value + lane * 0x9E3779B97F4A7C15L);
		return (float) ((mixed >>> 40) * 0x1.0p-24);
	}

	private static float signed(final long value, final int lane) {
		return unit(value, lane) * 2.0F - 1.0F;
	}
}
