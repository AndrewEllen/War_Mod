package com.andye.warmod.warhead.client;

import java.util.ArrayList;
import java.util.List;

/** Mutable build and emission state for one deterministic radial terrain path. */
public final class TerrainShockfrontSpoke {
	private static final double ACTIVE_DUST_DEPTH = 52.0;
	private static final long ACTIVE_DUST_LIFETIME_TICKS = 240L;

	private final double angle;
	private final List<TerrainShockfrontNode> nodes = new ArrayList<>();
	/*
	 * Rendering can query a spoke several times in one frame (shock front,
	 * settled smoke and nuclear wall). Cache the immutable view when topology
	 * changes instead of allocating List.copyOf(nodes) for every pass.
	 */
	private volatile List<TerrainShockfrontNode> nodeSnapshot = List.of();
	private int nextSampleIndex = 1;
	private int reachedNodeCount;
	private int frontierIndex = -1;
	private boolean complete;

	TerrainShockfrontSpoke(final double angle) {
		this.angle = angle;
	}

	public double angle() {
		return this.angle;
	}

	public List<TerrainShockfrontNode> snapshotNodes() {
		return this.nodeSnapshot;
	}

	synchronized int nextSampleIndex() {
		return this.nextSampleIndex;
	}

	synchronized void advanceSampleIndex() {
		this.nextSampleIndex++;
	}

	synchronized boolean complete() {
		return this.complete;
	}

	synchronized void markComplete() {
		this.complete = true;
	}

	synchronized void addNode(final TerrainShockfrontNode node) {
		this.nodes.add(node);
		this.nodeSnapshot = List.copyOf(this.nodes);
	}

	synchronized TerrainShockfrontNode previousNode() {
		return this.nodes.isEmpty() ? null : this.nodes.getLast();
	}

	public synchronized TerrainShockfrontNode frontier(final double pressureRadius) {
		if (this.nodes.isEmpty() || !Double.isFinite(pressureRadius) || pressureRadius < 0.0) return null;
		while (this.frontierIndex + 1 < this.nodes.size()
			&& this.nodes.get(this.frontierIndex + 1).cumulativePathDistance() <= pressureRadius) {
			this.frontierIndex++;
		}
		while (this.frontierIndex >= 0
			&& this.nodes.get(this.frontierIndex).cumulativePathDistance() > pressureRadius) {
			this.frontierIndex--;
		}
		if (this.frontierIndex < 0) return null;
		TerrainShockfrontNode result = this.nodes.get(this.frontierIndex);
		return result.visibleFromImpact() ? result : null;
	}

	synchronized void updateReached(final double pressureRadius, final long gameTime) {
		while (this.reachedNodeCount < this.nodes.size()) {
			TerrainShockfrontNode node = this.nodes.get(this.reachedNodeCount);
			if (node.cumulativePathDistance() > pressureRadius) break;
			if (node.visibleFromImpact()) node.markReady(gameTime);
			this.reachedNodeCount++;
		}
	}

	synchronized List<TerrainShockfrontNode> readyNodesNearFrontier(final double pressureRadius, final int maximumNodes) {
		if (maximumNodes <= 0 || this.reachedNodeCount <= 0) return List.of();
		List<TerrainShockfrontNode> selected = new ArrayList<>(Math.min(maximumNodes, 8));
		for (int index = this.reachedNodeCount - 1; index >= 0 && selected.size() < maximumNodes; index--) {
			TerrainShockfrontNode node = this.nodes.get(index);
			if (pressureRadius - node.cumulativePathDistance() > ACTIVE_DUST_DEPTH) break;
			if (node.state() == TerrainShockfrontNode.State.READY) selected.add(node);
		}
		return List.copyOf(selected);
	}

	synchronized List<TerrainShockfrontNode> activeDustNodesNearFrontier(final double pressureRadius,
		final int maximumNodes, final long gameTime) {
		if (maximumNodes <= 0 || this.reachedNodeCount <= 0) return List.of();
		List<TerrainShockfrontNode> selected = new ArrayList<>(Math.min(maximumNodes, 24));
		for (int index = this.reachedNodeCount - 1; index >= 0 && selected.size() < maximumNodes; index--) {
			TerrainShockfrontNode node = this.nodes.get(index);
			double behindFront = pressureRadius - node.cumulativePathDistance();
			boolean ready = node.state() == TerrainShockfrontNode.State.READY && behindFront <= ACTIVE_DUST_DEPTH;
			boolean recent = node.state() == TerrainShockfrontNode.State.EMITTED
				&& gameTime - node.emittedGameTime() <= ACTIVE_DUST_LIFETIME_TICKS;
			if (ready || recent) selected.add(node);
			if (behindFront > ACTIVE_DUST_DEPTH && !recent) break;
		}
		return List.copyOf(selected);
	}
}
