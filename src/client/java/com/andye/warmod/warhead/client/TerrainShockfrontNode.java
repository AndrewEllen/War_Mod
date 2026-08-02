package com.andye.warmod.warhead.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** One terrain surface sample along a pressure-front spoke. */
public record TerrainShockfrontNode(
	Vec3 position,
	BlockPos surfaceBlock,
	BlockState surfaceState,
	double cumulativePathDistance,
	double directDistance,
	boolean visibleFromImpact
) {
	public boolean valid() {
		return this.position != null
			&& this.position.isFinite()
			&& this.surfaceBlock != null
			&& this.surfaceState != null
			&& Double.isFinite(this.cumulativePathDistance)
			&& Double.isFinite(this.directDistance);
	}
}
