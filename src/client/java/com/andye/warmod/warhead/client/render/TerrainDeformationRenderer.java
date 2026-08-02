package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Extracts immutable, block-atlas terrain patches only around the travelling ground front. */
public final class TerrainDeformationRenderer {
	private static final double AHEAD_DISTANCE = 24.0;
	private static final double BEHIND_DISTANCE = 36.0;
	private TerrainDeformationRenderer() { }

	public static TerrainDeformationRenderState extract(final ClientLevel level, final Vec3 impactPosition,
		final double ageTicks, final float visualScale, final WarheadMesh.Lod lod,
		final List<TerrainShockfrontSpoke> spokes) {
		if (level == null || spokes == null || spokes.size() < 2) return new TerrainDeformationRenderState(impactPosition, ageTicks, visualScale, lod, List.of());
		double front = WarheadVisualMath.groundShockwaveDistance(ageTicks, visualScale);
		List<TerrainDeformationPatch> patches = new ArrayList<>();
		for (int spokeIndex = 0; spokeIndex < spokes.size(); spokeIndex++) {
			List<TerrainShockfrontNode> first = spokes.get(spokeIndex).snapshotNodes();
			List<TerrainShockfrontNode> second = spokes.get((spokeIndex + 1) % spokes.size()).snapshotNodes();
			int count = Math.min(first.size(), second.size()) - 1;
			for (int nodeIndex = 0; nodeIndex < count; nodeIndex++) {
				TerrainShockfrontNode n00 = first.get(nodeIndex), n01 = first.get(nodeIndex + 1);
				TerrainShockfrontNode n10 = second.get(nodeIndex), n11 = second.get(nodeIndex + 1);
				double path = (n00.cumulativePathDistance() + n01.cumulativePathDistance() + n10.cumulativePathDistance() + n11.cumulativePathDistance()) * 0.25;
				if (path < front - BEHIND_DISTANCE || path > front + AHEAD_DISTANCE) continue;
				if (!validPatch(n00, n01, n10, n11)) continue;
				BlockState state = n00.surfaceState();
				if (state.getRenderShape() == RenderShape.INVISIBLE || !state.getFluidState().isEmpty()) continue;
				TextureAtlasSprite sprite = resolveTopSprite(state, n00.surfaceBlock().asLong());
				if (sprite == null) continue;
				int light = LightCoordsUtil.getLightCoords(level, n00.surfaceBlock().above());
				patches.add(new TerrainDeformationPatch(n00.position(), n01.position(), n11.position(), n10.position(), path,
					light, n00.tintColor(), sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), n00.surfaceBlock().asLong()));
			}
		}
		return new TerrainDeformationRenderState(impactPosition, ageTicks, visualScale, lod, List.copyOf(patches));
	}

	private static boolean validPatch(final TerrainShockfrontNode... nodes) {
		for (TerrainShockfrontNode node : nodes) if (node == null || !node.valid() || !node.visibleFromImpact()) return false;
		for (int i = 0; i < nodes.length; i++) for (int j = i + 1; j < nodes.length; j++) {
			Vec3 a = nodes[i].position(), b = nodes[j].position();
			if (Math.abs(a.y - b.y) > 4.0 || a.distanceToSqr(b) > 18.0 * 18.0) return false;
		}
		return true;
	}

	private static TextureAtlasSprite resolveTopSprite(final BlockState state, final long seed) {
		BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(seed), parts);
		for (BlockStateModelPart part : parts) {
			var quads = part.getQuads(Direction.UP);
			if (!quads.isEmpty()) return quads.getFirst().materialInfo().sprite();
		}
		return model.particleMaterial().sprite();
	}
}