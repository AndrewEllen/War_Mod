package com.andye.warmod.warhead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class TerrainDeformationMesh {
	private TerrainDeformationMesh() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final TerrainDeformationRenderState state) {
		if (state == null || state.patches().isEmpty()) return;
		for (TerrainDeformationPatch patch : state.patches()) renderPatch(pose, buffer, state, patch);
	}

	private static void renderPatch(final PoseStack.Pose pose, final VertexConsumer buffer,
		final TerrainDeformationRenderState state, final TerrainDeformationPatch patch) {
		double delta = patch.cumulativePathDistance() - com.andye.warmod.warhead.WarheadVisualMath.groundShockwaveDistance(state.ageTicks());
		double envelope = Math.exp(-(delta * delta) / (2.0 * 11.0 * 11.0));
		double amplitude = amplitude(state.lod(), patch.seed()) * state.visualScale();
		double displacement = amplitude * Math.sin(delta * 1.05 - state.ageTicks() * 0.45) * envelope;
		double followingDelta = delta + 9.0;
		displacement += amplitude * 0.42 * Math.sin(followingDelta * 1.42 - state.ageTicks() * 0.48)
			* Math.exp(-(followingDelta * followingDelta) / (2.0 * 8.0 * 8.0));
		double trailingDelta = delta + 19.0;
		displacement += amplitude * 0.18 * Math.sin(trailingDelta * 1.78 - state.ageTicks() * 0.52)
			* Math.exp(-(trailingDelta * trailingDelta) / (2.0 * 6.0 * 6.0));
		Vec3 origin = state.impactPosition();
		Vec3[] base = { patch.corner0().subtract(origin), patch.corner1().subtract(origin), patch.corner2().subtract(origin), patch.corner3().subtract(origin) };
		Vec3[] top = { base[0].add(0.0, displacement, 0.0), base[1].add(0.0, displacement, 0.0), base[2].add(0.0, displacement, 0.0), base[3].add(0.0, displacement, 0.0) };
		int tint = patch.tintColor();
		int red = (tint >>> 16) & 255, green = (tint >>> 8) & 255, blue = tint & 255;
		vertex(pose, buffer, top[0], patch.u0(), patch.v0(), red, green, blue, patch.packedLight());
		vertex(pose, buffer, top[1], patch.u0(), patch.v1(), red, green, blue, patch.packedLight());
		vertex(pose, buffer, top[2], patch.u1(), patch.v1(), red, green, blue, patch.packedLight());
		vertex(pose, buffer, top[3], patch.u1(), patch.v0(), red, green, blue, patch.packedLight());
		if (Math.abs(displacement) > 0.03) for (int edge = 0; edge < 4; edge++) {
			int next = (edge + 1) & 3;
			vertex(pose, buffer, base[edge], patch.u0(), patch.v1(), red, green, blue, patch.packedLight());
			vertex(pose, buffer, base[next], patch.u1(), patch.v1(), red, green, blue, patch.packedLight());
			vertex(pose, buffer, top[next], patch.u1(), patch.v0(), red, green, blue, patch.packedLight());
			vertex(pose, buffer, top[edge], patch.u0(), patch.v0(), red, green, blue, patch.packedLight());
		}
	}

	private static double amplitude(final WarheadMesh.Lod lod, final long seed) {
		double unit = (mix(seed) & 0xFFFFL) / 65535.0;
		return lod == WarheadMesh.Lod.NEAR ? Mth.lerp(unit, 1.25, 1.75)
			: lod == WarheadMesh.Lod.MEDIUM ? Mth.lerp(unit, 0.75, 1.10) : Mth.lerp(unit, 0.30, 0.55);
	}
	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 point,
		final float u, final float v, final int red, final int green, final int blue, final int light) {
		buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(red, green, blue, 255)
			.setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
	private static long mix(long value) { value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27; value *= 0x94D049BB133111EBL; return value ^ (value >>> 31); }
}
