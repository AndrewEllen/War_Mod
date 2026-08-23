package com.andye.warmod.fire;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.fire.FireSurfaceAnchor.SurfaceKey;
import com.andye.warmod.fire.network.FireNetworking;
import com.andye.warmod.fire.wind.FireWindEngine;
import com.andye.warmod.item.component.FireDebugConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded surface-patch combustion. Fire is an authoritative heat/fuel field;
 * no vanilla fire blocks or Minecraft Particle instances are used.
 */
public final class FireSimulationManager {
    private static final int MAX_ACTIVE_PATCHES = 4_096;
    private static final int MAX_PATCH_UPDATES_PER_TICK = 256;
    private static final int MAX_NEW_IGNITIONS_PER_TICK = 32;
    private static final int MAX_PREHEAT_SURFACES = 8_192;
    private static final int MAX_WET_POSITIONS = 8_192;
	private static final int MAX_EMBERS = 96;
    private static final int NUCLEAR_DYING_EVICTION_BATCH = 128;
    private static final int NUCLEAR_HEALTHY_EVICTION_BATCH = 128;
    private static final int NETWORK_INTERVAL_TICKS = 10;
    private static final int EMBER_NETWORK_INTERVAL_TICKS = 3;
    private static final int MAX_WET_POSITIONS_PER_JET = 512;
    private static final int MAX_EMBER_COLLISION_STEPS = 12;
	/* Authoritative fire remains on the server thread, but it may not consume an
	 * unbounded share of a 50 ms tick when a nuclear ignition fills the queue. */
	private static final long FIRE_TICK_BUDGET_NANOS = 3_000_000L;
	private static final long FIRE_SNAPSHOT_PREP_BUDGET_NANOS = 2_000_000L;
	private static final int MIN_SNAPSHOT_PATCHES_PER_TICK = 256;
	private static final int MAX_DECAY_ENTRIES_PER_TICK = 256;
    private static final int FIRE_DAMAGE_INTERVAL_TICKS = 10;
    private static final int VENTILATION_CACHE_TICKS = 80;
    private static final int MAX_PLACEMENT_PROBES = 8_192;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int[][] SURFACE_OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final Map<ServerLevel, LevelState> LEVELS = new IdentityHashMap<>();
    private static boolean registered;

    private FireSimulationManager() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(FireSimulationManager::tick);
        ServerLevelEvents.LOAD.register((server, level) -> restore(level));
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            synchronized (FireSimulationManager.class) {
                checkpoint(level, LEVELS.get(level));
                LEVELS.remove(level);
            }
        });
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
            synchronized (FireSimulationManager.class) {
                for (ServerLevel level : server.getAllLevels())
                    checkpoint(level, LEVELS.get(level));
            }
        });
        registered = true;
    }

    public static synchronized void clearAll() { LEVELS.clear(); }

    /** Places an exact clicked surface plus a bounded radius of exposed fuel surfaces. */
    public static synchronized int igniteSurface(final ServerLevel level,
        final FireSurfaceAnchor primary, final FireDebugConfig config, final long seed) {
        return igniteSurface(level, primary, config, seed, false, false);
    }

    /** Debug placement may displace one dying patch when the global cap is full. */
    public static synchronized int igniteSurfacePrioritized(final ServerLevel level,
        final FireSurfaceAnchor primary, final FireDebugConfig config, final long seed) {
        return igniteSurface(level, primary, config, seed, true, false);
    }

    /**
     * Nuclear seeding is allowed to replace one dying patch for every requested
     * seed. This remains under the global 4,096-patch cap; unlike the debug
     * stick it is not artificially restricted to one eviction per game tick.
     */
    public static synchronized int igniteSurfaceNuclear(final ServerLevel level,
        final FireSurfaceAnchor primary, final FireDebugConfig config, final long seed) {
        return igniteSurface(level, primary, config, seed, true, true);
    }

    private static int igniteSurface(final ServerLevel level,
        final FireSurfaceAnchor primary, final FireDebugConfig config, final long seed,
        final boolean prioritizeAtCapacity, final boolean unrestrictedEvictions) {
        if (level == null || primary == null || config == null) return 0;
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState());
        if (!validSurface(level, primary, true)) return 0;
        if (state.wetness.getOrDefault(primary.host().asLong(), 0.0F) > 0.40F) return 0;
        if (!state.surfaceIndex.containsKey(primary.key())
            && state.patches.size() >= MAX_ACTIVE_PATCHES) {
            long now = level.getGameTime();
            if (!prioritizeAtCapacity) return 0;
            if (unrestrictedEvictions) {
                if (!evictNuclearBatch(state, now)) return 0;
            } else {
                if (state.lastPriorityEvictionTick == now
                    || !evictLowestPriorityPatch(state)) return 0;
                state.lastPriorityEvictionTick = now;
            }
        }
        int placed = igniteInternal(level, state, primary, config.intensity(), seed,
            true, true) ? 1 : 0;
        if (config.size() <= 1 || placed == 0) return placed;

        int maximum = Math.min(384, 1 + config.size() * config.size() * 2);
        double faceRadius = Math.min(0.82, 0.18 + (config.size() - 1) * 0.18);
        for (int u = 0; u < 4 && placed < maximum; u++) {
            for (int v = 0; v < 4 && placed < maximum; v++) {
                FireSurfaceAnchor anchor = FireSurfaceAnchor.grid(primary.host(),
                    primary.face(), u, v);
                if (anchor.key().equals(primary.key())
                    || anchor.position().distanceTo(primary.position()) > faceRadius) continue;
                if (igniteInternal(level, state, anchor, config.intensity(),
                    mix(seed ^ u * 31L ^ v * 131L), true, true)) placed++;
            }
        }

        double radius = 0.55 + (config.size() - 1) * 1.05;
        int reach = Mth.ceil(radius);
        Vec3 origin = primary.position();
        PriorityQueue<RankedSurface> nearest = new PriorityQueue<>(maximum + 1,
            (left, right) -> {
                int distance = Double.compare(right.distance(), left.distance());
                return distance != 0 ? distance : Long.compare(right.rank(), left.rank());
            });
        int placementProbes = 0;
        double scanRadiusSquared = (radius + 0.75) * (radius + 0.75);
        candidateScan:
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dy * dy + dz * dz > scanRadiusSquared) continue;
                    if (++placementProbes > MAX_PLACEMENT_PROBES) break candidateScan;
                    BlockPos host = primary.host().offset(dx, dy, dz);
                    if (!isLoaded(level, host) || host.equals(primary.host())) continue;
                    BlockState candidateState = level.getBlockState(host);
                    if (candidateState.isAir() || !level.getFluidState(host).isEmpty()) continue;
                    FireFuelProfile profile = FireFuelProfile.of(candidateState);
                    Direction face = bestExposedFace(level, host, origin);
                    if (face == null) continue;
                    FireSurfaceAnchor anchor = FireSurfaceAnchor.center(host, face);
                    double distance = anchor.position().distanceTo(origin);
                    if (distance <= radius + 0.45) {
                        nearest.add(new RankedSurface(anchor, distance,
                            mix(seed ^ host.asLong() ^ face.ordinal()), profile.flammable()));
                        if (nearest.size() > maximum) nearest.poll();
                    }
                }
            }
        }
        List<RankedSurface> candidates = new ArrayList<>(nearest);
        candidates.sort((left, right) -> {
            int fuel = Boolean.compare(right.flammable(), left.flammable());
            if (fuel != 0) return fuel;
            int distance = Double.compare(left.distance(), right.distance());
            return distance != 0 ? distance : Long.compare(left.rank(), right.rank());
        });
        for (RankedSurface candidate : candidates) {
            if (placed >= maximum || state.patches.size() >= MAX_ACTIVE_PATCHES) break;
            float falloff = (float) Mth.clamp(1.0 - candidate.distance()
                / Math.max(0.1, radius + 0.35), 0.0, 1.0);
            float intensity = config.intensity() * (0.52F + falloff * 0.48F);
            if (igniteInternal(level, state, candidate.anchor(), intensity,
                candidate.rank(), true, true)) placed++;
        }
        return placed;
    }

    public static synchronized int suppress(final ServerLevel level, final ServerPlayer source,
        final Vec3 center, final double radius, final float amount) {
        return suppressJet(level, source, center, center, radius, amount);
    }

    /** Applies one straight suppression jet with a single bounded scan of active fire. */
    public static synchronized int suppressJet(final ServerLevel level,
        final ServerPlayer source, final Vec3 start, final Vec3 end,
        final double radius, final float amount) {
        LevelState state = LEVELS.get(level);
        if (state == null || start == null || end == null || !start.isFinite()
            || !end.isFinite() || radius <= 0.0
            || amount <= 0.0F) return 0;
        double radiusSquared = radius * radius;
        int affected = 0;
        int minChunkX = Mth.floor(Math.min(start.x, end.x) - radius) >> 4;
        int maxChunkX = Mth.floor(Math.max(start.x, end.x) + radius) >> 4;
        int minChunkZ = Mth.floor(Math.min(start.z, end.z) - radius) >> 4;
        int maxChunkZ = Mth.floor(Math.max(start.z, end.z) + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Set<Long> patchIds = state.patchChunkIndex.get(chunkKey(chunkX, chunkZ));
                if (patchIds == null) continue;
                for (long patchId : patchIds) {
                    Patch patch = state.patches.get(patchId);
                    if (patch == null) continue;
                    Vec3 position = patch.anchor.position();
                    if (distanceToSegmentSquared(position, start, end) > radiusSquared) continue;
                    if (source != null && !visibleFrom(level, source, start, position)) continue;
                    patch.heat = Math.max(0.0F, patch.heat - amount);
                    patch.coverage = Math.max(0.02F, patch.coverage - amount * 0.16F);
                    if (patch.heat < 0.24F) patch.phase = FirePhase.SMOLDERING;
                    addWetness(state, patch.anchor.host().asLong(), amount * 1.35F, true,
					level.getGameTime());
                    affected++;
                }
            }
        }

        Vec3 travel = end.subtract(start);
        double length = travel.length();
        int samples = Math.max(1, Mth.ceil(length / Math.max(0.85, radius * 0.72)));
        Set<Long> wetted = new HashSet<>();
        int reach = Mth.ceil(radius);
        for (int sampleIndex = 0; sampleIndex <= samples
            && wetted.size() < MAX_WET_POSITIONS_PER_JET; sampleIndex++) {
            Vec3 center = start.add(travel.scale(sampleIndex / (double) samples));
            BlockPos origin = BlockPos.containing(center);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        BlockPos host = origin.offset(dx, dy, dz);
                        long key = host.asLong();
                        if (wetted.contains(key) || !isLoaded(level, host)
                            || Vec3.atCenterOf(host).distanceToSqr(center) > radiusSquared)
                            continue;
                        if (FireFuelProfile.of(level.getBlockState(host)).flammable()) {
                            addWetness(state, key, amount, false, level.getGameTime());
                            wetted.add(key);
                            if (wetted.size() >= MAX_WET_POSITIONS_PER_JET) break;
                        }
                    }
                    if (wetted.size() >= MAX_WET_POSITIONS_PER_JET) break;
                }
                if (wetted.size() >= MAX_WET_POSITIONS_PER_JET) break;
            }
        }
        return affected;
    }

    public static boolean isLoaded(final ServerLevel level, final BlockPos position) {
        return level.getChunkSource().hasChunk(
            SectionPos.blockToSectionCoord(position.getX()),
            SectionPos.blockToSectionCoord(position.getZ()));
    }

    private static synchronized void tick(final ServerLevel level) {
        long diagnosticsStarted = WarModPerformanceDiagnostics.begin();
        FireWindEngine.tick(level);
        LevelState state = LEVELS.get(level);
        if (state == null) {
            WarModPerformanceDiagnostics.gauge(
                WarModPerformanceDiagnostics.Gauge.ACTIVE_FIRE_PATCHES, 0L);
            WarModPerformanceDiagnostics.gauge(
                WarModPerformanceDiagnostics.Gauge.ACTIVE_FIRE_EMBERS, 0L);
            WarModPerformanceDiagnostics.gauge(
                WarModPerformanceDiagnostics.Gauge.FIRE_SNAPSHOT_IN_PROGRESS, 0L);
            WarModPerformanceDiagnostics.gauge(
                WarModPerformanceDiagnostics.Gauge.FIRE_SNAPSHOT_PENDING_PATCHES, 0L);
            WarModPerformanceDiagnostics.record(
                WarModPerformanceDiagnostics.Subsystem.FIRE_SIMULATION, diagnosticsStarted);
            return;
        }
        long now = level.getGameTime();
		long simulationDeadline = System.nanoTime() + FIRE_TICK_BUDGET_NANOS;
        boolean hadPatches = !state.patches.isEmpty();
        boolean hadEmbers = !state.embers.isEmpty();
        state.newIgnitionsThisTick = 0;
        decayWetness(state, now, simulationDeadline);
        decayPreheat(state, now, simulationDeadline);
        tickEmbers(level, state, now, simulationDeadline);

        int updates = Math.min(MAX_PATCH_UPDATES_PER_TICK, state.workQueue.size());
        for (int index = 0; index < updates; index++) {
			if ((index & 7) == 0 && System.nanoTime() >= simulationDeadline) break;
            long id = state.workQueue.removeFirst();
            Patch patch = state.patches.get(id);
            if (patch == null) continue;
            updatePatch(level, state, patch, now);
            if (state.patches.containsKey(id)) state.workQueue.addLast(id);
        }

		boolean hasViewers = !PlayerLookup.level(level).isEmpty();
        if (hasViewers && !state.snapshotInProgress && ((hadPatches && state.patches.isEmpty())
			|| now % NETWORK_INTERVAL_TICKS == 0L)) startSnapshot(state);
		long snapshotDiagnosticsStarted = state.snapshotInProgress
			? WarModPerformanceDiagnostics.begin() : 0L;
		advanceSnapshot(level, state);
		if (snapshotDiagnosticsStarted != 0L) WarModPerformanceDiagnostics.record(
			WarModPerformanceDiagnostics.Subsystem.FIRE_SNAPSHOT_PREPARATION,
			snapshotDiagnosticsStarted);
        if ((hadEmbers || !state.embers.isEmpty())
            && now % EMBER_NETWORK_INTERVAL_TICKS == 0L) sendEmberSnapshot(level, state);
        if (now % 24L == 0L && !state.patches.isEmpty()) playCrackle(level, state, now);
        if (state.patches.isEmpty() && state.wetness.isEmpty()
            && state.preheat.isEmpty() && state.embers.isEmpty()) {
            checkpoint(level, null);
            LEVELS.remove(level);
        } else if (now % 400L == 0L) checkpoint(level, state);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.ACTIVE_FIRE_PATCHES, state.patches.size());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.ACTIVE_FIRE_EMBERS, state.embers.size());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.FIRE_SNAPSHOT_IN_PROGRESS,
            state.snapshotInProgress ? 1L : 0L);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.FIRE_SNAPSHOT_PENDING_PATCHES,
            state.snapshotPatchIds.size());
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.FIRE_SIMULATION, diagnosticsStarted);
    }

    private static void updatePatch(final ServerLevel level, final LevelState state,
        final Patch patch, final long now) {
        BlockPos host = patch.anchor.host();
        if (!isLoaded(level, host)) return;
        int elapsed = (int) Mth.clamp(now - patch.lastUpdateTick, 1L, 40L);
        patch.lastUpdateTick = now;
        FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(host));
        if (!profile.flammable() && !patch.surfaceFlame) {
            removePatch(state, patch);
            return;
        }
        boolean water = level.getFluidState(host).is(FluidTags.WATER)
            || level.getFluidState(host.relative(patch.anchor.face())).is(FluidTags.WATER);
        float wetness = state.wetness.getOrDefault(host.asLong(), 0.0F);
		float clump = clumpFactor(state, patch);
		float combustionPressure = 0.35F + patch.targetIntensity * patch.targetIntensity * 1.65F
			+ clump * 0.22F;
        if (water) {
            addWetness(state, host.asLong(), 1.5F, true, now);
            patch.heat -= 0.18F * elapsed;
        } else if (wetness > 0.0F) {
            patch.heat -= (0.006F + wetness * 0.025F) * elapsed;
        }

        if (profile.flammable() && !water && wetness < 0.45F
            && patch.phase != FirePhase.DECAYING
            && patch.phase != FirePhase.SMOLDERING) {
            float fuelPotential = Mth.clamp(0.18F + profile.heatRelease() * 0.45F
                + patch.coverage * 0.30F + Math.min(1.5F, clump) * 0.16F,
                0.10F, 1.0F);
            if (fuelPotential > patch.targetIntensity) {
                float growthRate = 0.00055F + patch.heat * patch.coverage * 0.0034F
                    + Math.min(1.5F, clump) * 0.0008F;
                patch.targetIntensity += (fuelPotential - patch.targetIntensity)
                    * Math.min(1.0F, elapsed * growthRate);
            }
        }

        long age = now - patch.ignitionGameTime;
        if (patch.surfaceFlame && !profile.flammable()) {
            long maximumSurfaceAge = 220L + Math.round(patch.targetIntensity * 300.0F);
            if (age >= maximumSurfaceAge) {
                patch.phase = FirePhase.SMOLDERING;
                patch.fuel = Math.min(patch.fuel, 0.04F);
                patch.heat = Math.min(patch.heat, 0.16F);
            }
        }
        long kindlingCheckTick = 60L + Math.floorMod(mix(patch.seed), 36L);
        if (patch.phase == FirePhase.GROWING && patch.targetIntensity <= 0.28F
            && age - elapsed < kindlingCheckTick && age >= kindlingCheckTick
            && patch.coverage < 0.28F) {
            double survivalChance = Mth.clamp(0.12 + patch.targetIntensity * 1.8
                + profile.heatRelease() * 0.18, 0.18, 0.88);
            if (unit(mix(patch.seed ^ 0x4B494E444C494E47L)) > survivalChance) {
                patch.phase = FirePhase.SMOLDERING;
                patch.heat = Math.min(patch.heat, 0.10F);
                patch.fuel = Math.min(patch.fuel, 0.12F);
            }
        }
        switch (patch.phase) {
            case IGNITION -> {
                patch.heat += (patch.targetIntensity * 0.34F - patch.heat)
                    * Math.min(1.0F, elapsed * 0.045F);
                patch.coverage = Math.min(0.10F, patch.coverage + elapsed * 0.0008F);
                if (age >= 24L + (long) ((1.0F - patch.targetIntensity) * 54.0F))
                    patch.phase = FirePhase.GROWING;
            }
            case GROWING -> {
                patch.heat += (patch.targetIntensity - patch.heat)
                    * Math.min(1.0F, elapsed * (0.018F + patch.targetIntensity * 0.018F)
					* (1.0F + clump * 0.20F));
                patch.coverage += elapsed * (0.0022F + patch.targetIntensity * 0.0036F)
					* (1.0F + clump * 0.24F);
                patch.fuel -= elapsed / (float) Math.max(600, patch.burnTicks * 3);
                if (patch.coverage >= 0.94F) {
                    patch.coverage = 0.94F;
                    patch.phase = FirePhase.FLAMING;
                }
            }
            case FLAMING -> {
                patch.heat += (patch.targetIntensity * profile.heatRelease() - patch.heat)
                    * Math.min(1.0F, elapsed * 0.026F);
                patch.coverage = Math.min(1.0F, patch.coverage + elapsed * 0.0007F);
                patch.fuel -= elapsed * combustionPressure
					/ (float) Math.max(400, patch.burnTicks);
                if (patch.fuel < 0.28F) patch.phase = FirePhase.DECAYING;
            }
            case DECAYING -> {
                patch.fuel -= elapsed * combustionPressure
					/ (float) Math.max(300, patch.burnTicks);
                patch.heat -= elapsed * 0.0018F;
                patch.coverage = Math.max(0.20F, patch.coverage - elapsed * 0.0014F);
                if (patch.fuel <= 0.05F || patch.heat < 0.24F)
                    patch.phase = FirePhase.SMOLDERING;
            }
            case SMOLDERING -> {
                patch.fuel -= elapsed / (float) Math.max(240, patch.burnTicks / 2);
                patch.heat -= elapsed * (water || wetness > 0.0F ? 0.018F : 0.0024F);
                patch.coverage = Math.max(0.05F, patch.coverage - elapsed * 0.0012F);
            }
        }
        patch.fuel = Math.max(0.0F, patch.fuel);
        patch.heat = Mth.clamp(patch.heat, 0.0F, 1.2F);
        patch.coverage = Mth.clamp(patch.coverage, 0.0F, 1.0F);

        if (!patch.surfaceFlame && !patch.consumed && patch.fuel <= 0.0F
            && profile.flammable() && level.getBlockEntity(host) == null) {
            level.setBlock(host, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            patch.consumed = true;
            removeHostPatches(state, host);
            return;
        }
        if (patch.heat <= 0.018F || (patch.fuel <= 0.0F && patch.phase == FirePhase.SMOLDERING)) {
            removePatch(state, patch);
            return;
        }

        long hostKey = patch.anchor.host().asLong();
        if ((patch.phase == FirePhase.GROWING || patch.phase == FirePhase.FLAMING)
            && patch.heat > 0.075F && now >= patch.nextTransferTick
            && now >= state.nextHostTransferTick.getOrDefault(hostKey, 0L)
            && state.newIgnitionsThisTick < MAX_NEW_IGNITIONS_PER_TICK) {
            int transferInterval = Math.max(6,
                18 - (int) (patch.targetIntensity * 10.0F)
                    - (int) Math.min(3.0F, clump * 2.0F));
            int dueTransfers = 1 + Math.min(3, (int) Math.max(0L,
                (now - patch.nextTransferTick) / transferInterval));
            /* A patch can be revisited far less often than its desired transfer
               cadence when the fixed 256-update budget is busy. Scale the one
               bounded scan by missed intervals instead of paying for repeats. */
            transferHeat(level, state, patch, profile, now, dueTransfers);
            patch.nextTransferTick = now + transferInterval;
            state.nextHostTransferTick.put(hostKey, now + transferInterval);
        }
        if (patch.heat > 0.20F && patch.coverage > 0.18F
            && patch.phase != FirePhase.SMOLDERING
            && now >= state.nextHostDamageTick.getOrDefault(hostKey, 0L)) {
            state.nextHostDamageTick.put(hostKey, now + FIRE_DAMAGE_INTERVAL_TICKS);
            damageEntities(level, patch);
        }
        if ((patch.phase == FirePhase.FLAMING || patch.phase == FirePhase.GROWING)
            && patch.heat > 0.22F && patch.coverage > 0.28F
            && state.embers.size() < MAX_EMBERS
            && now >= state.nextHostEmberAttemptTick.getOrDefault(hostKey, 0L)) {
            int attemptInterval = Math.max(8, 24 - (int) (clump * 10.0F));
            state.nextHostEmberAttemptTick.put(hostKey, now + attemptInterval);
            double emberChance = patch.heat * patch.coverage * patch.targetIntensity
                * profile.emberSusceptibility() * (0.12 + clump * 0.18);
            if (unit(mix(patch.seed ^ hostKey ^ now)) < emberChance) {
                spawnEmber(level, state, patch, now);
            }
        }
    }

    private static void transferHeat(final ServerLevel level, final LevelState state,
        final Patch source, final FireFuelProfile sourceProfile, final long now,
        final int dueTransfers) {
        Vec3 origin = source.anchor.position();
        Vec3 wind = FireWindEngine.windAt(level, origin).scale(
            ventilationFactor(level, state, source.anchor.host(), now));
        double horizontalWind = Math.sqrt(wind.x * wind.x + wind.z * wind.z);
        Vec3 windDirection = horizontalWind > 1.0E-5
            ? new Vec3(wind.x / horizontalWind, 0.0, wind.z / horizontalWind) : Vec3.ZERO;
        SplittableRandom random = new SplittableRandom(mix(source.seed ^ now));

        for (int[] offset : SURFACE_OFFSETS) {
            FireSurfaceAnchor target = source.anchor.gridOffset(offset[0], offset[1]);
            if (target != null) depositAlongSurface(level, state, source,
                sourceProfile, target, now, dueTransfers);
        }

        for (Direction direction : DIRECTIONS) {
            BlockPos candidate = source.anchor.host().relative(direction);
			depositToBlock(level, state, source, sourceProfile, candidate, origin,
                windDirection, horizontalWind, now, 1.0F, dueTransfers, true);
        }
        /* Only close radiation/convection may heat without contact. Long-range,
           wind-biased ignition is carried by the authoritative firebrands below. */
        int radius = 2;
        int samples = 5 + Mth.ceil(source.targetIntensity * 5.0F);
        for (int index = 0; index < samples; index++) {
            int dx = random.nextInt(-radius, radius + 1);
            int dy = random.nextInt(-Math.max(1, radius / 2), radius + 1);
            int dz = random.nextInt(-radius, radius + 1);
            if (dx == 0 && dy == 0 && dz == 0) continue;
            BlockPos candidate = source.anchor.host().offset(dx, dy, dz);
			if (candidate.distSqr(source.anchor.host()) > 5.0) continue;
            float sampling = dy > 0 ? 1.28F : 0.62F;
            depositToBlock(level, state, source, sourceProfile, candidate, origin,
				windDirection, horizontalWind, now, sampling, dueTransfers, false);
        }
    }

    private static void depositAlongSurface(final ServerLevel level, final LevelState state,
        final Patch source, final FireFuelProfile profile, final FireSurfaceAnchor target,
        final long now, final int dueTransfers) {
        float dose = 0.025F + source.heat * source.coverage * profile.heatRelease()
            * (0.12F + source.targetIntensity * 0.16F);
        if (source.heat > 0.45F && source.coverage > 0.18F)
            dose *= 1.55F + Math.min(0.35F, source.targetIntensity * 0.35F);
        dose *= dueTransfers;
        if (dose <= 0.003F) return;
        addPreheat(level, state, target, dose, source.targetIntensity,
            source.seed, now, profile);
    }

    private static void depositToBlock(final ServerLevel level, final LevelState state,
        final Patch source, final FireFuelProfile sourceProfile, final BlockPos candidate,
		final Vec3 origin, final Vec3 windDirection, final double windSpeed,
        final long now, final float samplingScale, final int dueTransfers,
        final boolean directContact) {
        if (!isLoaded(level, candidate) || candidate.equals(source.anchor.host())) return;
        FireFuelProfile targetProfile = FireFuelProfile.of(level.getBlockState(candidate));
        if (!targetProfile.flammable()
            || state.wetness.getOrDefault(candidate.asLong(), 0.0F) > 0.35F) return;
        Direction face = bestExposedFace(level, candidate, origin);
        if (face == null) return;
        FireSurfaceAnchor target = FireSurfaceAnchor.center(candidate, face);
        /* Touching fuel is conductive/radiative contact, not a long ray. Dense
           crowns otherwise reject their own adjacent leaves as path blockers. */
        if (!directContact && !clearHeatPath(level, origin, target.position())) return;
        Vec3 delta = target.position().subtract(origin);
        double distance = Math.max(0.65, delta.length());
        Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
        double alignment = horizontal.lengthSqr() > 1.0E-5 && windSpeed > 0.0
            ? horizontal.normalize().dot(windDirection) : 0.0;
        double windBias = 1.0 + Mth.clamp(alignment, -0.35, 1.0)
            * Math.min(0.75, windSpeed * 2.5);
        double convection = delta.y > 0.0 ? Math.min(1.10, delta.y * 0.42) : -0.10;
        double verticalContact = delta.y > 0.25 && distance < 1.8 ? 1.55 : 1.0;
        double distanceFalloff = 1.0 / Math.max(1.0, distance * distance * 0.72);
        double directCanopyCoupling = 1.0;
        if (directContact && source.heat > 0.45F && source.coverage > 0.18F) {
            boolean crownFuel = sourceProfile.emberSusceptibility() >= 0.95F
                || targetProfile.emberSusceptibility() >= 0.95F;
            directCanopyCoupling = crownFuel ? 2.45 : 1.42;
            if (delta.y > 0.10) directCanopyCoupling *= 1.22;
        }
        double targetFuelCoupling = targetProfile == FireFuelProfile.HIGH ? 1.55
            : targetProfile == FireFuelProfile.MEDIUM ? 1.18 : 1.0;
        float dose = (float) (source.heat * source.coverage * sourceProfile.heatRelease()
			* (0.24 + source.targetIntensity * 0.32 + convection)
            * distanceFalloff * samplingScale * verticalContact * windBias
            * directCanopyCoupling * targetFuelCoupling * dueTransfers);
        if (dose <= 0.004F) return;
        addPreheat(level, state, target, dose, source.targetIntensity, source.seed, now,
            targetProfile);
    }

    private static void addPreheat(final ServerLevel level, final LevelState state,
        final FireSurfaceAnchor target, final float amount, final float sourceIntensity,
        final long sourceSeed, final long now, final FireFuelProfile profile) {
        Long existingId = state.surfaceIndex.get(target.key());
        if (existingId != null) {
            Patch existingPatch = state.patches.get(existingId);
            if (existingPatch != null) {
                existingPatch.heat = Mth.clamp(existingPatch.heat
                    + amount * (0.18F + sourceIntensity * 0.16F), 0.0F, 1.2F);
                existingPatch.coverage = Mth.clamp(existingPatch.coverage
                    + amount * 0.035F, 0.0F, 1.0F);
                existingPatch.targetIntensity = Mth.clamp(existingPatch.targetIntensity
                    + amount * (0.045F + sourceIntensity * 0.035F)
                        * (1.0F - existingPatch.targetIntensity), 0.10F, 1.0F);
                if (existingPatch.phase == FirePhase.SMOLDERING
                    && existingPatch.heat > 0.20F) existingPatch.phase = FirePhase.GROWING;
                return;
            }
        }
        Exposure exposure = state.preheat.get(target.key());
        float existing = exposure == null ? 0.0F : Math.max(0.0F,
            exposure.dose - (now - exposure.lastTouched) * 0.00018F);
        float dose = existing + amount;
        if (dose >= profile.ignitionThreshold()
            && state.newIgnitionsThisTick < MAX_NEW_IGNITIONS_PER_TICK) {
            float intensity = Mth.clamp(0.16F + sourceIntensity * 0.62F, 0.20F, 0.90F);
            if (igniteInternal(level, state, target, intensity,
                mix(sourceSeed ^ target.host().asLong() ^ now), false, false)) {
                state.newIgnitionsThisTick++;
                state.preheat.remove(target.key());
            }
            return;
        }
        if (exposure == null && state.preheat.size() >= MAX_PREHEAT_SURFACES) return;
		if (exposure == null && state.preheatDecayQueued.add(target.key()))
			state.preheatDecayQueue.addLast(target.key());
        state.preheat.put(target.key(), new Exposure(dose, now));
    }

    private static boolean igniteInternal(final ServerLevel level, final LevelState state,
        final FireSurfaceAnchor anchor, final float intensity, final long seed,
        final boolean direct, final boolean allowSurfaceFlame) {
        if (!validSurface(level, anchor, allowSurfaceFlame)
            || state.wetness.getOrDefault(anchor.host().asLong(), 0.0F) > 0.40F) return false;
        Long existingId = state.surfaceIndex.get(anchor.key());
        if (existingId != null) {
            Patch existing = state.patches.get(existingId);
            if (existing != null) {
                existing.targetIntensity = Math.max(existing.targetIntensity, intensity);
                existing.fuel = Math.max(existing.fuel, 0.35F);
                return true;
            }
        }
        if (state.patches.size() >= MAX_ACTIVE_PATCHES) return false;
        FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(anchor.host()));
        boolean nonFuelSurface = !profile.flammable();
        boolean surfaceFlame = nonFuelSurface || !profile.consumable();
        int burnTicks = nonFuelSurface
            ? 120 + Math.round(intensity * 260.0F) : profile.burnTicks();
        long now = level.getGameTime();
        long id = state.nextId++;
        Patch patch = new Patch(id, anchor, Mth.clamp(intensity, 0.10F, 1.0F),
            direct ? FirePhase.GROWING : FirePhase.IGNITION,
            direct ? 0.12F : 0.055F, direct ? 0.045F : 0.018F,
            burnTicks, seed, now, surfaceFlame);
        state.patches.put(id, patch);
        state.membershipGeneration++;
        state.surfaceIndex.put(anchor.key(), id);
		state.hostPatchCounts.merge(anchor.host().asLong(), 1, Integer::sum);
        indexPatch(state, patch);
        state.workQueue.addLast(id);
        return true;
    }

    private static boolean validSurface(final ServerLevel level,
        final FireSurfaceAnchor anchor, final boolean allowSurfaceFlame) {
        if (!isLoaded(level, anchor.host())
            || !level.getWorldBorder().isWithinBounds(anchor.host())
            || level.getFluidState(anchor.host()).is(FluidTags.WATER)) return false;
		BlockState hostState = level.getBlockState(anchor.host());
		if (hostState.isAir()) return false;
        FireFuelProfile profile = FireFuelProfile.of(hostState);
        if (!profile.flammable() && !allowSurfaceFlame) return false;
        BlockPos outside = anchor.host().relative(anchor.face());
        if (isOpenSurfaceSpace(level, outside)) return true;
        if (!profile.flammable() || !isLoaded(level, outside)
            || !level.getFluidState(outside).isEmpty()) return false;
        return FireFuelProfile.of(level.getBlockState(outside)).flammable();
    }

    private static Direction bestExposedFace(final ServerLevel level, final BlockPos host,
        final Vec3 source) {
        Direction best = null;
        Direction combustibleContact = null;
        double bestScore = -Double.MAX_VALUE;
        double contactScore = -Double.MAX_VALUE;
        Vec3 center = Vec3.atCenterOf(host);
        Vec3 towardSource = source.subtract(center);
        for (Direction face : DIRECTIONS) {
            BlockPos outside = host.relative(face);
            double score = towardSource.x * face.getStepX() + towardSource.y * face.getStepY()
                + towardSource.z * face.getStepZ();
            if (isOpenSurfaceSpace(level, outside)) {
                if (score > bestScore) { bestScore = score; best = face; }
            } else if (isLoaded(level, outside) && level.getFluidState(outside).isEmpty()
                && FireFuelProfile.of(level.getBlockState(outside)).flammable()
                && score > contactScore) {
                contactScore = score;
                combustibleContact = face;
            }
        }
        return best != null ? best : combustibleContact;
    }

    private static boolean isOpenSurfaceSpace(final ServerLevel level,
        final BlockPos position) {
        if (!isLoaded(level, position)) return false;
        BlockState state = level.getBlockState(position);
        return state.isAir() || (state.getFluidState().isEmpty()
            && state.getCollisionShape(level, position).isEmpty());
    }

    /** Direct tools remain usable at the global cap without allowing unbounded state. */
    private static boolean evictLowestPriorityPatch(final LevelState state) {
        Patch selected = null;
        double selectedScore = Double.MAX_VALUE;
        for (Patch patch : state.patches.values()) {
            int phasePriority = switch (patch.phase) {
                case SMOLDERING -> 0;
                case DECAYING -> 1;
                case IGNITION -> 2;
                case GROWING -> 3;
                case FLAMING -> 4;
            };
            double score = phasePriority * 5.0 + patch.heat * patch.coverage * 3.0
                + patch.targetIntensity + patch.fuel * 0.5;
            if (score < selectedScore || (score == selectedScore && selected != null
                && patch.ignitionGameTime < selected.ignitionGameTime)) {
                selected = patch;
                selectedScore = score;
            }
        }
        if (selected == null) return false;
        removePatch(state, selected);
        return true;
    }

    /** Opens a bounded group of slots with one O(n log batch) scan, avoiding
     * one full 4,096-patch scan per nuclear seed. Dying fire is always chosen
     * first; at most one small healthy batch may be displaced per tick. */
    private static boolean evictNuclearBatch(final LevelState state, final long now) {
        int limit = NUCLEAR_DYING_EVICTION_BATCH;
        boolean dyingOnly = true;
        PriorityQueue<Patch> selected = lowestPriorityPatches(state, limit, true);
        if (selected.isEmpty()) {
            if (state.lastHealthyNuclearEvictionTick == now) return false;
            dyingOnly = false;
            limit = NUCLEAR_HEALTHY_EVICTION_BATCH;
            selected = lowestPriorityPatches(state, limit, false);
            state.lastHealthyNuclearEvictionTick = now;
        }
        if (selected.isEmpty()) return false;
        for (Patch patch : selected) removePatch(state, patch);
        /* Batched removal otherwise leaves enough stale IDs to consume the
           entire 256-patch update poll budget on the following ticks. */
        state.workQueue.clear();
        state.workQueue.addAll(state.patches.keySet());
        return dyingOnly || !selected.isEmpty();
    }

    private static PriorityQueue<Patch> lowestPriorityPatches(final LevelState state,
        final int limit, final boolean dyingOnly) {
        PriorityQueue<Patch> selected = new PriorityQueue<>(limit + 1,
            (left, right) -> Double.compare(patchPriority(right), patchPriority(left)));
        for (Patch patch : state.patches.values()) {
            if (dyingOnly && patch.phase != FirePhase.SMOLDERING
                && patch.phase != FirePhase.DECAYING) continue;
            selected.add(patch);
            if (selected.size() > limit) selected.poll();
        }
        return selected;
    }

    private static double patchPriority(final Patch patch) {
        int phasePriority = switch (patch.phase) {
            case SMOLDERING -> 0;
            case DECAYING -> 1;
            case IGNITION -> 2;
            case GROWING -> 3;
            case FLAMING -> 4;
        };
        return phasePriority * 5.0 + patch.heat * patch.coverage * 3.0
            + patch.targetIntensity + patch.fuel * 0.5;
    }

    private static void spawnEmber(final ServerLevel level, final LevelState state,
        final Patch patch, final long now) {
        Vec3 wind = FireWindEngine.windAt(level, patch.anchor.position()).scale(
            ventilationFactor(level, state, patch.anchor.host(), now));
        long seed = mix(patch.seed ^ now ^ state.embers.size());
        SplittableRandom random = new SplittableRandom(seed);
        Vec3 velocity = new Vec3(wind.x * random.nextDouble(0.55, 1.25)
            + random.nextDouble(-0.035, 0.035), random.nextDouble(0.055, 0.13),
            wind.z * random.nextDouble(0.55, 1.25) + random.nextDouble(-0.035, 0.035));
        state.embers.addLast(new Ember(state.nextEmberId++,
            patch.anchor.position().add(0.0, 0.12, 0.0), velocity,
            patch.targetIntensity, seed, now, random.nextInt(80, 171)));
    }

    private static void tickEmbers(final ServerLevel level, final LevelState state, final long now,
		final long deadline) {
		int updates = state.embers.size();
		for (int index = 0; index < updates; index++) {
			if ((index & 3) == 0 && System.nanoTime() >= deadline) break;
			Ember ember = state.embers.removeFirst();
			if (now - ember.startTick >= ember.lifetime) continue;
            double progress = Mth.clamp((now - ember.startTick)
                / (double) Math.max(1, ember.lifetime), 0.0, 1.0);
            ember.velocity = stepEmberVelocity(ember.velocity,
                FireWindEngine.windAt(level, ember.position), ember.seed,
                ember.startTick, now, progress);
            Vec3 next = ember.position.add(ember.velocity);
			if (!advanceEmber(level, state, ember, next, now)) {
				ember.position = next;
				state.embers.addLast(ember);
			}
        }
    }

	private static boolean advanceEmber(final ServerLevel level, final LevelState fireState,
		final Ember ember, final Vec3 next, final long now) {
		Vec3 travel = next.subtract(ember.position);
		int steps = Math.min(MAX_EMBER_COLLISION_STEPS,
			Math.max(1, Mth.ceil(travel.length() * 4.0)));
		for (int step = 1; step <= steps; step++) {
			Vec3 sample = ember.position.add(travel.scale(step / (double) steps));
			BlockPos host = BlockPos.containing(sample);
			if (!isLoaded(level, host)) return true;
			BlockState stateAt = level.getBlockState(host);
			if (stateAt.isAir()) continue;
			FireFuelProfile profile = FireFuelProfile.of(stateAt);
			if (profile.flammable()) {
				Direction face = oppositeDominant(ember.velocity);
				FireSurfaceAnchor anchor = FireSurfaceAnchor.center(host, face);
				double ignitionChance = Mth.clamp(0.18 + ember.intensity * 0.58
					* profile.emberSusceptibility(), 0.12, 0.88);
				float contactDose = unit(mix(ember.seed ^ host.asLong() ^ now)) < ignitionChance
					? 0.55F + ember.intensity * profile.emberSusceptibility() * 0.95F
					: 0.18F + ember.intensity * 0.25F;
				addPreheat(level, fireState, anchor, contactDose, ember.intensity,
					ember.seed, now, profile);
			}
			return true;
		}
		return false;
	}

    /** Shared deterministic ember integrator used by server collision and client prediction. */
    public static Vec3 stepEmberVelocity(final Vec3 velocity, final Vec3 wind,
        final long seed, final long startTick, final double sampleTick,
        final double ageProgress) {
        Vec3 safeWind = wind == null ? Vec3.ZERO : wind;
        double horizontalSpeed = Math.sqrt(safeWind.x * safeWind.x + safeWind.z * safeWind.z);
        double seedAngle = unit(seed ^ 0x454D4245525F4355L) * Mth.TWO_PI;
        Vec3 lateral = horizontalSpeed > 1.0E-5
            ? new Vec3(-safeWind.z / horizontalSpeed, 0.0, safeWind.x / horizontalSpeed)
            : new Vec3(Math.cos(seedAngle), 0.0, Math.sin(seedAngle));
        double age = Math.max(0.0, sampleTick - startTick);
        double amplitude = 0.008 + Math.min(0.040, horizontalSpeed * 0.040)
            * (0.55 + 0.45 * (1.0 - Mth.clamp(ageProgress, 0.0, 1.0)));
        double sway = Math.sin(age * 0.19 + seedAngle)
            + Math.sin(age * 0.071 + seedAngle * 1.73) * 0.46;
        double forwardFlutter = Math.cos(age * 0.127 + seedAngle * 0.61) * amplitude * 0.22;
        Vec3 forward = horizontalSpeed > 1.0E-5
            ? new Vec3(safeWind.x / horizontalSpeed, 0.0, safeWind.z / horizontalSpeed)
            : new Vec3(-lateral.z, 0.0, lateral.x);
        double lift = 0.010 * (1.0 - ageProgress) - 0.006 * ageProgress
            + Math.sin(age * 0.163 + seedAngle * 0.79) * 0.0045;
        return velocity.scale(0.90).add(safeWind.scale(0.10))
            .add(lateral.scale(sway * amplitude)).add(forward.scale(forwardFlutter))
            .add(0.0, lift, 0.0);
    }

    private static Direction oppositeDominant(final Vec3 velocity) {
        double ax = Math.abs(velocity.x), ay = Math.abs(velocity.y), az = Math.abs(velocity.z);
        if (ay >= ax && ay >= az) return velocity.y > 0.0 ? Direction.DOWN : Direction.UP;
        if (ax >= az) return velocity.x > 0.0 ? Direction.WEST : Direction.EAST;
        return velocity.z > 0.0 ? Direction.NORTH : Direction.SOUTH;
    }

    private static void removePatch(final LevelState state, final Patch patch) {
        if (state.patches.remove(patch.id) == null) return;
        state.membershipGeneration++;
        state.surfaceIndex.remove(patch.anchor.key());
		unindexPatch(state, patch);
		long hostKey = patch.anchor.host().asLong();
		int remaining = state.hostPatchCounts.getOrDefault(hostKey, 1) - 1;
		if (remaining <= 0) {
			state.hostPatchCounts.remove(hostKey);
			state.nextHostEmberAttemptTick.remove(hostKey);
			state.nextHostTransferTick.remove(hostKey);
			state.nextHostDamageTick.remove(hostKey);
			state.ventilation.remove(hostKey);
		} else state.hostPatchCounts.put(hostKey, remaining);
    }

	private static void indexPatch(final LevelState state, final Patch patch) {
		state.patchChunkIndex.computeIfAbsent(chunkKey(patch.anchor.host()),
			ignored -> new HashSet<>()).add(patch.id);
	}

	private static void unindexPatch(final LevelState state, final Patch patch) {
		long key = chunkKey(patch.anchor.host());
		Set<Long> ids = state.patchChunkIndex.get(key);
		if (ids == null) return;
		ids.remove(patch.id);
		if (ids.isEmpty()) state.patchChunkIndex.remove(key);
	}

	private static long chunkKey(final BlockPos position) {
		return chunkKey(position.getX() >> 4, position.getZ() >> 4);
	}

	private static long chunkKey(final int x, final int z) {
		return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
	}

	private static float clumpFactor(final LevelState state, final Patch patch) {
		BlockPos host = patch.anchor.host();
		int burningHosts = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					long key = BlockPos.asLong(host.getX() + dx, host.getY() + dy,
						host.getZ() + dz);
					if (state.hostPatchCounts.getOrDefault(key, 0) > 0) burningHosts++;
				}
			}
		}
		/* A few sub-patches or two adjacent logs are ordinary fire. Clumping
		 * begins only once a genuinely dense group of distinct blocks is alight. */
		return Mth.clamp((burningHosts - 6) / 12.0F, 0.0F, 1.5F);
	}

    private static void removeHostPatches(final LevelState state, final BlockPos host) {
        List<Patch> matches = new ArrayList<>();
        for (Patch candidate : state.patches.values())
            if (candidate.anchor.host().equals(host)) matches.add(candidate);
        for (Patch candidate : matches) removePatch(state, candidate);
    }

    private static void startSnapshot(final LevelState state) {
		state.snapshotPatchIds.clear();
		state.snapshotPatchIds.addAll(state.patches.keySet());
		state.snapshotPatches.clear();
		state.snapshotWind.clear();
		state.snapshotGeneration = state.membershipGeneration;
		state.snapshotInProgress = true;
	}

	/** Builds the authoritative visual snapshot in small main-thread slices.
	 * World-state reads stay safe while the old one-tick 4,096-patch spike is removed. */
	private static void advanceSnapshot(final ServerLevel level, final LevelState state) {
		if (!state.snapshotInProgress) return;
		long deadline = System.nanoTime() + FIRE_SNAPSHOT_PREP_BUDGET_NANOS;
		int processed = 0;
		while (!state.snapshotPatchIds.isEmpty()
			&& (processed < MIN_SNAPSHOT_PATCHES_PER_TICK || System.nanoTime() < deadline)) {
			long id = state.snapshotPatchIds.removeFirst();
			Patch patch = state.patches.get(id);
			processed++;
			if (patch == null) continue;
            FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(patch.anchor.host()));
            float smoke = smokeProduction(patch, profile);
            BlockPos host = patch.anchor.host();
            long windCell = BlockPos.asLong(host.getX() >> 3, host.getY() >> 3,
                host.getZ() >> 3);
            Vec3 wind = state.snapshotWind.computeIfAbsent(windCell,
                ignored -> FireWindEngine.windAt(level, patch.anchor.position())).scale(
                    ventilationFactor(level, state, host, level.getGameTime()));
			state.snapshotPatches.add(new FireCellSnapshot(patch.id, patch.anchor,
                patch.targetIntensity, patch.heat, patch.coverage, smoke, patch.phase,
                patch.seed, patch.ignitionGameTime, wind));
        }
		if (!state.snapshotPatchIds.isEmpty()) return;
		state.snapshotInProgress = false;
		FireNetworking.sendSnapshot(level, List.copyOf(state.snapshotPatches),
			emberSnapshots(level, state), state.snapshotGeneration == state.membershipGeneration);
		state.snapshotPatches.clear();
		state.snapshotWind.clear();
    }

    private static void sendEmberSnapshot(final ServerLevel level, final LevelState state) {
        if (PlayerLookup.level(level).isEmpty()) return;
        FireNetworking.sendEmberSnapshot(level, emberSnapshots(level, state));
    }

    private static List<FireEmberSnapshot> emberSnapshots(final ServerLevel level,
        final LevelState state) {
		List<FireEmberSnapshot> snapshots = new ArrayList<>(state.embers.size());
		for (Ember ember : state.embers) snapshots.add(new FireEmberSnapshot(ember.id,
			ember.position, ember.velocity, FireWindEngine.windAt(level, ember.position),
            ember.intensity, ember.seed, ember.startTick, ember.lifetime));
        return snapshots;
    }

    private static void checkpoint(final ServerLevel level, final LevelState state) {
		FireSavedData patchData = FireSavedData.get(level);
		FireEmberSavedData emberData = FireEmberSavedData.get(level);
		if (state == null) {
			if (!patchData.entries().isEmpty()) patchData.replace(List.of());
			if (!emberData.entries().isEmpty()) emberData.replace(List.of());
			return;
		}
        long now = level.getGameTime();
        List<FireSavedData.Entry> entries = new ArrayList<>(state.patches.size());
        for (Patch patch : state.patches.values()) entries.add(new FireSavedData.Entry(
            patch.anchor.host(), patch.anchor.face().ordinal(), patch.anchor.localX(),
            patch.anchor.localY(), patch.anchor.localZ(), patch.targetIntensity,
            patch.phase.ordinal(), patch.heat, patch.coverage, patch.fuel, patch.burnTicks,
            patch.seed, patch.ignitionGameTime, Math.max(0L, patch.nextTransferTick - now),
            patch.surfaceFlame, patch.consumed));
		patchData.replace(entries);
		List<FireEmberSavedData.Entry> savedEmbers = new ArrayList<>(state.embers.size());
		for (Ember ember : state.embers) {
			int remaining = ember.lifetime - (int) Math.max(0L, now - ember.startTick);
			if (remaining <= 0) continue;
			savedEmbers.add(new FireEmberSavedData.Entry(ember.position.x, ember.position.y,
				ember.position.z, ember.velocity.x, ember.velocity.y, ember.velocity.z,
				ember.intensity, ember.seed, remaining));
		}
		emberData.replace(savedEmbers);
    }

    private static void restore(final ServerLevel level) {
        List<FireSavedData.Entry> saved = FireSavedData.get(level).entries();
		List<FireEmberSavedData.Entry> savedEmbers = FireEmberSavedData.get(level).entries();
		if (saved.isEmpty() && savedEmbers.isEmpty()) return;
        LevelState state = new LevelState();
        long now = level.getGameTime();
        for (FireSavedData.Entry entry : saved) {
            if (state.patches.size() >= MAX_ACTIVE_PATCHES) break;
            if (entry.face() < 0 || entry.face() >= DIRECTIONS.length || entry.phase() < 0
                || entry.phase() >= FirePhase.values().length) continue;
            FireSurfaceAnchor anchor = new FireSurfaceAnchor(entry.host(), DIRECTIONS[entry.face()],
                entry.localX(), entry.localY(), entry.localZ());
            if (!Float.isFinite(entry.intensity()) || !Float.isFinite(entry.heat())
                || !Float.isFinite(entry.coverage()) || !Float.isFinite(entry.fuel())) continue;
            long id = state.nextId++;
            Patch patch = new Patch(id, anchor, Mth.clamp(entry.intensity(), 0.10F, 1.0F),
                FirePhase.values()[entry.phase()], Mth.clamp(entry.heat(), 0.0F, 1.2F),
                Mth.clamp(entry.coverage(), 0.0F, 1.0F),
                Mth.clamp(entry.burnTicks(), 240, 12_000), entry.seed(), now,
                entry.surfaceFlame());
            patch.fuel = Mth.clamp(entry.fuel(), 0.0F, 1.0F);
            patch.ignitionGameTime = Math.min(entry.ignitionGameTime(), now);
            patch.nextTransferTick = now + Mth.clamp(entry.nextTransferDelay(), 0L, 200L);
            patch.consumed = entry.consumed();
            state.patches.put(id, patch);
            state.surfaceIndex.put(anchor.key(), id);
			state.hostPatchCounts.merge(anchor.host().asLong(), 1, Integer::sum);
            indexPatch(state, patch);
            state.workQueue.addLast(id);
        }
		for (FireEmberSavedData.Entry entry : savedEmbers) {
			if (state.embers.size() >= MAX_EMBERS || !Double.isFinite(entry.x())
				|| !Double.isFinite(entry.y()) || !Double.isFinite(entry.z())
				|| !Double.isFinite(entry.velocityX()) || !Double.isFinite(entry.velocityY())
				|| !Double.isFinite(entry.velocityZ()) || Math.abs(entry.velocityX()) > 4.0
				|| Math.abs(entry.velocityY()) > 4.0 || Math.abs(entry.velocityZ()) > 4.0
				|| !Float.isFinite(entry.intensity())
				|| entry.remainingLifetime() <= 0 || entry.remainingLifetime() > 200) continue;
			state.embers.addLast(new Ember(state.nextEmberId++,
				new Vec3(entry.x(), entry.y(), entry.z()),
				new Vec3(entry.velocityX(), entry.velocityY(), entry.velocityZ()),
				Mth.clamp(entry.intensity(), 0.10F, 1.0F), entry.seed(), now,
				entry.remainingLifetime()));
		}
		if (!state.patches.isEmpty() || !state.embers.isEmpty()) LEVELS.put(level, state);
    }

    private static float smokeProduction(final Patch patch, final FireFuelProfile profile) {
        float stage = switch (patch.phase) {
            case IGNITION -> 0.035F;
            case GROWING -> 0.08F + patch.coverage * 0.10F;
            case FLAMING -> 0.10F + profile.smokeSoot() * 0.22F;
            case DECAYING -> 0.28F + profile.smokeSoot() * 0.32F;
            case SMOLDERING -> 0.48F + profile.smokeSoot() * 0.40F;
        };
        return Mth.clamp(stage * (0.28F + patch.targetIntensity * 0.72F)
            * (0.25F + patch.coverage * 0.75F), 0.01F, 1.0F);
    }

    private static void playCrackle(final ServerLevel level, final LevelState state, final long now) {
        int selected = (int) Math.floorMod(mix(now ^ state.patches.size()), state.patches.size());
        Iterator<Patch> iterator = state.patches.values().iterator();
        Patch patch = iterator.next();
        for (int index = 0; index < selected && iterator.hasNext(); index++) patch = iterator.next();
		float clump = clumpFactor(state, patch);
        level.playSound(null, patch.anchor.host(), SoundEvents.CAMPFIRE_CRACKLE,
			SoundSource.BLOCKS, Mth.clamp(0.20F + patch.targetIntensity * 0.42F
				+ clump * 0.12F, 0.20F, 0.95F),
            0.84F + (float) unit(patch.seed ^ now) * 0.34F);
    }

    private static void damageEntities(final ServerLevel level, final Patch patch) {
        Vec3 normal = Vec3.atLowerCornerOf(patch.anchor.face().getUnitVec3i()).scale(0.28);
        Vec3 center = patch.anchor.position().add(normal).add(0.0, 0.30, 0.0);
        AABB contact = AABB.ofSize(center, 1.12, 1.55, 1.12);
        float damage = 0.65F + patch.heat * 0.85F + patch.targetIntensity * 0.50F;
        float burnSeconds = 2.0F + patch.targetIntensity * 4.0F;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, contact,
            candidate -> candidate.isAlive() && !candidate.fireImmune())) {
            entity.igniteForSeconds(burnSeconds);
            entity.hurtServer(level, level.damageSources().inFire(), damage);
        }
    }

    /**
     * Cheap, cached shelter sampling keeps outdoor spatial wind intact while preventing
     * sealed rooms from behaving like open fields. It intentionally samples only short
     * loaded runs; smoke presentation performs its own client-side bounded clearance pass.
     */
    private static float ventilationFactor(final ServerLevel level, final LevelState state,
        final BlockPos host, final long now) {
        long key = host.asLong();
        CachedVentilation cached = state.ventilation.get(key);
        if (cached != null && cached.expiresAt() >= now) return cached.factor();
        BlockPos air = host.above();
        float factor;
        if (isLoaded(level, air) && level.canSeeSky(air)) {
            factor = 1.0F;
        } else {
            int openDirections = 0;
            int longestRun = 0;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int run = 0;
                for (int distance = 1; distance <= 6; distance++) {
                    BlockPos sample = air.relative(direction, distance);
                    if (!isLoaded(level, sample) || !level.getFluidState(sample).isEmpty()
                        || !level.getBlockState(sample).getCollisionShape(level, sample).isEmpty()) break;
                    run++;
                }
                if (run >= 3) openDirections++;
                longestRun = Math.max(longestRun, run);
            }
            int verticalRun = 0;
            for (int distance = 1; distance <= 6; distance++) {
                BlockPos sample = air.above(distance);
                if (!isLoaded(level, sample) || !level.getFluidState(sample).isEmpty()
                    || !level.getBlockState(sample).getCollisionShape(level, sample).isEmpty()) break;
                verticalRun++;
            }
            factor = Mth.clamp(0.04F + longestRun * 0.065F + openDirections * 0.08F
                + verticalRun * 0.025F, 0.04F, 0.82F);
        }
        state.ventilation.put(key, new CachedVentilation(factor,
            now + VENTILATION_CACHE_TICKS + Math.floorMod(mix(key), 24L)));
        return factor;
    }

    private static boolean clearHeatPath(final ServerLevel level, final Vec3 start,
        final Vec3 target) {
        Vec3 travel = target.subtract(start);
        int steps = Math.max(1, Mth.ceil(travel.length() * 3.0));
        BlockPos source = BlockPos.containing(start);
        BlockPos destination = BlockPos.containing(target);
        for (int index = 1; index < steps; index++) {
            BlockPos position = BlockPos.containing(start.add(travel.scale(index / (double) steps)));
            if (position.equals(source) || position.equals(destination)) continue;
            if (!isLoaded(level, position)
                || !level.getBlockState(position).getCollisionShape(level, position).isEmpty())
                return false;
        }
        return true;
    }

    private static boolean visibleFrom(final ServerLevel level, final ServerPlayer source,
        final Vec3 start, final Vec3 target) {
        HitResult hit = level.clip(new ClipContext(start, target, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, source));
        return hit.getType() == HitResult.Type.MISS
            || hit.getLocation().distanceToSqr(target) <= 0.90;
    }

    private static double distanceToSegmentSquared(final Vec3 point,
        final Vec3 start, final Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared <= 1.0E-8) return point.distanceToSqr(start);
        double progress = Mth.clamp(point.subtract(start).dot(segment) / lengthSquared,
            0.0, 1.0);
        return point.distanceToSqr(start.add(segment.scale(progress)));
    }

    private static void decayWetness(final LevelState state, final long now,
		final long deadline) {
		int count = Math.min(MAX_DECAY_ENTRIES_PER_TICK, state.wetnessDecayQueue.size());
		for (int index = 0; index < count; index++) {
			if ((index & 31) == 0 && System.nanoTime() >= deadline) break;
			long key = state.wetnessDecayQueue.removeFirst();
			state.wetnessDecayQueued.remove(key);
			Float value = state.wetness.get(key);
			if (value == null) {
				state.wetnessLastDecay.remove(key);
				continue;
			}
			long previous = state.wetnessLastDecay.getOrDefault(key, now);
			float remaining = value - 0.006F * Math.max(0L, now - previous);
			if (remaining <= 0.0F) {
				state.wetness.remove(key);
				state.wetnessLastDecay.remove(key);
			} else {
				state.wetness.put(key, remaining);
				state.wetnessLastDecay.put(key, now);
				state.wetnessDecayQueued.add(key);
				state.wetnessDecayQueue.addLast(key);
			}
		}
    }

    private static void decayPreheat(final LevelState state, final long now,
		final long deadline) {
		int count = Math.min(MAX_DECAY_ENTRIES_PER_TICK, state.preheatDecayQueue.size());
		for (int index = 0; index < count; index++) {
			if ((index & 31) == 0 && System.nanoTime() >= deadline) break;
			SurfaceKey key = state.preheatDecayQueue.removeFirst();
			state.preheatDecayQueued.remove(key);
			Exposure exposure = state.preheat.get(key);
			if (exposure == null) continue;
			if (now - exposure.lastTouched > 240L || exposure.dose <= 0.005F) {
				state.preheat.remove(key);
			} else {
				state.preheatDecayQueued.add(key);
				state.preheatDecayQueue.addLast(key);
			}
		}
    }

    private static void addWetness(final LevelState state, final long key, final float amount,
        final boolean priority, final long now) {
        Float existing = state.wetness.get(key);
        if (existing == null && state.wetness.size() >= MAX_WET_POSITIONS) {
            if (!priority) return;
            Iterator<Long> iterator = state.wetness.keySet().iterator();
            if (iterator.hasNext()) { iterator.next(); iterator.remove(); }
        }
        state.wetness.put(key, Math.min(1.5F, (existing == null ? 0.0F : existing) + amount));
		if (existing == null) {
			state.wetnessLastDecay.put(key, now);
			if (state.wetnessDecayQueued.add(key)) state.wetnessDecayQueue.addLast(key);
		}
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(final long value) { return (mix(value) >>> 11) * 0x1.0p-53; }

    private static final class LevelState {
        private final LinkedHashMap<Long, Patch> patches = new LinkedHashMap<>();
        private final HashMap<SurfaceKey, Long> surfaceIndex = new HashMap<>();
		private final HashMap<Long, Integer> hostPatchCounts = new HashMap<>();
		private final HashMap<Long, Long> nextHostEmberAttemptTick = new HashMap<>();
		private final HashMap<Long, Long> nextHostTransferTick = new HashMap<>();
		private final HashMap<Long, Long> nextHostDamageTick = new HashMap<>();
		private final HashMap<Long, CachedVentilation> ventilation = new HashMap<>();
        private final HashMap<Long, HashSet<Long>> patchChunkIndex = new HashMap<>();
        private final ArrayDeque<Long> workQueue = new ArrayDeque<>();
        private final HashMap<Long, Float> wetness = new HashMap<>();
        private final HashMap<SurfaceKey, Exposure> preheat = new HashMap<>();
		private final HashMap<Long, Long> wetnessLastDecay = new HashMap<>();
		private final ArrayDeque<Long> wetnessDecayQueue = new ArrayDeque<>();
		private final HashSet<Long> wetnessDecayQueued = new HashSet<>();
		private final ArrayDeque<SurfaceKey> preheatDecayQueue = new ArrayDeque<>();
		private final HashSet<SurfaceKey> preheatDecayQueued = new HashSet<>();
        private final ArrayDeque<Ember> embers = new ArrayDeque<>();
		private final ArrayDeque<Long> snapshotPatchIds = new ArrayDeque<>();
		private final ArrayList<FireCellSnapshot> snapshotPatches = new ArrayList<>();
		private final HashMap<Long, Vec3> snapshotWind = new HashMap<>();
		private boolean snapshotInProgress;
		private long membershipGeneration;
		private long snapshotGeneration;
        private long nextId = 1L;
		private long nextEmberId = 1L;
        private long lastPriorityEvictionTick = Long.MIN_VALUE;
        private long lastHealthyNuclearEvictionTick = Long.MIN_VALUE;
        private int newIgnitionsThisTick;
    }

    private static final class Patch {
        private final long id;
        private final FireSurfaceAnchor anchor;
        private float targetIntensity;
        private FirePhase phase;
        private float heat;
        private float coverage;
        private float fuel = 1.0F;
        private final int burnTicks;
        private final long seed;
        private long ignitionGameTime;
        private long lastUpdateTick;
        private long nextTransferTick;
        private final boolean surfaceFlame;
        private boolean consumed;

        private Patch(final long id, final FireSurfaceAnchor anchor,
            final float targetIntensity, final FirePhase phase, final float heat,
            final float coverage, final int burnTicks, final long seed, final long now,
            final boolean surfaceFlame) {
            this.id = id; this.anchor = anchor; this.targetIntensity = targetIntensity;
            this.phase = phase; this.heat = heat; this.coverage = coverage;
            this.burnTicks = burnTicks; this.seed = seed; this.ignitionGameTime = now;
            this.lastUpdateTick = now; this.nextTransferTick = now + 12L;
            this.surfaceFlame = surfaceFlame;
        }
    }

    private static final class Ember {
		private final long id;
        private Vec3 position;
        private Vec3 velocity;
        private final float intensity;
        private final long seed;
        private final long startTick;
        private final int lifetime;
        private Ember(final long id, final Vec3 position, final Vec3 velocity, final float intensity,
            final long seed, final long startTick, final int lifetime) {
			this.id = id; this.position = position; this.velocity = velocity; this.intensity = intensity;
            this.seed = seed; this.startTick = startTick; this.lifetime = lifetime;
        }
    }

    private record Exposure(float dose, long lastTouched) { }
    private record CachedVentilation(float factor, long expiresAt) { }
    private record RankedSurface(FireSurfaceAnchor anchor, double distance, long rank,
        boolean flammable) { }
}
