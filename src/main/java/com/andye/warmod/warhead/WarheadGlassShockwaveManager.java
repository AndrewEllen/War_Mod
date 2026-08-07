package com.andye.warmod.warhead;

import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
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
    private static final int NUCLEAR_AFTERMATH_DELAY_TICKS = 80;
    private static final int NUCLEAR_AFTERMATH_COLUMNS_PER_TICK = 2_048;
    private static final long LEVEL_WORK_BUDGET_NANOS = 8_000_000L;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<Block, Boolean> GLASS_BLOCK_CACHE = new IdentityHashMap<>();
    private static final Predicate<BlockState> PRESSURE_RELEVANT = state ->
        Wave.isGlass(state) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
            || Wave.isFragileSurface(state);
    private static final Map<ServerLevel, ArrayDeque<Wave>> WAVES = new IdentityHashMap<>();
    private static boolean registered;

    private WarheadGlassShockwaveManager() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(WarheadGlassShockwaveManager::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            synchronized (WarheadGlassShockwaveManager.class) {
                WAVES.remove(level);
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
        WAVES.computeIfAbsent(level, ignored -> new ArrayDeque<>()).addLast(new Wave(
            center, payload.impactGameTime(), payload.visualSeed(), maximumRadius,
            visualScale, nuclear));
    }

    private static double conventionalGlassRadius(final float visualScale) {
        if (visualScale < 0.49F) return 36.0;
        if (visualScale < 0.82F) return 64.0;
        if (visualScale < 1.19F) return 104.0;
        return 152.0;
    }

    private static synchronized void tick(final ServerLevel level) {
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

    private static final class Wave {
        private final Vec3 center;
        private final long startGameTime;
        private final long seed;
        private final double maximumRadius;
        private final float visualScale;
        private final boolean nuclear;
        private final double craterRadius;
        private final int aftermathRadius;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        private final LongOpenHashSet pressureColumns = new LongOpenHashSet(MAX_COLUMNS_PER_WAVE_TICK * 2);
        private long lastPressureGameTime;
        private double processedRadius;
        private boolean pressureComplete;
        private int aftermathX;
        private int aftermathZ;

        private Wave(final Vec3 center, final long startGameTime, final long seed,
            final double maximumRadius, final float visualScale, final boolean nuclear) {
            this.center = center;
            this.startGameTime = startGameTime;
            this.seed = seed;
            this.maximumRadius = maximumRadius;
            this.visualScale = visualScale;
            this.nuclear = nuclear;
            this.craterRadius = nuclear ? 12.0 + 13.0 * visualScale : 0.0;
            this.aftermathRadius = nuclear
                ? Mth.ceil(craterRadius * 1.55) : 0;
            this.aftermathX = -aftermathRadius;
            this.aftermathZ = -aftermathRadius;
            this.lastPressureGameTime = startGameTime - 1L;
        }

        private boolean advance(final ServerLevel level, final long gameTime) {
            if (!pressureComplete) {
                long pressureGameTime = Math.min(gameTime, lastPressureGameTime + 1L);
                advancePressure(level, pressureGameTime);
                lastPressureGameTime = pressureGameTime;
                if (processedRadius + 0.01 < maximumRadius) return false;
                pressureComplete = true;
                if (!nuclear) return true;
            }
            if (!nuclear) return true;
            if (gameTime - startGameTime < NUCLEAR_AFTERMATH_DELAY_TICKS) return false;
            return advanceNuclearAftermath(level);
        }

        private void advancePressure(final ServerLevel level, final long gameTime) {
            double targetRadius = Math.min(maximumRadius,
                Math.max(0.0, gameTime - startGameTime + 1.0) * SPEED_BLOCKS_PER_TICK);
            if (targetRadius <= processedRadius + 0.01) return;

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

        private void processPressureColumn(final ServerLevel level, final int x, final int z,
            final double radialDistance) {
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
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
                            double stripChance = Mth.clamp(0.18 + scorchIntensity * 0.92,
                                0.0, 1.0);
                            if (unit(hash) < stripChance) {
                                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                            }
                            continue;
                        }
                        if (state.is(BlockTags.LOGS)) {
                            double ashChance = Mth.clamp((scorchIntensity - 0.16) * 1.18,
                                0.0, 0.92);
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

        private boolean advanceNuclearAftermath(final ServerLevel level) {
            int visited = 0;
            while (aftermathX <= aftermathRadius
                && visited < NUCLEAR_AFTERMATH_COLUMNS_PER_TICK) {
                int dx = aftermathX;
                int dz = aftermathZ;
                aftermathZ++;
                if (aftermathZ > aftermathRadius) {
                    aftermathZ = -aftermathRadius;
                    aftermathX++;
                }
                if ((double) dx * dx + (double) dz * dz
                    > (double) aftermathRadius * aftermathRadius) continue;
                int x = Mth.floor(center.x) + dx;
                int z = Mth.floor(center.z) + dz;
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                transformNuclearColumn(level, x, z, Math.sqrt((double) dx * dx + (double) dz * dz));
                visited++;
            }
            return aftermathX > aftermathRadius;
        }

        private void transformNuclearColumn(final ServerLevel level, final int x,
            final int z, final double radialDistance) {
            double craterNormalized = radialDistance / Math.max(1.0, craterRadius);
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (surfaceY < level.dimensionType().minY()) return;
            long columnHash = seed ^ ((long) x << 32) ^ (z & 0xFFFFFFFFL)
                ^ 0x4E55434C45415235L;

            for (int depth = 0; depth <= 6; depth++) {
                cursor.set(x, surfaceY - depth, z);
                if (!level.isInWorldBounds(cursor)) continue;
                BlockState state = level.getBlockState(cursor);
                if (state.isAir()) continue;
                long hash = columnHash ^ cursor.asLong() ^ depth * 0x9E3779B97F4A7C15L;

                if (state.is(Blocks.SAND)) {
                    level.setBlock(cursor, fusedSand(hash, false, craterNormalized), UPDATE_FLAGS);
                    continue;
                }
                if (state.is(Blocks.RED_SAND)) {
                    level.setBlock(cursor, fusedSand(hash, true, craterNormalized), UPDATE_FLAGS);
                    continue;
                }

                if (isSoil(state) && craterNormalized <= 1.52) {
                    level.setBlock(cursor, scorchedSoil(hash, craterNormalized), UPDATE_FLAGS);
                    continue;
                }

                if (craterNormalized <= 0.98 && isCommonRock(state)
                    && (depth == 0 || exposedToAir(level, cursor))) {
                    level.setBlock(cursor, darkCraterRock(hash, craterNormalized), UPDATE_FLAGS);
                }
            }

            if (craterNormalized <= 1.36 && unit(columnHash ^ 0x464952455F414654L)
                < Math.max(0.0, 0.13 - craterNormalized * 0.055)) {
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

    private static double unit(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}
