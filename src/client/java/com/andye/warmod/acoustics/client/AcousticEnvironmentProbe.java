package com.andye.warmod.acoustics.client;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;

public final class AcousticEnvironmentProbe {
	private static final double MAX_RAY_DISTANCE = 24.0;
	private static final List<Vec3> RAY_DIRECTIONS = List.of(
		new Vec3(1.0, 0.0, 0.0),
		new Vec3(-1.0, 0.0, 0.0),
		new Vec3(0.0, 1.0, 0.0),
		new Vec3(0.0, -1.0, 0.0),
		new Vec3(0.0, 0.0, 1.0),
		new Vec3(0.0, 0.0, -1.0),
		new Vec3(1.0, 0.0, 1.0).normalize(),
		new Vec3(1.0, 0.0, -1.0).normalize(),
		new Vec3(-1.0, 0.0, 1.0).normalize(),
		new Vec3(-1.0, 0.0, -1.0).normalize()
	);

	private AcousticEnvironmentProbe() {
	}

	public static AcousticEnvironment probe(final ClientLevel level, final Vec3 listenerPosition) {
		BlockPos listenerBlock = BlockPos.containing(listenerPosition);
		int hitCount = 0;
		double reflectionDistanceTotal = 0.0;
		for (Vec3 direction : RAY_DIRECTIONS) {
			Vec3 end = listenerPosition.add(
				direction.x * MAX_RAY_DISTANCE,
				direction.y * MAX_RAY_DISTANCE,
				direction.z * MAX_RAY_DISTANCE
			);
			if (!level.isLoaded(BlockPos.containing(end))) {
				continue;
			}

			BlockHitResult hit = level.clip(new ClipContext(
				listenerPosition,
				end,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()
			));
			if (hit.getType() == HitResult.Type.BLOCK) {
				hitCount++;
				reflectionDistanceTotal += listenerPosition.distanceTo(hit.getLocation());
			}
		}

		return new AcousticEnvironment(
			hitCount / 10.0,
			hitCount == 0 ? 0.0 : reflectionDistanceTotal / hitCount,
			level.canSeeSky(listenerBlock)
		);
	}
}
