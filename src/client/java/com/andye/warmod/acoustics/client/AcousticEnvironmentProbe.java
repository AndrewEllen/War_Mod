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
import org.jspecify.annotations.Nullable;

public final class AcousticEnvironmentProbe {
	private static final double MAX_RAY_DISTANCE = 24.0;
	private static final double MAX_TRANSMISSION_DISTANCE = 192.0;
	private static final double TRANSMISSION_STEP = 0.72;
	private static final int[] EXPLOSION_REFLECTION_DISTANCES = {48, 96, 160, 240};
	private static final int[] SMALL_REFLECTION_DISTANCES = {36, 72, 128};
	private static final int EXPLOSION_REFLECTION_SPOKES = 12;
	private static final int SMALL_REFLECTION_SPOKES = 8;
	private static final int MAX_TERRAIN_REFLECTIONS = 3;
	private static final double[] EXPLOSION_REFLECTION_ELEVATIONS = {-0.10, 0.04, 0.18};
	private static final double[] SMALL_REFLECTION_ELEVATIONS = {0.0, 0.14};
	private static final List<RayOffset> TRANSMISSION_RAYS = List.of(
		new RayOffset(0.0, 0.0, 0.36),
		new RayOffset(0.90, 0.0, 0.16),
		new RayOffset(-0.90, 0.0, 0.16),
		new RayOffset(0.0, 0.90, 0.16),
		new RayOffset(0.0, -0.90, 0.16)
	);
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
		int bareTerrainHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			listenerBlock.getX(), listenerBlock.getZ());
		/* Leaves block canSeeSky, but a forest canopy is still an outdoor acoustic
		 * space. Only solid terrain/roof above the listener suppresses hill returns. */
		boolean openSky = level.canSeeSky(listenerBlock)
			|| listenerPosition.y >= bareTerrainHeight - 0.5;
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

		PathTransmission transmission = transmission(level, sourcePosition, listenerPosition);
		double terrainRelief = terrainRelief(level, sourcePosition, listenerPosition);
		List<AcousticReflection> reflections = responseProfile.distantTerrainReflections() && openSky
			? terrainReflections(level, sourcePosition, listenerPosition, responseProfile) : List.of();
		for (AcousticReflection reflection : reflections) {
			terrainRelief = Math.max(terrainRelief, reflection.strength());
		}
		return new AcousticEnvironment(
			hitCount / 10.0,
			hitCount == 0 ? 0.0 : reflectionDistanceTotal / hitCount,
			openSky,
			transmission.solidObstruction(),
			terrainRelief,
			transmission.foliageAbsorption(),
			reflections
		);
	}

	private static List<AcousticReflection> terrainReflections(final ClientLevel level,
		final Vec3 source, final Vec3 listener, final AcousticResponseProfile responseProfile) {
		boolean explosion = responseProfile == AcousticResponseProfile.EXPLOSION;
		int[] distances = explosion ? EXPLOSION_REFLECTION_DISTANCES : SMALL_REFLECTION_DISTANCES;
		int spokes = explosion ? EXPLOSION_REFLECTION_SPOKES : SMALL_REFLECTION_SPOKES;
		double[] elevations = explosion
			? EXPLOSION_REFLECTION_ELEVATIONS : SMALL_REFLECTION_ELEVATIONS;
		int maximumReflections = explosion ? MAX_TERRAIN_REFLECTIONS : 1;
		List<TerrainReflectionCandidate> candidates = new ArrayList<>();
		scanTerrainAround(level, source, listener, listener, distances, spokes,
			elevations, candidates);
		if (source.distanceToSqr(listener) >= 48.0 * 48.0) {
			scanTerrainAround(level, source, listener, source, distances, spokes,
				elevations, candidates);
		}
		candidates.sort(Comparator.comparingDouble(TerrainReflectionCandidate::score).reversed());
		List<AcousticReflection> selected = new ArrayList<>(maximumReflections);
		for (TerrainReflectionCandidate candidate : candidates) {
			boolean separated = selected.stream().allMatch(existing ->
				existing.position().distanceToSqr(candidate.reflection().position()) >= 48.0 * 48.0);
			if (!separated) continue;
			selected.add(candidate.reflection());
			if (selected.size() >= maximumReflections) break;
		}
		return List.copyOf(selected);
	}

	private static void scanTerrainAround(final ClientLevel level, final Vec3 source,
		final Vec3 listener, final Vec3 anchor,
		final int[] distances, final int spokes,
		final double[] elevations,
		final List<TerrainReflectionCandidate> candidates) {
		BlockPos anchorBlock = BlockPos.containing(anchor);
		if (!level.isLoaded(anchorBlock)) return;
		double directDistance = source.distanceTo(listener);
		for (int spoke = 0; spoke < spokes; spoke++) {
			double angle = Math.PI * 2.0 * spoke / spokes;
			for (double elevation : elevations) {
				Vec3 direction = new Vec3(Math.cos(angle), elevation, Math.sin(angle)).normalize();
				Vec3 end = null;
				for (int index = distances.length - 1; index >= 0; index--) {
					Vec3 candidateEnd = anchor.add(direction.scale(distances[index]));
					if (level.isLoaded(BlockPos.containing(candidateEnd))) {
						end = candidateEnd;
						break;
					}
				}
				if (end == null) continue;
				BlockHitResult hit = firstReflectiveHit(level, anchor, end, direction);
				if (hit == null || anchor.distanceToSqr(hit.getLocation()) < 12.0 * 12.0) continue;
				var state = level.getBlockState(hit.getBlockPos());
				if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) continue;

				Vec3 normal = hit.getDirection().getUnitVec3();
				Vec3 position = hit.getLocation().add(normal.scale(0.08));
				Vec3 towardSource = source.subtract(position);
				Vec3 towardListener = listener.subtract(position);
				if (towardSource.lengthSqr() < 1.0E-6 || towardListener.lengthSqr() < 1.0E-6) continue;
				towardSource = towardSource.normalize();
				towardListener = towardListener.normalize();
				double sourceFacing = Math.max(0.0, normal.dot(towardSource));
				double listenerFacing = Math.max(0.0, normal.dot(towardListener));
				if (sourceFacing * listenerFacing < 0.015) continue;

				Vec3 incoming = position.subtract(source).normalize();
				Vec3 reflected = incoming.subtract(normal.scale(2.0 * incoming.dot(normal))).normalize();
				double specular = Math.max(0.0, reflected.dot(towardListener));
				double reflectedPath = source.distanceTo(position) + position.distanceTo(listener);
				double extraPath = reflectedPath - directDistance;
				if (!Double.isFinite(reflectedPath) || extraPath < 2.0) continue;

				PathRay sourcePath = sampleRay(level, position.add(normal.scale(0.18)), source,
					MAX_TRANSMISSION_DISTANCE);
				double pathTransmission = sourcePath.solidTransmission()
					* (0.82 + sourcePath.foliageTransmission() * 0.18);
				double verticalFace = 1.0 - Math.abs(normal.y);
				double distanceStrength = Math.max(0.30,
					1.0 - position.distanceTo(anchor) / 420.0);
				double strength = Math.min(1.0,
					(0.28 + specular * 0.50 + sourceFacing * listenerFacing * 0.22)
						* (0.58 + verticalFace * 0.42) * distanceStrength);
				if (strength < 0.08 || pathTransmission < 0.08) continue;
				double score = strength * pathTransmission
					* Math.min(1.30, 0.84 + extraPath / 320.0);
				candidates.add(new TerrainReflectionCandidate(new AcousticReflection(
					position, reflectedPath, strength, pathTransmission), score));
			}
		}
	}

	/** Casts through a bounded number of leaf/log hits so forest cover does not hide mountains. */
	private static @Nullable BlockHitResult firstReflectiveHit(final ClientLevel level,
		final Vec3 start, final Vec3 end, final Vec3 direction) {
		Vec3 castStart = start;
		for (int pass = 0; pass < 12; pass++) {
			BlockHitResult hit = level.clip(new ClipContext(castStart, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
			if (hit.getType() != HitResult.Type.BLOCK) return null;
			var state = level.getBlockState(hit.getBlockPos());
			if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) return hit;
			castStart = hit.getLocation().add(direction.scale(1.10));
			if (castStart.distanceToSqr(end) < 1.0) return null;
		}
		return null;
	}

	private static PathTransmission transmission(final ClientLevel level, final Vec3 source,
		final Vec3 listener) {
		Vec3 delta = source.subtract(listener);
		if (!delta.isFinite() || delta.lengthSqr() < 0.0625) {
			return new PathTransmission(0.0, 0.0);
		}
		Vec3 direction = delta.normalize();
		Vec3 right = direction.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() < 1.0E-6) right = new Vec3(1.0, 0.0, 0.0);
		else right = right.normalize();
		Vec3 up = right.cross(direction).normalize();
		double solidEnergy = 0.0;
		double foliageEnergy = 0.0;
		double totalWeight = 0.0;
		for (RayOffset offset : TRANSMISSION_RAYS) {
			Vec3 shiftedListener = listener.add(right.scale(offset.right()))
				.add(up.scale(offset.up()));
			Vec3 shiftedSource = source.add(right.scale(offset.right()))
				.add(up.scale(offset.up()));
			PathRay ray = sampleRay(level, shiftedListener, shiftedSource,
				MAX_TRANSMISSION_DISTANCE);
			solidEnergy += ray.solidTransmission() * offset.weight();
			foliageEnergy += ray.foliageTransmission() * offset.weight();
			totalWeight += offset.weight();
		}
		return new PathTransmission(
			1.0 - solidEnergy / totalWeight,
			1.0 - foliageEnergy / totalWeight
		);
	}

	private static PathRay sampleRay(final ClientLevel level, final Vec3 start,
		final Vec3 target, final double maximumDistance) {
		Vec3 delta = target.subtract(start);
		double distance = delta.length();
		if (!Double.isFinite(distance) || distance < 0.05) return new PathRay(1.0, 1.0);
		double sampledDistance = Math.min(distance, maximumDistance);
		Vec3 direction = delta.scale(1.0 / distance);
		int steps = Math.max(1, (int) Math.ceil(sampledDistance / TRANSMISSION_STEP));
		double solidUnits = 0.0;
		double foliageUnits = 0.0;
		long previousBlock = Long.MIN_VALUE;
		/* The final sample is the sound emitter itself. A block-face impact must
		 * not treat the struck block as cover between that surface and the ear. */
		boolean reachesTarget = sampledDistance >= distance - 1.0E-6;
		int finalStep = reachesTarget ? steps - 1 : steps;
		for (int step = 1; step <= finalStep; step++) {
			double along = sampledDistance * step / steps;
			BlockPos block = BlockPos.containing(start.add(direction.scale(along)));
			long packed = block.asLong();
			if (packed == previousBlock) continue;
			previousBlock = packed;
			if (!level.isLoaded(block)) continue;
			var state = level.getBlockState(block);
			if (state.isAir() || state.liquid()) continue;
			if (state.is(BlockTags.LEAVES)) {
				foliageUnits += 1.0;
			} else if (state.is(BlockTags.LOGS)) {
				solidUnits += 0.34;
			} else if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) {
				solidUnits += 1.35;
			} else if (state.blocksMotion()) {
				solidUnits += state.isCollisionShapeFullBlock(level, block) ? 1.0 : 0.48;
			}
		}
		return new PathRay(Math.exp(-solidUnits * 0.56),
			Math.exp(-foliageUnits * 0.026));
	}

	/** Samples relief only; attenuation comes from the bounded source-to-listener ray fan. */
	private static double terrainRelief(final ClientLevel level, final Vec3 source,
		final Vec3 listener) {
		int loaded = 0;
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
			minimumHeight = Math.min(minimumHeight, height);
			maximumHeight = Math.max(maximumHeight, height);
			loaded++;
		}
		return loaded < 2 ? 0.0
			: Math.min(1.0, (maximumHeight - minimumHeight) / 72.0);
	}

	private record RayOffset(double right, double up, double weight) { }
	private record PathRay(double solidTransmission, double foliageTransmission) { }
	private record PathTransmission(double solidObstruction, double foliageAbsorption) { }
	private record TerrainReflectionCandidate(AcousticReflection reflection, double score) {
	}
}
