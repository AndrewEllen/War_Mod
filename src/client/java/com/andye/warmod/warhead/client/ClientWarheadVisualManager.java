package com.andye.warmod.warhead.client;

import com.andye.warmod.acoustics.client.ExplosionShakeManager;
import com.andye.warmod.diagnostics.client.ClientPerformanceTelemetry;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadEffectProfile;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.WarheadYieldScaling;
import com.andye.warmod.warhead.client.render.ConventionalBlastParticleRenderer;
import com.andye.warmod.warhead.client.render.NuclearParticleCloudRenderer;
import com.andye.warmod.particle.gpu.GpuParticleEngine;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadRemovePayload;
import com.andye.warmod.warhead.network.ClientboundWarheadTimingCorrectionPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class ClientWarheadVisualManager {
	public static final ClientWarheadVisualManager INSTANCE = new ClientWarheadVisualManager();
	private static final long TERRAIN_BUILD_BUDGET_NANOS = 900_000L;
	private static final int TERRAIN_BUILD_SAFETY_LIMIT = 8_192;
	private static final double TERRAIN_LOOKAHEAD_BLOCKS = 96.0;

	private final Map<UUID, WarheadVisualState> activeWarheads = new LinkedHashMap<>();
	private final Map<UUID, ImpactVisualState> activeImpacts = new LinkedHashMap<>();
	private final Map<UUID, TerrainShockfrontField> preImpactShockfronts = new LinkedHashMap<>();
	private final Set<UUID> volumetricImpacts = new HashSet<>();
	private final Set<UUID> deliveredVisualShake = new HashSet<>();
	private final Set<UUID> deliveredReturnShake = new HashSet<>();
	private final Set<UUID> nuclearFlashExposed = new HashSet<>();
	private final Map<UUID, Long> highestStateSequences = new LinkedHashMap<>();
	private final Set<UUID> terminalWarheads = new HashSet<>();
	private ClientLevel activeLevel;

	private ClientWarheadVisualManager() {
	}

	public synchronized void acceptLaunch(final ClientboundWarheadLaunchPayload payload) {
		if (!payload.isWellFormed() || !this.ensureCurrentLevel(Minecraft.getInstance().level)) return;
		if (!this.acceptSequence(payload.warheadId(), payload.stateSequence(), false)) return;
		this.activeWarheads.remove(payload.warheadId());
		this.removeOldestIfAtCapacity(this.activeWarheads, WarheadConstants.MAX_ACTIVE_CLIENT_WARHEADS);
		this.activeWarheads.put(payload.warheadId(), WarheadVisualState.fromPayload(payload));
		this.preImpactShockfronts.put(payload.warheadId(), new TerrainShockfrontField(
			new Vec3(payload.targetX(), payload.targetY(), payload.targetZ()), payload.visualSeed()));
	}

	public synchronized void acceptImpact(final ClientboundWarheadImpactPayload payload) {
		long payloadStarted = System.nanoTime();
		try {
			this.acceptImpactInternal(payload);
		} finally {
			ClientPerformanceTelemetry.recordImpactPayloadAcceptNanos(
				Math.max(0L, System.nanoTime() - payloadStarted));
		}
	}

	private void acceptImpactInternal(final ClientboundWarheadImpactPayload payload) {
		if (!payload.isWellFormed() || !this.ensureCurrentLevel(Minecraft.getInstance().level)) return;
		if (!this.acceptSequence(payload.warheadId(), payload.stateSequence(), true)) return;
		ImpactVisualState existing = this.activeImpacts.get(payload.warheadId());
		if (existing != null
			&& existing.visualSeed() == payload.visualSeed()
			&& existing.payloadType() == payload.payloadType()
			&& existing.impactGameTime() == payload.impactGameTime()) {
			double dx = existing.impactPosition().x - payload.impactX();
			double dy = existing.impactPosition().y - payload.impactY();
			double dz = existing.impactPosition().z - payload.impactZ();
			if (dx * dx + dy * dy + dz * dz <= 1.0E-6) return;
		}
		this.activeWarheads.remove(payload.warheadId());
		this.activeImpacts.remove(payload.warheadId());
		this.volumetricImpacts.remove(payload.warheadId());
		this.deliveredVisualShake.remove(payload.warheadId());
		this.deliveredReturnShake.remove(payload.warheadId());
		Vec3 incomingPosition = new Vec3(payload.impactX(), payload.impactY(), payload.impactZ());
		TerrainShockfrontField prepared = this.preImpactShockfronts.remove(payload.warheadId());
		if (prepared != null && (prepared.visualSeed() != payload.visualSeed()
			|| prepared.impactPosition().distanceToSqr(incomingPosition) > 16.0)) prepared = null;
		long constructionStarted = System.nanoTime();
		ImpactVisualState incoming = ImpactVisualState.fromPayload(payload, prepared);
		ClientPerformanceTelemetry.recordImpactVisualStateConstructionNanos(
			Math.max(0L, System.nanoTime() - constructionStarted));
		this.nuclearFlashExposed.remove(payload.warheadId());
		if (incoming.payloadType() == WarheadPayloadType.NUCLEAR
			&& hasDirectNuclearView(Minecraft.getInstance(), incoming.impactPosition())) {
			this.nuclearFlashExposed.add(payload.warheadId());
		}
		this.assignVolumetricSlot(payload.warheadId(), incoming);
		this.removeOldestImpactIfAtCapacity(WarheadConstants.MAX_ACTIVE_CLIENT_IMPACTS);
		this.activeImpacts.put(payload.warheadId(), incoming);
	}

	public synchronized void acceptTimingCorrection(final ClientboundWarheadTimingCorrectionPayload payload) {
		if (!payload.isWellFormed() || !this.ensureCurrentLevel(Minecraft.getInstance().level)) return;
		if (!this.acceptSequence(payload.warheadId(), payload.stateSequence(), false)) return;
		WarheadVisualState state = this.activeWarheads.get(payload.warheadId());
		if (state != null) state.applyTimingCorrection(payload);
	}

	public synchronized void acceptRemove(final ClientboundWarheadRemovePayload payload) {
		if (!payload.isWellFormed() || !this.ensureCurrentLevel(Minecraft.getInstance().level)) return;
		if (!this.acceptSequence(payload.warheadId(), payload.stateSequence(), true)) return;
		this.activeWarheads.remove(payload.warheadId());
		this.preImpactShockfronts.remove(payload.warheadId());
	}

	public synchronized void tick(final Minecraft client) {
		if (!this.ensureCurrentLevel(client.level)) return;
		long gameTime = client.level.getGameTime();
		TerrainSurfaceCache.INSTANCE.beginTick(client.level, gameTime);
		ClientDebrisBatchManager.INSTANCE.tick(client.level, gameTime);

		Iterator<Map.Entry<UUID, WarheadVisualState>> warheadIterator = this.activeWarheads.entrySet().iterator();
		while (warheadIterator.hasNext()) {
			Map.Entry<UUID, WarheadVisualState> entry = warheadIterator.next();
			if (entry.getValue().isExpired(gameTime, 0.0)) {
				this.preImpactShockfronts.remove(entry.getKey());
				warheadIterator.remove();
			}
		}
		Iterator<Map.Entry<UUID, ImpactVisualState>> impactIterator = this.activeImpacts.entrySet().iterator();
		while (impactIterator.hasNext()) {
			Map.Entry<UUID, ImpactVisualState> entry = impactIterator.next();
			if (entry.getValue().isExpired(gameTime, 0.0)) {
				this.volumetricImpacts.remove(entry.getKey());
				this.deliveredVisualShake.remove(entry.getKey());
				this.deliveredReturnShake.remove(entry.getKey());
				this.nuclearFlashExposed.remove(entry.getKey());
				impactIterator.remove();
			}
		}

		this.deliverSupplementalImpactShake(client, gameTime);
		this.deliverNuclearReturnShake(client, gameTime);

		long terrainStarted = System.nanoTime();
		long terrainDeadline = terrainStarted + TERRAIN_BUILD_BUDGET_NANOS;
		int remainingImpactCount = Math.max(1, this.activeImpacts.size());
		for (ImpactVisualState state : this.activeImpacts.values()) {
			long nowNanos = System.nanoTime();
			if (nowNanos >= terrainDeadline) break;
			double age = state.ageTicks(gameTime, 0.0);
			if (state.payloadType() == WarheadPayloadType.NUCLEAR) {
				age *= WarheadVisualMath.NUCLEAR_TIME_SCALE;
			}
			float radiusScale = WarheadYieldScaling.radiusScale(state.payloadType(), state.visualScale());
			double requiredDistance = Math.min(
				TerrainShockfrontField.MAX_HORIZONTAL_RANGE,
				WarheadVisualMath.groundShockwaveDistance(age, radiusScale) + TERRAIN_LOOKAHEAD_BLOCKS
			);
			long sliceDeadline = nowNanos + Math.max(1L,
				(terrainDeadline - nowNanos) / remainingImpactCount);
			state.terrainShockfrontField().buildToDistanceUntil(
				client.level,
				requiredDistance,
				TERRAIN_BUILD_SAFETY_LIMIT,
				sliceDeadline
			);
			remainingImpactCount--;
		}
		int remainingWarnings = Math.max(1, this.activeWarheads.size());
		for (Map.Entry<UUID, WarheadVisualState> entry : this.activeWarheads.entrySet()) {
			long nowNanos = System.nanoTime();
			if (nowNanos >= terrainDeadline) break;
			TerrainShockfrontField field = this.preImpactShockfronts.get(entry.getKey());
			if (field == null) continue;
			long sliceDeadline = nowNanos + Math.max(1L,
				(terrainDeadline - nowNanos) / remainingWarnings);
			field.buildToDistanceUntil(client.level, TerrainShockfrontField.MAX_HORIZONTAL_RANGE,
				TERRAIN_BUILD_SAFETY_LIMIT, sliceDeadline);
			remainingWarnings--;
		}
		ClientPerformanceTelemetry.recordTerrainShockfrontNanos(
			Math.max(0L, System.nanoTime() - terrainStarted));

	}

	public synchronized void clear() {
		this.activeWarheads.clear();
		this.activeImpacts.clear();
		this.preImpactShockfronts.clear();
		this.volumetricImpacts.clear();
		this.deliveredVisualShake.clear();
		this.deliveredReturnShake.clear();
		this.nuclearFlashExposed.clear();
		this.highestStateSequences.clear();
		this.terminalWarheads.clear();
		TerrainSurfaceCache.INSTANCE.clear();
		ClientDebrisBatchManager.INSTANCE.clear();
		ConventionalBlastParticleRenderer.clearLevel();
		NuclearParticleCloudRenderer.clearLevel();
		GpuParticleEngine.clearLevel();
		this.activeLevel = null;
	}

	public synchronized Snapshot snapshot(final ClientLevel level) {
		if (level == null || this.activeLevel != level) return Snapshot.EMPTY;
		return new Snapshot(List.copyOf(this.activeWarheads.values()), List.copyOf(this.activeImpacts.values()));
	}

	private boolean ensureCurrentLevel(final ClientLevel level) {
		if (level == null) {
			this.activeWarheads.clear();
			this.activeImpacts.clear();
			this.preImpactShockfronts.clear();
			this.volumetricImpacts.clear();
			this.deliveredVisualShake.clear();
			this.deliveredReturnShake.clear();
			this.nuclearFlashExposed.clear();
			this.highestStateSequences.clear();
			this.terminalWarheads.clear();
			TerrainSurfaceCache.INSTANCE.clear();
			ClientDebrisBatchManager.INSTANCE.clear();
			ConventionalBlastParticleRenderer.clearLevel();
			NuclearParticleCloudRenderer.clearLevel();
			GpuParticleEngine.clearLevel();
			this.activeLevel = null;
			return false;
		}
		if (this.activeLevel != level) {
			this.activeWarheads.clear();
			this.activeImpacts.clear();
			this.preImpactShockfronts.clear();
			this.volumetricImpacts.clear();
			this.deliveredVisualShake.clear();
			this.deliveredReturnShake.clear();
			this.nuclearFlashExposed.clear();
			this.highestStateSequences.clear();
			this.terminalWarheads.clear();
			TerrainSurfaceCache.INSTANCE.clear();
			ClientDebrisBatchManager.INSTANCE.clear();
			ConventionalBlastParticleRenderer.clearLevel();
			NuclearParticleCloudRenderer.clearLevel();
			GpuParticleEngine.clearLevel();
			this.activeLevel = level;
		}
		return true;
	}

	private boolean acceptSequence(final UUID id, final long sequence,
		final boolean terminal) {
		if (id == null || sequence <= this.highestStateSequences.getOrDefault(id, 0L)
			|| this.terminalWarheads.contains(id)) return false;
		this.highestStateSequences.put(id, sequence);
		if (terminal) this.terminalWarheads.add(id);
		while (this.highestStateSequences.size() > 512) {
			UUID oldest = this.highestStateSequences.keySet().iterator().next();
			this.highestStateSequences.remove(oldest);
			this.terminalWarheads.remove(oldest);
		}
		return true;
	}


	private void deliverSupplementalImpactShake(final Minecraft client, final long gameTime) {
		if (client.player == null) return;
		Vec3 listener = client.player.position();
		for (Map.Entry<UUID, ImpactVisualState> entry : this.activeImpacts.entrySet()) {
			UUID id = entry.getKey();
			if (this.deliveredVisualShake.contains(id)) continue;
			ImpactVisualState state = entry.getValue();
			boolean needsSupplement = state.payloadType() == WarheadPayloadType.NUCLEAR
				|| state.effectProfile() == WarheadEffectProfile.TACTICAL_HE;
			if (!needsSupplement) continue;
			double distance = listener.distanceTo(state.impactPosition());
			double arrivalTick = distance / WarheadVisualMath.AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
			if (state.ageTicks(gameTime, 0.0) + 1.0E-4 < arrivalTick) continue;
			float radiusScale = WarheadYieldScaling.radiusScale(state.payloadType(), state.visualScale());
			ExplosionShakeManager.INSTANCE.addVisualImpact(
				state.visualSeed(),
				distance,
				radiusScale,
				state.payloadType() == WarheadPayloadType.NUCLEAR
			);
			this.deliveredVisualShake.add(id);
		}
	}

	private void deliverNuclearReturnShake(final Minecraft client, final long gameTime) {
		if (client.player == null) return;
		Vec3 listener = client.player.position();
		for (Map.Entry<UUID, ImpactVisualState> entry : this.activeImpacts.entrySet()) {
			UUID id = entry.getKey();
			if (this.deliveredReturnShake.contains(id)) continue;
			ImpactVisualState state = entry.getValue();
			if (state.payloadType() != WarheadPayloadType.NUCLEAR) continue;
			double distance = listener.distanceTo(state.impactPosition());
			float radiusScale = WarheadYieldScaling.radiusScale(state.payloadType(), state.visualScale());
			double maximum = WarheadVisualMath.nuclearReturnWaveMaximumRadius(radiusScale);
			if (distance > maximum) { this.deliveredReturnShake.add(id); continue; }
			double arrival = WarheadVisualMath.nuclearReturnWaveStartTicks(radiusScale)
				+ (maximum - distance) / WarheadVisualMath.AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
			if (state.ageTicks(gameTime, 0.0) + 1.0E-4 < arrival) continue;
			ExplosionShakeManager.INSTANCE.addVisualImpact(
				state.visualSeed() ^ 0x52455455524E5741L, distance, radiusScale * 0.72, true);
			this.deliveredReturnShake.add(id);
		}
	}

	/**
	 * Keeps all impact states for their pressure shells and terrain fronts, but
	 * limits the expensive fire/smoke field when those fields occupy the same
	 * volume. The newest impact receives the slot and the oldest overlapping
	 * volumetric is demoted to shockwave-only rendering.
	 */
	private void assignVolumetricSlot(final UUID incomingId, final ImpactVisualState incoming) {
		int maximum = incoming.payloadType() == com.andye.warmod.warhead.WarheadPayloadType.NUCLEAR ? 1 : 2;
		float radiusScale = WarheadYieldScaling.radiusScale(incoming.payloadType(), incoming.visualScale());
		double radius = (incoming.payloadType() == com.andye.warmod.warhead.WarheadPayloadType.NUCLEAR
			? 48.0 : 14.0) * radiusScale;
		if (incoming.effectProfile().name().startsWith("ANTI_AIR")) {
			maximum = 4;
			radius = 5.0;
		}

		double radiusSquared = radius * radius;
		ArrayList<UUID> overlapping = new ArrayList<>();
		for (Map.Entry<UUID, ImpactVisualState> entry : this.activeImpacts.entrySet()) {
			if (!this.volumetricImpacts.contains(entry.getKey())) continue;
			ImpactVisualState existing = entry.getValue();
			if (existing.payloadType() != incoming.payloadType()
				|| existing.effectProfile() != incoming.effectProfile()) continue;
			if (existing.impactPosition().distanceToSqr(incoming.impactPosition()) <= radiusSquared) {
				overlapping.add(entry.getKey());
			}
		}
		for (int index = 0; index <= overlapping.size() - maximum; index++) {
			this.volumetricImpacts.remove(overlapping.get(index));
		}
		this.volumetricImpacts.add(incomingId);
	}

	public synchronized boolean shouldRenderVolumetrics(final UUID impactId) {
		return impactId != null && this.volumetricImpacts.contains(impactId);
	}

	public synchronized boolean isNuclearFlashExposed(final UUID impactId) {
		return impactId != null && this.nuclearFlashExposed.contains(impactId);
	}

	private static boolean hasDirectNuclearView(final Minecraft client,
		final Vec3 impactPosition) {
		if (client.level == null || client.player == null
			|| !client.level.isLoaded(BlockPos.containing(impactPosition))) return false;
		Vec3 eye = client.player.getEyePosition();
		Vec3 towardImpact = impactPosition.subtract(eye);
		if (!towardImpact.isFinite() || towardImpact.lengthSqr() < 1.0E-6) return true;
		if (client.player.getViewVector(1.0F).normalize().dot(towardImpact.normalize()) < 0.50) {
			return false;
		}
		BlockHitResult hit = client.level.clip(new ClipContext(
			eye, impactPosition, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
			CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS
			|| hit.getLocation().distanceToSqr(impactPosition) <= 16.0;
	}

	private void removeOldestImpactIfAtCapacity(final int capacity) {
		while (this.activeImpacts.size() >= capacity) {
			Iterator<UUID> iterator = this.activeImpacts.keySet().iterator();
			if (!iterator.hasNext()) return;
			UUID removed = iterator.next();
			iterator.remove();
			this.volumetricImpacts.remove(removed);
			this.deliveredVisualShake.remove(removed);
			this.deliveredReturnShake.remove(removed);
			this.nuclearFlashExposed.remove(removed);
		}
	}

	private <T> void removeOldestIfAtCapacity(final Map<UUID, T> states, final int capacity) {
		while (states.size() >= capacity) {
			Iterator<UUID> iterator = states.keySet().iterator();
			if (!iterator.hasNext()) return;
			UUID removed = iterator.next();
			iterator.remove();
			if (states == this.activeWarheads) this.preImpactShockfronts.remove(removed);
		}
	}

	public record Snapshot(List<WarheadVisualState> warheads, List<ImpactVisualState> impacts) {
		private static final Snapshot EMPTY = new Snapshot(List.of(), List.of());
	}
}
