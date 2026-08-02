package com.andye.warmod.warhead.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** One terrain surface sample along a pressure-front spoke. */
public final class TerrainShockfrontNode {
	public enum State { PENDING, READY, EMITTED }

	private final Vec3 position;
	private final BlockPos surfaceBlock;
	private final BlockState surfaceState;
	private final double cumulativePathDistance;
	private final double directDistance;
	private final boolean visibleFromImpact;
	private final int tintColor;
	private State state = State.PENDING;
	private long readyGameTime = Long.MIN_VALUE;
	private long emittedGameTime = Long.MIN_VALUE;

	public TerrainShockfrontNode(final Vec3 position, final BlockPos surfaceBlock, final BlockState surfaceState,
		final double cumulativePathDistance, final double directDistance, final boolean visibleFromImpact, final int tintColor) {
		this.position = position;
		this.surfaceBlock = surfaceBlock;
		this.surfaceState = surfaceState;
		this.cumulativePathDistance = cumulativePathDistance;
		this.directDistance = directDistance;
		this.visibleFromImpact = visibleFromImpact;
		this.tintColor = tintColor;
	}

	public Vec3 position() { return this.position; }
	public BlockPos surfaceBlock() { return this.surfaceBlock; }
	public BlockState surfaceState() { return this.surfaceState; }
	public double cumulativePathDistance() { return this.cumulativePathDistance; }
	public double directDistance() { return this.directDistance; }
	public boolean visibleFromImpact() { return this.visibleFromImpact; }
	public int tintColor() { return this.tintColor; }
	public synchronized State state() { return this.state; }
	public synchronized long readyGameTime() { return this.readyGameTime; }
	public synchronized long emittedGameTime() { return this.emittedGameTime; }

	public synchronized void markReady(final long gameTime) {
		if (this.state == State.PENDING) {
			this.state = State.READY;
			this.readyGameTime = gameTime;
		}
	}

	public synchronized void markEmitted(final long gameTime) {
		if (this.state == State.READY) {
			this.state = State.EMITTED;
			this.emittedGameTime = gameTime;
		}
	}

	public boolean valid() {
		return this.position != null && this.position.isFinite() && this.surfaceBlock != null && this.surfaceState != null
			&& Double.isFinite(this.cumulativePathDistance) && Double.isFinite(this.directDistance);
	}
}