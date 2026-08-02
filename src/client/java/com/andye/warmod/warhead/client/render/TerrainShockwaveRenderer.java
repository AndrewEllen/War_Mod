package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.TerrainRingSampler.RingSample;
import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class TerrainShockwaveRenderer {
	private TerrainShockwaveRenderer() {
	}

	public static void render(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final List<RingSample> samples,
		final Vec3 center,
		final float width,
		final float alpha,
		final int red,
		final int green,
		final int blue
	) {
		if (samples == null || samples.size() < 3 || width <= 0.0F || alpha <= 0.0F) {
			return;
		}

		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		for (int index = 0; index < samples.size(); index++) {
			RingSample current = samples.get(index);
			RingSample next = samples.get((index + 1) % samples.size());
			if (!current.valid() || !next.valid() || next.breakBefore()) {
				continue;
			}

			double currentDx = current.x() - center.x;
			double currentDz = current.z() - center.z;
			double nextDx = next.x() - center.x;
			double nextDz = next.z() - center.z;
			double currentLength = Math.sqrt(currentDx * currentDx + currentDz * currentDz);
			double nextLength = Math.sqrt(nextDx * nextDx + nextDz * nextDz);
			if (currentLength < 0.001 || nextLength < 0.001) {
				continue;
			}

			double currentOffsetX = currentDx / currentLength * width * 0.5;
			double currentOffsetZ = currentDz / currentLength * width * 0.5;
			double nextOffsetX = nextDx / nextLength * width * 0.5;
			double nextOffsetZ = nextDz / nextLength * width * 0.5;
			ribbonVertex(pose, buffer, current.x() - currentOffsetX, current.y(), current.z() - currentOffsetZ, center, red, green, blue, alphaByte, (float) index / samples.size(), 0.0F);
			ribbonVertex(pose, buffer, current.x() + currentOffsetX, current.y(), current.z() + currentOffsetZ, center, red, green, blue, alphaByte, (float) index / samples.size(), 1.0F);
			ribbonVertex(pose, buffer, next.x() + nextOffsetX, next.y(), next.z() + nextOffsetZ, center, red, green, blue, alphaByte, (float) (index + 1) / samples.size(), 1.0F);
			ribbonVertex(pose, buffer, next.x() - nextOffsetX, next.y(), next.z() - nextOffsetZ, center, red, green, blue, alphaByte, (float) (index + 1) / samples.size(), 0.0F);
		}
	}

	/** Renders the active terrain frontier using the sampled surface paths. */
	public static void renderFrontier(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final List<TerrainShockfrontSpoke> spokes,
		final Vec3 center,
		final double pressureRadius,
		final int desiredSpokes,
		final float width,
		final float alpha,
		final int red,
		final int green,
		final int blue
	) {
		if (spokes == null || spokes.size() < 3 || desiredSpokes < 3 || width <= 0.0F || alpha <= 0.0F) {
			return;
		}
		int count = Math.min(desiredSpokes, spokes.size());
		int alphaByte = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		for (int index = 0; index < count; index++) {
			TerrainShockfrontSpoke currentSpoke = spokes.get(index * spokes.size() / count);
			TerrainShockfrontSpoke nextSpoke = spokes.get(((index + 1) % count) * spokes.size() / count);
			TerrainShockfrontNode current = currentSpoke.frontier(pressureRadius);
			TerrainShockfrontNode next = nextSpoke.frontier(pressureRadius);
			if (current == null || next == null) {
				continue;
			}

			double currentDx = current.position().x - center.x;
			double currentDz = current.position().z - center.z;
			double nextDx = next.position().x - center.x;
			double nextDz = next.position().z - center.z;
			double currentLength = Math.sqrt(currentDx * currentDx + currentDz * currentDz);
			double nextLength = Math.sqrt(nextDx * nextDx + nextDz * nextDz);
			if (currentLength < 0.001 || nextLength < 0.001) {
				continue;
			}
			double currentOffsetX = currentDx / currentLength * width * 0.5;
			double currentOffsetZ = currentDz / currentLength * width * 0.5;
			double nextOffsetX = nextDx / nextLength * width * 0.5;
			double nextOffsetZ = nextDz / nextLength * width * 0.5;
			ribbonVertex(pose, buffer, current.position().x - currentOffsetX, current.position().y, current.position().z - currentOffsetZ, center, red, green, blue, alphaByte, (float) index / count, 0.0F);
			ribbonVertex(pose, buffer, current.position().x + currentOffsetX, current.position().y, current.position().z + currentOffsetZ, center, red, green, blue, alphaByte, (float) index / count, 1.0F);
			ribbonVertex(pose, buffer, next.position().x + nextOffsetX, next.position().y, next.position().z + nextOffsetZ, center, red, green, blue, alphaByte, (float) (index + 1) / count, 1.0F);
			ribbonVertex(pose, buffer, next.position().x - nextOffsetX, next.position().y, next.position().z - nextOffsetZ, center, red, green, blue, alphaByte, (float) (index + 1) / count, 0.0F);
		}
	}

	private static void ribbonVertex(
		final PoseStack.Pose pose,
		final VertexConsumer buffer,
		final double x,
		final double y,
		final double z,
		final Vec3 center,
		final int red,
		final int green,
		final int blue,
		final int alpha,
		final float u,
		final float v
	) {
		buffer.addVertex(pose, (float) (x - center.x), (float) (y - center.y), (float) (z - center.z))
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(0xF000F0)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}