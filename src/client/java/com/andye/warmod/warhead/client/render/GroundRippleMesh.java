package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Terrain-following overlay mesh; UV0.x carries shader-applied vertical displacement. */
public final class GroundRippleMesh {
	private GroundRippleMesh() { }

	public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final GroundRippleRenderState state) {
		if (state == null || state.spokes() == null || state.spokes().size() < 3) return;
		double front = WarheadVisualMath.groundShockwaveDistance(state.ageTicks(), state.visualScale());
		if (front <= 0.0 || state.ageTicks() >= WarheadVisualMath.AIR_SHOCKWAVE_DURATION_TICKS) return;
		int desired = state.lod() == WarheadMesh.Lod.NEAR ? 80 : state.lod() == WarheadMesh.Lod.MEDIUM ? 48 : 24;
		int count = Math.min(desired, state.spokes().size());
		double amplitude = switch (state.lod()) { case NEAR -> 0.58; case MEDIUM -> 0.34; case FAR -> 0.16; };
		float fade = (float) Math.min(0.34, WarheadVisualMath.groundShockwaveAlpha(state.ageTicks()) * 0.52);
		for (int spokeIndex = 0; spokeIndex < count; spokeIndex++) {
			List<TerrainShockfrontNode> current = state.spokes().get(spokeIndex * state.spokes().size() / count).snapshotNodes();
			List<TerrainShockfrontNode> next = state.spokes().get(((spokeIndex + 1) % count) * state.spokes().size() / count).snapshotNodes();
			int radialCount = Math.min(current.size(), next.size());
			for (int radial = 0; radial + 1 < radialCount; radial++) {
				TerrainShockfrontNode a = current.get(radial), b = next.get(radial), c = next.get(radial + 1), d = current.get(radial + 1);
				if (!usable(a, front) || !usable(b, front) || !usable(c, front) || !usable(d, front)) continue;
				if (gap(a, b) || gap(b, c) || gap(c, d) || gap(d, a)) continue;
				vertex(pose, buffer, a, state.impactPosition(), state.ageTicks(), front, amplitude, fade);
				vertex(pose, buffer, b, state.impactPosition(), state.ageTicks(), front, amplitude, fade);
				vertex(pose, buffer, c, state.impactPosition(), state.ageTicks(), front, amplitude, fade);
				vertex(pose, buffer, d, state.impactPosition(), state.ageTicks(), front, amplitude, fade);
			}
		}
	}

	private static boolean usable(final TerrainShockfrontNode node, final double front) {
		return node != null && node.valid() && node.visibleFromImpact() && Math.abs(node.cumulativePathDistance() - front) <= 18.0;
	}

	private static boolean gap(final TerrainShockfrontNode first, final TerrainShockfrontNode second) {
		return Math.abs(first.position().y - second.position().y) > 3.5 || first.position().distanceTo(second.position()) > 20.0;
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final TerrainShockfrontNode node,
		final Vec3 center, final double age, final double front, final double amplitude, final float baseAlpha) {
		double delta = node.cumulativePathDistance() - front;
		double envelope = Math.exp(-(delta * delta) / (2.0 * 8.0 * 8.0));
		double displacement = amplitude * Math.sin(delta * 1.10 - age * 0.42) * envelope;
		int alpha = Mth.clamp((int) (baseAlpha * envelope * 255.0F), 0, 255);
		buffer.addVertex(pose, (float) (node.position().x - center.x), (float) (node.position().y - center.y + 0.035), (float) (node.position().z - center.z))
			.setColor(142, 137, 126, alpha).setUv((float) displacement, 0.0F).setOverlay(0).setLight(0xC000C0).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}