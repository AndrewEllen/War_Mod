package com.andye.warmod.testtool;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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
		if (!allRayChunksLoaded(level, start, end)) {
			return Optional.empty();
		}

		BlockHitResult result = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return result.getType() == HitResult.Type.BLOCK ? Optional.of(result) : Optional.empty();
	}

	private static boolean allRayChunksLoaded(final ServerLevel level, final Vec3 start, final Vec3 end) {
		int minChunkX = Math.floorDiv(Mth.floor(Math.min(start.x, end.x)), 16);
		int maxChunkX = Math.floorDiv(Mth.floor(Math.max(start.x, end.x)), 16);
		int minChunkZ = Math.floorDiv(Mth.floor(Math.min(start.z, end.z)), 16);
		int maxChunkZ = Math.floorDiv(Mth.floor(Math.max(start.z, end.z)), 16);

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
					return false;
				}
			}
		}
		return true;
	}
}
