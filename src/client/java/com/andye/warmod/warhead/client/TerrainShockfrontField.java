package com.andye.warmod.warhead.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bounded, terrain-aware radial paths for the ground pressure front.
 *
 * <p>The field is deliberately built in small batches. It never asks the client
 * chunk source to load a chunk and it stops a spoke when terrain or occlusion
 * makes the path unreliable.
 */
public final class TerrainShockfrontField {
	public static final int MAX_HORIZONTAL_RANGE = 512;
	public static final int SAMPLE_SPACING = 2;
	public static final int MAX_SPOKES = 256;

	private final Vec3 impactPosition;
	private final long visualSeed;
	private final List<TerrainShockfrontSpoke> spokes;
	private int nextSpokeToBuild;

	public TerrainShockfrontField(final Vec3 impactPosition, final long visualSeed) {
		this.impactPosition = impactPosition;
		this.visualSeed = visualSeed;
		double phase = (visualSeed & 0xFFFFL) / 65536.0 * Math.PI * 2.0 / MAX_SPOKES;
		List<TerrainShockfrontSpoke> generated = new ArrayList<>(MAX_SPOKES);
		for (int index = 0; index < MAX_SPOKES; index++) {
			generated.add(new TerrainShockfrontSpoke(phase + Math.PI * 2.0 * index / MAX_SPOKES));
		}
		this.spokes = List.copyOf(generated);
	}

	public Vec3 impactPosition() {
		return this.impactPosition;
	}

	public long visualSeed() {
		return this.visualSeed;
	}

	/** Builds at most {@code maximumNodes} new terrain samples. */
	public synchronized int build(final ClientLevel level, final int maximumNodes) {
		if (level == null || maximumNodes <= 0) return 0;
		int built = 0;
		int consecutiveComplete = 0;
		while (built < maximumNodes && consecutiveComplete < this.spokes.size()) {
			TerrainShockfrontSpoke spoke = this.spokes.get(this.nextSpokeToBuild);
			this.nextSpokeToBuild = (this.nextSpokeToBuild + 1) % this.spokes.size();
			if (spoke.complete()) { consecutiveComplete++; continue; }
			consecutiveComplete = 0;
			int sampleIndex = spoke.nextSampleIndex();
			if (sampleIndex > MAX_HORIZONTAL_RANGE / SAMPLE_SPACING) { spoke.markComplete(); continue; }
			this.buildOneSample(level, spoke, sampleIndex);
			spoke.advanceSampleIndex();
			built++;
		}
		return built;
	}

	public List<TerrainShockfrontSpoke> snapshotSpokes() {
		return this.spokes;
	}

	public List<TerrainShockfrontNode> readyNodes(
		final double pressureRadius,
		final int desiredSpokes,
		final int maximumNodes,
		final long gameTime
	) {
		if (!Double.isFinite(pressureRadius) || pressureRadius <= 0.0 || maximumNodes <= 0) return List.of();
		int count = Math.max(1, Math.min(this.spokes.size(), desiredSpokes));
		List<List<TerrainShockfrontNode>> perSpoke = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			TerrainShockfrontSpoke spoke = this.spokes.get(index * this.spokes.size() / count);
			spoke.updateReached(pressureRadius, gameTime);
			perSpoke.add(spoke.readyNodesNearFrontier(pressureRadius, 8));
		}
		List<TerrainShockfrontNode> selected = new ArrayList<>(Math.min(maximumNodes, count * 8));
		for (int layer = 0; layer < 8 && selected.size() < maximumNodes; layer++) {
			for (List<TerrainShockfrontNode> spokeNodes : perSpoke) {
				if (selected.size() >= maximumNodes) break;
				if (layer < spokeNodes.size()) selected.add(spokeNodes.get(layer));
			}
		}
		return List.copyOf(selected);
	}
	public List<TerrainShockfrontNode> activeDustNodes(
		final double pressureRadius,
		final int desiredSpokes,
		final int maximumNodes,
		final long gameTime
	) {
		this.readyNodes(pressureRadius, desiredSpokes, maximumNodes, gameTime);
		int count = Math.max(1, Math.min(this.spokes.size(), desiredSpokes));
		List<List<TerrainShockfrontNode>> perSpoke = new ArrayList<>(count);
		int maximumDepth = 0;
		for (int index = 0; index < count; index++) {
			TerrainShockfrontSpoke spoke = this.spokes.get(index * this.spokes.size() / count);
			List<TerrainShockfrontNode> candidates = new ArrayList<>();
			for (TerrainShockfrontNode node : spoke.snapshotNodes()) {
				boolean recent = node.state() == TerrainShockfrontNode.State.EMITTED && gameTime - node.emittedGameTime() <= 100L;
				boolean ready = node.state() == TerrainShockfrontNode.State.READY && pressureRadius - node.cumulativePathDistance() <= 36.0;
				if (recent || ready) candidates.add(node);
			}
			candidates.sort((first, second) -> Double.compare(second.cumulativePathDistance(), first.cumulativePathDistance()));
			perSpoke.add(List.copyOf(candidates));
			maximumDepth = Math.max(maximumDepth, candidates.size());
		}
		List<TerrainShockfrontNode> active = new ArrayList<>(maximumNodes);
		for (int layer = 0; layer < maximumDepth && active.size() < maximumNodes; layer++) {
			for (List<TerrainShockfrontNode> spokeNodes : perSpoke) {
				if (active.size() >= maximumNodes) break;
				if (layer < spokeNodes.size()) active.add(spokeNodes.get(layer));
			}
		}
		return List.copyOf(active);
	}
	public void markEmitted(final TerrainShockfrontNode node, final long gameTime) {
		if (node != null) node.markEmitted(gameTime);
	}

	private void buildOneSample(final ClientLevel level, final TerrainShockfrontSpoke spoke, final int sampleIndex) {
		double horizontalDistance = sampleIndex * SAMPLE_SPACING;
		double x = this.impactPosition.x + Math.cos(spoke.angle()) * horizontalDistance;
		double z = this.impactPosition.z + Math.sin(spoke.angle()) * horizontalDistance;
		int blockX = (int) Math.floor(x);
		int blockZ = (int) Math.floor(z);
		ChunkAccess chunk = level.getChunkSource().getChunkNow(
			SectionPos.blockToSectionCoord(blockX),
			SectionPos.blockToSectionCoord(blockZ)
		);
		if (chunk == null) {
			spoke.markComplete();
			return;
		}

		int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) - 1;
		BlockPos surfaceBlock = new BlockPos(blockX, surfaceY, blockZ);
		BlockState surfaceState = level.getBlockState(surfaceBlock);
		if (surfaceState.isAir() || surfaceState.getCollisionShape(level, surfaceBlock).isEmpty()) {
			spoke.markComplete();
			return;
		}

		Vec3 position = new Vec3(x, surfaceY + 1.08, z);
		TerrainShockfrontNode previous = spoke.previousNode();
		Vec3 previousPosition = previous == null ? this.impactPosition.add(0.0, 1.0, 0.0) : previous.position();
		double verticalChange = Math.abs(position.y - previousPosition.y);
		if (verticalChange > 6.0) {
			spoke.markComplete();
			return;
		}

		double stepDistance = previous == null
			? Math.sqrt(horizontalDistance * horizontalDistance + Math.pow(position.y - this.impactPosition.y, 2.0))
			: previousPosition.distanceTo(position);
		double cumulativeDistance = (previous == null ? 0.0 : previous.cumulativePathDistance()) + stepDistance;
		boolean visible = isVisibleFromImpact(level, this.impactPosition.add(0.0, 1.0, 0.0), position.add(0.0, 1.0, 0.0));
		spoke.addNode(new TerrainShockfrontNode(
			position,
			surfaceBlock,
			surfaceState,
			cumulativeDistance,
			this.impactPosition.distanceTo(position),
			visible,
			surfaceState.getMapColor(level, surfaceBlock).col
		));
		if (!visible) {
			spoke.markComplete();
		}
	}

	private static boolean isVisibleFromImpact(final ClientLevel level, final Vec3 from, final Vec3 to) {
		AtomicBoolean missingChunk = new AtomicBoolean(false);
		ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
		Optional<BlockHitResult> hit = BlockGetter.traverseBlocks(
			from,
			to,
			context,
			(clipContext, pos) -> {
				if (!isLoaded(level, pos.getX(), pos.getZ())) {
					missingChunk.set(true);
					return Optional.empty();
				}
				BlockState state = level.getBlockState(pos);
				VoxelShape shape = clipContext.getBlockShape(state, level, pos);
				BlockHitResult blockHit = level.clipWithInteractionOverride(from, to, pos, shape, state);
				return blockHit == null ? null : Optional.of(blockHit);
			},
			ignored -> Optional.empty()
		);
		return !missingChunk.get() && (hit == null || hit.isEmpty());
	}

	private static boolean isLoaded(final ClientLevel level, final int blockX, final int blockZ) {
		return level.getChunkSource().getChunkNow(
			SectionPos.blockToSectionCoord(blockX),
			SectionPos.blockToSectionCoord(blockZ)
		) != null;
	}
}
