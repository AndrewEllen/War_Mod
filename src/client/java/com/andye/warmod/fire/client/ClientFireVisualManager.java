package com.andye.warmod.fire.client;

import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.FireSimulationManager;
import com.andye.warmod.fire.FireSurfaceAnchor;
import com.andye.warmod.fire.network.ClientboundFireStatePayload;
import com.andye.warmod.fire.network.ClientboundFireWindImpulsePayload;
import com.andye.warmod.fire.wind.FireWindImpulse;
import com.andye.warmod.particle.gpu.GpuParticleEngine;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ClientFireVisualManager {
    public static final ClientFireVisualManager INSTANCE = new ClientFireVisualManager();
    /* A complete authoritative snapshot is deliberately built over several
       bounded server ticks. Keep the last published visual stable between cycles. */
    private static final int EXPIRY_TICKS = 160;
    private final Map<Long, VisualPatch> patches = new LinkedHashMap<>();
	private final Map<Long, EmberVisual> embers = new LinkedHashMap<>();
	private final Map<Long, VisualSmokeCluster> smokeClusters = new LinkedHashMap<>();
    private final ArrayDeque<FireWindImpulse> windImpulses = new ArrayDeque<>();
    private ClientLevel activeLevel;
    private long highestGeneration = Long.MIN_VALUE;

    private ClientFireVisualManager() { }

    public synchronized void accept(final ClientboundFireStatePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (payload == null || !payload.isWellFormed() || !ensureCurrentLevel(level)) {
            GpuParticleEngine.recordFirePacket(false, false);
            return;
        }
        if (payload.generation() <= highestGeneration) {
            GpuParticleEngine.recordFirePacket(false, true);
            return;
        }
        GpuParticleEngine.recordFirePacket(true, false);
        highestGeneration = payload.generation();
        long receivedAt = level.getGameTime();
        boolean patchUpdate = payload.complete() || !payload.entries().isEmpty();
        HashSet<Long> received = new HashSet<>(payload.entries().size());
        for (ClientboundFireStatePayload.Entry entry : payload.entries()) {
            received.add(entry.id());
            VisualPatch previous = patches.get(entry.id());
            Vec3 incomingWind = new Vec3(entry.windX(), entry.windY(), entry.windZ());
            Vec3 wind = previous == null ? incomingWind : previous.wind().lerp(incomingWind, 0.34);
            float heat = lerp(previous == null ? entry.heat() : previous.heat(), entry.heat(), 0.44F);
            float coverage = lerp(previous == null ? entry.coverage() : previous.coverage(),
                entry.coverage(), 0.38F);
            float smoke = lerp(previous == null ? entry.smoke() : previous.smoke(),
                entry.smoke(), 0.40F);
            Direction face = Direction.values()[Byte.toUnsignedInt(entry.face())];
            FireSurfaceAnchor anchor = new FireSurfaceAnchor(BlockPos.of(entry.packedHost()), face,
                entry.localX(), entry.localY(), entry.localZ());
            patches.put(entry.id(), new VisualPatch(entry.id(), anchor, entry.intensity(),
                heat, coverage, smoke, entry.phase(), entry.seed(), entry.ignitionGameTime(),
                wind, previous == null ? 0.0F : previous.clumpStrength(), receivedAt));
        }
        for (long removedId : payload.removedPatchIds()) patches.remove(removedId);
        if (payload.complete()) patches.keySet().removeIf(id -> !received.contains(id));
		if (patchUpdate) recomputeClumps();
		HashSet<Long> receivedEmbers = new HashSet<>(payload.embers().size());
		for (ClientboundFireStatePayload.EmberEntry entry : payload.embers()) {
			receivedEmbers.add(entry.id());
			Vec3 incoming = new Vec3(entry.x(), entry.y(), entry.z());
			Vec3 velocity = new Vec3(entry.velocityX(), entry.velocityY(), entry.velocityZ());
			Vec3 wind = new Vec3(entry.windX(), entry.windY(), entry.windZ());
			EmberVisual visual = embers.get(entry.id());
			if (visual == null) embers.put(entry.id(), new EmberVisual(entry.id(), incoming,
				velocity, wind, entry.intensity(), entry.seed(), entry.startGameTime(),
				entry.lifetime(), payload.serverGameTime(), receivedAt));
			else visual.accept(incoming, velocity, wind, entry.intensity(), entry.seed(),
				entry.startGameTime(), entry.lifetime(), payload.serverGameTime(), receivedAt);
		}
		if (payload.emberComplete()) embers.keySet().removeIf(id -> !receivedEmbers.contains(id));
        HashSet<Long> receivedSmokeClusters = new HashSet<>(payload.smokeClusters().size());
        for (ClientboundFireStatePayload.SmokeClusterEntry entry : payload.smokeClusters()) {
            receivedSmokeClusters.add(entry.id());
            smokeClusters.put(entry.id(), new VisualSmokeCluster(entry.id(),
                new Vec3(entry.x(), entry.y(), entry.z()), entry.smoke(), entry.heat(),
                entry.radius(), new Vec3(entry.windX(), entry.windY(), entry.windZ()),
                entry.seed(), entry.memberCount(), receivedAt));
        }
        if (payload.smokeClusterComplete())
            smokeClusters.keySet().removeIf(id -> !receivedSmokeClusters.contains(id));
    }

    public synchronized void acceptImpulse(final ClientboundFireWindImpulsePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (payload == null || !payload.isWellFormed() || !ensureCurrentLevel(level)) return;
        while (windImpulses.size() >= 32) windImpulses.removeFirst();
        windImpulses.addLast(payload.impulse());
    }

    public synchronized void tick(final Minecraft client) {
        if (!ensureCurrentLevel(client.level)) return;
        ClientSmokeFlowField.INSTANCE.tick(client.level);
        long now = client.level.getGameTime();
        windImpulses.removeIf(impulse -> impulse.expired(now));
        Iterator<VisualPatch> iterator = patches.values().iterator();
        while (iterator.hasNext())
            if (now - iterator.next().lastSeenClientTick() > EXPIRY_TICKS) iterator.remove();
		Iterator<EmberVisual> emberIterator = embers.values().iterator();
		while (emberIterator.hasNext()) {
			EmberVisual ember = emberIterator.next();
			if (now - ember.lastSeenClientTick > 24
				|| now - ember.startGameTime > ember.lifetime + 4L) emberIterator.remove();
			else ember.simulate(now, effectiveWind(ember.position, ember.wind, now));
		}
		Iterator<VisualSmokeCluster> smokeClusterIterator = smokeClusters.values().iterator();
		while (smokeClusterIterator.hasNext())
			if (now - smokeClusterIterator.next().lastSeenClientTick() > EXPIRY_TICKS)
				smokeClusterIterator.remove();
    }

    public synchronized List<VisualPatch> snapshot(final ClientLevel level) {
        return level == null || activeLevel != level ? List.of() : List.copyOf(patches.values());
    }

	public synchronized List<VisualEmber> emberSnapshot(final ClientLevel level) {
		if (level == null || activeLevel != level) return List.of();
		return embers.values().stream().map(EmberVisual::snapshot).toList();
	}

    public synchronized List<VisualSmokeCluster> smokeClusterSnapshot(final ClientLevel level) {
        return level == null || activeLevel != level ? List.of()
            : List.copyOf(smokeClusters.values());
    }

    public synchronized Vec3 effectiveWind(final Vec3 position, final Vec3 baseWind,
        final double gameTime) {
        Vec3 result = baseWind == null ? Vec3.ZERO : baseWind;
        if (position == null || !position.isFinite()) return result;
        for (FireWindImpulse impulse : windImpulses)
            result = result.add(impulse.sample(position, gameTime));
        double length = result.length();
        return length > 2.5 ? result.scale(2.5 / length) : result;
    }

    public synchronized void clear() {
        patches.clear(); embers.clear(); smokeClusters.clear(); windImpulses.clear();
        ClientSmokeFlowField.INSTANCE.clear();
        highestGeneration = Long.MIN_VALUE;
        activeLevel = null;
    }

    private boolean ensureCurrentLevel(final ClientLevel level) {
        if (level == null) { clear(); return false; }
		if (activeLevel != level) {
            patches.clear(); embers.clear(); smokeClusters.clear(); windImpulses.clear();
            ClientSmokeFlowField.INSTANCE.clear();
            highestGeneration = Long.MIN_VALUE;
            activeLevel = level;
        }
        return true;
    }

    private static float lerp(final float from, final float to, final float amount) {
        return from + (to - from) * amount;
    }

	private void recomputeClumps() {
		List<VisualPatch> visible = List.copyOf(patches.values());
		Map<Long, Float> hostEnergy = new HashMap<>();
		for (VisualPatch patch : visible) {
			if (patch.phase() == FirePhase.SMOLDERING || patch.phase() == FirePhase.DECAYING)
				continue;
			hostEnergy.merge(patch.anchor().host().asLong(), patch.heat() * patch.coverage(),
				Math::max);
		}
		for (VisualPatch patch : visible) {
			float energy = 0.0F;
			int burningHosts = 0;
			BlockPos host = patch.anchor().host();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						Float candidateEnergy = hostEnergy.get(BlockPos.asLong(
							host.getX() + dx, host.getY() + dy, host.getZ() + dz));
						if (candidateEnergy == null) continue;
						energy += candidateEnergy;
						burningHosts++;
					}
				}
			}
			float density = Mth.clamp((burningHosts - 6) / 12.0F, 0.0F, 1.5F);
			float averageEnergy = burningHosts == 0 ? 0.0F : energy / burningHosts;
			float strength = density * Mth.clamp(averageEnergy * 1.25F, 0.0F, 1.0F);
			patches.put(patch.id(), new VisualPatch(patch.id(), patch.anchor(), patch.intensity(),
				patch.heat(), patch.coverage(), patch.smoke(), patch.phase(), patch.seed(),
				patch.ignitionGameTime(), patch.wind(), strength, patch.lastSeenClientTick()));
		}
	}


    public record VisualPatch(long id, FireSurfaceAnchor anchor, float intensity,
        float heat, float coverage, float smoke, FirePhase phase, long seed,
        long ignitionGameTime, Vec3 wind, float clumpStrength, long lastSeenClientTick) { }
	public record VisualEmber(long id, Vec3 position, Vec3 velocity, Vec3 wind,
        float intensity, long seed, long startGameTime, int lifetime,
		long serverSampleGameTime, long lastSeenClientTick, List<EmberTrailSample> trail) { }
	public record EmberTrailSample(Vec3 position, Vec3 wind, long gameTime) { }
	public record VisualSmokeCluster(long id, Vec3 position, float smoke, float heat,
        float radius, Vec3 wind, long seed, int memberCount, long lastSeenClientTick) { }

	/** Eight history samples make the renderer follow the real local path instead of
	 * drawing a rigid billboard chain behind the latest server position. */
	private static final class EmberVisual {
		private static final int MAX_TRAIL_SAMPLES = 8;
		private final long id;
		private Vec3 position;
		private Vec3 velocity;
		private Vec3 wind;
		private float intensity;
		private long seed;
		private long startGameTime;
		private int lifetime;
		private long serverSampleGameTime;
		private long lastSeenClientTick;
		private long simulatedGameTime;
		private final ArrayDeque<EmberTrailSample> trail = new ArrayDeque<>();

		private EmberVisual(final long id, final Vec3 position, final Vec3 velocity,
			final Vec3 wind, final float intensity, final long seed, final long startGameTime,
			final int lifetime, final long serverSampleGameTime, final long receivedAt) {
			this.id = id; this.position = position; this.velocity = velocity; this.wind = wind;
			this.intensity = intensity; this.seed = seed; this.startGameTime = startGameTime;
			this.lifetime = lifetime; this.serverSampleGameTime = serverSampleGameTime;
			this.lastSeenClientTick = receivedAt; this.simulatedGameTime = receivedAt;
			appendTrail(receivedAt);
		}

		private void accept(final Vec3 incomingPosition, final Vec3 incomingVelocity,
			final Vec3 incomingWind, final float incomingIntensity, final long incomingSeed,
			final long incomingStart, final int incomingLifetime, final long serverTime,
			final long receivedAt) {
			/* Correct gentle packet drift without visually snapping a windborne path. */
			if (position.distanceToSqr(incomingPosition) > 2.25) {
				position = incomingPosition; velocity = incomingVelocity; trail.clear();
			} else {
				position = position.lerp(incomingPosition, 0.46);
				velocity = velocity.lerp(incomingVelocity, 0.42);
			}
			wind = wind.lerp(incomingWind, 0.48); intensity = incomingIntensity;
			seed = incomingSeed; startGameTime = incomingStart; lifetime = incomingLifetime;
			serverSampleGameTime = serverTime; lastSeenClientTick = receivedAt;
			simulatedGameTime = Math.max(simulatedGameTime, receivedAt);
			if (trail.isEmpty()) appendTrail(receivedAt);
		}

		private void simulate(final long now, final Vec3 effectiveWind) {
			while (simulatedGameTime < now) {
				simulatedGameTime++;
				double progress = Math.min(1.0, Math.max(0.0,
					(simulatedGameTime - startGameTime) / (double) Math.max(1, lifetime)));
				velocity = FireSimulationManager.stepEmberVelocity(velocity, effectiveWind, seed,
					startGameTime, simulatedGameTime, progress);
				position = position.add(velocity);
				appendTrail(simulatedGameTime);
			}
		}

		private void appendTrail(final long gameTime) {
			trail.addLast(new EmberTrailSample(position, wind, gameTime));
			while (trail.size() > MAX_TRAIL_SAMPLES) trail.removeFirst();
		}

		private VisualEmber snapshot() {
			return new VisualEmber(id, position, velocity, wind, intensity, seed,
				startGameTime, lifetime, serverSampleGameTime, lastSeenClientTick,
				List.copyOf(trail));
		}
	}
}
