package com.andye.warmod.warhead;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.diagnostics.WarheadLifecycleDiagnostics;
import com.andye.warmod.fire.wind.FireWindEngine;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.testtool.TestExplosionService;
import com.andye.warmod.testtool.WarheadExplosionDropContext;
import com.andye.warmod.warhead.network.ClientboundWarheadDebrisPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class WarheadImpactService {
	private static final int[][] DEBRIS_NEIGHBOURS = {
		{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
		{1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}
	};

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
		long impactStarted = WarModPerformanceDiagnostics.begin();
		try {
		Objects.requireNonNull(level);
		Objects.requireNonNull(id);
		Objects.requireNonNull(pos);
		Objects.requireNonNull(payloadType);
		if (!pos.isFinite()) throw new IllegalArgumentException("impactPosition must be finite");

		WarheadYield yield = WarheadYieldRegistry.resolve(level, id, radarRootTrackId, payloadType);
		boolean customFire = WarheadYieldRegistry.usesCustomFire(level, id, radarRootTrackId);
		StrategicExplosionProfile craterProfile = StrategicExplosionProfiles.get(yield);
		Vec3 effectivePosition = WarheadExplosionWorkManager.resolveDetonationCenter(level, pos, yield);
		double preparationReadiness = yield.nuclear()
			? WarheadPreparationCoordinator.readinessPercent(level, id) : 100.0;
		if (yield.nuclear()) {
			WarheadLifecycleDiagnostics.impactAttempt(level, id, preparationReadiness,
				null, pos, effectivePosition);
		}
		WarheadImpactEvent event = WarheadImpactEvent.create(id, level.getGameTime(),
			effectivePosition, yield, seed);
		/* Capture the atmospheric field before adding the radial blast impulse. The
		   persistent cloud should drift with weather, not translate away from its own
		   detonation as though the shockwave were a prevailing wind. */
		Vec3 ambientWind = FireWindEngine.windAt(level, event.impactPosition());
		FireWindEngine.addExplosionImpulse(level, event.impactPosition(),
			48.0 + yield.visualScale() * (yield.nuclear() ? 72.0 : 38.0),
			0.55 + yield.visualScale() * (yield.nuclear() ? 0.72 : 0.34),
			yield.nuclear() ? 300 : 90, yield.nuclear());
		if (registerRadarImpact) {
			RadarTrackingService.registerImpact(
				level,
				id,
				radarRootTrackId,
				event.impactPosition(),
				yield.payloadType(),
				yield.visualScale()
			);
		}

		long visualPacketStarted = WarModPerformanceDiagnostics.begin();
		ClientboundWarheadImpactPayload visualPayload = event.visualPayload(ambientWind);
		WarModPerformanceDiagnostics.record(
			WarModPerformanceDiagnostics.Subsystem.VISUAL_PACKET_PREPARATION,
			visualPacketStarted);
		WarheadVisualNetworking.sendImpact(level, visualPayload,
			event.impactPosition(), customFire, yield.nuclear());
		if (yield.nuclear()) WarheadLifecycleDiagnostics.visualImpact(level, id);

		float thudVolume = Mth.clamp(0.50F + yield.visualScale() * 0.09F, 0.55F, 1.15F);
		AcousticEngine.playSoundAtTime(
			level,
			event.impactPosition(),
			AcousticSounds.WARHEAD_IMPACT_THUD_ID,
			SoundSource.BLOCKS,
			thudVolume,
			Mth.clamp(1.08F - yield.visualScale() * 0.035F, 0.78F, 1.10F),
			event.impactServerTick(), event.acousticEventId("impact_thud"),
			event.seed() ^ 0x494D504143545448L
		);
		AcousticEngine.playSoundAtTime(
			level,
			event.impactPosition(),
			yield == WarheadYield.HIGH_EXPLOSIVE
				? AcousticSounds.TACTICAL_HE_EXPLOSION_ID
				: AcousticSounds.LARGE_EXPLOSION_ID,
			SoundSource.BLOCKS,
			yield.acousticVolume(),
			yield.acousticPitch(), event.impactServerTick(),
			event.acousticEventId("main_explosion"),
			event.seed() ^ 0x4D41494E5F424F4FL
		);

		/* Physical impact is authoritative now. Terrain preparation is deliberately
		 * sequenced after flash, sound, radar and entity blast dispatch so it can
		 * never suppress or postpone the observable detonation. */
		List<WarheadExplosionDropContext.DestroyedBlock> destroyedBlocks;
		if (yield.nuclear()) {
			WarheadExplosionWorkManager.detonateEntitiesOnly(level, owner,
				event.impactPosition(), yield);
			WarheadLifecycleDiagnostics.entityBlast(level, id);
			destroyedBlocks = TestExplosionService.captureDebris(level, id,
				event.impactPosition(), yield, seed);
			WarheadPreImpactPreparationManager.invalidateAround(level, id,
				event.impactPosition(), yield,
				WarheadFootprintCalculator.calculate(yield.payloadType(), yield,
					event.impactPosition()).maximumMutationRadius());
			ConsumedPreparedImpact sealed = WarheadPreparationCoordinator.sealImpact(
				level, radarRootTrackId, id, radarRootTrackId,
				event.impactPosition(), yield, seed, customFire);
			boolean commitStarted = sealed != null && WarheadPreparedCommitManager.begin(level,
				sealed.preparationId(), sealed.plan(), owner, yield, seed, customFire);
			if (!commitStarted && !WarheadPreparedCommitManager.active(level, id)) {
				WarMod.LOGGER.error("Could not begin detached prepared terrain commit for {}; "
					+ "starting the complete bounded live compiler", id);
				if (sealed != null) WarheadPreparationCoordinator.completeCommit(level,
					sealed.preparationId(), id);
				commitStarted = WarheadPreparedCommitManager.beginLiveFallback(level, id,
					event.impactPosition(), owner, yield, seed, customFire);
			}
			if (!commitStarted && !WarheadPreparedCommitManager.active(level, id)) {
				WarMod.LOGGER.error("No complete terrain commit owner could be established for {}",
					id);
			}
		} else {
			destroyedBlocks = TestExplosionService.createExplosion(level, owner, id,
				event.impactPosition(), event.yield(), event.seed(), customFire);
		}
		spawnDebris(level, event, destroyedBlocks, craterProfile);

		if (SharedConstants.IS_RUNNING_IN_IDE) {
			WarMod.LOGGER.info("Warhead {} impacted: sequence={}, yield={}, position={}",
				id, event.impactSequence(), yield.getSerializedName(), event.impactPosition());
		}
		} finally {
			WarModPerformanceDiagnostics.record(
				WarModPerformanceDiagnostics.Subsystem.IMPACT_SERVICE_TOTAL,
				impactStarted);
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
		Vec3 ambientWind = FireWindEngine.windAt(level, pos);
		WarheadVisualNetworking.sendImpact(level, new ClientboundWarheadImpactPayload(
			id, pos.x, pos.y, pos.z, level.getGameTime(), seed,
			WarheadPayloadType.CONVENTIONAL, scale, (float) ambientWind.x,
			(float) ambientWind.z, effect
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
		final WarheadImpactEvent event,
		final List<WarheadExplosionDropContext.DestroyedBlock> destroyedBlocks,
		final StrategicExplosionProfile craterProfile
	) {
		UUID impactId = event.impactId();
		Vec3 center = event.impactPosition();
		long seed = event.seed();
		WarheadYield yield = event.yield();
		int blockBudget = Math.min(yield.maximumDebris(), destroyedBlocks.size());
		if (blockBudget <= 0) return;
		PriorityQueue<RankedDestroyedBlock> selected = new PriorityQueue<>(
			blockBudget + 1,
			Comparator.comparingLong(RankedDestroyedBlock::rank).reversed()
		);
		for (WarheadExplosionDropContext.DestroyedBlock block : destroyedBlocks) {
			/* Surface and structure samples rank ahead of deep homogeneous material. */
			long rank = mix(block.position().asLong() ^ seed ^ 0x444542524953L);
			RankedDestroyedBlock candidate = new RankedDestroyedBlock(rank, block);
			if (selected.size() < blockBudget) selected.add(candidate);
			else if (rank < selected.peek().rank()) { selected.poll(); selected.add(candidate); }
		}
		List<RankedDestroyedBlock> ranked = new ArrayList<>(selected);
		ranked.sort(Comparator.comparingLong(RankedDestroyedBlock::rank));
		Long2ObjectOpenHashMap<WarheadExplosionDropContext.DestroyedBlock> available =
			new Long2ObjectOpenHashMap<>(Math.max(16, ranked.size() * 2));
		for (RankedDestroyedBlock entry : ranked) available.put(entry.block().position().asLong(), entry.block());

		List<ClientboundWarheadDebrisPayload.Entry> entries = new ArrayList<>();
		int consumedBlocks = 0;
		for (int rootIndex = 0; rootIndex < ranked.size() && consumedBlocks < blockBudget && entries.size() < 320; rootIndex++) {
			WarheadExplosionDropContext.DestroyedBlock rankedRoot = ranked.get(rootIndex).block();
			WarheadExplosionDropContext.DestroyedBlock root = available.remove(rankedRoot.position().asLong());
			if (root == null) continue;
			SplittableRandom random = new SplittableRandom(mix(seed ^ root.position().asLong()));
			boolean large = consumedBlocks < yield.maximumLargeDebris();
			int desiredParts = large
				? (yield.nuclear() ? random.nextInt(22, 49) : random.nextInt(10, 37))
				: (random.nextDouble() < 0.52 ? random.nextInt(3, 13) : random.nextInt(1, 5));
			desiredParts = Math.min(desiredParts, blockBudget - consumedBlocks);

			BlockPos rootPos = root.position();
			ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
			frontier.add(rootPos);
			List<ClientboundWarheadDebrisPayload.Part> parts = new ArrayList<>(desiredParts);
			parts.add(new ClientboundWarheadDebrisPayload.Part(
				Block.BLOCK_STATE_REGISTRY.getId(root.originalState()), (byte) 0, (byte) 0, (byte) 0));
			consumedBlocks++;
			while (!frontier.isEmpty() && parts.size() < desiredParts) {
				BlockPos current = frontier.removeFirst();
				int phase = random.nextInt(DEBRIS_NEIGHBOURS.length);
				for (int index = 0; index < DEBRIS_NEIGHBOURS.length && parts.size() < desiredParts; index++) {
					int[] offset = DEBRIS_NEIGHBOURS[(index + phase) % DEBRIS_NEIGHBOURS.length];
					BlockPos candidatePos = current.offset(offset[0], offset[1], offset[2]);
					int dx = candidatePos.getX() - rootPos.getX();
					int dy = candidatePos.getY() - rootPos.getY();
					int dz = candidatePos.getZ() - rootPos.getZ();
					if (Math.abs(dx) > 12 || Math.abs(dy) > 12 || Math.abs(dz) > 12) continue;
					WarheadExplosionDropContext.DestroyedBlock candidate = available.remove(candidatePos.asLong());
					if (candidate == null) continue;
					parts.add(new ClientboundWarheadDebrisPayload.Part(
						Block.BLOCK_STATE_REGISTRY.getId(candidate.originalState()),
						(byte) dx, (byte) dy, (byte) dz));
					frontier.addLast(candidatePos);
					consumedBlocks++;
				}
			}

			Vec3 spawn = Vec3.atCenterOf(rootPos);
			Vec3 radial = new Vec3(spawn.x - center.x, 0.0, spawn.z - center.z);
			if (radial.lengthSqr() < 1.0E-5) radial = new Vec3(random.nextDouble(-1.0, 1.0), 0.0, random.nextDouble(-1.0, 1.0));
			Vec3 outward = radial.normalize();
			Vec3 sideways = new Vec3(-outward.z, 0.0, outward.x);
			double normalized = Math.min(1.0, Math.sqrt(radial.lengthSqr()) / Math.max(1.0, craterProfile.horizontalRadius()));
			double massFactor = 1.0 / Math.sqrt(Math.max(1.0, parts.size() * 0.34));
			double horizontal = random.nextDouble(large ? 0.22 : 0.34, large ? 0.62 : 0.88)
				* yield.debrisVelocityScale() * (0.72 + 0.28 * massFactor);
			double vertical = random.nextDouble(large ? 1.28 : 0.82, large ? 2.72 : 1.92)
				* (0.82 + 0.18 * massFactor)
				+ (1.0 - normalized) * (yield.nuclear() ? 1.05 : 0.58);
			Vec3 velocity = outward.scale(horizontal)
				.add(sideways.scale(random.nextDouble(-0.20, 0.20)))
				.add(0.0, Math.min(yield.nuclear() ? 4.35 : 3.05, vertical * yield.debrisVelocityScale()), 0.0);
			double spinLimit = parts.size() > 6 ? 0.045 : 0.12;
			Vec3 spin = new Vec3(random.nextDouble(-spinLimit, spinLimit),
				random.nextDouble(-spinLimit, spinLimit), random.nextDouble(-spinLimit, spinLimit));
			int lifetime = random.nextInt(yield.nuclear() ? 160 : 105, yield.nuclear() ? 330 : 235);
			Vec3 offset = spawn.subtract(center);
			entries.add(new ClientboundWarheadDebrisPayload.Entry(
				(float) offset.x, (float) offset.y, (float) offset.z,
				(float) velocity.x, (float) velocity.y, (float) velocity.z,
				(float) spin.x, (float) spin.y, (float) spin.z,
				1.0F, lifetime, List.copyOf(parts)
			));
		}
		long debrisPartCount = 0L;
		for (ClientboundWarheadDebrisPayload.Entry entry : entries)
			debrisPartCount += entry.parts().size();
		WarModPerformanceDiagnostics.add(
			WarModPerformanceDiagnostics.Gauge.DEBRIS_PARTS_GENERATED,
			debrisPartCount);
		WarheadVisualNetworking.sendDebris(level, new ClientboundWarheadDebrisPayload(
			impactId, center.x, center.y, center.z, event.impactServerTick(),
			yield.nuclear(), entries
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
