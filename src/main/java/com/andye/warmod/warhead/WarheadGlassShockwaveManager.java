package com.andye.warmod.warhead;

import com.andye.warmod.diagnostics.WarModPerformanceDiagnostics;
import com.andye.warmod.scheduler.WarModServerWorkScheduler;
import com.andye.warmod.scheduler.WarModServerWorkScheduler.WorkClass;
import com.andye.warmod.scheduler.WarModServerWorkScheduler.WorkPermit;
import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import com.andye.warmod.warhead.curtain.NuclearDestructionCurtainEmitter;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded nuclear terrain response. Prepared heat/fireball changes drain as
 * soon as the impact flash begins, while glass remains locked to the same
 * 343 m/s pressure front used by the client visuals. Work is deterministic and
 * never forces unrelated chunks to load.
 */
public final class WarheadGlassShockwaveManager {
    private static final double SPEED_BLOCKS_PER_TICK =
        WarheadVisualMath.AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int MAX_COLUMNS_PER_WAVE_TICK = 2_048;
    private static final long PREPARATION_WORK_BUDGET_NANOS = 3_000_000L;
    private static final int NUCLEAR_PREPARATION_COLUMNS_PER_TICK = 8_192;
    private static final long IMPACT_PREPARATION_CATCHUP_NANOS = 6_000_000L;
    private static final long ACTIVE_PREPARATION_CATCHUP_NANOS = 3_000_000L;
    private static final int IMPACT_PREPARATION_CATCHUP_COLUMNS = 65_536;
    /* These are already-discovered mutations, not scan work. Keep enough headroom
       to drain a dense forest annulus during the same tick that the pressure front
       crosses it; preprocessing remains separately time-bounded. */
    private static final int PREPARED_MUTATIONS_PER_WAVE_TICK = 32_768;
    private static final int PREPARED_SURFACE_MUTATIONS_PER_WAVE_TICK = 8_192;
    /* Pull only a tiny queue batch before re-checking the shared deadline. */
    private static final int PREPARED_MICROBATCH = 32;
    private static final int PREPARED_PHASE_COUNT = 10;
    private static final int MAX_PENDING_CRATER_FIRE = 2_048;
    private static final int MAX_PENDING_SURFACE_FIRE = 2_048;
	private static final int MAX_CRATER_FIRE_RETRY_TICKS = 1_200;
    /* Heightmaps are the authoritative starting point.  Never turn a bad
       surface observation into an unbounded cave descent. */
    private static final int SURFACE_SUPPORT_DESCENT = 8;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[] HORIZONTAL_DIRECTIONS = {Direction.NORTH,
        Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final Map<Block, Boolean> GLASS_BLOCK_CACHE = new IdentityHashMap<>();
    private static final Predicate<BlockState> PRESSURE_RELEVANT = state ->
        Wave.isGlass(state) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
            || Wave.isFragileSurface(state);
    private static final Map<ServerLevel, ArrayDeque<Wave>> WAVES = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<java.util.UUID, NuclearTerrainPreparation>>
        NUCLEAR_PREPARATIONS = new IdentityHashMap<>();
    private static boolean registered;

    private WarheadGlassShockwaveManager() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(WarheadGlassShockwaveManager::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            synchronized (WarheadGlassShockwaveManager.class) {
                WAVES.remove(level);
                NUCLEAR_PREPARATIONS.remove(level);
            }
        });
        registered = true;
    }

    public static synchronized void schedule(final ServerLevel level,
        final ClientboundWarheadImpactPayload payload, final Vec3 center,
        final boolean customFire) {
        if (level == null || payload == null || center == null || !center.isFinite()) return;
        if (payload.effectProfile() == WarheadEffectProfile.ANTI_AIR_INTERCEPTION
            || payload.effectProfile() == WarheadEffectProfile.ANTI_AIR_SAFE_SELF_DESTRUCT) return;

        boolean nuclear = payload.payloadType() == WarheadPayloadType.NUCLEAR;
        float visualScale = Mth.clamp(payload.impactVisualScale(), 0.28F, 4.2F);
        double craterRadius = nuclear ? 12.0 + 13.0 * visualScale : 0.0;
        int aftermathRadius = nuclear
            ? Mth.ceil(nuclearAftermathRadius(craterRadius, visualScale)) : 0;
        int glassRadius = nuclear ? nuclearGlassRadius(aftermathRadius, visualScale) : 0;
        double maximumRadius = nuclear
            ? Math.max(Math.max(72.0 + visualScale * 58.0,
                nuclearAftermathRadius(craterRadius, visualScale)), glassRadius)
            : conventionalGlassRadius(visualScale);
        NuclearTerrainPreparation preparation = nuclear
            ? takePreparation(level, payload.warheadId()) : null;
        if (nuclear && preparation == null) {
            /* Direct/master-stick detonation fallback: discover only chunks already present. */
            preparation = new NuclearTerrainPreparation(
                center, payload.visualSeed(), aftermathRadius, glassRadius,
                payload.impactGameTime() + 420L
            );
        }
        if (nuclear) {
            /* Establish the opaque cover at impact instead of waiting for the
               first terrain-pressure pass to finish under a busy tick. */
            NuclearDestructionCurtainEmitter.crossedShells(level, payload.warheadId(), center,
                payload.visualSeed(), visualScale, 0.0, Math.min(12.0, maximumRadius), false);
        }
        Wave wave = new Wave(
            payload.warheadId(), center, payload.impactGameTime(), payload.visualSeed(), maximumRadius,
            visualScale, nuclear, preparation, customFire);
        WAVES.computeIfAbsent(level, ignored -> new ArrayDeque<>()).addLast(wave);
    }

    /**
     * Read-only terrain discovery for a known incoming nuclear impact.  It is
     * intentionally server-side: GPU queries cannot safely read or mutate an
     * authoritative Minecraft world.  Only chunks already loaded by the
     * caller's normal route/impact leases are inspected.
     */
    public static synchronized void prepareNuclearTerrain(
        final ServerLevel level,
        final java.util.UUID impactId,
        final Vec3 center,
        final WarheadYield yield,
        final long seed,
        final int lifetimeTicks
    ) {
        if (level == null || impactId == null || center == null || !center.isFinite()
            || yield == null || !yield.nuclear()) return;
        float visualScale = Mth.clamp(yield.visualScale(), 0.28F, 4.2F);
        double craterRadius = 12.0 + 13.0 * visualScale;
        int aftermathRadius = Mth.ceil(nuclearAftermathRadius(craterRadius, visualScale));
        int glassRadius = nuclearGlassRadius(aftermathRadius, visualScale);
        long expiresAt = level.getGameTime() + Math.max(1, lifetimeTicks);
        Map<java.util.UUID, NuclearTerrainPreparation> preparations =
            NUCLEAR_PREPARATIONS.computeIfAbsent(level, ignored -> new HashMap<>());
        NuclearTerrainPreparation existing = preparations.get(impactId);
        if (existing != null && existing.compatible(center, seed, aftermathRadius)) {
            existing.extend(expiresAt);
            return;
        }
		/* The carrier starts this work under its radar-root ID.  Once the
		 * terminal entity has its final impact ID, move the compatible prepared
		 * scan instead of throwing away the flight-time work and starting over. */
		java.util.UUID compatibleId = null;
		for (Map.Entry<java.util.UUID, NuclearTerrainPreparation> entry
			: preparations.entrySet()) {
			if (!entry.getKey().equals(impactId)
				&& entry.getValue().compatible(center, seed, aftermathRadius)) {
				compatibleId = entry.getKey();
				existing = entry.getValue();
				break;
			}
		}
		if (compatibleId != null) {
			preparations.remove(compatibleId);
			existing.extend(expiresAt);
			preparations.put(impactId, existing);
			return;
		}
        preparations.put(impactId, new NuclearTerrainPreparation(
            center, seed, aftermathRadius, glassRadius, expiresAt));
    }

    /** Drops a carrier's speculative scan when it separates without impact. */
    public static synchronized void cancelNuclearPreparation(final ServerLevel level,
        final java.util.UUID impactId) {
        Map<java.util.UUID, NuclearTerrainPreparation> preparations =
            NUCLEAR_PREPARATIONS.get(level);
        if (preparations == null) return;
        NuclearTerrainPreparation removed = preparations.remove(impactId);
        if (removed != null) removed.stopDiscovery();
        if (preparations.isEmpty()) NUCLEAR_PREPARATIONS.remove(level);
    }

    private static int nuclearGlassRadius(final int aftermathRadius, final float visualScale) {
        return Mth.ceil(Math.max(aftermathRadius * 1.28,
            conventionalGlassRadius(visualScale) * 1.55));
    }

    private static double conventionalGlassRadius(final float visualScale) {
        if (visualScale < 0.49F) return 36.0;
        if (visualScale < 0.82F) return 64.0;
        if (visualScale < 1.19F) return 104.0;
        return 152.0;
    }

    private static synchronized void tick(final ServerLevel level) {
        long diagnosticsStarted = WarModPerformanceDiagnostics.begin();
        advancePreparations(level);
        ArrayDeque<Wave> waves = WAVES.get(level);
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.ACTIVE_NUCLEAR_PREPARATIONS,
            NUCLEAR_PREPARATIONS.getOrDefault(level, Map.of()).size());
        long pendingMutations = NUCLEAR_PREPARATIONS.getOrDefault(level, Map.of()).values()
            .stream().mapToLong(NuclearTerrainPreparation::pendingMutationCount).sum();
        if (waves != null) {
            for (Wave wave : waves) {
                if (wave.preparation != null) pendingMutations += wave.preparation.pendingMutationCount();
                pendingMutations += wave.pendingCraterFire.size() + wave.pendingSurfaceFire.size();
            }
        }
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.PENDING_NUCLEAR_MUTATIONS, pendingMutations);
        if (waves == null || waves.isEmpty()) {
            WarModPerformanceDiagnostics.gauge(
                WarModPerformanceDiagnostics.Gauge.ACTIVE_NUCLEAR_WAVES, 0L);
            WarModPerformanceDiagnostics.record(
                WarModPerformanceDiagnostics.Subsystem.NUCLEAR_WAVE, diagnosticsStarted);
            return;
        }
        long gameTime = level.getGameTime();
		LongOpenHashSet dirtyBiomeChunks = new LongOpenHashSet();
        try (WorkPermit permit = WarModServerWorkScheduler.acquire(level,
            WorkClass.NUCLEAR_AFTERMATH, 5_000_000L)) {
            if (permit.available()) {
                long deadline = permit.deadlineNanos();
                int scheduledWaves = waves.size();
                for (int index = 0; index < scheduledWaves; index++) {
                    if (index > 0 && System.nanoTime() >= deadline) break;
                    Wave wave = waves.removeFirst();
                    if (!wave.advance(level, gameTime, deadline)) waves.addLast(wave);
					wave.drainDirtyBiomeChunks(dirtyBiomeChunks);
                }
            }
        }
		if (!dirtyBiomeChunks.isEmpty()) {
			List<ChunkAccess> changed = new ArrayList<>(dirtyBiomeChunks.size());
			for (long packed : dirtyBiomeChunks) {
				int chunkX = (int) (packed >> 32);
				int chunkZ = (int) packed;
				if (level.getChunkSource().hasChunk(chunkX, chunkZ)) {
					changed.add(level.getChunk(chunkX, chunkZ));
				}
			}
			if (!changed.isEmpty()) {
				level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
			}
		}
        WarModPerformanceDiagnostics.gauge(
            WarModPerformanceDiagnostics.Gauge.ACTIVE_NUCLEAR_WAVES, waves.size());
        if (waves.isEmpty()) WAVES.remove(level);
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.NUCLEAR_WAVE, diagnosticsStarted);
    }

    private static double nuclearAftermathRadius(final double craterRadius,
        final float visualScale) {
        /* Tactical reaches the former heavy-nuclear footprint; strategic and
           heavy yields extend about fifty percent beyond their old 3x scars. */
        double multiplier = visualScale < 2.20F ? 6.125 : 5.625;
        return craterRadius * multiplier;
    }

    private static void advancePreparations(final ServerLevel level) {
        long diagnosticsStarted = WarModPerformanceDiagnostics.begin();
        Map<java.util.UUID, NuclearTerrainPreparation> preparations = NUCLEAR_PREPARATIONS.get(level);
        if (preparations == null || preparations.isEmpty()) {
            WarModPerformanceDiagnostics.record(
                WarModPerformanceDiagnostics.Subsystem.NUCLEAR_PREPARATION, diagnosticsStarted);
            return;
        }
        long now = level.getGameTime();
        try (WorkPermit permit = WarModServerWorkScheduler.acquire(level,
            WorkClass.BACKGROUND_PREP, PREPARATION_WORK_BUDGET_NANOS)) {
            if (permit.available()) {
                long deadline = permit.deadlineNanos();
                Iterator<Map.Entry<java.util.UUID, NuclearTerrainPreparation>> iterator =
                    preparations.entrySet().iterator();
                int remaining = NUCLEAR_PREPARATION_COLUMNS_PER_TICK;
                int entriesRemaining = preparations.size();
                while (iterator.hasNext()) {
                    if (System.nanoTime() >= deadline) break;
                    Map.Entry<java.util.UUID, NuclearTerrainPreparation> entry = iterator.next();
                    NuclearTerrainPreparation preparation = entry.getValue();
                    int slice = Math.max(1, remaining / Math.max(1, entriesRemaining));
                    entriesRemaining--;
                    if (now >= preparation.expiresAt) {
                        iterator.remove();
                        continue;
                    }
                    if (remaining <= 0 || preparation.complete()) continue;
                    remaining -= preparation.advance(level, Math.min(slice, remaining), deadline);
                }
            }
        }
        if (preparations.isEmpty()) NUCLEAR_PREPARATIONS.remove(level);
        WarModPerformanceDiagnostics.record(
            WarModPerformanceDiagnostics.Subsystem.NUCLEAR_PREPARATION, diagnosticsStarted);
    }

    private static NuclearTerrainPreparation takePreparation(
        final ServerLevel level, final java.util.UUID impactId
    ) {
        Map<java.util.UUID, NuclearTerrainPreparation> preparations = NUCLEAR_PREPARATIONS.get(level);
        if (preparations == null) return null;
        NuclearTerrainPreparation preparation = preparations.remove(impactId);
        if (preparations.isEmpty()) NUCLEAR_PREPARATIONS.remove(level);
        return preparation;
    }

    public static synchronized boolean hasPendingWork(final ServerLevel level,
        final java.util.UUID warheadId) {
        Map<java.util.UUID, NuclearTerrainPreparation> preparations =
            NUCLEAR_PREPARATIONS.get(level);
        if (preparations != null && preparations.containsKey(warheadId)) return true;
        ArrayDeque<Wave> waves = WAVES.get(level);
        if (waves == null) return false;
        for (Wave wave : waves) if (wave.warheadId.equals(warheadId)) return true;
        return false;
    }

    private static final class Wave {
        private final java.util.UUID warheadId;
        private final Vec3 center;
        private final long startGameTime;
        private final long seed;
        private final double maximumRadius;
        private final float visualScale;
        private final boolean nuclear;
		private final boolean customFire;
        private final double craterRadius;
        private final int aftermathRadius;
        private final NuclearTerrainPreparation preparation;
        private int remainingCraterCustomFirePlacements;
        private int remainingGroundCustomFirePlacements;
        private int remainingTreeCustomFirePlacements;
        private final ArrayDeque<PendingCraterFire> pendingCraterFire = new ArrayDeque<>();
        private final ArrayDeque<PendingSurfaceFire> pendingSurfaceFire = new ArrayDeque<>();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        private final LongOpenHashSet pressureColumns = new LongOpenHashSet(MAX_COLUMNS_PER_WAVE_TICK * 2);
		private final LongOpenHashSet dirtyBiomeChunks = new LongOpenHashSet();
		private Holder<Biome> basaltDeltas;
        private double processedRadius;
        private boolean pressureComplete;
		private double curtainRadius;
		private boolean curtainCompletionSent;
        private int preparedPhaseCursor;
        private boolean pressurePassActive;
        private double pressurePassTarget;
        private double pressurePassInner;
        private double pressurePassCompletedRadius;
        private int pressurePassBands;
        private int pressurePassSamples;
        private int pressurePassBand;
        private int pressurePassSample;
        private int pressurePassVisitedSamples;
        private int pressurePassProcessedColumns;
        private long pressurePassGameTime;

        private Wave(final java.util.UUID warheadId, final Vec3 center,
            final long startGameTime, final long seed,
            final double maximumRadius, final float visualScale, final boolean nuclear,
            final NuclearTerrainPreparation preparation, final boolean customFire) {
            this.warheadId = warheadId;
            this.center = center;
            this.startGameTime = startGameTime;
            this.seed = seed;
            this.maximumRadius = maximumRadius;
            this.visualScale = visualScale;
            this.nuclear = nuclear;
			this.customFire = customFire;
            this.craterRadius = nuclear ? 12.0 + 13.0 * visualScale : 0.0;
            /* Keep the excavated crater unchanged; only the burned surface reaches out further. */
            this.aftermathRadius = nuclear
                ? Mth.ceil(nuclearAftermathRadius(craterRadius, visualScale)) : 0;
            this.preparation = preparation;
            int customFireBudget = Mth.clamp(
                720 + Math.round(visualScale * 340.0F), 800, 2_200);
            this.remainingCraterCustomFirePlacements = Math.round(customFireBudget * 0.35F);
            this.remainingGroundCustomFirePlacements = Math.round(customFireBudget * 0.30F);
            this.remainingTreeCustomFirePlacements = customFireBudget
                - remainingCraterCustomFirePlacements - remainingGroundCustomFirePlacements;
        }

        private boolean advance(final ServerLevel level, final long gameTime,
            final long deadline) {
            if (nuclear && preparation != null && !preparation.complete()) {
                if (gameTime <= preparation.expiresAt) {
                    long preparationDeadline = Math.min(deadline,
                        System.nanoTime() + ACTIVE_PREPARATION_CATCHUP_NANOS);
                    if (System.nanoTime() < preparationDeadline) {
                        preparation.advance(level, IMPACT_PREPARATION_CATCHUP_COLUMNS,
                            preparationDeadline);
                    }
                } else {
                    preparation.stopDiscovery();
                }
            }
            if (!pressureComplete) {
                /* Never let a busy server leave this wave permanently behind.
                 * Prepared changes jump to the same current-time radius used by
                 * the client instead of catching up only one missed tick per tick. */
                long pressureGameTime = gameTime;
                advancePressure(level, pressureGameTime, deadline);
                if (nuclear && preparation != null) {
                    double thermalRadius = preparation.maximumPreparedRadius();
                    applyPreparedSurfaceTerrain(level, thermalRadius, deadline);
                    applyPreparedThermalTerrain(level, thermalRadius, deadline);
                }
                drainPendingCraterFire(level, deadline);
                drainPendingSurfaceFire(level, deadline);
                if (processedRadius + 0.01 < maximumRadius) return false;
                pressureComplete = true;
            }
            if (!nuclear || preparation == null) return true;
            double preparedRadius = preparation.maximumPreparedRadius();
            /* Glass is pressure-front work and remains first even after the visible
             * front has completed; thermal queues must never consume its deadline. */
            applyPreparedGlassWave(level, preparedRadius, deadline);
            applyPreparedSurfaceTerrain(level, preparedRadius, deadline);
            applyPreparedThermalTerrain(level, preparedRadius, deadline);
            drainPendingCraterFire(level, deadline);
            drainPendingSurfaceFire(level, deadline);
            /* Keep bounded direct-impact fallback work alive until its loaded terrain is drained. */
			boolean complete = !preparation.hasPendingWork() && pendingCraterFire.isEmpty()
				&& pendingSurfaceFire.isEmpty();
			if (complete && !curtainCompletionSent) {
				NuclearDestructionCurtainEmitter.crossedShells(level, warheadId, center,
					seed, visualScale, curtainRadius, curtainRadius, true);
				curtainCompletionSent = true;
			}
			return complete;
        }
        private void advancePressure(final ServerLevel level, final long gameTime,
            final long deadline) {
            double elapsedTicks = Math.max(0.0, gameTime - startGameTime + 1.0);
            if (nuclear) elapsedTicks *= WarheadVisualMath.NUCLEAR_TIME_SCALE;
            double targetRadius = Math.min(maximumRadius,
                elapsedTicks * SPEED_BLOCKS_PER_TICK);
			if (nuclear && targetRadius > curtainRadius + 0.01) {
				NuclearDestructionCurtainEmitter.crossedShells(level, warheadId, center,
					seed, visualScale, curtainRadius, targetRadius,
					false);
				curtainRadius = targetRadius;
			}
			if (nuclear && preparation != null) {
				/* Thermal terrain drains independently during the flash. Glass alone
				 * follows the finite-speed pressure shell so physical shattering stays
				 * locked to the visible front. */
				applyPreparedGlassWave(level, targetRadius, deadline);
				processedRadius = targetRadius;
				return;
			}
            if (!pressurePassActive) {
                if (targetRadius <= processedRadius + 0.01) return;
                pressurePassActive = true;
                pressurePassTarget = targetRadius;
                pressurePassInner = Math.max(0.0, processedRadius - 1.25);
                double annulusWidth = Math.max(0.75, pressurePassTarget - pressurePassInner);
                pressurePassBands = Mth.clamp((int) Math.ceil(annulusWidth / 2.25), 2, 8);
                pressurePassSamples = Mth.clamp(
                    (int) Math.ceil(Math.PI * 2.0 * Math.max(2.0, pressurePassTarget) * 1.30),
                    72, Math.max(72, MAX_COLUMNS_PER_WAVE_TICK / pressurePassBands));
                pressurePassBand = 0;
                pressurePassSample = 0;
                pressurePassVisitedSamples = 0;
                pressurePassProcessedColumns = 0;
                pressurePassCompletedRadius = processedRadius;
                pressurePassGameTime = gameTime;
                pressureColumns.clear();
            }

            pressureWork:
            while (pressurePassBand < pressurePassBands
                && pressurePassProcessedColumns < MAX_COLUMNS_PER_WAVE_TICK) {
                double bandFraction = (pressurePassBand + 0.5) / pressurePassBands;
                double radius = pressurePassInner
                    + (pressurePassTarget - pressurePassInner) * bandFraction;
                double phase = unit(seed ^ pressurePassGameTime * 31L
                    ^ pressurePassBand * 0x9E3779B97F4A7C15L);
                while (pressurePassSample < pressurePassSamples
                    && pressurePassProcessedColumns < MAX_COLUMNS_PER_WAVE_TICK) {
                    if ((pressurePassVisitedSamples & 31) == 0
                        && System.nanoTime() >= deadline) break pressureWork;
                    int sample = pressurePassSample++;
                    pressurePassVisitedSamples++;
                    double angle = (sample + phase) / pressurePassSamples * Math.PI * 2.0;
                    int x = Mth.floor(center.x + Math.cos(angle) * radius);
                    int z = Mth.floor(center.z + Math.sin(angle) * radius);
                    long packedColumn = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                    if (!pressureColumns.add(packedColumn)) continue;
                    processPressureColumn(level, x, z, radius);
                    pressurePassProcessedColumns++;
                }
                if (pressurePassSample >= pressurePassSamples) {
                    pressurePassCompletedRadius = radius;
                    pressurePassBand++;
                    pressurePassSample = 0;
                }
            }
            boolean passComplete = pressurePassBand >= pressurePassBands;
            double appliedRadius = passComplete ? pressurePassTarget : pressurePassCompletedRadius;
            if (appliedRadius <= processedRadius + 0.01) return;
            if (nuclear && preparation != null) {
                /* Pressure stripping runs first so newly placed fire is never
                   deleted again by this same shockwave tick. */
                applyPreparedNuclearTerrain(level, appliedRadius, deadline);
            }
            processedRadius = appliedRadius;
            if (passComplete) pressurePassActive = false;
        }

        private void applyPreparedGlassWave(final ServerLevel level,
            final double targetRadius, final long deadline) {
            int shell = NuclearTerrainPreparation.shellFor(targetRadius);
            int remaining = 8_192;
            while (remaining > 0 && System.nanoTime() < deadline) {
                LongArrayList batch = preparation.takeGlass(shell,
                    Math.min(256, remaining));
                if (batch.isEmpty()) return;
                for (long packed : batch) applyPreparedEntry(level, 2, packed);
                remaining -= batch.size();
            }
        }

        /** Applies heat/fireball terrain independently of the finite-speed pressure shell. */
        private void applyPreparedThermalTerrain(final ServerLevel level,
            final double targetRadius, final long deadline) {
            if (System.nanoTime() >= deadline) return;
            int shell = NuclearTerrainPreparation.shellFor(targetRadius);
            int remaining = PREPARED_MUTATIONS_PER_WAVE_TICK;
            int emptyPhases = 0;
            while (remaining > 0 && emptyPhases < PREPARED_PHASE_COUNT - 2
                && System.nanoTime() < deadline) {
                int phase = preparedPhaseCursor;
                preparedPhaseCursor = (preparedPhaseCursor + 1) % PREPARED_PHASE_COUNT;
                if (phase == 1 || phase == 2) continue;
                LongArrayList batch = takePreparedBatch(phase, shell,
                    Math.min(PREPARED_MICROBATCH, remaining));
                if (batch.isEmpty()) {
                    emptyPhases++;
                    continue;
                }
                emptyPhases = 0;
                for (long packed : batch) applyPreparedEntry(level, phase, packed);
                remaining -= batch.size();
            }
        }

        private void applyPreparedNuclearTerrain(final ServerLevel level,
            final double targetRadius, final long deadline) {
            if (System.nanoTime() >= deadline) return;
            int shell = NuclearTerrainPreparation.shellFor(targetRadius);
            int remaining = PREPARED_MUTATIONS_PER_WAVE_TICK;
            int emptyPhases = 0;
            while (remaining > 0 && emptyPhases < PREPARED_PHASE_COUNT
                && System.nanoTime() < deadline) {
                int phase = preparedPhaseCursor;
                preparedPhaseCursor = (preparedPhaseCursor + 1) % PREPARED_PHASE_COUNT;
                LongArrayList batch = takePreparedBatch(phase, shell,
                    Math.min(PREPARED_MICROBATCH, remaining));
                if (batch.isEmpty()) {
                    emptyPhases++;
                    continue;
                }
                emptyPhases = 0;
                for (long packed : batch) applyPreparedEntry(level, phase, packed);
                remaining -= batch.size();
            }
        }

		/** Ground replacement is a visual prerequisite for vegetation aftermath.
		 * Drain every discovered surface column through the current visual shell
		 * before the round-robin tree/structure phases are allowed to advance. */
		private void applyPreparedSurfaceTerrain(final ServerLevel level,
			final double targetRadius, final long deadline) {
			int shell = NuclearTerrainPreparation.shellFor(targetRadius);
			int remaining = PREPARED_SURFACE_MUTATIONS_PER_WAVE_TICK;
			while (remaining > 0 && System.nanoTime() < deadline) {
				LongArrayList batch = preparation.takeSurfaceColumns(shell,
					Math.min(256, remaining));
				if (batch.isEmpty()) return;
				for (long packed : batch) applyPreparedEntry(level, 1, packed);
				remaining -= batch.size();
			}
		}

        private LongArrayList takePreparedBatch(final int phase, final int shell,
            final int limit) {
            return switch (phase) {
                case 0 -> preparation.takeSnow(shell, limit);
                case 1 -> preparation.takeSurfaceColumns(shell,
                    Math.min(limit, PREPARED_SURFACE_MUTATIONS_PER_WAVE_TICK));
                case 2 -> preparation.takeGlass(shell, limit);
                case 3 -> preparation.takeFragile(shell, limit);
                case 4 -> preparation.takeLeaves(shell, limit);
                case 5 -> preparation.takeLogs(shell, limit);
                case 6 -> preparation.takeStructuralLogs(shell, limit);
                case 7 -> preparation.takePlanks(shell, limit);
                case 8 -> preparation.takeHangingMoss(shell, limit);
                case 9 -> preparation.takeCobble(shell, limit);
                default -> new LongArrayList();
            };
        }

        private void applyPreparedEntry(final ServerLevel level, final int phase,
            final long packed) {
            BlockPos position = BlockPos.of(packed);
            if (!loaded(level, position)) return;
            switch (phase) {
                case 0 -> {
                    if (isSnowLike(level.getBlockState(position))) {
                        level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                    }
                }
                case 1 -> {
                    double dx = position.getX() + 0.5 - center.x;
                    double dz = position.getZ() + 0.5 - center.z;
                    transformNuclearColumn(level, position.getX(), position.getZ(),
                        Math.sqrt(dx * dx + dz * dz));
                }
                case 2 -> applyPreparedGlass(level, position, packed);
                case 3 -> applyPreparedFragile(level, position, packed);
                case 4 -> applyPreparedLeaves(level, position, packed);
                case 5 -> applyPreparedLog(level, position, packed);
                case 6 -> applyPreparedStructuralLog(level, position, packed);
                case 7 -> applyPreparedPlank(level, position, packed);
                case 8 -> applyPreparedHangingMoss(level, position);
                case 9 -> applyPreparedCobble(level, position, packed);
                default -> { }
            }
        }

        private void applyPreparedGlass(final ServerLevel level, final BlockPos position,
            final long packed) {
            BlockState state = level.getBlockState(position);
            double normalized = horizontalDistance(position)
                / Math.max(1.0, preparation.maximumPreparedRadius());
            double chance = normalized <= 0.72 ? 1.0
                : Mth.clamp((1.0 - normalized) / 0.28, 0.0, 1.0);
            if (isGlass(state) && unit(seed ^ packed ^ 0x474C4153535F4E55L) < chance) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            }
        }

        private void applyPreparedFragile(final ServerLevel level, final BlockPos position,
            final long packed) {
            double normalized = horizontalDistance(position) / Math.max(1.0, aftermathRadius);
            double chance = normalized <= 0.78 ? 1.0
                : Mth.clamp((1.0 - normalized) / 0.22, 0.0, 1.0);
            BlockState state = level.getBlockState(position);
            if (isFragileSurface(state) && (state.is(Blocks.SUGAR_CANE)
                || unit(seed ^ packed ^ 0x46524147494C455FL) < chance)) {
                applyFragileAftermath(level, position, state, normalized,
                    seed ^ packed ^ 0x46524147494C455FL);
            }
        }

        private void applyPreparedLeaves(final ServerLevel level, final BlockPos position,
            final long packed) {
            BlockState state = level.getBlockState(position);
            if (!state.is(BlockTags.LEAVES)) return;
            boolean retained = true;
            double normalized = horizontalDistance(position) / Math.max(1.0, aftermathRadius);
            long hash = seed ^ packed ^ 0x4C45415645535F4EL;
            if (normalized <= 0.70) {
                double crownRetention = customFire && normalized > 0.30
                    ? 0.48 + Mth.clamp((normalized - 0.30) / 0.40, 0.0, 1.0) * 0.24 : 0.0;
                if (unit(hash ^ 0x43524F574E5F4649L) < crownRetention) {
                    level.setBlock(position, Blocks.PALE_OAK_LEAVES.defaultBlockState()
                        .setValue(BlockStateProperties.PERSISTENT, true), UPDATE_FLAGS);
                } else {
                    level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                    retained = false;
                }
            } else {
                double outer = Mth.clamp((1.0 - normalized) / 0.30, 0.0, 1.0);
                double stripChance = outer * 0.72;
                double paleChance = 0.10 + outer * 0.64;
                double selector = unit(hash);
                if (selector < stripChance) {
                    level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                    retained = false;
                } else if (selector < stripChance + paleChance) {
                    level.setBlock(position, Blocks.PALE_OAK_LEAVES.defaultBlockState()
                        .setValue(BlockStateProperties.PERSISTENT, true), UPDATE_FLAGS);
                }
            }
            if (retained && customFire) placeTreeFire(level, position, normalized, packed, 0.58);
        }

        private void applyPreparedLog(final ServerLevel level, final BlockPos position,
            final long packed) {
            BlockState state = level.getBlockState(position);
            if (!state.is(BlockTags.LOGS)) return;
            double normalized = horizontalDistance(position) / Math.max(1.0, aftermathRadius);
            if (normalized <= 0.34) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            } else if (normalized <= 0.62) {
                level.setBlock(position, paleLog(state), UPDATE_FLAGS);
                placeTreeFire(level, position, normalized, packed, 1.0);
                placeTreeRemnants(level, position, normalized, packed);
            } else {
                int groundY = terrainSurfaceY(level, position.getX(), position.getZ());
                double upperBias = Mth.clamp((position.getY() - groundY - 5.0) / 30.0,
                    0.0, 1.0);
                double distanceHeat = Mth.clamp((0.94 - normalized) / 0.38, 0.0, 1.0);
                double chance = distanceHeat * (0.28 + upperBias * 0.72);
                if (unit(seed ^ packed ^ 0x4C4F47535F415348L) < chance) {
                    level.setBlock(position, paleLog(state), UPDATE_FLAGS);
                    placeTreeFire(level, position, normalized, packed, 1.0);
                    placeTreeRemnants(level, position, normalized, packed);
                }
            }
        }

        private void applyPreparedStructuralLog(final ServerLevel level,
            final BlockPos position, final long packed) {
            BlockState state = level.getBlockState(position);
            double normalized = horizontalDistance(position) / Math.max(1.0, aftermathRadius);
            double chance = normalized <= 0.64 ? 1.0
                : Mth.clamp((0.90 - normalized) / 0.26, 0.0, 1.0);
            if (state.is(BlockTags.LOGS)
                && unit(seed ^ packed ^ 0x5354525543544C47L) < chance) {
                level.setBlock(position, paleLog(state), UPDATE_FLAGS);
            }
        }

        private void applyPreparedPlank(final ServerLevel level, final BlockPos position,
            final long packed) {
            BlockState state = level.getBlockState(position);
            double normalized = horizontalDistance(position) / Math.max(1.0, aftermathRadius);
            double chance = normalized <= 0.58 ? 1.0
                : Mth.clamp((0.84 - normalized) / 0.26, 0.0, 1.0);
            if (state.is(BlockTags.PLANKS)
                && unit(seed ^ packed ^ 0x504C414E4B5F4153L) < chance) {
                level.setBlock(position, Blocks.PALE_OAK_WOOD.defaultBlockState(), UPDATE_FLAGS);
            }
        }

        private void applyPreparedHangingMoss(final ServerLevel level,
            final BlockPos position) {
            if (!level.getBlockState(position).isAir()) return;
            BlockState moss = Blocks.PALE_HANGING_MOSS.defaultBlockState()
                .setValue(BlockStateProperties.TIP, true);
            if (moss.canSurvive(level, position)) level.setBlock(position, moss, UPDATE_FLAGS);
        }

        private void applyPreparedCobble(final ServerLevel level, final BlockPos position,
            final long packed) {
            BlockState state = level.getBlockState(position);
            double normalized = horizontalDistance(position) / Math.max(1.0, aftermathRadius);
            double chance = normalized <= 0.50 ? 1.0
                : Mth.clamp((0.76 - normalized) / 0.26, 0.0, 1.0);
            if (isCobbleStructure(state)
                && unit(seed ^ packed ^ 0x434F42424C455F44L) < chance) {
                level.setBlock(position, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), UPDATE_FLAGS);
            }
        }

        private boolean loaded(final ServerLevel level, final BlockPos position) {
            return level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4);
        }

        private double horizontalDistance(final BlockPos position) {
            double dx = position.getX() + 0.5 - center.x;
            double dz = position.getZ() + 0.5 - center.z;
            return Math.sqrt(dx * dx + dz * dz);
        }

		private void drainDirtyBiomeChunks(final LongOpenHashSet target) {
			target.addAll(dirtyBiomeChunks);
			dirtyBiomeChunks.clear();
		}

        private static BlockState paleLog(final BlockState original) {
            BlockState replacement = Blocks.PALE_OAK_LOG.defaultBlockState();
            if (original.hasProperty(BlockStateProperties.AXIS)) {
                replacement = replacement.setValue(BlockStateProperties.AXIS,
                    original.getValue(BlockStateProperties.AXIS));
            }
            return replacement;
        }

        private void placeTreeFire(final ServerLevel level, final BlockPos trunk,
            final double normalized, final long packed, final double chanceScale) {
            if (!customFire) {
                double legacyChance = 0.22 * Mth.clamp(
                    (0.82 - normalized) / 0.48, 0.0, 1.0);
                if (unit(seed ^ packed ^ 0x545245455F464952L) >= legacyChance) return;
                WarheadFirePlacement.placeAbove(level, trunk, false,
                    (float) Mth.clamp(0.62 + (1.0 - normalized) * 0.38, 0.10, 1.0),
                    seed ^ packed ^ 0x545245455F464952L, UPDATE_FLAGS);
                return;
            }
            double heat = Mth.clamp((0.94 - normalized) / 0.60, 0.0, 1.0);
            double chance = (0.08 + 0.58 * heat * heat) * chanceScale;
            if (unit(seed ^ packed ^ 0x545245455F464952L) >= chance) return;
			if (!placeBlastFacingFire(level, trunk,
				(float) Mth.clamp(0.35 + heat * 0.65, 0.10, 1.0),
				seed ^ packed ^ 0x545245455F464952L)) {
                queuePendingSurfaceFire(trunk, true,
                    (float) Mth.clamp(0.35 + heat * 0.65, 0.10, 1.0),
                    seed ^ packed ^ 0x545245455F464952L);
            }
        }

        private boolean placeGroundFireAbove(final ServerLevel level, final BlockPos host,
            final float intensity, final long fireSeed) {
            if (customFire && remainingGroundCustomFirePlacements <= 0) return false;
            boolean placed = WarheadFirePlacement.placeAbove(level, host, customFire,
                intensity, fireSeed, UPDATE_FLAGS);
            if (placed && customFire) remainingGroundCustomFirePlacements--;
            return placed;
        }

        private boolean placeCraterFireAbove(final ServerLevel level, final BlockPos host,
            final float intensity, final long fireSeed) {
            if (customFire && remainingCraterCustomFirePlacements <= 0) return false;
            boolean placed = WarheadFirePlacement.placeAbove(level, host, customFire,
                intensity, fireSeed, UPDATE_FLAGS);
            if (placed && customFire) remainingCraterCustomFirePlacements--;
            return placed;
        }

        private boolean placeBlastFacingFire(final ServerLevel level, final BlockPos host,
            final float intensity, final long fireSeed) {
            if (customFire && remainingTreeCustomFirePlacements <= 0) return false;
            boolean placed = WarheadFirePlacement.placeBlastFacing(level, host, center,
                customFire, intensity, fireSeed, UPDATE_FLAGS);
            if (placed && customFire) remainingTreeCustomFirePlacements--;
            return placed;
        }

        private void placeTreeRemnants(final ServerLevel level, final BlockPos log,
            final double normalized, final long packed) {
            double heat = Mth.clamp((0.92 - normalized) / 0.58, 0.0, 1.0);
            long hash = seed ^ packed ^ 0x545245455F52454DL;
            if (unit(hash) < 0.055 * heat) {
                Direction toward = horizontalTowardCenter(log);
                Direction outward = unit(hash ^ 0x57414C4C5F46414EL) < 0.5
                    ? clockwise(toward) : clockwise(toward).getOpposite();
                BlockPos fanPos = log.relative(outward);
                if (level.isInWorldBounds(fanPos) && level.getBlockState(fanPos).isAir()) {
                    BlockState fan = deadCoralWallState((int) (hash >>> 24))
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, outward)
                        .setValue(BlockStateProperties.WATERLOGGED, false);
                    if (fan.canSurvive(level, fanPos))
                        level.setBlock(fanPos, fan, UPDATE_FLAGS);
                }
            }
            if (unit(hash ^ 0x48414E47494E475FL) < 0.045 * heat) {
                BlockPos mossPos = log.below();
                BlockState moss = Blocks.PALE_HANGING_MOSS.defaultBlockState()
                    .setValue(BlockStateProperties.TIP, true);
                if (level.isInWorldBounds(mossPos) && level.getBlockState(mossPos).isAir()
                    && moss.canSurvive(level, mossPos)) {
                    level.setBlock(mossPos, moss, UPDATE_FLAGS);
                }
            }
        }

        private Direction horizontalTowardCenter(final BlockPos position) {
            double dx = center.x - (position.getX() + 0.5);
            double dz = center.z - (position.getZ() + 0.5);
            if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0.0
                ? Direction.EAST : Direction.WEST;
            return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
        }

        private static Direction clockwise(final Direction direction) {
            return switch (direction) {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.SOUTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                default -> Direction.NORTH;
            };
        }

        private void processPressureColumn(final ServerLevel level, final int x, final int z,
            final double radialDistance) {
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return;
            int surfaceY = terrainSurfaceY(level, x, z);
            int belowSurface = nuclear ? 8 : 5;
            int aboveSurface = nuclear ? 52 : 36;
            double normalizedDistance = Mth.clamp(radialDistance / maximumRadius, 0.0, 1.0);
            double glassChance = normalizedDistance <= 0.74
                ? 1.0
                : Mth.clamp((1.0 - normalizedDistance) / 0.26, 0.0, 1.0);
            double scorchLimit = nuclear ? 0.84 : 0.46;
            double scorchIntensity = normalizedDistance >= scorchLimit
                ? 0.0 : 1.0 - normalizedDistance / scorchLimit;

            scorchGround(level, x, surfaceY, z, scorchIntensity);

            int minimumY = Math.max(level.dimensionType().minY(), surfaceY - belowSurface);
            int maximumY = Math.min(
                level.dimensionType().minY() + level.dimensionType().height() - 1,
                surfaceY + aboveSurface
            );
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            int y = minimumY;
            while (y <= maximumY) {
                int sectionEnd = Math.min(maximumY, (y & ~15) + 15);
                LevelChunkSection section = chunk.getSection(level.getSectionIndex(y));
                if (!section.maybeHas(PRESSURE_RELEVANT)) {
                    y = sectionEnd + 1;
                    continue;
                }
                for (; y <= sectionEnd; y++) {
                    cursor.set(x, y, z);
                    if (!level.isInWorldBounds(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;

                    if (isGlass(state)) {
                        long hash = seed ^ cursor.asLong() ^ 0x474C4153535F5634L;
                        if (unit(hash) <= glassChance) {
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                        }
                        continue;
                    }

                    if (scorchIntensity > 0.0) {
                        long hash = seed ^ cursor.asLong() ^ 0x4153485F54524545L;
                        if (state.is(BlockTags.LEAVES)) {
                            if (nuclear && preparation != null) continue;
                            /* A nuclear fireball fully strips the trees nearest to its crater. */
                            double stripChance = nuclear && radialDistance <= craterRadius * 2.15
                                ? 1.0
                                : Mth.clamp(0.18 + scorchIntensity * 0.92, 0.0, 1.0);
                            if (unit(hash) < stripChance) {
                                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                            }
                            continue;
                        }
                        if (state.is(BlockTags.LOGS)) {
                            if (nuclear && preparation != null) continue;
                            /* Preserve the existing pale-log visual, but make its inner blast path reliable. */
                            double ashChance = nuclear && radialDistance <= craterRadius * 2.55
                                ? 1.0
                                : Mth.clamp((scorchIntensity - 0.16) * 1.18, 0.0, 0.92);
                            if (unit(hash ^ 0x50414C455F4F414BL) < ashChance) {
                                level.setBlock(cursor, Blocks.PALE_OAK_LOG.defaultBlockState(),
                                    UPDATE_FLAGS);
                            }
                            continue;
                        }
                    }

                    if (nuclear && normalizedDistance < 0.82 && isFragileSurface(state)) {
                        double chance = (1.0 - normalizedDistance / 0.82) * 0.82;
                        if (unit(seed ^ cursor.asLong() ^ 0x5355524641434556L) < chance) {
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                        }
                    }
                }
            }
        }

        private void transformNuclearColumn(final ServerLevel level, final int x,
            final int z, final double radialDistance) {
            double craterNormalized = radialDistance / Math.max(1.0, craterRadius);
            double aftermathNormalized = radialDistance / Math.max(1.0, aftermathRadius);
            int surfaceY = terrainSurfaceY(level, x, z);
            if (surfaceY < level.dimensionType().minY()) return;
			paintSurfaceBiome(level, x, surfaceY, z);
            long columnHash = seed ^ ((long) x << 32) ^ (z & 0xFFFFFFFFL)
                ^ 0x4E55434C45415235L;
            boolean mudPatch = aftermathNormalized > 0.22 && aftermathNormalized < 0.92
                && clusteredPatch(x, z, 11, 0.105, 1.7, 3.2,
                    0x4D55445F50415443L);
            boolean sulfurPatch = aftermathNormalized < 0.88
                && clusteredPatch(x, z, 9, 0.36, 2.5, 5.2,
                    0x53554C4655525041L)
                && touchesWater(level, x, surfaceY, z);

            int replacementDepth = aftermathNormalized < 0.45 ? 3
                : aftermathNormalized < 0.78 ? 2 : 1;
            for (int depth = 0; depth <= replacementDepth; depth++) {
                cursor.set(x, surfaceY - depth, z);
                if (!level.isInWorldBounds(cursor)) continue;
                BlockState state = level.getBlockState(cursor);
                if (state.isAir()) continue;
                long hash = columnHash ^ cursor.asLong() ^ depth * 0x9E3779B97F4A7C15L;

                if (depth == 0 && sulfurPatch && isNaturalSurface(state)) {
                    BlockState sulfur = unit(hash ^ 0x504F54454E545F53L) < 0.085
                        ? Blocks.POTENT_SULFUR.defaultBlockState()
                        : Blocks.SULFUR.defaultBlockState();
                    level.setBlock(cursor, sulfur, UPDATE_FLAGS);
                    continue;
                }
                if (depth == 0 && mudPatch && isSoil(state)) {
                    level.setBlock(cursor, Blocks.MUD.defaultBlockState(), UPDATE_FLAGS);
                    continue;
                }

                if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                    continue;
                }

                if (state.is(Blocks.SAND)) {
                    BlockState replacement = craterNormalized <= 1.65
                        ? fusedSand(hash, false, craterNormalized)
                        : outerFusedSand(hash, false);
                    level.setBlock(cursor, replacement, UPDATE_FLAGS);
                    continue;
                }
                if (state.is(Blocks.RED_SAND)) {
                    BlockState replacement = craterNormalized <= 1.65
                        ? fusedSand(hash, true, craterNormalized)
                        : outerFusedSand(hash, true);
                    level.setBlock(cursor, replacement, UPDATE_FLAGS);
                    continue;
                }

                if (isSoil(state) && craterNormalized <= 1.70) {
                    level.setBlock(cursor, scorchedSoil(hash, craterNormalized), UPDATE_FLAGS);
                    continue;
                }

                /*
                 * Beyond the unchanged crater rim, replace only the natural
                 * surface layers.  The deterministic chance feathers the edge
                 * rather than producing a perfectly circular scar.
                 */
                double edgeFalloff = Math.pow(Math.max(0.0, 1.0 - aftermathNormalized), 0.65);
                double outerReplacementChance = aftermathNormalized <= 0.78
                    ? 1.0 : 0.18 + edgeFalloff * 0.82;
                if (isSoil(state) && unit(hash ^ 0x4F555445525F4153L)
                    < outerReplacementChance) {
                    level.setBlock(cursor, outerScorchedSoil(hash), UPDATE_FLAGS);
                    continue;
                }

                if (isCommonRock(state) && (depth == 0 || exposedToAir(level, cursor))) {
                    double rockChance = craterNormalized <= 1.38 ? 1.0
                        : Mth.clamp((0.58 - aftermathNormalized) / 0.34, 0.0, 0.82);
                    if (unit(hash ^ 0x524F434B5F534341L) < rockChance) {
                        level.setBlock(cursor, darkCraterRock(hash, craterNormalized), UPDATE_FLAGS);
                    }
                }
            }

            /* Prepared columns form connected one-block fissures through the
               crater. Rounded fire pockets remain flame-only, never stray magma. */
            if (customFire && craterFireCrack(x, z, craterNormalized)) {
                if (pendingCraterFire.size() < MAX_PENDING_CRATER_FIRE) {
                    float crackIntensity = (float) Mth.clamp(
                        0.62 + (1.0 - Math.min(1.0, craterNormalized)) * 0.36,
                        0.10, 1.0);
                    pendingCraterFire.addLast(new PendingCraterFire(x, z, crackIntensity,
                        columnHash ^ 0x435241434B5F4649L, 0));
                }
            } else if ((customFire && firePocket(x, z, aftermathNormalized))
                || (!customFire && legacyFirePocket(x, z, aftermathNormalized))) {
                cursor.set(x, surfaceY + 1, z);
                if (level.isInWorldBounds(cursor) && level.getBlockState(cursor).isAir()) {
				double pocketHeat = Mth.clamp((0.96 - aftermathNormalized) / 0.76,
					0.0, 1.0);
				float pocketIntensity = customFire
					? (float) Mth.clamp(0.30 + pocketHeat * 0.58
						+ visualScale * 0.025, 0.10, 1.0)
					: Mth.clamp(0.72F + visualScale * 0.07F, 0.10F, 1.0F);
					BlockPos groundHost = cursor.below();
					long fireSeed = columnHash ^ 0x464952455F504F53L;
					if (!placeGroundFireAbove(level, groundHost, pocketIntensity, fireSeed)) {
						queuePendingSurfaceFire(groundHost, false, pocketIntensity, fireSeed);
					}
                }
            } else if (aftermathNormalized > 0.30 && aftermathNormalized < 0.96) {
                placeAshDecoration(level, x, surfaceY, z, columnHash, aftermathNormalized);
            }
        }

        private void drainPendingCraterFire(final ServerLevel level, final long deadline) {
            if (pendingCraterFire.isEmpty()
                || !WarheadExplosionWorkManager.isCraterExcavationComplete(level, warheadId)) {
                return;
            }
            int attempts = Math.min(512, pendingCraterFire.size());
            for (int index = 0; index < attempts; index++) {
				if ((index & 15) == 0 && System.nanoTime() >= deadline) break;
                PendingCraterFire pending = pendingCraterFire.removeFirst();
                if (!level.getChunkSource().hasChunk(pending.x() >> 4, pending.z() >> 4)) {
                    if (pending.attempts() < MAX_CRATER_FIRE_RETRY_TICKS)
						pendingCraterFire.addLast(
                        new PendingCraterFire(pending.x(), pending.z(), pending.intensity(),
                            pending.seed(), pending.attempts() + 1));
                    continue;
                }
                int surfaceY = terrainSurfaceY(level, pending.x(), pending.z());
                cursor.set(pending.x(), surfaceY, pending.z());
                if (!level.isInWorldBounds(cursor) || level.getBlockState(cursor).isAir()) {
                    if (pending.attempts() < MAX_CRATER_FIRE_RETRY_TICKS)
						pendingCraterFire.addLast(
                        new PendingCraterFire(pending.x(), pending.z(), pending.intensity(),
                            pending.seed(), pending.attempts() + 1));
                    continue;
                }
                /* Final one-block crater veins are written only after excavation,
                   so neither the magma nor its attached custom flame is overwritten. */
                level.setBlock(cursor, Blocks.MAGMA_BLOCK.defaultBlockState(), UPDATE_FLAGS);
                if (remainingCraterCustomFirePlacements > 0
                    && !placeCraterFireAbove(level, cursor.immutable(), pending.intensity(),
                        pending.seed())
					&& pending.attempts() < MAX_CRATER_FIRE_RETRY_TICKS) {
                    pendingCraterFire.addLast(new PendingCraterFire(pending.x(), pending.z(),
                        pending.intensity(), pending.seed(), pending.attempts() + 1));
                }
            }
        }

        private void queuePendingSurfaceFire(final BlockPos host, final boolean tree,
            final float intensity, final long fireSeed) {
            if (!customFire || pendingSurfaceFire.size() >= MAX_PENDING_SURFACE_FIRE) return;
            if (tree && remainingTreeCustomFirePlacements <= 0) return;
            if (!tree && remainingGroundCustomFirePlacements <= 0) return;
            pendingSurfaceFire.addLast(new PendingSurfaceFire(host.asLong(), tree,
                intensity, fireSeed, 0));
        }

        private void drainPendingSurfaceFire(final ServerLevel level, final long deadline) {
            int attempts = Math.min(512, pendingSurfaceFire.size());
            for (int index = 0; index < attempts; index++) {
				if ((index & 15) == 0 && System.nanoTime() >= deadline) break;
                PendingSurfaceFire pending = pendingSurfaceFire.removeFirst();
                if ((pending.tree() && remainingTreeCustomFirePlacements <= 0)
                    || (!pending.tree() && remainingGroundCustomFirePlacements <= 0)) continue;
                BlockPos original = BlockPos.of(pending.packedHost());
                if (!level.getChunkSource().hasChunk(original.getX() >> 4,
                    original.getZ() >> 4)) {
                    requeuePendingSurfaceFire(pending);
                    continue;
                }
                BlockPos host = original;
                if (!pending.tree() && level.getBlockState(host).isAir()) {
                    int surfaceY = terrainSurfaceY(level, host.getX(), host.getZ());
                    host = new BlockPos(host.getX(), surfaceY, host.getZ());
                }
                boolean placed = pending.tree()
                    ? placeBlastFacingFire(level, host, pending.intensity(), pending.seed())
                    : placeGroundFireAbove(level, host, pending.intensity(), pending.seed());
                if (!placed) requeuePendingSurfaceFire(pending);
            }
        }

        private void requeuePendingSurfaceFire(final PendingSurfaceFire pending) {
            if (pending.attempts() >= 80) return;
            pendingSurfaceFire.addLast(new PendingSurfaceFire(pending.packedHost(),
                pending.tree(), pending.intensity(), pending.seed(), pending.attempts() + 1));
        }

		@SuppressWarnings("unchecked")
		private void paintSurfaceBiome(final ServerLevel level, final int x,
			final int surfaceY, final int z) {
			if (basaltDeltas == null) {
				basaltDeltas = level.registryAccess().lookupOrThrow(Registries.BIOME)
					.getOrThrow(Biomes.BASALT_DELTAS);
			}
			LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
			int localQuartX = QuartPos.fromBlock(x) & 3;
			int localQuartZ = QuartPos.fromBlock(z) & 3;
			int minimumQuartY = QuartPos.fromBlock(surfaceY);
			int maximumQuartY = QuartPos.fromBlock(surfaceY + 10);
			boolean changed = false;
			for (int quartY = minimumQuartY; quartY <= maximumQuartY; quartY++) {
				int blockY = QuartPos.toBlock(quartY);
				if (blockY < level.dimensionType().minY()
					|| blockY >= level.dimensionType().minY() + level.dimensionType().height()) continue;
				LevelChunkSection section = chunk.getSection(level.getSectionIndex(blockY));
				if (section.getNoiseBiome(localQuartX, quartY & 3, localQuartZ)
					.is(Biomes.BASALT_DELTAS)) continue;
				PalettedContainer<Holder<Biome>> biomes =
					(PalettedContainer<Holder<Biome>>) section.getBiomes();
				biomes.set(localQuartX, quartY & 3, localQuartZ, basaltDeltas);
				changed = true;
			}
			if (changed) {
				chunk.markUnsaved();
				dirtyBiomeChunks.add(((long) chunk.getPos().x() << 32)
					^ (chunk.getPos().z() & 0xFFFFFFFFL));
			}
		}

        private BlockState fusedSand(final long hash, final boolean redSand,
            final double craterNormalized) {
            double selector = unit(hash ^ 0x46555345445F534EL);
            double innerHeat = Mth.clamp(1.25 - craterNormalized, 0.0, 1.0);
            if (!redSand) {
                if (selector < 0.10 + innerHeat * 0.10) return Blocks.TINTED_GLASS.defaultBlockState();
                if (selector < 0.22 + innerHeat * 0.14) return Blocks.STAINED_GLASS.black().defaultBlockState();
                if (selector < 0.34 + innerHeat * 0.12) return Blocks.STAINED_GLASS.gray().defaultBlockState();
                if (selector < 0.46 + innerHeat * 0.10) return Blocks.STAINED_GLASS.lightGray().defaultBlockState();
                if (selector < 0.68) return Blocks.DYED_TERRACOTTA.white().defaultBlockState();
                if (selector < 0.84) return Blocks.CALCITE.defaultBlockState();
                return Blocks.SANDSTONE.defaultBlockState();
            }
            if (selector < 0.20 + innerHeat * 0.16) return Blocks.STAINED_GLASS.black().defaultBlockState();
            if (selector < 0.34 + innerHeat * 0.12) return Blocks.STAINED_GLASS.gray().defaultBlockState();
            if (selector < 0.72) return Blocks.TERRACOTTA.defaultBlockState();
            if (selector < 0.88) return Blocks.RED_SANDSTONE.defaultBlockState();
            return Blocks.GRAVEL.defaultBlockState();
        }

        private BlockState outerFusedSand(final long hash, final boolean redSand) {
            double selector = unit(hash ^ 0x4F5554455253414EL);
            if (redSand) {
                if (selector < 0.62) return Blocks.TERRACOTTA.defaultBlockState();
                if (selector < 0.84) return Blocks.RED_SANDSTONE.defaultBlockState();
                return Blocks.GRAVEL.defaultBlockState();
            }
            if (selector < 0.48) return Blocks.DYED_TERRACOTTA.white().defaultBlockState();
            if (selector < 0.72) return Blocks.SANDSTONE.defaultBlockState();
            if (selector < 0.90) return Blocks.GRAVEL.defaultBlockState();
            return Blocks.STAINED_GLASS.lightGray().defaultBlockState();
        }

        private BlockState scorchedSoil(final long hash, final double craterNormalized) {
            double selector = unit(hash ^ 0x53434F524348534FL);
            if (craterNormalized < 0.82) {
                if (selector < 0.06) return deadCoralBlockState((int) (hash >>> 18));
                if (selector < 0.28) return Blocks.TUFF.defaultBlockState();
                if (selector < 0.50) return Blocks.COARSE_DIRT.defaultBlockState();
                if (selector < 0.68) return Blocks.PALE_MOSS_BLOCK.defaultBlockState();
                if (selector < 0.84) return Blocks.ROOTED_DIRT.defaultBlockState();
                return Blocks.PODZOL.defaultBlockState();
            }
            if (selector < 0.055) return deadCoralBlockState((int) (hash >>> 18));
            if (selector < 0.30) return Blocks.PODZOL.defaultBlockState();
            if (selector < 0.56) return Blocks.COARSE_DIRT.defaultBlockState();
            if (selector < 0.72) return Blocks.PALE_MOSS_BLOCK.defaultBlockState();
            if (selector < 0.88) return Blocks.TUFF.defaultBlockState();
            return Blocks.ROOTED_DIRT.defaultBlockState();
        }

        private BlockState outerScorchedSoil(final long hash) {
            double selector = unit(hash ^ 0x4F55544552534F49L);
            if (selector < 0.035) return deadCoralBlockState((int) (hash >>> 18));
            if (selector < 0.28) return Blocks.COARSE_DIRT.defaultBlockState();
            if (selector < 0.47) return Blocks.PODZOL.defaultBlockState();
            if (selector < 0.61) return Blocks.MYCELIUM.defaultBlockState();
            if (selector < 0.75) return Blocks.PALE_MOSS_BLOCK.defaultBlockState();
            if (selector < 0.88) return Blocks.TUFF.defaultBlockState();
            return Blocks.ROOTED_DIRT.defaultBlockState();
        }

        private BlockState darkCraterRock(final long hash, final double craterNormalized) {
            double selector = unit(hash ^ 0x4441524B5F524F43L);
            if (craterNormalized < 0.58) {
                if (selector < 0.28) return Blocks.BASALT.defaultBlockState();
                if (selector < 0.48) return Blocks.BLACKSTONE.defaultBlockState();
                if (selector < 0.72) return Blocks.DEEPSLATE.defaultBlockState();
                return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            }
            if (selector < 0.26) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            if (selector < 0.50) return Blocks.DEEPSLATE.defaultBlockState();
            if (selector < 0.72) return Blocks.TUFF.defaultBlockState();
            if (selector < 0.88) return Blocks.BASALT.defaultBlockState();
            return Blocks.BLACKSTONE.defaultBlockState();
        }

        private boolean firePocket(final int x, final int z,
            final double aftermathNormalized) {
            if (aftermathNormalized >= 0.92) return false;
            final int cellSize = 12;
            int cellX = Math.floorDiv(x, cellSize);
            int cellZ = Math.floorDiv(z, cellSize);
            long cellHash = seed ^ ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL)
                ^ 0x46495245504F434BL;
            double heat = Mth.clamp((0.96 - aftermathNormalized) / 0.76, 0.0, 1.0);
            double selectionChance = 0.045 + 0.145 * heat;
            if (unit(cellHash) >= selectionChance) return false;
            double centerX = cellX * cellSize + 1.5 + unit(cellHash ^ 0x5843454E544552L) * 9.0;
            double centerZ = cellZ * cellSize + 1.5 + unit(cellHash ^ 0x5A43454E544552L) * 9.0;
            double dx = x + 0.5 - centerX;
            double dz = z + 0.5 - centerZ;
            double pocketRadius = 2.0 + unit(cellHash ^ 0x5241444955535F50L) * 1.8;
            return dx * dx + dz * dz <= pocketRadius * pocketRadius;
        }

        private boolean legacyFirePocket(final int x, final int z,
            final double aftermathNormalized) {
            if (aftermathNormalized >= 0.86) return false;
            final int cellSize = 14;
            int cellX = Math.floorDiv(x, cellSize);
            int cellZ = Math.floorDiv(z, cellSize);
            long cellHash = seed ^ ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL)
                ^ 0x46495245504F434BL;
            double selectionChance = 0.11 * Mth.clamp(
                1.05 - aftermathNormalized, 0.22, 1.0);
            if (unit(cellHash) >= selectionChance) return false;
            double centerX = cellX * cellSize + 2.0
                + unit(cellHash ^ 0x5843454E544552L) * 10.0;
            double centerZ = cellZ * cellSize + 2.0
                + unit(cellHash ^ 0x5A43454E544552L) * 10.0;
            double dx = x + 0.5 - centerX;
            double dz = z + 0.5 - centerZ;
            double pocketRadius = 1.8
                + unit(cellHash ^ 0x5241444955535F50L) * 1.5;
            return dx * dx + dz * dz <= pocketRadius * pocketRadius;
        }

        private boolean clusteredPatch(final int x, final int z, final int cellSize,
            final double selectionChance, final double minimumRadius,
            final double maximumRadius, final long salt) {
            int cellX = Math.floorDiv(x, cellSize);
            int cellZ = Math.floorDiv(z, cellSize);
            long hash = seed ^ ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL) ^ salt;
            if (unit(hash) >= selectionChance) return false;
            double centerX = cellX * cellSize + 1.0
                + unit(hash ^ 0x58434C5553544552L) * (cellSize - 2.0);
            double centerZ = cellZ * cellSize + 1.0
                + unit(hash ^ 0x5A434C5553544552L) * (cellSize - 2.0);
            double dx = x + 0.5 - centerX;
            double dz = z + 0.5 - centerZ;
            double radius = minimumRadius + unit(hash ^ 0x52434C5553544552L)
                * (maximumRadius - minimumRadius);
            return dx * dx + dz * dz <= radius * radius;
        }

        private boolean touchesWater(final ServerLevel level, final int x,
            final int surfaceY, final int z) {
            cursor.set(x, surfaceY + 1, z);
            if (level.isInWorldBounds(cursor)
                && level.getFluidState(cursor).is(FluidTags.WATER)) return true;
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                for (int distance = 1; distance <= 2; distance++) {
                    cursor.set(x + direction.getStepX() * distance, surfaceY,
                        z + direction.getStepZ() * distance);
                    if (level.isInWorldBounds(cursor)
                        && level.getFluidState(cursor).is(FluidTags.WATER)) return true;
                    cursor.move(Direction.UP);
                    if (level.isInWorldBounds(cursor)
                        && level.getFluidState(cursor).is(FluidTags.WATER)) return true;
                }
            }
            return false;
        }

        private boolean craterFireCrack(final int x, final int z,
            final double craterNormalized) {
			return craterNormalized <= 1.02 && NuclearCrackField.contains(seed,
				center.x, center.z, x + 0.5, z + 0.5, craterRadius * 1.02);
        }

        private void applyFragileAftermath(final ServerLevel level, final BlockPos position,
            final BlockState original, final double normalized, final long hash) {
            if (isSnowLike(original)) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                return;
            }
            if (original.is(Blocks.SUGAR_CANE)) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                return;
            }
            if (isAquaticPlant(original)) {
                if (original.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && original.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        == DoubleBlockHalf.UPPER) {
                    level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                    return;
                }
                BlockState support = level.getBlockState(position.below());
                if (support.is(Blocks.SULFUR)
                    && unit(hash ^ 0x5350494B455F4151L) < 0.38) {
                    BlockState spike = Blocks.SULFUR_SPIKE.defaultBlockState()
                        .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)
                        .setValue(BlockStateProperties.SPELEOTHEM_THICKNESS,
                            SpeleothemThickness.TIP)
                        .setValue(BlockStateProperties.WATERLOGGED,
                            level.getFluidState(position).is(Fluids.WATER));
                    if (spike.canSurvive(level, position)) {
                        level.setBlock(position, spike, UPDATE_FLAGS);
                        return;
                    }
                }
                level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                return;
            }
            if (original.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && original.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    == DoubleBlockHalf.UPPER) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                return;
            }
            double selector = unit(hash ^ 0x4452595F504C414EL);
            BlockState replacement;
            if (original.is(Blocks.BUSH)) {
                replacement = selector < 0.82 ? Blocks.TALL_DRY_GRASS.defaultBlockState()
                    : Blocks.DEAD_BUSH.defaultBlockState();
            } else if (selector < 0.006) {
                replacement = Blocks.WITHER_ROSE.defaultBlockState();
            } else if (selector < 0.011) {
                replacement = Blocks.CLOSED_EYEBLOSSOM.defaultBlockState();
            } else if (selector < 0.016) {
                replacement = Blocks.CLOSED_EYEBLOSSOM.defaultBlockState();
            } else if (selector < 0.18) {
                replacement = Blocks.TALL_DRY_GRASS.defaultBlockState();
            } else if (selector < 0.38) {
                replacement = Blocks.SHORT_DRY_GRASS.defaultBlockState();
            } else if (selector < 0.52) {
                replacement = Blocks.DEAD_BUSH.defaultBlockState();
            } else if (selector < 0.58) {
                replacement = deadCoralFanState((int) (hash >>> 20));
            } else {
                replacement = Blocks.AIR.defaultBlockState();
            }
            if (!replacement.isAir() && replacement.canSurvive(level, position)) {
                level.setBlock(position, replacement, UPDATE_FLAGS);
            } else {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            }
        }

        private static boolean isAquaticPlant(final BlockState state) {
            return state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
        }

        private static BlockState deadCoralBlockState(final int selector) {
            return switch (Math.floorMod(selector, 5)) {
                case 0 -> Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState();
                case 1 -> Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState();
                case 2 -> Blocks.DEAD_FIRE_CORAL_BLOCK.defaultBlockState();
                case 3 -> Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState();
                default -> Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState();
            };
        }

        private static BlockState deadCoralFanState(final int selector) {
            BlockState state = switch (Math.floorMod(selector, 5)) {
                case 0 -> Blocks.DEAD_BRAIN_CORAL_FAN.defaultBlockState();
                case 1 -> Blocks.DEAD_BUBBLE_CORAL_FAN.defaultBlockState();
                case 2 -> Blocks.DEAD_FIRE_CORAL_FAN.defaultBlockState();
                case 3 -> Blocks.DEAD_HORN_CORAL_FAN.defaultBlockState();
                default -> Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState();
            };
            return state.hasProperty(BlockStateProperties.WATERLOGGED)
                ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
        }

        private static BlockState deadCoralWallState(final int selector) {
            BlockState state = switch (Math.floorMod(selector, 5)) {
                case 0 -> Blocks.DEAD_BRAIN_CORAL_WALL_FAN.defaultBlockState();
                case 1 -> Blocks.DEAD_BUBBLE_CORAL_WALL_FAN.defaultBlockState();
                case 2 -> Blocks.DEAD_FIRE_CORAL_WALL_FAN.defaultBlockState();
                case 3 -> Blocks.DEAD_HORN_CORAL_WALL_FAN.defaultBlockState();
                default -> Blocks.DEAD_TUBE_CORAL_WALL_FAN.defaultBlockState();
            };
            return state.hasProperty(BlockStateProperties.WATERLOGGED)
                ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
        }

        private void placeAshDecoration(final ServerLevel level, final int x,
            final int surfaceY, final int z, final long hash,
            final double aftermathNormalized) {
            cursor.set(x, surfaceY + 1, z);
            if (!level.isInWorldBounds(cursor) || !level.getBlockState(cursor).isAir()) return;
            double fade = Mth.clamp((1.0 - aftermathNormalized) / 0.70, 0.0, 1.0);
            double selector = unit(hash ^ 0x4153485F4445434FL);
            double density = 0.012 + fade * 0.046;
            if (selector >= density) return;
            double kind = unit(hash ^ 0x4153485F4B494E44L);
            BlockState decoration;
            if (kind < 0.20) decoration = Blocks.DEAD_BUSH.defaultBlockState();
            else if (kind < 0.43) decoration = Blocks.SHORT_DRY_GRASS.defaultBlockState();
            else if (kind < 0.61) decoration = Blocks.TALL_DRY_GRASS.defaultBlockState();
            else if (kind < 0.73) decoration = Blocks.PALE_MOSS_CARPET.defaultBlockState();
            else if (kind < 0.92) decoration = deadCoralFanState((int) (hash >>> 20));
            else if (kind < 0.947) decoration = Blocks.WITHER_ROSE.defaultBlockState();
            else if (kind < 0.974) decoration = Blocks.CLOSED_EYEBLOSSOM.defaultBlockState();
            else decoration = Blocks.CLOSED_EYEBLOSSOM.defaultBlockState();
            if (decoration.canSurvive(level, cursor)) {
                level.setBlock(cursor, decoration, UPDATE_FLAGS);
            }
        }

        private boolean exposedToAir(final ServerLevel level, final BlockPos position) {
            for (Direction direction : DIRECTIONS) {
                neighbour.setWithOffset(position, direction);
                if (level.isInWorldBounds(neighbour) && level.getBlockState(neighbour).isAir()) {
                    return true;
                }
            }
            return false;
        }

        private void scorchGround(final ServerLevel level, final int x, final int surfaceY,
            final int z, final double intensity) {
            if (intensity <= 0.0) return;
            cursor.set(x, surfaceY, z);
            if (!level.isInWorldBounds(cursor)) return;
            BlockState state = level.getBlockState(cursor);
            if (!isSoil(state)) return;
            long hash = seed ^ cursor.asLong() ^ 0x53434F5243484544L;
            double replaceChance = Mth.clamp(0.18 + intensity * 0.82, 0.0, 1.0);
            if (unit(hash) >= replaceChance) return;
            BlockState replacement = unit(hash ^ 0x504F445A4F4C5F31L) < 0.68
                ? Blocks.PODZOL.defaultBlockState()
                : Blocks.COARSE_DIRT.defaultBlockState();
            level.setBlock(cursor, replacement, UPDATE_FLAGS);
        }

        private static boolean isSoil(final BlockState state) {
            return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL);
        }

        private static boolean isNaturalSurface(final BlockState state) {
            return isSoil(state) || state.is(Blocks.MUD) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY);
        }

        private static boolean isCommonRock(final BlockState state) {
            return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE);
        }

        private static boolean isCobbleStructure(final BlockState state) {
            return state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE);
        }

        private static boolean isGlass(final BlockState state) {
            Block block = state.getBlock();
            return GLASS_BLOCK_CACHE.computeIfAbsent(block,
                candidate -> candidate.getDescriptionId().contains("glass"));
        }

        private static boolean isFragileSurface(final BlockState state) {
            return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.VINE) || isSnowLike(state)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BUSH) || state.is(Blocks.FIREFLY_BUSH)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SHORT_DRY_GRASS) || state.is(Blocks.TALL_DRY_GRASS)
                || state.is(Blocks.SUGAR_CANE) || isAquaticPlant(state)
                || state.is(Blocks.PALE_HANGING_MOSS)
                || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)
                || state.getBlock().getDescriptionId().contains("sapling");
        }

        private static boolean isSnowLike(final BlockState state) {
            return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
        }

        private record PendingCraterFire(int x, int z, float intensity, long seed,
            int attempts) { }
        private record PendingSurfaceFire(long packedHost, boolean tree, float intensity,
            long seed, int attempts) { }
    }

    /**
     * Finds terrain from the ocean-floor heightmap while skipping only a small
     * cover stack.  A bounded probe prevents malformed columns, caves and deep
     * fluid from becoming expensive underground discovery work.
     */
    private static int terrainSurfaceY(final ServerLevel level, final int x, final int z) {
        int minimumY = level.dimensionType().minY();
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(x, y, z);
        for (int descent = 0; descent <= SURFACE_SUPPORT_DESCENT && y >= minimumY;
            descent++, y--) {
            position.set(x, y, z);
            BlockState state = level.getBlockState(position);
            if (!state.isAir() && state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                && !state.is(BlockTags.PLANKS) && !Wave.isGlass(state)
                && !Wave.isCobbleStructure(state) && !Wave.isFragileSurface(state)
                && !state.is(Blocks.LEAF_LITTER)) {
                return y;
            }
        }
        return minimumY - 1;
    }

    /**
     * Incremental, loaded-chunk-only discovery of the surface and exposed
     * vegetation the nuclear pressure front will reach.  The scan stores no
     * block states: mutations re-check the live block state when the front
     * arrives, so another explosion or a player edit cannot be overwritten.
     */
    private static final class NuclearTerrainPreparation {
        private static final int TREE_SCAN_ABOVE_SURFACE = 64;
        private static final int TREE_SCAN_BELOW_SURFACE = 0;
        private final Vec3 center;
        private final long seed;
        private final double treeRadius;
        private final int radius;
        private final Map<Integer, LongArrayList> leavesByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> logsByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> structuralLogsByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> planksByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> glassByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> cobbleByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> snowByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> fragileByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> hangingMossByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> surfacesByShell = new HashMap<>();
        private long expiresAt;
        private final LongOpenHashSet deferredColumns = new LongOpenHashSet();
        private final ArrayDeque<Long> deferredQueue = new ArrayDeque<>();
        private int scanRing;
        private int scanPerimeterOffset;
        private boolean mainScanComplete;

        private NuclearTerrainPreparation(final Vec3 center, final long seed,
            final int aftermathRadius, final int glassRadius, final long expiresAt) {
            this.center = center;
            this.seed = seed;
            this.treeRadius = aftermathRadius;
            this.radius = glassRadius;
            this.expiresAt = expiresAt;
            this.scanRing = 0;
        }

        private boolean compatible(final Vec3 otherCenter, final long otherSeed,
            final int otherRadius) {
            return seed == otherSeed && (int) treeRadius == otherRadius
				&& Math.abs(center.x - otherCenter.x) <= 1.0E-6
				&& Math.abs(center.z - otherCenter.z) <= 1.0E-6;
        }

        private void extend(final long otherExpiresAt) {
            expiresAt = Math.max(expiresAt, otherExpiresAt);
        }

        private void stopDiscovery() {
            mainScanComplete = true;
            deferredColumns.clear();
            deferredQueue.clear();
        }

        private boolean complete() {
            return mainScanComplete && deferredColumns.isEmpty();
        }

        private double maximumPreparedRadius() {
            return radius;
        }

        private boolean hasPendingWork() {
            return !complete() || !leavesByShell.isEmpty() || !logsByShell.isEmpty()
                || !structuralLogsByShell.isEmpty() || !planksByShell.isEmpty()
                || !glassByShell.isEmpty()
                || !cobbleByShell.isEmpty() || !snowByShell.isEmpty()
                || !fragileByShell.isEmpty()
                || !hangingMossByShell.isEmpty()
                || !surfacesByShell.isEmpty();
        }

        private int advance(final ServerLevel level, final int budget, final long deadline) {
            if (complete() || budget <= 0) return 0;
            int centerX = Mth.floor(center.x);
            int centerZ = Mth.floor(center.z);
            /* Wait for the actual impact chunk rather than permanently skipping it. */
            if (!level.getChunkSource().hasChunk(centerX >> 4, centerZ >> 4)) return 0;

            int used = 0;
            double radiusSqr = (double) radius * radius;
            double treeRadiusSqr = treeRadius * treeRadius;
            int retryBudget = Math.max(1, budget / 4);
            while (!deferredQueue.isEmpty() && used < retryBudget
                && System.nanoTime() < deadline) {
                long packed = deferredQueue.removeFirst();
                int x = (int) (packed >> 32);
                int z = (int) packed;
                used++;
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    deferredQueue.addLast(packed);
                    continue;
                }
                deferredColumns.remove(packed);
                discoverColumn(level, centerX, centerZ, x, z, radiusSqr, treeRadiusSqr);
            }
            while (!mainScanComplete && used < budget && System.nanoTime() < deadline) {
                long packedOffset = nextRadialOffset();
                if (packedOffset == Long.MIN_VALUE) {
                    mainScanComplete = true;
                    break;
                }
                int dx = (int) (packedOffset >> 32);
                int dz = (int) packedOffset;
                if ((double) dx * dx + (double) dz * dz > radiusSqr) continue;
                used++;
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    long packed = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                    if (deferredColumns.add(packed)) deferredQueue.addLast(packed);
                    continue;
                }
                discoverColumn(level, centerX, centerZ, x, z, radiusSqr, treeRadiusSqr);
            }
            return used;
        }
        private long nextRadialOffset() {
            if (scanRing > radius) return Long.MIN_VALUE;
            if (scanRing == 0) {
                scanRing = 1;
                return 0L;
            }
            int ring = scanRing;
            int sideLength = ring * 2;
            int side = scanPerimeterOffset / sideLength;
            int offset = scanPerimeterOffset % sideLength;
            scanPerimeterOffset++;
            if (scanPerimeterOffset >= sideLength * 4) {
                scanPerimeterOffset = 0;
                scanRing++;
            }
            int dx;
            int dz;
            switch (side) {
                case 0 -> { dx = -ring + offset; dz = -ring; }
                case 1 -> { dx = ring; dz = -ring + offset; }
                case 2 -> { dx = ring - offset; dz = ring; }
                default -> { dx = -ring; dz = ring - offset; }
            }
            return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
        }

        private void discoverColumn(final ServerLevel level, final int centerX,
            final int centerZ, final int x, final int z, final double radiusSqr,
            final double treeRadiusSqr) {
            int dx = x - centerX;
            int dz = z - centerZ;
            double distanceSqr = (double) dx * dx + (double) dz * dz;
            if (distanceSqr > radiusSqr) return;
            int surfaceY = terrainSurfaceY(level, x, z);
            if (surfaceY < level.dimensionType().minY()) return;
            int shell = shellFor(Math.sqrt(distanceSqr));
            if (distanceSqr <= treeRadiusSqr) {
                surfacesByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                    .add(BlockPos.asLong(x, 0, z));
                discoverVerticalTargets(level, x, z, surfaceY, shell);
            } else {
                discoverGlassTargets(level, x, z, surfaceY, shell);
            }
            long columnHash = seed ^ ((long) x << 32) ^ (z & 0xFFFFFFFFL)
                ^ 0x48414E474D4F5353L;
            if (distanceSqr <= treeRadiusSqr && unit(columnHash) < 0.035) {
                discoverHangingMossTarget(level, x, z, surfaceY, shell);
            }
        }

        private void discoverGlassTargets(final ServerLevel level, final int x,
            final int z, final int surfaceY, final int shell) {
            int minimumY = Math.max(level.dimensionType().minY(), surfaceY);
            int maximumY = Math.min(level.dimensionType().minY()
                + level.dimensionType().height() - 1, surfaceY + TREE_SCAN_ABOVE_SURFACE);
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            int y = minimumY;
            while (y <= maximumY) {
                int sectionEnd = Math.min(maximumY, (y & ~15) + 15);
                LevelChunkSection section = chunk.getSection(level.getSectionIndex(y));
                if (!section.maybeHas(Wave::isGlass)) {
                    y = sectionEnd + 1;
                    continue;
                }
                for (; y <= sectionEnd; y++) {
                    position.set(x, y, z);
                    if (Wave.isGlass(level.getBlockState(position))) {
                        glassByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(position.asLong());
                    }
                }
            }
        }

        private void discoverHangingMossTarget(final ServerLevel level, final int x,
            final int z, final int surfaceY, final int shell) {
            int minimumY = Math.max(level.dimensionType().minY(), surfaceY - 8);
            int maximumY = Math.min(level.dimensionType().minY()
                + level.dimensionType().height() - 1, surfaceY + 10);
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            BlockState moss = Blocks.PALE_HANGING_MOSS.defaultBlockState()
                .setValue(BlockStateProperties.TIP, true);
            for (int y = maximumY; y >= minimumY; y--) {
                position.set(x, y, z);
                if (level.getBlockState(position).isAir() && moss.canSurvive(level, position)) {
                    hangingMossByShell.computeIfAbsent(shell,
                        ignored -> new LongArrayList()).add(position.asLong());
                    return;
                }
            }
        }

        private void discoverVerticalTargets(final ServerLevel level, final int x, final int z,
            final int surfaceY, final int shell) {
            int minimumY = Math.max(level.dimensionType().minY(), surfaceY - TREE_SCAN_BELOW_SURFACE);
            int maximumY = Math.min(level.dimensionType().minY() + level.dimensionType().height() - 1,
                surfaceY + TREE_SCAN_ABOVE_SURFACE);
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            boolean naturalTreeColumn = columnContainsLeaves(
                level, chunk, x, z, minimumY, maximumY, cursor);
            int y = minimumY;
            while (y <= maximumY) {
                int sectionEnd = Math.min(maximumY, (y & ~15) + 15);
                LevelChunkSection section = chunk.getSection(level.getSectionIndex(y));
                if (!section.maybeHas(state -> state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                    || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)
                    || Wave.isGlass(state) || Wave.isCobbleStructure(state)
                    || Wave.isFragileSurface(state) || Wave.isSnowLike(state))) {
                    y = sectionEnd + 1;
                    continue;
                }
                for (; y <= sectionEnd; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (Wave.isSnowLike(state)) {
                        snowByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(cursor.asLong());
                    } else if (state.is(BlockTags.LEAVES)) {
                        leavesByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(cursor.asLong());
                    } else if (state.is(BlockTags.LOGS)) {
                        Map<Integer, LongArrayList> target = naturalTreeColumn
                            ? logsByShell : structuralLogsByShell;
                        target.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(cursor.asLong());
                    } else if (state.is(BlockTags.PLANKS)) {
                        planksByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(cursor.asLong());
                    } else if (Wave.isGlass(state)) {
                        glassByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(cursor.asLong());
                    } else if (Wave.isCobbleStructure(state)) {
                        cobbleByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(cursor.asLong());
                    } else if (Wave.isFragileSurface(state) || state.is(BlockTags.FLOWERS)
                        || state.is(BlockTags.CROPS)
                        || state.getBlock().getDescriptionId().contains("sapling")) {
                        fragileByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(cursor.asLong());
                    }
                }
            }
        }

        private static boolean columnContainsLeaves(final ServerLevel level,
            final LevelChunk chunk, final int x, final int z, final int minimumY,
            final int maximumY, final BlockPos.MutableBlockPos cursor) {
            int y = minimumY;
            while (y <= maximumY) {
                int sectionEnd = Math.min(maximumY, (y & ~15) + 15);
                LevelChunkSection section = chunk.getSection(level.getSectionIndex(y));
                if (!section.maybeHas(state -> state.is(BlockTags.LEAVES))) {
                    y = sectionEnd + 1;
                    continue;
                }
                for (; y <= sectionEnd; y++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(BlockTags.LEAVES)) return true;
                }
            }
            return false;
        }

        private static int shellFor(final double distance) {
            return Math.max(0, Mth.floor(distance / SPEED_BLOCKS_PER_TICK));
        }

        private LongArrayList takeLeaves(final int shell, final int limit) {
            return takeThrough(leavesByShell, shell, limit);
        }

        private LongArrayList takeLogs(final int shell, final int limit) {
            return takeThrough(logsByShell, shell, limit);
        }

        private LongArrayList takeStructuralLogs(final int shell, final int limit) {
            return takeThrough(structuralLogsByShell, shell, limit);
        }

        private LongArrayList takePlanks(final int shell, final int limit) {
            return takeThrough(planksByShell, shell, limit);
        }

        private LongArrayList takeGlass(final int shell, final int limit) {
            return takeThrough(glassByShell, shell, limit);
        }

        private LongArrayList takeCobble(final int shell, final int limit) {
            return takeThrough(cobbleByShell, shell, limit);
        }

        private LongArrayList takeSnow(final int shell, final int limit) {
            return takeThrough(snowByShell, shell, limit);
        }

        private LongArrayList takeFragile(final int shell, final int limit) {
            return takeThrough(fragileByShell, shell, limit);
        }

        private LongArrayList takeHangingMoss(final int shell, final int limit) {
            return takeThrough(hangingMossByShell, shell, limit);
        }

        private LongArrayList takeSurfaceColumns(final int shell, final int limit) {
            return takeThrough(surfacesByShell, shell, limit);
        }

        private boolean hasSurfaceThrough(final int maximumShell) {
            for (Map.Entry<Integer, LongArrayList> entry : surfacesByShell.entrySet()) {
                if (entry.getKey() <= maximumShell && !entry.getValue().isEmpty()) return true;
            }
            return false;
        }

        private long pendingMutationCount() {
            return queued(surfacesByShell) + queued(snowByShell) + queued(glassByShell)
                + queued(fragileByShell) + queued(leavesByShell) + queued(logsByShell)
                + queued(structuralLogsByShell) + queued(planksByShell)
                + queued(hangingMossByShell) + queued(cobbleByShell);
        }

        private static long queued(final Map<Integer, LongArrayList> source) {
            long count = 0L;
            for (LongArrayList values : source.values()) count += values.size();
            return count;
        }

        private static LongArrayList takeThrough(final Map<Integer, LongArrayList> source,
            final int maximumShell, final int limit) {
            LongArrayList result = new LongArrayList(Math.max(0, limit));
            if (limit <= 0) return result;
            Iterator<Map.Entry<Integer, LongArrayList>> iterator = source.entrySet().iterator();
            while (iterator.hasNext() && result.size() < limit) {
                Map.Entry<Integer, LongArrayList> entry = iterator.next();
                if (entry.getKey() > maximumShell) continue;
                LongArrayList values = entry.getValue();
                int count = Math.min(limit - result.size(), values.size());
                for (int index = 0; index < count; index++) {
                    result.add(values.getLong(index));
                }
                values.removeElements(0, count);
                if (values.isEmpty()) iterator.remove();
            }
            return result;
        }
    }

    private static double unit(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}
