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
		for (int dx = -8; dx <= 8; dx++) {
			for (int dy = -8; dy <= 8; dy++) {
				for (int dz = -8; dz <= 8; dz++) {
					if (dx * dx + dy * dy + dz * dz > 64) continue;
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
		return List.copyOf(eligible.subList(0, Math.min(48, eligible.size())));
	}

	private static void spawnDestroyedDebris(final ServerLevel level, final Vec3 center, final long seed,
		final List<DebrisCandidate> candidates) {
		List<DebrisCandidate> destroyed = new ArrayList<>();
		for (DebrisCandidate candidate : candidates) {
			BlockState after = level.getBlockState(candidate.position());
			if (!after.equals(candidate.state())) destroyed.add(candidate);
		}
		destroyed.sort(Comparator.comparingLong(candidate -> mix(candidate.position().asLong() ^ seed ^ 0x444542524953L)));
		for (int index = 0; index < Math.min(24, destroyed.size()); index++) {
			DebrisCandidate candidate = destroyed.get(index);
			SplittableRandom random = new SplittableRandom(seed ^ candidate.position().asLong());
			Vec3 spawn = Vec3.atCenterOf(candidate.position());
			Vec3 outward = new Vec3(spawn.x - center.x, 0.0, spawn.z - center.z);
			if (outward.lengthSqr() < 1.0E-5) outward = new Vec3(random.nextDouble(-1.0, 1.0), 0.0, random.nextDouble(-1.0, 1.0));
			outward = outward.normalize();
			double horizontalSpeed = random.nextDouble(0.25, 0.90);
			Vec3 velocity = outward.scale(horizontalSpeed).add(random.nextDouble(-0.08, 0.08), random.nextDouble(0.45, 1.30), random.nextDouble(-0.08, 0.08));
			Vec3 spin = new Vec3(random.nextDouble(-0.24, 0.24), random.nextDouble(-0.24, 0.24), random.nextDouble(-0.24, 0.24));
			level.addFreshEntity(new WarheadDebrisEntity(level, candidate.state(), spawn, velocity, spin, random.nextInt(50, 101)));
		}
	}

	private static long mix(long value) {
		value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27; value *= 0x94D049BB133111EBL; return value ^ (value >>> 31);
	}

	private record DebrisCandidate(BlockPos position, BlockState state) { }
}