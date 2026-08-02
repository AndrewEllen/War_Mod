package com.andye.warmod.warhead.client;

import java.util.ArrayList;
import java.util.List;

/** Mutable build and emission state for one deterministic radial terrain path. */
public final class TerrainShockfrontSpoke {
	private final double angle;
	private final List<TerrainShockfrontNode> nodes = new ArrayList<>();
	private int nextSampleIndex = 1;
	private boolean complete;

	TerrainShockfrontSpoke(final double angle) { this.angle = angle; }
	public double angle() { return this.angle; }
	public synchronized List<TerrainShockfrontNode> snapshotNodes() { return List.copyOf(this.nodes); }
	synchronized int nextSampleIndex() { return this.nextSampleIndex; }
	synchronized void advanceSampleIndex() { this.nextSampleIndex++; }
	synchronized boolean complete() { return this.complete; }
	synchronized void markComplete() { this.complete = true; }
	synchronized void addNode(final TerrainShockfrontNode node) { this.nodes.add(node); }
	synchronized TerrainShockfrontNode previousNode() { return this.nodes.isEmpty() ? null : this.nodes.getLast(); }

	public synchronized TerrainShockfrontNode frontier(final double pressureRadius) {
		TerrainShockfrontNode result = null;
		for (TerrainShockfrontNode node : this.nodes) {
			if (node.cumulativePathDistance() > pressureRadius) break;
			result = node;
		}
		return result == null || !result.visibleFromImpact() ? null : result;
	}

	synchronized void updateReached(final double pressureRadius, final long gameTime) {
		for (TerrainShockfrontNode node : this.nodes) {
			if (node.cumulativePathDistance() > pressureRadius) break;
			if (node.visibleFromImpact()) node.markReady(gameTime);
		}
	}

	synchronized List<TerrainShockfrontNode> readyNodes(final int maximumNodes) {
		List<TerrainShockfrontNode> ready = new ArrayList<>();
		for (TerrainShockfrontNode node : this.nodes) {
			if (ready.size() >= maximumNodes) break;
			if (node.state() == TerrainShockfrontNode.State.READY) ready.add(node);
		}
		return List.copyOf(ready);
	}
}