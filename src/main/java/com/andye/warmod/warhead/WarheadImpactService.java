package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.testtool.TestExplosionService;
import com.andye.warmod.warhead.network.ClientboundWarheadDebrisPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class WarheadImpactService {
	private WarheadImpactService() {
	}

	public static void impact(final ServerLevel level, final @Nullable ServerPlayer owner, final UUID id,
		final Vec3 pos, final long seed) {
		impact(level, owner, id, id, pos, seed, WarheadPayloadType.CONVENTIONAL);
	}

	public static void impact(final ServerLevel level, final @Nullable ServerPlayer owner, final UUID id,
		final Vec3 pos, final long seed, final WarheadPayloadType payloadType) {
		impact(level, owner, id, id, pos, seed, payloadType);
	}

	public static void impact(final ServerLevel level, final @Nullable ServerPlayer owner, final UUID id,
		final UUID radarRootTrackId, final Vec3 pos, final long seed, final WarheadPayloadType payloadType) {
		detonateAt(level, owner, id, radarRootTrackId, pos, seed, payloadType);
	}

	public static void detonateAt(final ServerLevel level, final @Nullable ServerPlayer owner, final UUID id,
		final UUID radarRootTrackId, final Vec3 pos, final long seed, final WarheadPayloadType payloadType) {
		detonateAt(level, owner, id, radarRootTrackId, pos, seed, payloadType, true);
	}

	public static void detonateAt(final ServerLevel level, final @Nullable ServerPlayer owner, final UUID id,
		final UUID radarRootTrackId, final Vec3 pos, final long seed, final WarheadPayloadType payloadType,
		final boolean registerRadarImpact) {
		detonateWithProfile(level, owner, id, radarRootTrackId, pos, seed, payloadType,
			WarheadImpactProfiles.get(payloadType), registerRadarImpact);
	}

	public static void detonateTacticalHe(final ServerLevel level, final @Nullable ServerPlayer owner,
		final UUID id, final Vec3 pos, final long seed) {
		detonateWithProfile(level, owner, id, id, pos, seed, WarheadPayloadType.CONVENTIONAL,
			WarheadImpactProfiles.tacticalHe(), false);
	}

	private static void detonateWithProfile(final ServerLevel level, final @Nullable ServerPlayer owner,
		final UUID id, final UUID radarRootTrackId, final Vec3 pos, final long seed,
		final WarheadPayloadType payloadType, final WarheadImpactProfile profile,
		final boolean registerRadarImpact) {
		Objects.requireNonNull(level);
		Objects.requireNonNull(id);
		Objects.requireNonNull(pos);
		if (!pos.isFinite()) throw new IllegalArgumentException("impactPosition must be finite");
		boolean tactical = profile == WarheadImpactProfiles.tacticalHe();
		Vec3 effectivePosition = tactical
			? pos
			: WarheadExplosionWorkManager.resolveDetonationCenter(level, pos, payloadType);
		if (registerRadarImpact) {
			RadarTrackingService.registerImpact(level, id, radarRootTrackId, effectivePosition, payloadType, profile.impactVisualScale());
		}

		WarheadVisualNetworking.sendImpact(level, new ClientboundWarheadImpactPayload(
			id, effectivePosition.x, effectivePosition.y, effectivePosition.z, level.getGameTime(), seed, payloadType, profile.impactVisualScale(),
			tactical ? WarheadEffectProfile.TACTICAL_HE
				: payloadType == WarheadPayloadType.NUCLEAR ? WarheadEffectProfile.NUCLEAR : WarheadEffectProfile.CONVENTIONAL
		), effectivePosition);
		List<com.andye.warmod.testtool.WarheadExplosionDropContext.DestroyedBlock> destroyedBlocks = tactical
			? TestExplosionService.createExplosion(level, owner, effectivePosition, profile.explosionStrength())
			: TestExplosionService.createExplosion(level, owner, id, effectivePosition, payloadType, seed);
		spawnDebris(level, id, effectivePosition, seed, destroyedBlocks, profile);
		AcousticEngine.playSound(level, effectivePosition, AcousticSounds.WARHEAD_IMPACT_THUD_ID, SoundSource.BLOCKS, 0.72F, 1.0F);
		AcousticEngine.playSound(level, effectivePosition,
			tactical ? AcousticSounds.TACTICAL_HE_EXPLOSION_ID : AcousticSounds.LARGE_EXPLOSION_ID,
			SoundSource.BLOCKS, profile.acousticVolume(), profile.acousticPitch());
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			WarMod.LOGGER.info("Warhead {} emitted impact thud and explosion at {}", id, effectivePosition);
			if (payloadType == WarheadPayloadType.NUCLEAR) WarMod.LOGGER.info("Nuclear warhead {} impacted at {}", id, effectivePosition);
		}
	}

	public static void detonateAntiAir(final ServerLevel level, final UUID id, final Vec3 pos, final long seed,
		final WarheadEffectProfile effect) {
		float scale = switch (effect) {
			case ANTI_AIR_INTERCEPTION -> 0.22F;
			case ANTI_AIR_SAFE_SELF_DESTRUCT -> 0.12F;
			case ANTI_AIR_FALLBACK -> 0.36F;
			case ANTI_AIR_LAUNCH_FAILURE -> 0.30F;
			default -> 0.24F;
		};
		WarheadVisualNetworking.sendImpact(level, new ClientboundWarheadImpactPayload(
			id, pos.x, pos.y, pos.z, level.getGameTime(), seed, WarheadPayloadType.CONVENTIONAL, scale, effect
		), pos);
		if (effect == WarheadEffectProfile.ANTI_AIR_FALLBACK) {
			TestExplosionService.createExplosion(level, null, pos, 8.0F);
		} else if (effect == WarheadEffectProfile.ANTI_AIR_LAUNCH_FAILURE) {
			TestExplosionService.createExplosion(level, null, pos, 7.0F);
		}
		AcousticEngine.playSound(level, pos,
			effect == WarheadEffectProfile.ANTI_AIR_SAFE_SELF_DESTRUCT
				? AcousticSounds.TACTICAL_HE_EXPLOSION_ID : AcousticSounds.LARGE_EXPLOSION_ID,
			SoundSource.BLOCKS,
			effect == WarheadEffectProfile.ANTI_AIR_SAFE_SELF_DESTRUCT ? 0.25F : 0.55F,
			effect == WarheadEffectProfile.ANTI_AIR_SAFE_SELF_DESTRUCT ? 1.25F : 1.1F);
	}

	private static void spawnDebris(final ServerLevel level, final UUID impactId, final Vec3 center, final long seed,
		final List<com.andye.warmod.testtool.WarheadExplosionDropContext.DestroyedBlock> destroyedBlocks,
		final WarheadImpactProfile profile) {
		int count = Math.min(profile.maximumDebrisEntities(), destroyedBlocks.size());
		if (count <= 0) return;
		PriorityQueue<RankedDestroyedBlock> selected = new PriorityQueue<>(
			count + 1,
			Comparator.comparingLong(RankedDestroyedBlock::rank).reversed()
		);
		for (com.andye.warmod.testtool.WarheadExplosionDropContext.DestroyedBlock block : destroyedBlocks) {
			long rank = mix(block.position().asLong() ^ seed ^ 0x444542524953L);
			RankedDestroyedBlock candidate = new RankedDestroyedBlock(rank, block);
			if (selected.size() < count) {
				selected.add(candidate);
			} else if (rank < selected.peek().rank()) {
				selected.poll();
				selected.add(candidate);
			}
		}
		List<RankedDestroyedBlock> ranked = new ArrayList<>(selected);
		ranked.sort(Comparator.comparingLong(RankedDestroyedBlock::rank));
		int largeCount = Math.min(profile.maximumLargeDebrisEntities(), ranked.size());
		List<ClientboundWarheadDebrisPayload.Entry> entries = new ArrayList<>(ranked.size());
		for (int index = 0; index < ranked.size(); index++) {
			com.andye.warmod.testtool.WarheadExplosionDropContext.DestroyedBlock destroyed = ranked.get(index).block();
			BlockPos blockPosition = destroyed.position();
			BlockState originalState = destroyed.originalState();
			SplittableRandom random = new SplittableRandom(mix(seed ^ blockPosition.asLong()));
			boolean large = index < largeCount;
			Vec3 spawn = Vec3.atCenterOf(blockPosition);
			Vec3 radial = new Vec3(spawn.x - center.x, 0.0, spawn.z - center.z);
			if (radial.lengthSqr() < 1.0E-5) {
				radial = new Vec3(random.nextDouble(-1.0, 1.0), 0.0, random.nextDouble(-1.0, 1.0));
			}
			Vec3 outward = radial.normalize();
			Vec3 sideways = new Vec3(-outward.z, 0.0, outward.x);
			double normalized = Math.min(1.0, Math.sqrt(radial.lengthSqr()) / profile.debrisSampleRadius());
			double horizontal = (large ? random.nextDouble(0.15, 0.48) : random.nextDouble(0.22, 0.68))
				* profile.debrisVelocityScale();
			double vertical = (large ? random.nextDouble(0.24, 0.66) : random.nextDouble(0.30, 0.82))
				+ (1.0 - normalized) * 0.12;
			Vec3 velocity = outward.scale(horizontal)
				.add(sideways.scale(random.nextDouble(-0.08, 0.08)))
				.add(0.0, Math.min(1.12, vertical * profile.debrisVelocityScale()), 0.0);
			double spinLimit = large ? 0.14 : 0.30;
			Vec3 spin = new Vec3(
				random.nextDouble(-spinLimit, spinLimit),
				random.nextDouble(-spinLimit, spinLimit),
				random.nextDouble(-spinLimit, spinLimit)
			);
			float scale = (float) (large ? random.nextDouble(0.65, 1.05) : random.nextDouble(0.25, 0.60));
			int lifetime = large ? random.nextInt(65, 126) : random.nextInt(50, 106);
			Vec3 offset = spawn.subtract(center);
			entries.add(new ClientboundWarheadDebrisPayload.Entry(
				Block.BLOCK_STATE_REGISTRY.getId(originalState),
				(float) offset.x, (float) offset.y, (float) offset.z,
				(float) velocity.x, (float) velocity.y, (float) velocity.z,
				(float) spin.x, (float) spin.y, (float) spin.z,
				scale, lifetime
			));
		}
		WarheadVisualNetworking.sendDebris(level, new ClientboundWarheadDebrisPayload(
			impactId, center.x, center.y, center.z, level.getGameTime(), entries
		), center);
	}

	private static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private record RankedDestroyedBlock(long rank,
		com.andye.warmod.testtool.WarheadExplosionDropContext.DestroyedBlock block) {
	}
}
