package com.andye.warmod.warhead.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Bounded terrain-aware radial paths for the ground pressure front. */
public final class TerrainShockfrontField {
	public static final int MAX_HORIZONTAL_RANGE = 512;
	public static final int SAMPLE_SPACING = 2;
	public static final int MAX_SPOKES = 256;
	/* Keep the terrain path comfortably ahead of a 17.15 block/tick pressure
	 * front.  The old 2,048-node ceiling could only extend eight samples per
	 * spoke (16 blocks), which left intermittent holes at full shockwave speed. */
	private static final int MAX_BUILD_PER_CALL = 8_192;
	/* Two-block samples may legitimately climb steep hills and stepped terrain. */
	private static final double MAX_VERTICAL_STEP = 18.0;

	private final Vec3 impactPosition;
	private final long visualSeed;
	private final List<TerrainShockfrontSpoke> spokes;
	private final int[] buildOrder = new int[MAX_SPOKES];
	private int nextSpokeToBuild;

	public TerrainShockfrontField(final Vec3 impactPosition, final long visualSeed) {
		this.impactPosition = impactPosition;
		this.visualSeed = visualSeed;
		double phase = (visualSeed & 0xFFFFL) / 65536.0 * Math.PI * 2.0 / MAX_SPOKES;
		List<TerrainShockfrontSpoke> generated = new ArrayList<>(MAX_SPOKES);
		for (int index = 0; index < MAX_SPOKES; index++) {
			generated.add(new TerrainShockfrontSpoke(
				phase + Math.PI * 2.0 * index / MAX_SPOKES));
			/* Bit reversal spreads every early refinement pass around the full
			 * circle instead of building one contiguous angular wedge first. */
			this.buildOrder[index] = Integer.reverse(index) >>> 24;
		}
		this.spokes = List.copyOf(generated);
	}

	public Vec3 impactPosition() { return this.impactPosition; }
	public long visualSeed() { return this.visualSeed; }

	public synchronized int build(final ClientLevel level, final int maximumNodes) {
		return this.buildToDistance(level, MAX_HORIZONTAL_RANGE, maximumNodes);
	}

	public synchronized int buildToDistance(final ClientLevel level,
		final double requiredDistance, final int maximumNodes) {
		return this.buildToDistanceUntil(level, requiredDistance, maximumNodes, Long.MAX_VALUE);
	}

	public synchronized int buildToDistanceUntil(final ClientLevel level,
		final double requiredDistance, final int maximumNodes, final long deadlineNanos) {
		if (level == null || maximumNodes <= 0 || !Double.isFinite(requiredDistance)) return 0;
		int targetSampleIndex = Math.max(1, Math.min(
			MAX_HORIZONTAL_RANGE / SAMPLE_SPACING,
			(int) Math.ceil(Math.max(0.0, requiredDistance) / SAMPLE_SPACING)));
		int buildLimit = Math.min(maximumNodes, MAX_BUILD_PER_CALL);
		int built = 0;
		int unavailable = 0;
		while (built < buildLimit && unavailable < this.spokes.size()) {
			if (System.nanoTime() >= deadlineNanos) break;
			TerrainShockfrontSpoke spoke = this.spokes.get(
				this.buildOrder[this.nextSpokeToBuild]);
			this.nextSpokeToBuild = (this.nextSpokeToBuild + 1) % this.spokes.size();
			if (spoke.complete() || spoke.nextSampleIndex() > targetSampleIndex) {
				unavailable++;
				continue;
			}
			if (!this.buildOneSample(level, spoke, spoke.nextSampleIndex())) {
				unavailable++;
				continue;
			}
			unavailable = 0;
			spoke.advanceSampleIndex();
			built++;
		}
		return built;
	}

	public List<TerrainShockfrontSpoke> snapshotSpokes() { return this.spokes; }

	public List<TerrainShockfrontNode> readyNodes(final double pressureRadius,
		final int desiredSpokes, final int maximumNodes, final long gameTime) {
		if (!Double.isFinite(pressureRadius) || pressureRadius <= 0.0
			|| maximumNodes <= 0) return List.of();
		int count = Math.max(1, Math.min(this.spokes.size(), desiredSpokes));
		List<List<TerrainShockfrontNode>> perSpoke = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			TerrainShockfrontSpoke spoke =
				this.spokes.get(index * this.spokes.size() / count);
			spoke.updateReached(pressureRadius, gameTime);
			perSpoke.add(spoke.readyNodesNearFrontier(pressureRadius, 8));
		}
		return interleave(perSpoke, maximumNodes);
	}

	public List<TerrainShockfrontNode> activeDustNodes(final double pressureRadius,
		final int desiredSpokes, final int maximumNodes, final long gameTime) {
		if (!Double.isFinite(pressureRadius) || pressureRadius <= 0.0
			|| maximumNodes <= 0) return List.of();
		int count = Math.max(1, Math.min(this.spokes.size(), desiredSpokes));
		int perSpokeLimit = Math.max(1,
			Math.min(16, (maximumNodes + count - 1) / count + 2));
		List<List<TerrainShockfrontNode>> perSpoke = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			TerrainShockfrontSpoke spoke =
				this.spokes.get(index * this.spokes.size() / count);
			spoke.updateReached(pressureRadius, gameTime);
			perSpoke.add(spoke.activeDustNodesNearFrontier(
				pressureRadius, perSpokeLimit, gameTime));
		}
		return interleave(perSpoke, maximumNodes);
	}

	public void markEmitted(final TerrainShockfrontNode node, final long gameTime) {
		if (node != null) node.markEmitted(gameTime);
	}

	private boolean buildOneSample(final ClientLevel level,
		final TerrainShockfrontSpoke spoke, final int sampleIndex) {
		double horizontalDistance = sampleIndex * SAMPLE_SPACING;
		double x = this.impactPosition.x + Math.cos(spoke.angle()) * horizontalDistance;
		double z = this.impactPosition.z + Math.sin(spoke.angle()) * horizontalDistance;
		int blockX = (int) Math.floor(x);
		int blockZ = (int) Math.floor(z);
		if (level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4) == null) return false;
		TerrainSurfaceCache.SurfaceSample surface =
			TerrainSurfaceCache.INSTANCE.sample(level, x, z);
		if (surface == null) {
			spoke.markComplete();
			return true;
		}

		TerrainShockfrontNode previous = spoke.previousNode();
		Vec3 previousPosition = previous == null
			? this.impactPosition.add(0.0, 1.0, 0.0) : previous.position();
		double verticalChange = Math.abs(surface.position().y - previousPosition.y);
		if (verticalChange > MAX_VERTICAL_STEP) {
			spoke.markComplete();
			return true;
		}

		double stepDistance = previous == null
			? Math.sqrt(horizontalDistance * horizontalDistance
				+ Math.pow(surface.position().y - this.impactPosition.y, 2.0))
			: previousPosition.distanceTo(surface.position());
		double cumulativeDistance = (previous == null
			? 0.0 : previous.cumulativePathDistance()) + stepDistance;
		spoke.addNode(new TerrainShockfrontNode(surface.position(),
			surface.surfaceBlock(), surface.surfaceState(), cumulativeDistance,
			this.impactPosition.distanceTo(surface.position()), true,
			surface.tintColor()));
		return true;
	}

	private static List<TerrainShockfrontNode> interleave(
		final List<List<TerrainShockfrontNode>> perSpoke, final int maximumNodes) {
		if (perSpoke.isEmpty() || maximumNodes <= 0) return List.of();
		int maximumDepth = 0;
		for (List<TerrainShockfrontNode> nodes : perSpoke) {
			maximumDepth = Math.max(maximumDepth, nodes.size());
		}
		List<TerrainShockfrontNode> selected = new ArrayList<>(
			Math.min(maximumNodes, perSpoke.size() * maximumDepth));
		for (int layer = 0; layer < maximumDepth
			&& selected.size() < maximumNodes; layer++) {
			for (List<TerrainShockfrontNode> nodes : perSpoke) {
				if (selected.size() >= maximumNodes) break;
				if (layer < nodes.size()) selected.add(nodes.get(layer));
			}
		}
		return List.copyOf(selected);
	}
}
