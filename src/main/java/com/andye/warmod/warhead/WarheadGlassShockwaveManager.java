package com.andye.warmod.warhead;

import com.andye.warmod.warhead.network.ClientboundWarheadImpactPayload;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded physical glass and fragile-surface response carried by the same
 * 343 m/s pressure front used by the client visuals. Work is deterministic,
 * never requests chunk loads, and scales its maximum range with yield.
 */
public final class WarheadGlassShockwaveManager {
    private static final double SPEED_BLOCKS_PER_TICK = WarheadVisualMath.AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK;
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int MAX_COLUMNS_PER_WAVE_TICK = 4_096;
    private static final Map<ServerLevel, List<Wave>> WAVES = new IdentityHashMap<>();
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
            : 10.0 + visualScale * 28.0;
        WAVES.computeIfAbsent(level, ignored -> new ArrayList<>()).add(new Wave(
            center, payload.impactGameTime(), payload.visualSeed(), maximumRadius, nuclear));
    }

    private static synchronized void tick(final ServerLevel level) {
        List<Wave> waves = WAVES.get(level);
        if (waves == null || waves.isEmpty()) return;
        Iterator<Wave> iterator = waves.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().advance(level, level.getGameTime())) iterator.remove();
        }
        if (waves.isEmpty()) WAVES.remove(level);
    }

    private static final class Wave {
        private final Vec3 center;
        private final long startGameTime;
        private final long seed;
        private final double maximumRadius;
        private final boolean nuclear;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private double processedRadius;

        private Wave(final Vec3 center, final long startGameTime, final long seed,
            final double maximumRadius, final boolean nuclear) {
            this.center = center;
            this.startGameTime = startGameTime;
            this.seed = seed;
            this.maximumRadius = maximumRadius;
            this.nuclear = nuclear;
        }

        private boolean advance(final ServerLevel level, final long gameTime) {
            double targetRadius = Math.min(maximumRadius,
                Math.max(0.0, gameTime - startGameTime + 1.0) * SPEED_BLOCKS_PER_TICK);
            if (targetRadius <= processedRadius + 0.01) return processedRadius >= maximumRadius;

            double innerRadius = Math.max(0.0, processedRadius - 1.25);
            double annulusWidth = Math.max(0.75, targetRadius - innerRadius);
            int radialBands = Mth.clamp((int) Math.ceil(annulusWidth / 2.25), 2, 8);
            int angularSamples = Mth.clamp(
                (int) Math.ceil(Math.PI * 2.0 * Math.max(2.0, targetRadius) * 1.45),
                72,
                Math.max(72, MAX_COLUMNS_PER_WAVE_TICK / radialBands)
            );

            LongOpenHashSet columns = new LongOpenHashSet(
                Math.min(MAX_COLUMNS_PER_WAVE_TICK * 2, angularSamples * radialBands * 2));
            int processedColumns = 0;
            for (int band = 0; band < radialBands && processedColumns < MAX_COLUMNS_PER_WAVE_TICK; band++) {
                double bandFraction = (band + 0.5) / radialBands;
                double radius = innerRadius + (targetRadius - innerRadius) * bandFraction;
                double phase = unit(seed ^ gameTime * 31L ^ band * 0x9E3779B97F4A7C15L);
                for (int sample = 0; sample < angularSamples
                    && processedColumns < MAX_COLUMNS_PER_WAVE_TICK; sample++) {
                    double angle = (sample + phase) / angularSamples * Math.PI * 2.0;
                    int x = Mth.floor(center.x + Math.cos(angle) * radius);
                    int z = Mth.floor(center.z + Math.sin(angle) * radius);
                    long packedColumn = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                    if (!columns.add(packedColumn)) continue;
                    processColumn(level, x, z, radius);
                    processedColumns++;
                }
            }

            processedRadius = targetRadius;
            return processedRadius >= maximumRadius;
        }

        private void processColumn(final ServerLevel level, final int x, final int z,
            final double radialDistance) {
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            int belowSurface = nuclear ? 8 : 4;
            int aboveSurface = nuclear ? 52 : 28;
            double normalizedDistance = Mth.clamp(radialDistance / maximumRadius, 0.0, 1.0);
            double glassChance = normalizedDistance <= 0.62
                ? 1.0
                : Mth.clamp((1.0 - normalizedDistance) / 0.38, 0.0, 1.0);

            for (int y = surfaceY - belowSurface; y <= surfaceY + aboveSurface; y++) {
                cursor.set(x, y, z);
                if (!level.isInWorldBounds(cursor)) continue;
                BlockState state = level.getBlockState(cursor);
                if (state.isAir()) continue;

                if (isGlass(state)) {
                    long hash = seed ^ cursor.asLong() ^ 0x474C4153535F5632L;
                    if (unit(hash) <= glassChance) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                    }
                    continue;
                }

                if (nuclear && normalizedDistance < 0.82 && isFragileSurface(state)) {
                    double chance = (1.0 - normalizedDistance / 0.82) * 0.82;
                    if (unit(seed ^ cursor.asLong() ^ 0x5355524641434556L) < chance) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                    }
                }
            }
        }

        private static boolean isGlass(final BlockState state) {
            String descriptionId = state.getBlock().getDescriptionId();
            return descriptionId.contains("glass");
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
