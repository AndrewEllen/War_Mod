package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadRemovePayload;
import com.andye.warmod.warhead.network.ClientboundWarheadTimingCorrectionPayload;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

public final class ClientWarheadVisualManager {
	public static final ClientWarheadVisualManager INSTANCE = new ClientWarheadVisualManager();
	private static final int TOTAL_TERRAIN_BUILD_BUDGET = 32_768;
	private static final int MAX_TERRAIN_BUILD_PER_IMPACT = 8_192;
	private static final double TERRAIN_LOOKAHEAD_BLOCKS = 36.0;

	private final Map<UUID, WarheadVisualState> activeWarheads = new LinkedHashMap<>();
	private final Map<UUID, ImpactVisualState> activeImpacts = new LinkedHashMap<>();
	private ClientLevel activeLevel;

	private ClientWarheadVisualManager() {
	}

	public synchronized void acceptLaunch(final ClientboundWarheadLaunchPayload payload) {
		if (!payload.isWellFormed() || !this.ensureCurrentLevel(Minecraft.getInstance().level)) return;
		this.activeWarheads.remove(payload.warheadId());
		this.removeOldestIfAtCapacity(this.activeWarheads, WarheadConstants.MAX_ACTIVE_CLIENT_WARHEADS);
		this.activeWarheads.put(payload.warheadId(), WarheadVisualState.fromPayload(payload));
	}

	public synchronized void acceptImpact(final ClientboundWarheadImpactPayload payload) {
		if (!payload.isWellFormed() || !this.ensureCurrentLevel(Minecraft.getInstance().level)) return;
		this.activeWarheads.remove(payload.warheadId());
		this.activeImpacts.remove(payload.warheadId());
		this.removeOldestIfAtCapacity(this.activeImpacts, WarheadConstants.MAX_ACTIVE_CLIENT_IMPACTS);
		this.activeImpacts.put(payload.warheadId(), ImpactVisualState.fromPayload(payload));
	}

	public synchronized void acceptTimingCorrection(final ClientboundWarheadTimingCorrectionPayload payload) {
		if (!payload.isWellFormed() || !this.ensureCurrentLevel(Minecraft.getInstance().level)) return;
		WarheadVisualState state = this.activeWarheads.get(payload.warheadId());
		if (state != null) state.applyTimingCorrection(payload);
	}

	public synchronized void acceptRemove(final ClientboundWarheadRemovePayload payload) {
		if (!payload.isWellFormed() || !this.ensureCurrentLevel(Minecraft.getInstance().level)) return;
		this.activeWarheads.remove(payload.warheadId());
	}

	public synchronized void tick(final Minecraft client) {
		if (!this.ensureCurrentLevel(client.level)) return;
		long gameTime = client.level.getGameTime();
		TerrainSurfaceCache.INSTANCE.beginTick(client.level, gameTime);
		ClientDebrisBatchManager.INSTANCE.tick(client.level, gameTime);

		Iterator<Map.Entry<UUID, WarheadVisualState>> warheadIterator = this.activeWarheads.entrySet().iterator();
		while (warheadIterator.hasNext()) {
			if (warheadIterator.next().getValue().isExpired(gameTime, 0.0)) warheadIterator.remove();
		}
		Iterator<Map.Entry<UUID, ImpactVisualState>> impactIterator = this.activeImpacts.entrySet().iterator();
		while (impactIterator.hasNext()) {
			if (impactIterator.next().getValue().isExpired(gameTime, 0.0)) impactIterator.remove();
		}

		int remainingTerrainBudget = TOTAL_TERRAIN_BUILD_BUDGET;
		int impactCount = Math.max(1, this.activeImpacts.size());
		int fairShare = Math.min(MAX_TERRAIN_BUILD_PER_IMPACT, Math.max(512, remainingTerrainBudget / impactCount));
		for (ImpactVisualState state : this.activeImpacts.values()) {
			if (remainingTerrainBudget <= 0) break;
			double age = state.ageTicks(gameTime, 0.0);
			double requiredDistance = Math.min(
				TerrainShockfrontField.MAX_HORIZONTAL_RANGE,
				WarheadVisualMath.groundShockwaveDistance(age) + TERRAIN_LOOKAHEAD_BLOCKS
			);
			int built = state.terrainShockfrontField().buildToDistance(
				client.level,
				requiredDistance,
				Math.min(fairShare, remainingTerrainBudget)
			);
			remainingTerrainBudget -= built;
		}

		this.spawnWarheadTrailParticles(client, gameTime);
	}

	public synchronized void clear() {
		this.activeWarheads.clear();
		this.activeImpacts.clear();
		TerrainSurfaceCache.INSTANCE.clear();
		ClientDebrisBatchManager.INSTANCE.clear();
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
			TerrainSurfaceCache.INSTANCE.clear();
			ClientDebrisBatchManager.INSTANCE.clear();
			this.activeLevel = null;
			return false;
		}
		if (this.activeLevel != level) {
			this.activeWarheads.clear();
			this.activeImpacts.clear();
			TerrainSurfaceCache.INSTANCE.clear();
			ClientDebrisBatchManager.INSTANCE.clear();
			this.activeLevel = level;
		}
		return true;
	}

	private <T> void removeOldestIfAtCapacity(final Map<UUID, T> states, final int capacity) {
		while (states.size() >= capacity) {
			Iterator<UUID> iterator = states.keySet().iterator();
			if (!iterator.hasNext()) return;
			iterator.next();
			iterator.remove();
		}
	}

	private int spawnWarheadTrailParticles(final Minecraft client, final long gameTime) {
		if (client.level == null || client.player == null) return 0;
		int spawned = 0;
		for (WarheadVisualState state : this.activeWarheads.values()) {
			if (spawned >= 4) return spawned;
			Vec3 position = state.positionAt(gameTime, 0.0);
			double distanceSquared = client.player.position().distanceToSqr(position);
			int perWarheadLimit = distanceSquared < 192.0 * 192.0 ? 2 : distanceSquared < 640.0 * 640.0 ? 1 : 0;
			if (perWarheadLimit == 0) continue;
			SplittableRandom random = new SplittableRandom(state.visualSeed() ^ gameTime);
			for (int particle = 0; particle < perWarheadLimit && spawned < 4; particle++) {
				client.level.addParticle(ParticleTypes.CLOUD,
					position.x + random.nextDouble(-0.12, 0.12), position.y + random.nextDouble(-0.12, 0.12),
					position.z + random.nextDouble(-0.12, 0.12), random.nextDouble(-0.025, 0.025),
					random.nextDouble(0.01, 0.06), random.nextDouble(-0.025, 0.025));
				spawned++;
			}
			if (spawned < 4 && (random.nextInt() & 3) == 0) {
				client.level.addParticle(ParticleTypes.FLAME, position.x, position.y, position.z, 0.0, 0.02, 0.0);
				spawned++;
			}
		}
		return spawned;
	}

	public record Snapshot(List<WarheadVisualState> warheads, List<ImpactVisualState> impacts) {
		private static final Snapshot EMPTY = new Snapshot(List.of(), List.of());
	}
}
