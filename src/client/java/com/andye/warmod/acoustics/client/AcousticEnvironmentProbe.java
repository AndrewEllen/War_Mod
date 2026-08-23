package com.andye.warmod.acoustics.client;

import com.andye.warmod.acoustics.model.AcousticResponseProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;

public final class AcousticEnvironmentProbe {
	private static final double MAX_RAY_DISTANCE = 24.0;
	private static final int[] TERRAIN_REFLECTION_DISTANCES = {48, 96, 160, 240};
	private static final int TERRAIN_REFLECTION_SPOKES = 12;
	private static final int MAX_TERRAIN_REFLECTIONS = 2;
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

	public static AcousticEnvironment probe(final ClientLevel level, final Vec3 sourcePosition,
		final Vec3 listenerPosition, final AcousticResponseProfile responseProfile) {
		BlockPos listenerBlock = BlockPos.containing(listenerPosition);
		boolean openSky = level.canSeeSky(listenerBlock);
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

		double obstruction = directObstruction(level, sourcePosition, listenerPosition);
		double[] terrain = terrainPath(level, sourcePosition, listenerPosition);
		obstruction = Math.max(obstruction, terrain[0]);
		List<AcousticReflection> reflections = responseProfile.distantTerrainReflections() && openSky
			? terrainReflections(level, sourcePosition, listenerPosition) : List.of();
		double terrainRelief = terrain[1];
		for (AcousticReflection reflection : reflections) {
			terrainRelief = Math.max(terrainRelief, reflection.strength());
		}
		return new AcousticEnvironment(
			hitCount / 10.0,
			hitCount == 0 ? 0.0 : reflectionDistanceTotal / hitCount,
			openSky,
			obstruction,
			terrainRelief,
			foliageAbsorption(level, listenerBlock),
			reflections
		);
	}

	private static List<AcousticReflection> terrainReflections(final ClientLevel level,
		final Vec3 source, final Vec3 listener) {
		List<TerrainReflectionCandidate> candidates = new ArrayList<>();
		scanTerrainAround(level, source, listener, listener, candidates);
		if (source.distanceToSqr(listener) >= 48.0 * 48.0) {
			scanTerrainAround(level, source, listener, source, candidates);
		}
		candidates.sort(Comparator.comparingDouble(TerrainReflectionCandidate::score).reversed());
		List<AcousticReflection> selected = new ArrayList<>(MAX_TERRAIN_REFLECTIONS);
		for (TerrainReflectionCandidate candidate : candidates) {
			boolean separated = selected.stream().allMatch(existing ->
				existing.position().distanceToSqr(candidate.reflection().position()) >= 48.0 * 48.0);
			if (!separated) continue;
			selected.add(candidate.reflection());
			if (selected.size() >= MAX_TERRAIN_REFLECTIONS) break;
		}
		return List.copyOf(selected);
	}

	private static void scanTerrainAround(final ClientLevel level, final Vec3 source,
		final Vec3 listener, final Vec3 anchor,
		final List<TerrainReflectionCandidate> candidates) {
		BlockPos anchorBlock = BlockPos.containing(anchor);
		if (!level.isLoaded(anchorBlock)) return;
		int anchorHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			anchorBlock.getX(), anchorBlock.getZ());
		double directDistance = source.distanceTo(listener);
		for (int spoke = 0; spoke < TERRAIN_REFLECTION_SPOKES; spoke++) {
			double angle = Math.PI * 2.0 * spoke / TERRAIN_REFLECTION_SPOKES;
			double directionX = Math.cos(angle);
			double directionZ = Math.sin(angle);
			for (int distance : TERRAIN_REFLECTION_DISTANCES) {
				int x = (int) Math.floor(anchor.x + directionX * distance);
				int z = (int) Math.floor(anchor.z + directionZ * distance);
				BlockPos column = new BlockPos(x, anchorBlock.getY(), z);
				if (!level.isLoaded(column)) continue;
				int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				double prominence = height - anchorHeight;
				if (prominence < 7.0) continue;

				Vec3 position = new Vec3(x + 0.5, height + 1.25, z + 0.5);
				double reflectedPath = source.distanceTo(position) + position.distanceTo(listener);
				double extraPath = reflectedPath - directDistance;
				if (!Double.isFinite(reflectedPath) || extraPath < 8.0) continue;

				Vec3 towardSource = source.subtract(position).normalize();
				Vec3 towardListener = listener.subtract(position).normalize();
				double sameSide = Math.max(0.0, (towardSource.dot(towardListener) + 1.0) * 0.5);
				double heightStrength = Math.min(1.0, prominence / 56.0);
				double distanceStrength = Math.max(0.42, 1.0 - distance / 430.0);
				double strength = Math.min(1.0, heightStrength * distanceStrength
					* (0.62 + sameSide * 0.38));
				if (strength < 0.10) continue;
				double score = strength * Math.min(1.35, 0.82 + extraPath / 280.0);
				candidates.add(new TerrainReflectionCandidate(
					new AcousticReflection(position, reflectedPath, strength), score));
			}
		}
	}

	private static double directObstruction(final ClientLevel level, final Vec3 source,
		final Vec3 listener) {
		Vec3 delta = source.subtract(listener);
		double distance = delta.length();
		if (!Double.isFinite(distance) || distance < 0.25) return 0.0;
		Vec3 end = listener.add(delta.scale(Math.min(distance, 96.0) / distance));
		if (!level.isLoaded(BlockPos.containing(end))) return 0.0;
		BlockHitResult hit = level.clip(new ClipContext(listener, end,
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.BLOCK) return 0.0;
		double hitDistance = listener.distanceTo(hit.getLocation());
		return hitDistance < 4.0 ? 0.82 : hitDistance < 16.0 ? 0.68 : 0.52;
	}

	/** Returns path obstruction and terrain relief without ever requesting an unloaded chunk. */
	private static double[] terrainPath(final ClientLevel level, final Vec3 source,
		final Vec3 listener) {
		int loaded = 0;
		int blocked = 0;
		int minimumHeight = Integer.MAX_VALUE;
		int maximumHeight = Integer.MIN_VALUE;
		for (int index = 1; index <= 12; index++) {
			double fraction = index / 13.0;
			double x = source.x + (listener.x - source.x) * fraction;
			double z = source.z + (listener.z - source.z) * fraction;
			BlockPos sample = BlockPos.containing(x, listener.y, z);
			if (!level.isLoaded(sample)) continue;
			int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				sample.getX(), sample.getZ());
			double lineY = source.y + (listener.y - source.y) * fraction;
			if (height > lineY + 2.5) blocked++;
			minimumHeight = Math.min(minimumHeight, height);
			maximumHeight = Math.max(maximumHeight, height);
			loaded++;
		}
		double obstruction = loaded == 0 ? 0.0
			: Math.min(0.88, blocked / (double) loaded * 0.88);
		double relief = loaded < 2 ? 0.0
			: Math.min(1.0, (maximumHeight - minimumHeight) / 72.0);
		return new double[] { obstruction, relief };
	}

	private static double foliageAbsorption(final ClientLevel level,
		final BlockPos listener) {
		int foliage = 0;
		int sampled = 0;
		for (int spoke = 0; spoke < 12; spoke++) {
			double angle = Math.PI * 2.0 * spoke / 12.0;
			int radius = spoke % 2 == 0 ? 8 : 16;
			int x = listener.getX() + (int) Math.round(Math.cos(angle) * radius);
			int z = listener.getZ() + (int) Math.round(Math.sin(angle) * radius);
			BlockPos column = new BlockPos(x, listener.getY(), z);
			if (!level.isLoaded(column)) continue;
			for (int y = listener.getY() - 5; y <= listener.getY() + 10; y += 3) {
				var state = level.getBlockState(new BlockPos(x, y, z));
				if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) foliage++;
				sampled++;
			}
		}
		return sampled == 0 ? 0.0 : Math.min(1.0, foliage / (sampled * 0.22));
	}

	private record TerrainReflectionCandidate(AcousticReflection reflection, double score) {
	}
}
