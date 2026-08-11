package com.andye.warmod.warhead;

import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded physical glass, vegetation and surface response carried by the same
 * 343 m/s pressure front used by the client visuals. Work is deterministic and
 * never forces unrelated chunks to load.
 */
public final class WarheadGlassShockwaveManager {
    private static final double SPEED_BLOCKS_PER_TICK =
        WarheadVisualMath.AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int MAX_COLUMNS_PER_WAVE_TICK = 2_048;
    private static final double NUCLEAR_AFTERMATH_RADIUS_SCALE = 3.0;
    private static final long LEVEL_WORK_BUDGET_NANOS = 8_000_000L;
    private static final int NUCLEAR_PREPARATION_COLUMNS_PER_TICK = 1_024;
    private static final int PREPARED_MUTATIONS_PER_WAVE_TICK = 6_144;
    private static final int PREPARED_SURFACE_MUTATIONS_PER_WAVE_TICK = 4_096;
    private static final Direction[] DIRECTIONS = Direction.values();
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
        final ClientboundWarheadImpactPayload payload, final Vec3 center) {
        if (level == null || payload == null || center == null || !center.isFinite()) return;
        if (payload.effectProfile() == WarheadEffectProfile.ANTI_AIR_INTERCEPTION
            || payload.effectProfile() == WarheadEffectProfile.ANTI_AIR_SAFE_SELF_DESTRUCT) return;

        boolean nuclear = payload.payloadType() == WarheadPayloadType.NUCLEAR;
        float visualScale = Mth.clamp(payload.impactVisualScale(), 0.28F, 4.2F);
        double maximumRadius = nuclear
            ? 72.0 + visualScale * 58.0
            : conventionalGlassRadius(visualScale);
        NuclearTerrainPreparation preparation = nuclear
            ? takePreparation(level, payload.warheadId()) : null;
        if (nuclear && preparation == null) {
            /* Direct/master-stick detonation fallback: discover only chunks already present. */
            double craterRadius = 12.0 + 13.0 * visualScale;
            preparation = new NuclearTerrainPreparation(
                center, payload.visualSeed(), craterRadius,
                Mth.ceil(craterRadius * NUCLEAR_AFTERMATH_RADIUS_SCALE),
                payload.impactGameTime() + 420L
            );
        }
        WAVES.computeIfAbsent(level, ignored -> new ArrayDeque<>()).addLast(new Wave(
            center, payload.impactGameTime(), payload.visualSeed(), maximumRadius,
            visualScale, nuclear, preparation));
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
        int aftermathRadius = Mth.ceil(craterRadius * NUCLEAR_AFTERMATH_RADIUS_SCALE);
        long expiresAt = level.getGameTime() + Math.max(1, lifetimeTicks);
        Map<java.util.UUID, NuclearTerrainPreparation> preparations =
            NUCLEAR_PREPARATIONS.computeIfAbsent(level, ignored -> new HashMap<>());
        NuclearTerrainPreparation existing = preparations.get(impactId);
        if (existing != null && existing.compatible(center, seed, aftermathRadius)) {
            existing.extend(expiresAt);
            return;
        }
        preparations.put(impactId, new NuclearTerrainPreparation(
            center, seed, craterRadius, aftermathRadius, expiresAt));
    }

    private static double conventionalGlassRadius(final float visualScale) {
        if (visualScale < 0.49F) return 36.0;
        if (visualScale < 0.82F) return 64.0;
        if (visualScale < 1.19F) return 104.0;
        return 152.0;
    }

    private static synchronized void tick(final ServerLevel level) {
        advancePreparations(level);
        ArrayDeque<Wave> waves = WAVES.get(level);
        if (waves == null || waves.isEmpty()) return;
        long gameTime = level.getGameTime();
        long deadline = System.nanoTime() + LEVEL_WORK_BUDGET_NANOS;
        int scheduledWaves = waves.size();
        for (int index = 0; index < scheduledWaves; index++) {
            if (index > 0 && System.nanoTime() >= deadline) break;
            Wave wave = waves.removeFirst();
            if (!wave.advance(level, gameTime)) waves.addLast(wave);
        }
        if (waves.isEmpty()) WAVES.remove(level);
    }

    private static void advancePreparations(final ServerLevel level) {
        Map<java.util.UUID, NuclearTerrainPreparation> preparations = NUCLEAR_PREPARATIONS.get(level);
        if (preparations == null || preparations.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Map.Entry<java.util.UUID, NuclearTerrainPreparation>> iterator =
            preparations.entrySet().iterator();
        int remaining = NUCLEAR_PREPARATION_COLUMNS_PER_TICK;
        int entriesRemaining = preparations.size();
        while (iterator.hasNext()) {
            Map.Entry<java.util.UUID, NuclearTerrainPreparation> entry = iterator.next();
            NuclearTerrainPreparation preparation = entry.getValue();
            int slice = Math.max(1, remaining / Math.max(1, entriesRemaining));
            entriesRemaining--;
            if (now >= preparation.expiresAt) {
                iterator.remove();
                continue;
            }
            if (remaining <= 0 || preparation.complete()) continue;
            remaining -= preparation.advance(level, Math.min(slice, remaining));
        }
        if (preparations.isEmpty()) NUCLEAR_PREPARATIONS.remove(level);
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

    private static final class Wave {
        private final Vec3 center;
        private final long startGameTime;
        private final long seed;
        private final double maximumRadius;
        private final float visualScale;
        private final boolean nuclear;
        private final double craterRadius;
        private final int aftermathRadius;
        private final NuclearTerrainPreparation preparation;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        private final LongOpenHashSet pressureColumns = new LongOpenHashSet(MAX_COLUMNS_PER_WAVE_TICK * 2);
        private long lastPressureGameTime;
        private double processedRadius;
        private boolean pressureComplete;

        private Wave(final Vec3 center, final long startGameTime, final long seed,
            final double maximumRadius, final float visualScale, final boolean nuclear,
            final NuclearTerrainPreparation preparation) {
            this.center = center;
            this.startGameTime = startGameTime;
            this.seed = seed;
            this.maximumRadius = maximumRadius;
            this.visualScale = visualScale;
            this.nuclear = nuclear;
            this.craterRadius = nuclear ? 12.0 + 13.0 * visualScale : 0.0;
            /* Keep the excavated crater unchanged; only the burned surface reaches out further. */
            this.aftermathRadius = nuclear
                ? Mth.ceil(craterRadius * NUCLEAR_AFTERMATH_RADIUS_SCALE) : 0;
            this.preparation = preparation;
            this.lastPressureGameTime = startGameTime - 1L;
        }

        private boolean advance(final ServerLevel level, final long gameTime) {
            if (nuclear && preparation != null && !preparation.complete()) {
                if (gameTime <= preparation.expiresAt) {
                    preparation.advance(level, NUCLEAR_PREPARATION_COLUMNS_PER_TICK);
                } else {
                    preparation.stopDiscovery();
                }
            }
            if (!pressureComplete) {
                long pressureGameTime = Math.min(gameTime, lastPressureGameTime + 1L);
                advancePressure(level, pressureGameTime);
                lastPressureGameTime = pressureGameTime;
                if (processedRadius + 0.01 < maximumRadius) return false;
                pressureComplete = true;
            }
            if (!nuclear || preparation == null) return true;
            applyPreparedNuclearTerrain(level, maximumRadius);
            /* Keep bounded direct-impact fallback work alive until its loaded terrain is drained. */
            return !preparation.hasPendingWork();
        }

        private void advancePressure(final ServerLevel level, final long gameTime) {
            double targetRadius = Math.min(maximumRadius,
                Math.max(0.0, gameTime - startGameTime + 1.0) * SPEED_BLOCKS_PER_TICK);
            if (targetRadius <= processedRadius + 0.01) return;

            if (nuclear && preparation != null) {
                applyPreparedNuclearTerrain(level, targetRadius);
            }

            double innerRadius = Math.max(0.0, processedRadius - 1.25);
            double annulusWidth = Math.max(0.75, targetRadius - innerRadius);
            int radialBands = Mth.clamp((int) Math.ceil(annulusWidth / 2.25), 2, 8);
            int angularSamples = Mth.clamp(
                (int) Math.ceil(Math.PI * 2.0 * Math.max(2.0, targetRadius) * 1.30),
                72,
                Math.max(72, MAX_COLUMNS_PER_WAVE_TICK / radialBands));

            pressureColumns.clear();
            int processedColumns = 0;
            for (int band = 0; band < radialBands
                && processedColumns < MAX_COLUMNS_PER_WAVE_TICK; band++) {
                double bandFraction = (band + 0.5) / radialBands;
                double radius = innerRadius + (targetRadius - innerRadius) * bandFraction;
                double phase = unit(seed ^ gameTime * 31L
                    ^ band * 0x9E3779B97F4A7C15L);
                for (int sample = 0; sample < angularSamples
                    && processedColumns < MAX_COLUMNS_PER_WAVE_TICK; sample++) {
                    double angle = (sample + phase) / angularSamples * Math.PI * 2.0;
                    int x = Mth.floor(center.x + Math.cos(angle) * radius);
                    int z = Mth.floor(center.z + Math.sin(angle) * radius);
                    long packedColumn = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                    if (!pressureColumns.add(packedColumn)) continue;
                    processPressureColumn(level, x, z, radius);
                    processedColumns++;
                }
            }
            processedRadius = targetRadius;
        }

        private void applyPreparedNuclearTerrain(final ServerLevel level,
            final double targetRadius) {
            int shell = NuclearTerrainPreparation.shellFor(targetRadius);
            int remaining = PREPARED_MUTATIONS_PER_WAVE_TICK;
            int surfaceBudget = Math.min(remaining,
                PREPARED_SURFACE_MUTATIONS_PER_WAVE_TICK);
            for (long packed : preparation.takeSurfaceColumns(shell, surfaceBudget)) {
                BlockPos column = BlockPos.of(packed);
                if (!level.getChunkSource().hasChunk(column.getX() >> 4, column.getZ() >> 4)) continue;
                double dx = column.getX() + 0.5 - center.x;
                double dz = column.getZ() + 0.5 - center.z;
                transformNuclearColumn(level, column.getX(), column.getZ(), Math.sqrt(dx * dx + dz * dz));
                remaining--;
                if (remaining <= 0) return;
            }

            int fairShare = Math.max(1, remaining / 2);
            for (long packed : preparation.takeLeaves(shell, fairShare)) {
                BlockPos position = BlockPos.of(packed);
                if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) continue;
                if (level.getBlockState(position).is(BlockTags.LEAVES)) {
                    level.setBlock(position, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                }
                remaining--;
                if (remaining <= 0) return;
            }
            for (long packed : preparation.takeLogs(shell, fairShare)) {
                BlockPos position = BlockPos.of(packed);
                if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) continue;
                if (level.getBlockState(position).is(BlockTags.LOGS)) {
                    level.setBlock(position, Blocks.PALE_OAK_LOG.defaultBlockState(), UPDATE_FLAGS);
                }
                remaining--;
                if (remaining <= 0) return;
            }
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
            long columnHash = seed ^ ((long) x << 32) ^ (z & 0xFFFFFFFFL)
                ^ 0x4E55434C45415235L;

            int replacementDepth = aftermathNormalized < 0.45 ? 3
                : aftermathNormalized < 0.78 ? 2 : 1;
            for (int depth = 0; depth <= replacementDepth; depth++) {
                cursor.set(x, surfaceY - depth, z);
                if (!level.isInWorldBounds(cursor)) continue;
                BlockState state = level.getBlockState(cursor);
                if (state.isAir()) continue;
                long hash = columnHash ^ cursor.asLong() ^ depth * 0x9E3779B97F4A7C15L;

                if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                    continue;
                }

                if (state.is(Blocks.SAND)) {
                    BlockState replacement = craterNormalized <= 1.65
                        ? fusedSand(hash, false, craterNormalized)
                        : Blocks.GRAVEL.defaultBlockState();
                    level.setBlock(cursor, replacement, UPDATE_FLAGS);
                    continue;
                }
                if (state.is(Blocks.RED_SAND)) {
                    BlockState replacement = craterNormalized <= 1.65
                        ? fusedSand(hash, true, craterNormalized)
                        : Blocks.TERRACOTTA.defaultBlockState();
                    level.setBlock(cursor, replacement, UPDATE_FLAGS);
                    continue;
                }

                if (isSoil(state) && craterNormalized <= 1.52) {
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

                if (craterNormalized <= 0.98 && isCommonRock(state)
                    && (depth == 0 || exposedToAir(level, cursor))) {
                    level.setBlock(cursor, darkCraterRock(hash, craterNormalized), UPDATE_FLAGS);
                }
            }

            /* Isolated burn pockets rather than a continuous ring of fire. */
            double fireChance = craterNormalized <= 1.20
                ? Math.max(0.0, 0.028 - craterNormalized * 0.016)
                : Math.max(0.0, 0.003 - aftermathNormalized * 0.002);
            if (unit(columnHash ^ 0x464952455F414654L) < fireChance) {
                cursor.set(x, surfaceY + 1, z);
                if (level.isInWorldBounds(cursor) && level.getBlockState(cursor).isAir()) {
                    level.setBlock(cursor, Blocks.FIRE.defaultBlockState(), UPDATE_FLAGS);
                }
            }
        }

        private BlockState fusedSand(final long hash, final boolean redSand,
            final double craterNormalized) {
            double selector = unit(hash ^ 0x46555345445F534EL);
            double innerHeat = Mth.clamp(1.25 - craterNormalized, 0.0, 1.0);
            if (!redSand) {
                if (selector < 0.20 + innerHeat * 0.28) return Blocks.GLASS.defaultBlockState();
                if (selector < 0.50 + innerHeat * 0.18) return Blocks.CALCITE.defaultBlockState();
                if (selector < 0.78) return Blocks.GRAVEL.defaultBlockState();
                return Blocks.TERRACOTTA.defaultBlockState();
            }
            if (selector < 0.46 + innerHeat * 0.20) return Blocks.TERRACOTTA.defaultBlockState();
            if (selector < 0.78) return Blocks.TERRACOTTA.defaultBlockState();
            return Blocks.GRAVEL.defaultBlockState();
        }

        private BlockState scorchedSoil(final long hash, final double craterNormalized) {
            double selector = unit(hash ^ 0x53434F524348534FL);
            if (craterNormalized < 0.82) {
                if (selector < 0.50) return Blocks.COARSE_DIRT.defaultBlockState();
                if (selector < 0.78) return Blocks.ROOTED_DIRT.defaultBlockState();
                return Blocks.PODZOL.defaultBlockState();
            }
            if (selector < 0.42) return Blocks.PODZOL.defaultBlockState();
            if (selector < 0.74) return Blocks.COARSE_DIRT.defaultBlockState();
            return Blocks.ROOTED_DIRT.defaultBlockState();
        }

        private BlockState outerScorchedSoil(final long hash) {
            double selector = unit(hash ^ 0x4F55544552534F49L);
            if (selector < 0.58) return Blocks.COARSE_DIRT.defaultBlockState();
            if (selector < 0.84) return Blocks.PODZOL.defaultBlockState();
            return Blocks.ROOTED_DIRT.defaultBlockState();
        }

        private BlockState darkCraterRock(final long hash, final double craterNormalized) {
            double selector = unit(hash ^ 0x4441524B5F524F43L);
            if (craterNormalized < 0.58) {
                return selector < 0.62
                    ? Blocks.DEEPSLATE.defaultBlockState()
                    : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            }
            if (selector < 0.46) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            if (selector < 0.80) return Blocks.DEEPSLATE.defaultBlockState();
            return Blocks.TUFF.defaultBlockState();
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

        private static boolean isCommonRock(final BlockState state) {
            return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE);
        }

        private static boolean isGlass(final BlockState state) {
            Block block = state.getBlock();
            return GLASS_BLOCK_CACHE.computeIfAbsent(block,
                candidate -> candidate.getDescriptionId().contains("glass"));
        }

        private static boolean isFragileSurface(final BlockState state) {
            return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.VINE) || state.is(Blocks.SNOW)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM);
        }
    }

    /**
     * Finds the actual terrain below fluids and vegetation. Heightmaps treat
     * trunks as motion-blocking, which previously started the tree scan near a
     * treetop and left the lower trunk, canopy edges and ground untouched.
     */
    private static int terrainSurfaceY(final ServerLevel level, final int x, final int z) {
        int minimumY = level.dimensionType().minY();
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(x, y, z);
        while (y >= minimumY) {
            position.set(x, y, z);
            BlockState state = level.getBlockState(position);
            if (!state.isAir() && state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                && !Wave.isFragileSurface(state)) {
                return y;
            }
            y--;
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
        private static final int TREE_SCAN_BELOW_SURFACE = 4;
        private final Vec3 center;
        private final long seed;
        private final double treeRadius;
        private final int radius;
        private final Map<Integer, LongArrayList> leavesByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> logsByShell = new HashMap<>();
        private final Map<Integer, LongArrayList> surfacesByShell = new HashMap<>();
        private long expiresAt;
        private final LongOpenHashSet deferredColumns = new LongOpenHashSet();
        private final ArrayDeque<Long> deferredQueue = new ArrayDeque<>();
        private int scanRing;
        private int scanPerimeterOffset;
        private boolean mainScanComplete;

        private NuclearTerrainPreparation(final Vec3 center, final long seed,
            final double craterRadius, final int radius, final long expiresAt) {
            this.center = center;
            this.seed = seed;
            this.treeRadius = radius;
            this.radius = radius;
            this.expiresAt = expiresAt;
            this.scanRing = 0;
        }

        private boolean compatible(final Vec3 otherCenter, final long otherSeed,
            final int otherRadius) {
            return seed == otherSeed && radius == otherRadius
                && center.distanceToSqr(otherCenter) <= 1.0E-6;
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

        private boolean hasPendingWork() {
            return !complete() || !leavesByShell.isEmpty() || !logsByShell.isEmpty()
                || !surfacesByShell.isEmpty();
        }

        private int advance(final ServerLevel level, final int budget) {
            if (complete() || budget <= 0) return 0;
            int centerX = Mth.floor(center.x);
            int centerZ = Mth.floor(center.z);
            /* Wait for the actual impact chunk rather than permanently skipping it. */
            if (!level.getChunkSource().hasChunk(centerX >> 4, centerZ >> 4)) return 0;

            int used = 0;
            double radiusSqr = (double) radius * radius;
            double treeRadiusSqr = treeRadius * treeRadius;
            int retryBudget = Math.max(1, budget / 4);
            while (!deferredQueue.isEmpty() && used < retryBudget) {
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
            while (!mainScanComplete && used < budget) {
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
            surfacesByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                .add(BlockPos.asLong(x, 0, z));
            if (distanceSqr <= treeRadiusSqr) discoverTrees(level, x, z, surfaceY, shell);
        }

        private void discoverTrees(final ServerLevel level, final int x, final int z,
            final int surfaceY, final int shell) {
            int minimumY = Math.max(level.dimensionType().minY(), surfaceY - TREE_SCAN_BELOW_SURFACE);
            int maximumY = Math.min(level.dimensionType().minY() + level.dimensionType().height() - 1,
                surfaceY + TREE_SCAN_ABOVE_SURFACE);
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int y = minimumY;
            while (y <= maximumY) {
                int sectionEnd = Math.min(maximumY, (y & ~15) + 15);
                LevelChunkSection section = chunk.getSection(level.getSectionIndex(y));
                if (!section.maybeHas(state -> state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS))) {
                    y = sectionEnd + 1;
                    continue;
                }
                for (; y <= sectionEnd; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(BlockTags.LEAVES)) {
                        leavesByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(cursor.asLong());
                    } else if (state.is(BlockTags.LOGS)) {
                        logsByShell.computeIfAbsent(shell, ignored -> new LongArrayList())
                            .add(cursor.asLong());
                    }
                }
            }
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

        private LongArrayList takeSurfaceColumns(final int shell, final int limit) {
            return takeThrough(surfacesByShell, shell, limit);
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
