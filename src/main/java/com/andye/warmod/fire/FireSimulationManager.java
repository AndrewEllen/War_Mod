package com.andye.warmod.fire;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.fire.FireSurfaceAnchor.SurfaceKey;
import com.andye.warmod.fire.network.FireNetworking;
import com.andye.warmod.fire.wind.FireWindEngine;
import com.andye.warmod.item.component.FireDebugConfig;
import com.andye.warmod.scheduler.WarModServerWorkScheduler;
import com.andye.warmod.scheduler.WarModServerWorkScheduler.WorkClass;
import com.andye.warmod.scheduler.WarModServerWorkScheduler.WorkPermit;
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
    private static final int MAX_ACTIVE_PATCHES = 12_288;
    private static final int MAX_TOTAL_PATCHES = 65_536;
    private static final int MAX_PATCH_UPDATES_PER_TICK = 1_536;
    private static final int MAX_DORMANT_REACTIVATIONS_PER_TICK = 256;
    private static final double ACTIVE_SIMULATION_RADIUS = 320.0;
    private static final double ACTIVE_SIMULATION_RADIUS_SQUARED =
        ACTIVE_SIMULATION_RADIUS * ACTIVE_SIMULATION_RADIUS;
    private static final int MAX_NEW_IGNITIONS_PER_TICK = 256;
    private static final int MAX_PREHEAT_SURFACES = 24_576;
    private static final int MAX_WET_POSITIONS = 8_192;
	private static final int MAX_EMBERS = 768;
    private static final int NUCLEAR_DYING_EVICTION_BATCH = 128;
    private static final int NUCLEAR_HEALTHY_EVICTION_BATCH = 128;
    private static final int NETWORK_INTERVAL_TICKS = 10;
    private static final int EMBER_NETWORK_INTERVAL_TICKS = 3;
    private static final int MAX_WET_POSITIONS_PER_JET = 512;
    private static final int MAX_EMBER_COLLISION_STEPS = 12;
	/* This is the showcase fire budget: enough headroom for crown fires to climb
	 * and throw firebrands while still leaving the rest of the server tick intact. */
    private static final long FIRE_TICK_BUDGET_NANOS = 14_000_000L;
    private static final long FIRE_NETWORK_RESERVATION_NANOS = 1_250_000L;
    private static final int COMPLETE_SNAPSHOT_INTERVAL_TICKS = 400;
    private static final double FIRE_VISUAL_RADIUS = 320.0;
    private static final double FIRE_VISUAL_RADIUS_SQUARED = FIRE_VISUAL_RADIUS * FIRE_VISUAL_RADIUS;
    private static final double SMOKE_CLUSTER_RADIUS = 1_536.0;
    private static final double SMOKE_CLUSTER_RADIUS_SQUARED =
        SMOKE_CLUSTER_RADIUS * SMOKE_CLUSTER_RADIUS;
    private static final int SMOKE_CLUSTER_CELL_SIZE = 32;
	private static final int MAX_DECAY_ENTRIES_PER_TICK = 256;
    private static final int FIRE_DAMAGE_INTERVAL_TICKS = 6;
    private static final int VENTILATION_CACHE_TICKS = 120;
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
            && state.activePatchCount >= MAX_ACTIVE_PATCHES) {
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
            if (placed >= maximum || state.activePatchCount >= MAX_ACTIVE_PATCHES) break;
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
                WarModPerformanceDiagnostics.Gauge.DORMANT_FIRE_PATCHES, 0L);
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
        boolean hadPatches = state.activePatchCount > 0;
        boolean hadEmbers = !state.embers.isEmpty();
        boolean hasViewers = !PlayerLookup.level(level).isEmpty();
        if (hasViewers && now % NETWORK_INTERVAL_TICKS == 0L)
            state.patchRefreshPending = true;
        if (hasViewers && (hadEmbers || !state.embers.isEmpty())
            && now % EMBER_NETWORK_INTERVAL_TICKS == 0L) state.emberRefreshPending = true;
        state.newIgnitionsThisTick = 0;
        /* A due replication cycle keeps a small share of the normal 8 ms lane.
           Dense fire can otherwise consume the entire cooperative allowance on
           every tick and starve the first visual snapshot indefinitely. */
        long activeBudget = state.patchRefreshPending || state.emberRefreshPending
            ? Math.min(FIRE_TICK_BUDGET_NANOS, 7_000_000L) : FIRE_TICK_BUDGET_NANOS;
        if (state.patchRefreshPending || state.emberRefreshPending)
            WarModServerWorkScheduler.reserve(level, WorkClass.FIRE_NETWORK,
                FIRE_NETWORK_RESERVATION_NANOS);
        try (WorkPermit permit = WarModServerWorkScheduler.acquire(level,
            WorkClass.FIRE_ACTIVE, activeBudget)) {
            if (permit.available()) {
                long simulationDeadline = permit.deadlineNanos();
                expireDormantPatches(state, now, simulationDeadline);
                reactivateRelevantPatches(level, state, now, simulationDeadline);
                decayWetness(state, now, simulationDeadline);
                decayPreheat(state, now, simulationDeadline);
                tickEmbers(level, state, now, simulationDeadline);

                int updates = Math.min(MAX_PATCH_UPDATES_PER_TICK, state.workQueue.size());
                for (int index = 0; index < updates; index++) {
					if ((index & 7) == 0 && System.nanoTime() >= simulationDeadline) break;
                    long id = state.workQueue.removeFirst();
                    Patch patch = state.patches.get(id);
                    if (patch == null || patch.simulationState != FireSimulationState.ACTIVE)
                        continue;
                    updatePatch(level, state, patch, now);
                    if (state.patches.containsKey(id)
                        && patch.simulationState == FireSimulationState.ACTIVE)
                        state.workQueue.addLast(id);
                }
            }
        }

        if (hasViewers && hadPatches && state.activePatchCount == 0)
            state.patchRefreshPending = true;
        if (!hasViewers) {
            state.patchRefreshPending = false;
            state.emberRefreshPending = false;
        }
        if (state.patchRefreshPending || state.emberRefreshPending) {
            try (WorkPermit permit = WarModServerWorkScheduler.acquire(level,
                WorkClass.FIRE_NETWORK, 4_000_000L)) {
                if (permit.available()) {
                    long snapshotDiagnosticsStarted = WarModPerformanceDiagnostics.begin();
                    boolean queued = sendAreaOfInterestDeltas(level, state, now,
                        state.patchRefreshPending, permit.deadlineNanos());
                    if (queued) {
                        state.patchRefreshPending = false;
                        state.emberRefreshPending = false;
                    }
                    WarModPerformanceDiagnostics.record(
                        WarModPerformanceDiagnostics.Subsystem.FIRE_SNAPSHOT_PREPARATION,
                        snapshotDiagnosticsStarted);
                }
            }
        }
        if (now % 10L == 0L && !state.patches.isEmpty()) playCrackle(level, state, now);
        if (state.patches.isEmpty() && state.wetness.isEmpty()
            && state.preheat.isEmpty() && state.embers.isEmpty()) {
            checkpoint(level, null);
            LEVELS.remove(level);
        }
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.ACTIVE_FIRE_PATCHES, state.activePatchCount);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.DORMANT_FIRE_PATCHES,
            state.patches.size() - state.activePatchCount);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.ACTIVE_FIRE_EMBERS, state.embers.size());
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.FIRE_SNAPSHOT_IN_PROGRESS, 0L);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.FIRE_SNAPSHOT_PENDING_PATCHES, 0L);
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.FIRE_SIMULATION, diagnosticsStarted);
    }

    private static void updatePatch(final ServerLevel level, final LevelState state,
        final Patch patch, final long now) {
        BlockPos host = patch.anchor.host();
        if (!isSimulationRelevant(level, host)) {
            makeDormant(state, patch, now);
            return;
        }
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
                float growthRate = 0.0050F + patch.heat * patch.coverage * 0.018F
					+ Math.min(1.5F, clump) * 0.0040F;
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
                patch.heat += (patch.targetIntensity * 0.52F - patch.heat)
					* Math.min(1.0F, elapsed * 0.095F);
                patch.coverage = Math.min(0.18F, patch.coverage + elapsed * 0.0040F);
                if (age >= 10L + (long) ((1.0F - patch.targetIntensity) * 24.0F))
                    patch.phase = FirePhase.GROWING;
            }
            case GROWING -> {
                patch.heat += (patch.targetIntensity - patch.heat)
                    * Math.min(1.0F, elapsed * (0.050F + patch.targetIntensity * 0.045F)
					* (1.0F + clump * 0.28F));
                patch.coverage += elapsed * (0.0090F + patch.targetIntensity * 0.012F)
					* (1.0F + clump * 0.34F);
                patch.fuel -= elapsed / (float) Math.max(180, patch.burnTicks * 2);
                if (patch.coverage >= 0.88F) {
                    patch.coverage = 0.88F;
                    patch.phase = FirePhase.FLAMING;
                }
            }
            case FLAMING -> {
                patch.heat += (patch.targetIntensity * profile.heatRelease() - patch.heat)
                    * Math.min(1.0F, elapsed * 0.026F);
                patch.coverage = Math.min(1.0F, patch.coverage + elapsed * 0.0032F);
                patch.fuel -= elapsed * combustionPressure
					/ (float) Math.max(160, patch.burnTicks);
                if (patch.fuel < 0.28F) patch.phase = FirePhase.DECAYING;
            }
            case DECAYING -> {
				patch.fuel -= elapsed * combustionPressure
					/ (float) Math.max(140, patch.burnTicks);
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
        patch.version++;

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
            int transferInterval = Math.max(3,
                11 - (int) (patch.targetIntensity * 7.0F)
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
            int attemptInterval = Math.max(3, 9 - (int) (clump * 4.0F));
            state.nextHostEmberAttemptTick.put(hostKey, now + attemptInterval);
            double emberChance = patch.heat * patch.coverage * patch.targetIntensity
                * profile.emberSusceptibility() * (0.46 + clump * 0.28);
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
        /* Buoyant convection is independent of atmospheric wind. Explicit
		 * upward probes make a leaf crown race vertically even in a sealed calm. */
		for (int rise = 2; rise <= 5; rise++) {
			BlockPos candidate = source.anchor.host().above(rise);
			depositToBlock(level, state, source, sourceProfile, candidate, origin,
				windDirection, horizontalWind, now, 2.3F / rise, dueTransfers, false);
		}
        int radius = 3;
        int samples = 12 + Mth.ceil(source.targetIntensity * 14.0F);
        for (int index = 0; index < samples; index++) {
            int dx = random.nextInt(-radius, radius + 1);
            int dy = random.nextInt(-Math.max(1, radius / 2), radius + 1);
            int dz = random.nextInt(-radius, radius + 1);
            if (dx == 0 && dy == 0 && dz == 0) continue;
            BlockPos candidate = source.anchor.host().offset(dx, dy, dz);
			if (candidate.distSqr(source.anchor.host()) > 12.0) continue;
            float sampling = dy > 0 ? 1.85F : 0.74F;
            depositToBlock(level, state, source, sourceProfile, candidate, origin,
				windDirection, horizontalWind, now, sampling, dueTransfers, false);
        }
    }

    private static void depositAlongSurface(final ServerLevel level, final LevelState state,
        final Patch source, final FireFuelProfile profile, final FireSurfaceAnchor target,
        final long now, final int dueTransfers) {
        float dose = 0.055F + source.heat * source.coverage * profile.heatRelease()
			* (0.24F + source.targetIntensity * 0.30F);
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
                if (existingPatch.simulationState == FireSimulationState.DORMANT
                    && !activateDormantPatch(state, existingPatch, now)) {
                    existingPatch = null;
                }
            }
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
                if (existing.simulationState == FireSimulationState.DORMANT
                    && !activateDormantPatch(state, existing, level.getGameTime())) {
                    existing = null;
                }
            }
            if (existing != null) {
                existing.targetIntensity = Math.max(existing.targetIntensity, intensity);
                existing.fuel = Math.max(existing.fuel, 0.35F);
                existing.version++;
                return true;
            }
        }
        if (state.activePatchCount >= MAX_ACTIVE_PATCHES
            || state.patches.size() >= MAX_TOTAL_PATCHES) return false;
        FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(anchor.host()));
        boolean nonFuelSurface = !profile.flammable();
        boolean surfaceFlame = nonFuelSurface || !profile.consumable();
        int burnTicks = nonFuelSurface
            ? 120 + Math.round(intensity * 260.0F) : profile.burnTicks();
        long now = level.getGameTime();
        long id = state.nextId++;
        Patch patch = new Patch(id, anchor, Mth.clamp(intensity, 0.10F, 1.0F),
            direct ? FirePhase.GROWING : FirePhase.IGNITION,
            direct ? 0.30F : 0.12F, direct ? 0.14F : 0.055F,
            burnTicks, seed, now, surfaceFlame);
        state.patches.put(id, patch);
        state.activePatchCount++;
        state.membershipGeneration++;
        state.surfaceIndex.put(anchor.key(), id);
		state.hostPatchCounts.merge(anchor.host().asLong(), 1, Integer::sum);
        indexActivePatch(state, patch);
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
            if (patch.simulationState != FireSimulationState.ACTIVE) continue;
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
        for (Patch patch : state.patches.values()) {
            if (patch.simulationState == FireSimulationState.ACTIVE)
                state.workQueue.addLast(patch.id);
        }
        return dyingOnly || !selected.isEmpty();
    }

    private static PriorityQueue<Patch> lowestPriorityPatches(final LevelState state,
        final int limit, final boolean dyingOnly) {
        PriorityQueue<Patch> selected = new PriorityQueue<>(limit + 1,
            (left, right) -> Double.compare(patchPriority(right), patchPriority(left)));
        for (Patch patch : state.patches.values()) {
            if (patch.simulationState != FireSimulationState.ACTIVE) continue;
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
        Vec3 velocity = new Vec3(wind.x * random.nextDouble(0.90, 1.85)
			+ random.nextDouble(-0.055, 0.055), random.nextDouble(0.13, 0.30),
			wind.z * random.nextDouble(0.90, 1.85) + random.nextDouble(-0.055, 0.055));
        state.embers.addLast(new Ember(state.nextEmberId++,
            patch.anchor.position().add(0.0, 0.12, 0.0), velocity,
            patch.targetIntensity, seed, now, random.nextInt(120, 281)));
    }

    private static void tickEmbers(final ServerLevel level, final LevelState state, final long now,
		final long deadline) {
		int updates = state.embers.size();
		for (int index = 0; index < updates; index++) {
			if ((index & 3) == 0 && System.nanoTime() >= deadline) break;
			Ember ember = state.embers.removeFirst();
			if (now - ember.startTick >= ember.lifetime) continue;
            if (!isSimulationRelevant(level, BlockPos.containing(ember.position))) continue;
            double progress = Mth.clamp((now - ember.startTick)
                / (double) Math.max(1, ember.lifetime), 0.0, 1.0);
            Vec3 emberWind = FireWindEngine.windAt(level, ember.position).scale(
				ventilationFactor(level, state, BlockPos.containing(ember.position), now));
            ember.velocity = stepEmberVelocity(ember.velocity, emberWind, ember.seed,
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
        double lift = 0.018 * (1.0 - ageProgress) - 0.005 * ageProgress
            + Math.sin(age * 0.163 + seedAngle * 0.79) * 0.0045;
        return velocity.scale(0.86).add(safeWind.scale(0.14))
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
        if (patch.simulationState == FireSimulationState.ACTIVE) {
            state.activePatchCount = Math.max(0, state.activePatchCount - 1);
            unindexActivePatch(state, patch);
            removeActiveHostMembership(state, patch);
        } else if (patch.simulationState == FireSimulationState.DORMANT) {
            unindexDormantPatch(state, patch);
        }
        patch.simulationState = FireSimulationState.EXPIRED;
        state.membershipGeneration++;
        state.surfaceIndex.remove(patch.anchor.key());
    }

    private static void removeActiveHostMembership(final LevelState state,
        final Patch patch) {
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

	private static void indexActivePatch(final LevelState state, final Patch patch) {
		state.patchChunkIndex.computeIfAbsent(chunkKey(patch.anchor.host()),
			ignored -> new HashSet<>()).add(patch.id);
        state.smokeCellIndex.computeIfAbsent(smokeCellKey(patch.anchor.host()),
            ignored -> new HashSet<>()).add(patch.id);
	}

	private static void unindexActivePatch(final LevelState state, final Patch patch) {
		long key = chunkKey(patch.anchor.host());
		Set<Long> ids = state.patchChunkIndex.get(key);
		if (ids != null) {
			ids.remove(patch.id);
			if (ids.isEmpty()) state.patchChunkIndex.remove(key);
		}
        unindexSmokePatch(state, patch);
	}

    private static void unindexSmokePatch(final LevelState state, final Patch patch) {
        long smokeKey = smokeCellKey(patch.anchor.host());
        Set<Long> smokeIds = state.smokeCellIndex.get(smokeKey);
        if (smokeIds != null) {
            smokeIds.remove(patch.id);
            if (smokeIds.isEmpty()) state.smokeCellIndex.remove(smokeKey);
        }
	}

    private static void indexDormantPatch(final LevelState state, final Patch patch) {
        state.dormantChunkIndex.computeIfAbsent(chunkKey(patch.anchor.host()),
            ignored -> new HashSet<>()).add(patch.id);
        state.smokeCellIndex.computeIfAbsent(smokeCellKey(patch.anchor.host()),
            ignored -> new HashSet<>()).add(patch.id);
    }

    private static void unindexDormantPatch(final LevelState state, final Patch patch) {
        long key = chunkKey(patch.anchor.host());
        Set<Long> ids = state.dormantChunkIndex.get(key);
        if (ids == null) return;
        ids.remove(patch.id);
        if (ids.isEmpty()) state.dormantChunkIndex.remove(key);
        unindexSmokePatch(state, patch);
    }

    private static boolean isSimulationRelevant(final ServerLevel level,
        final BlockPos position) {
        if (!isLoaded(level, position)) return false;
        Vec3 center = Vec3.atCenterOf(position);
        for (ServerPlayer player : PlayerLookup.level(level)) {
            if (player.distanceToSqr(center) <= ACTIVE_SIMULATION_RADIUS_SQUARED) return true;
        }
        return false;
    }

    private static void makeDormant(final LevelState state, final Patch patch,
        final long now) {
        if (patch.simulationState != FireSimulationState.ACTIVE) return;
        analyticallyAdvance(patch, now);
        if (analyticallyExpired(patch)) {
            removePatch(state, patch);
            return;
        }
        unindexActivePatch(state, patch);
        state.activePatchCount = Math.max(0, state.activePatchCount - 1);
        removeActiveHostMembership(state, patch);
        patch.simulationState = FireSimulationState.DORMANT;
        patch.version++;
        indexDormantPatch(state, patch);
        scheduleDormantExpiry(state, patch, now);
    }

    private static void reactivateRelevantPatches(final ServerLevel level,
        final LevelState state, final long now, final long deadline) {
        if (state.dormantChunkIndex.isEmpty()
            || state.activePatchCount >= MAX_ACTIVE_PATCHES) return;
        int chunkRadius = Mth.ceil(ACTIVE_SIMULATION_RADIUS / 16.0);
        int reactivated = 0;
        HashSet<Long> visitedChunks = new HashSet<>();
        for (ServerPlayer player : PlayerLookup.level(level)) {
            int playerChunkX = Mth.floor(player.getX()) >> 4;
            int playerChunkZ = Mth.floor(player.getZ()) >> 4;
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    if ((visitedChunks.size() & 63) == 0
                        && System.nanoTime() >= deadline) return;
                    long key = chunkKey(playerChunkX + dx, playerChunkZ + dz);
                    if (!visitedChunks.add(key)) continue;
                    Set<Long> ids = state.dormantChunkIndex.get(key);
                    if (ids == null || ids.isEmpty()) continue;
                    for (long id : Set.copyOf(ids)) {
                        Patch patch = state.patches.get(id);
                        if (patch == null || patch.simulationState != FireSimulationState.DORMANT)
                            continue;
                        if (!isSimulationRelevant(level, patch.anchor.host())) continue;
                        activateDormantPatch(state, patch, now);
                        reactivated++;
                        if (reactivated >= MAX_DORMANT_REACTIVATIONS_PER_TICK
                            || state.activePatchCount >= MAX_ACTIVE_PATCHES) return;
                    }
                }
            }
        }
    }

    private static boolean activateDormantPatch(final LevelState state, final Patch patch,
        final long now) {
        if (patch.simulationState != FireSimulationState.DORMANT) return true;
        analyticallyAdvance(patch, now);
        if (analyticallyExpired(patch)) {
            removePatch(state, patch);
            return false;
        }
        unindexDormantPatch(state, patch);
        patch.simulationState = FireSimulationState.ACTIVE;
        patch.dormantExpiryTick = Long.MAX_VALUE;
        patch.version++;
        state.activePatchCount++;
        state.hostPatchCounts.merge(patch.anchor.host().asLong(), 1, Integer::sum);
        indexActivePatch(state, patch);
        state.workQueue.addLast(patch.id);
        return true;
    }

    private static void expireDormantPatches(final LevelState state, final long now,
        final long deadline) {
        int processed = 0;
        while (!state.dormantExpiryQueue.isEmpty()
            && state.dormantExpiryQueue.peek().expiryTick() <= now) {
            if ((processed++ & 31) == 0 && System.nanoTime() >= deadline) return;
            DormantExpiry expiry = state.dormantExpiryQueue.poll();
            Patch patch = state.patches.get(expiry.patchId());
            if (patch == null || patch.simulationState != FireSimulationState.DORMANT
                || patch.dormantExpiryTick != expiry.expiryTick()) continue;
            analyticallyAdvance(patch, now);
            if (analyticallyExpired(patch)) removePatch(state, patch);
            else scheduleDormantExpiry(state, patch, now);
        }
    }

    private static void scheduleDormantExpiry(final LevelState state, final Patch patch,
        final long now) {
        float burnRate = analyticalBurnRate(patch);
        long fuelTicks = burnRate <= 1.0E-8F ? 12_000L
            : Mth.ceil(patch.fuel / burnRate);
        double heatRate = analyticalHeatDecay(patch);
        long heatTicks = patch.heat <= 0.018F ? 1L : (long) Math.ceil(
            Math.log(0.018F / patch.heat) / -Math.max(1.0E-6, heatRate));
        long remaining = Mth.clamp(Math.max(200L, Math.min(fuelTicks, heatTicks)),
            200L, 12_000L);
        patch.dormantExpiryTick = now + remaining;
        state.dormantExpiryQueue.add(new DormantExpiry(patch.id, patch.dormantExpiryTick));
    }

    private static void analyticallyAdvance(final Patch patch, final long now) {
        long elapsed = Math.max(0L, now - patch.lastUpdateTick);
        if (elapsed == 0L) return;
        patch.fuel = Math.max(0.0F, patch.fuel - analyticalBurnRate(patch) * elapsed);
        patch.heat = Mth.clamp((float) (patch.heat
            * Math.exp(-analyticalHeatDecay(patch) * elapsed)), 0.0F, 1.2F);
        double coverageDecay = patch.phase == FirePhase.SMOLDERING ? 0.0020 : 0.0009;
        patch.coverage = Mth.clamp((float) (patch.coverage
            * Math.exp(-coverageDecay * elapsed)), 0.0F, 1.0F);
        if (patch.fuel < 0.28F && patch.phase == FirePhase.FLAMING)
            patch.phase = FirePhase.DECAYING;
        if (patch.fuel <= 0.05F || patch.heat < 0.24F)
            patch.phase = FirePhase.SMOLDERING;
        patch.lastUpdateTick = now;
        patch.version++;
    }

    private static float analyticalBurnRate(final Patch patch) {
        float pressure = 0.35F + patch.targetIntensity * patch.targetIntensity * 1.65F;
        return switch (patch.phase) {
            case IGNITION, GROWING -> 1.0F / Math.max(180, patch.burnTicks * 2);
            case FLAMING -> pressure / Math.max(160, patch.burnTicks);
            case DECAYING -> pressure / Math.max(140, patch.burnTicks);
            case SMOLDERING -> 1.0F / Math.max(240, patch.burnTicks / 2);
        };
    }

    private static double analyticalHeatDecay(final Patch patch) {
        return switch (patch.phase) {
            case IGNITION, GROWING -> 0.0010;
            case FLAMING -> 0.0012;
            case DECAYING -> 0.0024;
            case SMOLDERING -> 0.0042;
        };
    }

    private static boolean analyticallyExpired(final Patch patch) {
        return patch.heat <= 0.018F
            || (patch.fuel <= 0.0F && patch.phase == FirePhase.SMOLDERING);
    }

	private static long chunkKey(final BlockPos position) {
		return chunkKey(position.getX() >> 4, position.getZ() >> 4);
	}

	private static long chunkKey(final int x, final int z) {
		return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
	}

    private static long smokeCellKey(final BlockPos position) {
        return chunkKey(Math.floorDiv(position.getX(), SMOKE_CLUSTER_CELL_SIZE),
            Math.floorDiv(position.getZ(), SMOKE_CLUSTER_CELL_SIZE));
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

    private static boolean sendAreaOfInterestDeltas(final ServerLevel level,
        final LevelState state, final long now, final boolean includePatches,
        final long deadline) {
        List<ServerPlayer> players = List.copyOf(PlayerLookup.level(level));
        if (players.isEmpty()) return false;
        state.replicationGeneration++;
        HashSet<java.util.UUID> present = new HashSet<>();
        List<FireNetworking.ViewerDelta> deltas = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            if (!deltas.isEmpty() && System.nanoTime() >= deadline) break;
            present.add(player.getUUID());
            ViewerReplication replication = state.viewerReplication.computeIfAbsent(
                player.getUUID(), ignored -> new ViewerReplication());
            deltas.add(buildViewerDelta(level, state, player, replication, now,
                state.replicationGeneration, includePatches));
        }
        state.viewerReplication.keySet().retainAll(present);
        FireNetworking.sendDeltas(level, deltas);
        return !deltas.isEmpty();
    }

    private static FireNetworking.ViewerDelta buildViewerDelta(final ServerLevel level,
        final LevelState state, final ServerPlayer player, final ViewerReplication replication,
        final long now, final long generation, final boolean includePatches) {
        Vec3 viewer = player.position();
        List<FireCellSnapshot> changed = List.of();
        List<FireCellSnapshot> clusterSources = List.of();
        List<Long> removed = List.of();
        boolean complete = false;
        if (includePatches) {
            NearbyPatches nearby = nearbyPatches(state, viewer);
            PriorityQueue<RankedPatch> nearest = nearby.nearest();
            int candidateCount = nearby.candidateCount();
            WarModPerformanceDiagnostics.add(
                WarModPerformanceDiagnostics.Gauge.FIRE_VIEWER_NEARBY_CANDIDATES,
                candidateCount);
            List<RankedPatch> selected = nearest.stream()
                .sorted((left, right) -> Double.compare(left.distanceSquared(),
                    right.distanceSquared())).toList();
            boolean recovery = replication.lastCompleteTick == Long.MIN_VALUE
                || now - replication.lastCompleteTick >= COMPLETE_SNAPSHOT_INTERVAL_TICKS;
            complete = recovery && candidateCount <= com.andye.warmod.fire.network
                .ClientboundFireStatePayload.MAX_ENTRIES;
            HashMap<Long, Long> currentVersions = new HashMap<>(selected.size());
            HashMap<Long, FireCellSnapshot> snapshotCache = new HashMap<>();
            ArrayList<FireCellSnapshot> updates = new ArrayList<>();
            for (RankedPatch ranked : selected) {
                Patch patch = ranked.patch();
                currentVersions.put(patch.id, patch.version);
                if (recovery || replication.knownVersions.getOrDefault(patch.id, Long.MIN_VALUE)
                    != patch.version) {
                    FireCellSnapshot snapshot = snapshotPatch(level, state, patch, now);
                    if (snapshot != null) {
                        updates.add(snapshot);
                        snapshotCache.put(patch.id, snapshot);
                    }
                }
            }
            ArrayList<Long> removals = new ArrayList<>();
            for (long knownId : replication.knownVersions.keySet()) {
                if (!currentVersions.containsKey(knownId)) removals.add(knownId);
            }
            changed = List.copyOf(updates);
            WarModPerformanceDiagnostics.add(
                WarModPerformanceDiagnostics.Gauge.FIRE_VIEWER_CHANGED_PATCHES,
                changed.size());
            removed = List.copyOf(removals);
            clusterSources = smokeClusterSources(level, state, viewer, now, snapshotCache);
            replication.knownVersions.clear();
            replication.knownVersions.putAll(currentVersions);
            if (recovery) replication.lastCompleteTick = now;
            if (complete) WarModPerformanceDiagnostics.add(
                WarModPerformanceDiagnostics.Gauge.FIRE_COMPLETE_SNAPSHOTS, 1L);
        }
        return new FireNetworking.ViewerDelta(player.getUUID(), viewer, now, generation, complete,
            changed, removed, nearbyEmbers(level, state, viewer), clusterSources, includePatches);
    }

    private static NearbyPatches nearbyPatches(final LevelState state,
        final Vec3 viewer) {
        int maximum = com.andye.warmod.fire.network.ClientboundFireStatePayload.MAX_ENTRIES;
        PriorityQueue<RankedPatch> nearest = new PriorityQueue<>(maximum + 1,
            (left, right) -> Double.compare(right.distanceSquared(), left.distanceSquared()));
        int chunkRadius = Mth.ceil(FIRE_VISUAL_RADIUS / 16.0);
        int playerChunkX = Mth.floor(viewer.x) >> 4;
        int playerChunkZ = Mth.floor(viewer.z) >> 4;
        int candidateCount = 0;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Set<Long> ids = state.patchChunkIndex.get(
                    chunkKey(playerChunkX + dx, playerChunkZ + dz));
                if (ids == null) continue;
                for (long id : ids) {
                    Patch patch = state.patches.get(id);
                    if (patch == null || patch.simulationState != FireSimulationState.ACTIVE)
                        continue;
                    double distanceSquared = viewer.distanceToSqr(patch.anchor.position());
                    if (distanceSquared > FIRE_VISUAL_RADIUS_SQUARED) continue;
                    candidateCount++;
                    nearest.add(new RankedPatch(patch, distanceSquared));
                    if (nearest.size() > maximum) nearest.poll();
                }
            }
        }
        return new NearbyPatches(nearest, candidateCount);
    }

    private static List<FireCellSnapshot> smokeClusterSources(final ServerLevel level,
        final LevelState state, final Vec3 viewer, final long now,
        final Map<Long, FireCellSnapshot> snapshotCache) {
        int cellRadius = Mth.ceil(SMOKE_CLUSTER_RADIUS / SMOKE_CLUSTER_CELL_SIZE);
        int viewerCellX = Math.floorDiv(Mth.floor(viewer.x), SMOKE_CLUSTER_CELL_SIZE);
        int viewerCellZ = Math.floorDiv(Mth.floor(viewer.z), SMOKE_CLUSTER_CELL_SIZE);
        ArrayList<FireCellSnapshot> result = new ArrayList<>();
        for (int dx = -cellRadius; dx <= cellRadius; dx++) {
            for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                Set<Long> ids = state.smokeCellIndex.get(
                    chunkKey(viewerCellX + dx, viewerCellZ + dz));
                if (ids == null) continue;
                for (long id : ids) {
                    Patch patch = state.patches.get(id);
                    if (patch == null || patch.simulationState == FireSimulationState.EXPIRED
                        || viewer.distanceToSqr(patch.anchor.position())
                            > SMOKE_CLUSTER_RADIUS_SQUARED) continue;
                    FireCellSnapshot snapshot = snapshotCache.computeIfAbsent(id,
                        ignored -> snapshotPatchForSmoke(level, state, patch, now));
                    if (snapshot != null && snapshot.smoke() >= 0.018F) result.add(snapshot);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<FireEmberSnapshot> nearbyEmbers(final ServerLevel level,
        final LevelState state, final Vec3 viewer) {
        int maximum = com.andye.warmod.fire.network.ClientboundFireStatePayload.MAX_EMBERS;
        PriorityQueue<RankedEmberSnapshot> nearest = new PriorityQueue<>(maximum + 1,
            (left, right) -> Double.compare(right.distanceSquared(), left.distanceSquared()));
        for (FireEmberSnapshot snapshot : emberSnapshots(level, state)) {
            double distanceSquared = viewer.distanceToSqr(snapshot.position());
            if (distanceSquared > FIRE_VISUAL_RADIUS_SQUARED) continue;
            nearest.add(new RankedEmberSnapshot(snapshot, distanceSquared));
            if (nearest.size() > maximum) nearest.poll();
        }
        return nearest.stream().sorted((left, right) -> Double.compare(
            left.distanceSquared(), right.distanceSquared()))
            .map(RankedEmberSnapshot::snapshot).toList();
    }

    private static FireCellSnapshot snapshotPatch(final ServerLevel level,
        final LevelState state, final Patch patch, final long now) {
        if (patch.simulationState != FireSimulationState.ACTIVE
            || !isLoaded(level, patch.anchor.host())) return null;
        FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(patch.anchor.host()));
        float smoke = smokeProduction(patch, profile);
        Vec3 wind = FireWindEngine.windAt(level, patch.anchor.position()).scale(
            ventilationFactor(level, state, patch.anchor.host(), now));
        return new FireCellSnapshot(patch.id, patch.anchor, patch.targetIntensity,
            patch.heat, patch.coverage, smoke, patch.phase, patch.seed,
            patch.ignitionGameTime, wind);
    }

    private static FireCellSnapshot snapshotPatchForSmoke(final ServerLevel level,
        final LevelState state, final Patch patch, final long now) {
        if (patch.simulationState == FireSimulationState.ACTIVE)
            return snapshotPatch(level, state, patch, now);
        if (patch.simulationState != FireSimulationState.DORMANT) return null;
        analyticallyAdvance(patch, now);
        if (analyticallyExpired(patch)) return null;
        float stage = switch (patch.phase) {
            case IGNITION -> 0.08F;
            case GROWING -> 0.20F + patch.coverage * 0.22F;
            case FLAMING -> 0.55F;
            case DECAYING -> 0.72F;
            case SMOLDERING -> 0.90F;
        };
        float smoke = Mth.clamp(stage * (0.28F + patch.targetIntensity * 0.72F)
            * (0.25F + patch.coverage * 0.75F), 0.01F, 1.0F);
        Vec3 wind = FireWindEngine.windAt(level, patch.anchor.position());
        return new FireCellSnapshot(patch.id, patch.anchor, patch.targetIntensity,
            patch.heat, patch.coverage, smoke, patch.phase, patch.seed,
            patch.ignitionGameTime, wind);
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
            patch.seed, patch.ignitionGameTime,
            patch.simulationState == FireSimulationState.DORMANT
                ? -Math.max(1L, now - patch.lastUpdateTick)
                : Math.max(0L, patch.nextTransferTick - now),
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
            if (state.patches.size() >= MAX_TOTAL_PATCHES) break;
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
            boolean dormant = entry.nextTransferDelay() < 0L;
            patch.lastUpdateTick = dormant
                ? now - Mth.clamp(-entry.nextTransferDelay(), 1L, 12_000L) : now;
            patch.nextTransferTick = now + Mth.clamp(entry.nextTransferDelay(), 0L, 200L);
            patch.consumed = entry.consumed();
            patch.simulationState = dormant
                ? FireSimulationState.DORMANT : FireSimulationState.ACTIVE;
            state.patches.put(id, patch);
            state.surfaceIndex.put(anchor.key(), id);
            if (dormant) {
                indexDormantPatch(state, patch);
                scheduleDormantExpiry(state, patch, now);
            } else {
                state.activePatchCount++;
				state.hostPatchCounts.merge(anchor.host().asLong(), 1, Integer::sum);
                indexActivePatch(state, patch);
                state.workQueue.addLast(id);
            }
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
            case IGNITION -> 0.08F;
            case GROWING -> 0.20F + patch.coverage * 0.22F;
            case FLAMING -> 0.24F + profile.smokeSoot() * 0.52F;
            case DECAYING -> 0.44F + profile.smokeSoot() * 0.48F;
            case SMOLDERING -> 0.62F + profile.smokeSoot() * 0.52F;
        };
        return Mth.clamp(stage * (0.28F + patch.targetIntensity * 0.72F)
            * (0.25F + patch.coverage * 0.75F), 0.01F, 1.0F);
    }

    private static void playCrackle(final ServerLevel level, final LevelState state, final long now) {
        if (state.activePatchCount <= 0) return;
        int voices = Math.min(16, Math.max(2, state.activePatchCount / 48 + 1));
        List<Patch> patches = new ArrayList<>(state.activePatchCount);
        for (Set<Long> ids : state.patchChunkIndex.values()) {
            for (long id : ids) {
                Patch patch = state.patches.get(id);
                if (patch != null && patch.simulationState == FireSimulationState.ACTIVE)
                    patches.add(patch);
            }
        }
        if (patches.isEmpty()) return;
        int stride = Math.max(1, patches.size() / voices);
        int start = Math.floorMod((int) mix(now ^ patches.size()), stride);
        for (int index = start, played = 0; index < patches.size() && played < voices;
			index += stride, played++) {
			Patch patch = patches.get(index);
			float clump = clumpFactor(state, patch);
			float volume = Mth.clamp(0.70F + patch.targetIntensity * 0.78F
				+ clump * 0.22F, 0.70F, 2.15F);
			level.playSound(null, patch.anchor.host(), SoundEvents.FIRE_AMBIENT,
				SoundSource.BLOCKS, volume, 0.82F + (float) unit(patch.seed ^ now) * 0.28F);
			if ((played & 1) == 0) level.playSound(null, patch.anchor.host(),
				SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, volume * 0.72F,
				0.88F + (float) unit(patch.seed + now) * 0.24F);
		}
    }

    private static void damageEntities(final ServerLevel level, final Patch patch) {
        Vec3 normal = Vec3.atLowerCornerOf(patch.anchor.face().getUnitVec3i()).scale(0.28);
        Vec3 center = patch.anchor.position().add(normal).add(0.0, 0.30, 0.0);
        AABB contact = AABB.ofSize(center, 1.12, 1.55, 1.12);
        float damage = 1.40F + patch.heat * 1.65F + patch.targetIntensity * 1.25F;
        float burnSeconds = 5.0F + patch.targetIntensity * 8.0F;
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
        private final HashMap<Long, HashSet<Long>> smokeCellIndex = new HashMap<>();
        private final HashMap<Long, HashSet<Long>> dormantChunkIndex = new HashMap<>();
        private final PriorityQueue<DormantExpiry> dormantExpiryQueue = new PriorityQueue<>(
            (left, right) -> Long.compare(left.expiryTick(), right.expiryTick()));
        private final ArrayDeque<Long> workQueue = new ArrayDeque<>();
        private final HashMap<Long, Float> wetness = new HashMap<>();
        private final HashMap<SurfaceKey, Exposure> preheat = new HashMap<>();
		private final HashMap<Long, Long> wetnessLastDecay = new HashMap<>();
		private final ArrayDeque<Long> wetnessDecayQueue = new ArrayDeque<>();
		private final HashSet<Long> wetnessDecayQueued = new HashSet<>();
		private final ArrayDeque<SurfaceKey> preheatDecayQueue = new ArrayDeque<>();
		private final HashSet<SurfaceKey> preheatDecayQueued = new HashSet<>();
        private final ArrayDeque<Ember> embers = new ArrayDeque<>();
		private long membershipGeneration;
        private long replicationGeneration;
        private final HashMap<java.util.UUID, ViewerReplication> viewerReplication = new HashMap<>();
        private long nextId = 1L;
		private long nextEmberId = 1L;
        private long lastPriorityEvictionTick = Long.MIN_VALUE;
        private long lastHealthyNuclearEvictionTick = Long.MIN_VALUE;
        private int activePatchCount;
        private int newIgnitionsThisTick;
        private boolean patchRefreshPending;
        private boolean emberRefreshPending;
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
        private FireSimulationState simulationState = FireSimulationState.ACTIVE;
        private long dormantExpiryTick = Long.MAX_VALUE;
        private long version = 1L;

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
    private record DormantExpiry(long patchId, long expiryTick) { }
    private record RankedPatch(Patch patch, double distanceSquared) { }
    private record NearbyPatches(PriorityQueue<RankedPatch> nearest, int candidateCount) { }
    private record RankedEmberSnapshot(FireEmberSnapshot snapshot, double distanceSquared) { }
    private static final class ViewerReplication {
        private final HashMap<Long, Long> knownVersions = new HashMap<>();
        private long lastCompleteTick = Long.MIN_VALUE;
    }
    private record RankedSurface(FireSurfaceAnchor anchor, double distance, long rank,
        boolean flammable) { }
}
