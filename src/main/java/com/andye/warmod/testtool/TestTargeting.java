package com.andye.warmod.testtool;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TestTargeting {
	private TestTargeting() {
	}

	public static Optional<BlockHitResult> findTarget(final ServerPlayer player, final double maximumDistance) {
		Objects.requireNonNull(player, "player");
		if (!Double.isFinite(maximumDistance) || maximumDistance <= 0.0) {
			throw new IllegalArgumentException("maximumDistance must be finite and greater than zero");
		}

		ServerLevel level = player.level();
		Vec3 start = player.getEyePosition();
		Vec3 direction = player.getLookAngle();
		Vec3 end = start.add(direction.x * maximumDistance, direction.y * maximumDistance, direction.z * maximumDistance);
		ClipContext context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
		return BlockGetter.traverseBlocks(start, end, context, (clipContext, pos) -> {
			int chunkX = SectionPos.blockToSectionCoord(pos.getX());
			int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
			if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
				return Optional.empty();
			}

			BlockState blockState = level.getBlockState(pos);
			VoxelShape blockShape = clipContext.getBlockShape(blockState, level, pos);
			BlockHitResult hit = level.clipWithInteractionOverride(start, end, pos, blockShape, blockState);
			return hit == null ? null : Optional.of(hit);
		}, ignored -> Optional.empty());
	}
}