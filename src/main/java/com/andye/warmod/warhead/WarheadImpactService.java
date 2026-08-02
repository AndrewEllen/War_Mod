package com.andye.warmod.warhead;

import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.entity.WarheadDebrisEntity;
import com.andye.warmod.testtool.TestExplosionService;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class WarheadImpactService {
	private static final int DEBRIS_SAMPLE_RADIUS = 32;
	private static final int MAX_DEBRIS_CANDIDATES = 4096;
	private static final int MAX_DEBRIS_ENTITIES = 640;
	private static final int MAX_LARGE_DEBRIS_ENTITIES = 256;

	private WarheadImpactService() { }

	public static void impact(final ServerLevel level, final ServerPlayer owner, final UUID warheadId,
		final Vec3 impactPosition, final long visualSeed) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(warheadId, "warheadId");
		Objects.requireNonNull(impactPosition, "impactPosition");
		if (!impactPosition.isFinite()) throw new IllegalArgumentException("impactPosition must be finite");

		WarheadVisualNetworking.sendImpact(level, new ClientboundWarheadImpactPayload(warheadId, impactPosition.x,
			impactPosition.y, impactPosition.z, level.getGameTime(), visualSeed, 1.0F), impactPosition);
		List<DebrisCandidate> debrisCandidates = sampleDebrisCandidates(level, impactPosition, visualSeed);
		TestExplosionService.createExplosion(level, owner, impactPosition);
		spawnDestroyedDebris(level, impactPosition, visualSeed, debrisCandidates);
		AcousticEngine.playSound(level, impactPosition, AcousticSounds.LARGE_EXPLOSION_ID, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	private static List<DebrisCandidate> sampleDebrisCandidates(final ServerLevel level, final Vec3 center, final long seed) {
		BlockPos origin = BlockPos.containing(center);
		List<DebrisCandidate> eligible = new ArrayList<>();
		for (int dx = -DEBRIS_SAMPLE_RADIUS; dx <= DEBRIS_SAMPLE_RADIUS; dx++) {
			for (int dy = -DEBRIS_SAMPLE_RADIUS; dy <= DEBRIS_SAMPLE_RADIUS; dy++) {
				for (int dz = -DEBRIS_SAMPLE_RADIUS; dz <= DEBRIS_SAMPLE_RADIUS; dz++) {
					if (dx * dx + dy * dy + dz * dz > DEBRIS_SAMPLE_RADIUS * DEBRIS_SAMPLE_RADIUS) continue;
					BlockPos pos = origin.offset(dx, dy, dz);
					if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) continue;
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
						|| state.getDestroySpeed(level, pos) < 0.0F || state.getRenderShape() == RenderShape.INVISIBLE) continue;
					eligible.add(new DebrisCandidate(pos.immutable(), state));
				}
			}
		}
		eligible.sort(Comparator.comparingLong(candidate -> mix(candidate.position().asLong() ^ seed)));
		return List.copyOf(eligible.subList(0, Math.min(MAX_DEBRIS_CANDIDATES, eligible.size())));
	}

	private static void spawnDestroyedDebris(final ServerLevel level, final Vec3 center, final long seed,
		final List<DebrisCandidate> candidates) {
		List<DebrisCandidate> destroyed = new ArrayList<>();
		for (DebrisCandidate candidate : candidates) {
			if (!level.getBlockState(candidate.position()).equals(candidate.state())) destroyed.add(candidate);
		}
		destroyed.sort(Comparator.comparingLong(candidate -> mix(candidate.position().asLong() ^ seed ^ 0x444542524953L)));
		int count = Math.min(MAX_DEBRIS_ENTITIES, destroyed.size());
		int largeCount = Math.min(MAX_LARGE_DEBRIS_ENTITIES, count);
		for (int index = 0; index < count; index++) {
			DebrisCandidate candidate = destroyed.get(index);
			SplittableRandom random = new SplittableRandom(mix(seed ^ candidate.position().asLong()));
			boolean large = index < largeCount;
			Vec3 spawn = Vec3.atCenterOf(candidate.position());
			Vec3 radial = new Vec3(spawn.x - center.x, 0.0, spawn.z - center.z);
			if (radial.lengthSqr() < 1.0E-5) radial = new Vec3(random.nextDouble(-1.0, 1.0), 0.0, random.nextDouble(-1.0, 1.0));
			Vec3 outward = radial.normalize();
			Vec3 sideways = new Vec3(-outward.z, 0.0, outward.x);
			double normalizedRadius = Math.min(1.0, Math.sqrt(radial.lengthSqr()) / DEBRIS_SAMPLE_RADIUS);
			double horizontalSpeed = large ? random.nextDouble(0.45, 1.50) : random.nextDouble(0.60, 2.00);
			double verticalMinimum = large ? 0.80 : 0.90;
			double verticalMaximum = large ? 2.10 : 2.60;
			double verticalBias = (1.0 - normalizedRadius) * (large ? 0.42 : 0.58);
			double verticalSpeed = Math.min(verticalMaximum, random.nextDouble(verticalMinimum, verticalMaximum) + verticalBias);
			double horizontalBias = 0.72 + normalizedRadius * 0.48;
			double sideSpeed = random.nextDouble(-0.30, 0.30) * (large ? 0.75 : 1.0);
			Vec3 velocity = outward.scale(horizontalSpeed * horizontalBias).add(sideways.scale(sideSpeed)).add(0.0, verticalSpeed, 0.0);
			double spinLimit = large ? 0.34 : 0.72;
			Vec3 spin = new Vec3(random.nextDouble(-spinLimit, spinLimit), random.nextDouble(-spinLimit, spinLimit), random.nextDouble(-spinLimit, spinLimit));
			float visualScale = (float) (large ? random.nextDouble(0.70, 1.15) : random.nextDouble(0.25, 0.65));
			int lifetime = large ? random.nextInt(70, 151) : random.nextInt(45, 111);
			level.addFreshEntity(new WarheadDebrisEntity(level, candidate.state(), spawn, velocity, spin, lifetime, visualScale));
		}
	}

	private static long mix(long value) {
		value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27; value *= 0x94D049BB133111EBL; return value ^ (value >>> 31);
	}

	private record DebrisCandidate(BlockPos position, BlockState state) { }
}