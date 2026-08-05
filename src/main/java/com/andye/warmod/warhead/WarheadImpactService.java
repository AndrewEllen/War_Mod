package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.testtool.TestExplosionService;
import com.andye.warmod.testtool.WarheadExplosionDropContext;
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
import net.minecraft.util.Mth;
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
		Objects.requireNonNull(level);
		Objects.requireNonNull(id);
		Objects.requireNonNull(pos);
		Objects.requireNonNull(payloadType);
		if (!pos.isFinite()) throw new IllegalArgumentException("impactPosition must be finite");

		WarheadYield yield = WarheadYieldRegistry.resolve(level, id, radarRootTrackId, payloadType);
		StrategicExplosionProfile craterProfile = StrategicExplosionProfiles.get(yield);
		Vec3 effectivePosition = WarheadExplosionWorkManager.resolveDetonationCenter(level, pos, yield);
		if (registerRadarImpact) {
			RadarTrackingService.registerImpact(
				level,
				id,
				radarRootTrackId,
				effectivePosition,
				yield.payloadType(),
				yield.visualScale()
			);
		}

		WarheadVisualNetworking.sendImpact(level, new ClientboundWarheadImpactPayload(
			id,
			effectivePosition.x,
			effectivePosition.y,
			effectivePosition.z,
			level.getGameTime(),
			seed,
			yield.payloadType(),
			yield.visualScale(),
			yield.effectProfile()
		), effectivePosition);

		List<WarheadExplosionDropContext.DestroyedBlock> destroyedBlocks = TestExplosionService.createExplosion(
			level,
			owner,
			id,
			effectivePosition,
			yield,
			seed
		);
		spawnDebris(level, id, effectivePosition, seed, destroyedBlocks, yield, craterProfile);

		float thudVolume = Mth.clamp(0.50F + yield.visualScale() * 0.09F, 0.55F, 1.15F);
		AcousticEngine.playSound(
			level,
			effectivePosition,
			AcousticSounds.WARHEAD_IMPACT_THUD_ID,
			SoundSource.BLOCKS,
			thudVolume,
			Mth.clamp(1.08F - yield.visualScale() * 0.035F, 0.78F, 1.10F)
		);
		AcousticEngine.playSound(
			level,
			effectivePosition,
			yield == WarheadYield.HIGH_EXPLOSIVE
				? AcousticSounds.TACTICAL_HE_EXPLOSION_ID
				: AcousticSounds.LARGE_EXPLOSION_ID,
			SoundSource.BLOCKS,
			yield.acousticVolume(),
			yield.acousticPitch()
		);

		if (SharedConstants.IS_RUNNING_IN_IDE) {
			WarMod.LOGGER.info("Warhead {} impacted: yield={}, position={}", id, yield.getSerializedName(), effectivePosition);
		}
	}

	public static void detonateTacticalHe(final ServerLevel level, final @Nullable ServerPlayer owner,
		final UUID id, final Vec3 pos, final long seed) {
		WarheadYieldRegistry.put(level, id, WarheadYield.HIGH_EXPLOSIVE);
		detonateAt(level, owner, id, id, pos, seed, WarheadPayloadType.CONVENTIONAL, false);
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
			id, pos.x, pos.y, pos.z, level.getGameTime(), seed,
			WarheadPayloadType.CONVENTIONAL, scale, effect
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

	private static void spawnDebris(
		final ServerLevel level,
		final UUID impactId,
		final Vec3 center,
		final long seed,
		final List<WarheadExplosionDropContext.DestroyedBlock> destroyedBlocks,
		final WarheadYield yield,
		final StrategicExplosionProfile craterProfile
	) {
		int count = Math.min(yield.maximumDebris(), destroyedBlocks.size());
		if (count <= 0) return;
		PriorityQueue<RankedDestroyedBlock> selected = new PriorityQueue<>(
			count + 1,
			Comparator.comparingLong(RankedDestroyedBlock::rank).reversed()
		);
		for (WarheadExplosionDropContext.DestroyedBlock block : destroyedBlocks) {
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
		int largeCount = Math.min(yield.maximumLargeDebris(), ranked.size());
		List<ClientboundWarheadDebrisPayload.Entry> entries = new ArrayList<>(ranked.size());
		for (int index = 0; index < ranked.size(); index++) {
			WarheadExplosionDropContext.DestroyedBlock destroyed = ranked.get(index).block();
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
			double normalized = Math.min(1.0, Math.sqrt(radial.lengthSqr()) / craterProfile.horizontalRadius());
			double horizontal = (large ? random.nextDouble(0.20, 0.62) : random.nextDouble(0.28, 0.82))
				* yield.debrisVelocityScale();
			double vertical = (large ? random.nextDouble(0.42, 1.05) : random.nextDouble(0.38, 0.96))
				+ (1.0 - normalized) * (yield.nuclear() ? 0.38 : 0.18);
			Vec3 velocity = outward.scale(horizontal)
				.add(sideways.scale(random.nextDouble(-0.12, 0.12)))
				.add(0.0, Math.min(yield.nuclear() ? 2.1 : 1.35, vertical * yield.debrisVelocityScale()), 0.0);
			double spinLimit = large ? 0.075 : 0.24;
			Vec3 spin = new Vec3(
				random.nextDouble(-spinLimit, spinLimit),
				random.nextDouble(-spinLimit, spinLimit),
				random.nextDouble(-spinLimit, spinLimit)
			);

			int clusterSize;
			float scale;
			if (large && yield.nuclear()) {
				clusterSize = index < Math.min(32, largeCount) ? random.nextInt(2, 4) : random.nextInt(1, 3);
				scale = (float) random.nextDouble(0.82, 1.42);
			} else if (large) {
				clusterSize = random.nextDouble() < 0.32 ? 2 : 1;
				scale = (float) random.nextDouble(0.72, 1.18);
			} else {
				clusterSize = 1;
				scale = (float) random.nextDouble(0.25, 0.66);
			}
			int lifetime = large
				? random.nextInt(yield.nuclear() ? 95 : 70, yield.nuclear() ? 190 : 135)
				: random.nextInt(50, 115);
			Vec3 offset = spawn.subtract(center);
			entries.add(new ClientboundWarheadDebrisPayload.Entry(
				Block.BLOCK_STATE_REGISTRY.getId(originalState),
				(float) offset.x, (float) offset.y, (float) offset.z,
				(float) velocity.x, (float) velocity.y, (float) velocity.z,
				(float) spin.x, (float) spin.y, (float) spin.z,
				scale, clusterSize, lifetime
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

	private record RankedDestroyedBlock(long rank, WarheadExplosionDropContext.DestroyedBlock block) {
	}
}
