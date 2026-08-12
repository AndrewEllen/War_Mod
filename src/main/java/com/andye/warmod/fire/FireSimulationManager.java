package com.andye.warmod.fire;

import com.andye.warmod.fire.network.FireNetworking;
import com.andye.warmod.fire.wind.FireWindEngine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded, server-authoritative combustion simulation. No vanilla fire blocks are used. */
public final class FireSimulationManager {
    private static final int MAX_ACTIVE_CELLS = 4_096;
    private static final int MAX_CELL_UPDATES_PER_TICK = 512;
    private static final int MAX_NEW_IGNITIONS_PER_TICK = 64;
    private static final int MAX_WET_POSITIONS = 8_192;
    private static final int NETWORK_INTERVAL_TICKS = 10;
    private static final int[] SPREAD_OFFSETS = {
        1, 0, 0, -1, 0, 0, 0, 1, 0, 0, -1, 0, 0, 0, 1, 0, 0, -1,
        1, 1, 0, -1, 1, 0, 0, 1, 1, 0, 1, -1,
        1, 0, 1, 1, 0, -1, -1, 0, 1, -1, 0, -1,
        1, 1, 1, 1, 1, -1, -1, 1, 1, -1, 1, -1
    };
    private static final Map<ServerLevel, LevelState> LEVELS = new IdentityHashMap<>();
    private static boolean registered;

    private FireSimulationManager() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerTickEvents.END_LEVEL_TICK.register(FireSimulationManager::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            synchronized (FireSimulationManager.class) { LEVELS.remove(level); }
        });
        registered = true;
    }

    public static synchronized void clearAll() { LEVELS.clear(); }

    public static synchronized boolean ignite(final ServerLevel level, final BlockPos position,
        final FireIntensity intensity, final long seed, final boolean allowSurfaceFlame) {
        if (level == null || position == null || intensity == null) return false;
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState());
        return igniteInternal(level, state, position, intensity.heat(), intensity.surfaceBurnTicks(),
            intensity.spreadIntervalTicks(), seed, allowSurfaceFlame);
    }

    public static synchronized int suppress(final ServerLevel level, final ServerPlayer source,
        final Vec3 center, final double radius, final float amount) {
        LevelState state = LEVELS.get(level);
        if (state == null || center == null || !center.isFinite() || radius <= 0.0 || amount <= 0.0F) return 0;
        int reach = Mth.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        double radiusSquared = radius * radius;
        int affected = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos position = origin.offset(dx, dy, dz);
                    if (Vec3.atCenterOf(position).distanceToSqr(center) > radiusSquared) continue;
                    if (!isLoaded(level, position)) continue;
                    long key = position.asLong();
                    Cell cell = state.cells.get(key);
                    if (cell != null && source != null && !visibleFrom(level, source, center,
                        Vec3.atCenterOf(position))) continue;
                    if (cell != null || FireFuelProfile.of(level.getBlockState(position)).flammable()) {
                        addWetness(state, key, amount * 1.25F, cell != null);
                    }
                    if (cell != null) {
                        cell.heat = Math.max(0.0F, cell.heat - amount);
                        cell.phase = cell.heat < 0.38F ? FirePhase.SMOLDERING : cell.phase;
                        affected++;
                    }
                }
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
        FireWindEngine.tick(level);
        LevelState state = LEVELS.get(level);
        if (state == null) return;
        long now = level.getGameTime();
        boolean hadCells = !state.cells.isEmpty();
        decayWetness(state);
        state.newIgnitionsThisTick = 0;

        int updates = Math.min(MAX_CELL_UPDATES_PER_TICK, state.workQueue.size());
        for (int index = 0; index < updates; index++) {
            long key = state.workQueue.removeFirst();
            Cell cell = state.cells.get(key);
            if (cell == null) continue;
            updateCell(level, state, cell, now);
            if (state.cells.containsKey(key)) state.workQueue.addLast(key);
        }

        if (hadCells && state.cells.isEmpty()) sendSnapshot(level, state);
        else if (now % NETWORK_INTERVAL_TICKS == 0L) sendSnapshot(level, state);
        if (now % 20L == 0L && !state.cells.isEmpty()) playCrackle(level, state, now);
        if (state.cells.isEmpty() && state.wetness.isEmpty()) LEVELS.remove(level);
    }

    private static void updateCell(final ServerLevel level, final LevelState state,
        final Cell cell, final long now) {
        if (!isLoaded(level, cell.position)) return;
        int elapsed = (int) Mth.clamp(now - cell.lastUpdateTick, 1L, 40L);
        cell.lastUpdateTick = now;
        BlockState blockState = level.getBlockState(cell.position);
        FireFuelProfile currentFuel = FireFuelProfile.of(blockState);
        boolean coveredByWater = level.getFluidState(cell.position).is(FluidTags.WATER)
            || level.getFluidState(cell.position.above()).is(FluidTags.WATER);
        float wetness = state.wetness.getOrDefault(cell.position.asLong(), 0.0F);

        if (coveredByWater) {
            addWetness(state, cell.position.asLong(), 1.5F, true);
            cell.heat -= 0.24F * elapsed;
        } else if (wetness > 0.0F) {
            cell.heat -= (0.018F + wetness * 0.075F) * elapsed;
        } else if (cell.fuel > 0.0F) {
            cell.fuel -= elapsed / (float) Math.max(20, cell.burnTicks);
            float targetHeat = cell.intensity;
            cell.heat += (targetHeat - cell.heat) * Math.min(1.0F, elapsed * 0.12F);
        } else {
            cell.heat -= 0.025F * elapsed;
        }

        cell.fuel = Math.max(0.0F, cell.fuel);
        cell.heat = Mth.clamp(cell.heat, 0.0F, 1.2F);
        cell.phase = cell.heat < 0.38F || cell.fuel < 0.20F
            ? FirePhase.SMOLDERING : FirePhase.FLAMING;

        if (!cell.surfaceFlame && !cell.consumed && cell.fuel <= 0.0F
            && currentFuel.flammable() && level.getBlockEntity(cell.position) == null) {
            level.setBlock(cell.position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            cell.consumed = true;
        }

        if (cell.heat <= 0.025F) {
            state.cells.remove(cell.position.asLong());
            return;
        }

        if (cell.phase == FirePhase.FLAMING && cell.heat > 0.42F
            && now >= cell.nextSpreadTick && state.newIgnitionsThisTick < MAX_NEW_IGNITIONS_PER_TICK) {
            spread(level, state, cell, now);
            int interval = Math.max(4, cell.spreadIntervalTicks
                + (int) (unit(mix(cell.seed ^ now)) * 6.0));
            cell.nextSpreadTick = now + interval;
        }
    }

    private static void spread(final ServerLevel level, final LevelState state,
        final Cell source, final long now) {
        Vec3 wind = FireWindEngine.windAt(level, Vec3.atCenterOf(source.position));
        Vec3 windHorizontal = new Vec3(wind.x, 0.0, wind.z);
        double windLength = windHorizontal.length();
        Vec3 windDirection = windLength > 1.0E-5 ? windHorizontal.scale(1.0 / windLength) : Vec3.ZERO;
        SplittableRandom random = new SplittableRandom(mix(source.seed ^ now));
        int phase = random.nextInt(SPREAD_OFFSETS.length / 3);
        int ignited = 0;

        for (int checked = 0; checked < SPREAD_OFFSETS.length / 3 && ignited < 2; checked++) {
            int offsetIndex = ((checked + phase) % (SPREAD_OFFSETS.length / 3)) * 3;
            int dx = SPREAD_OFFSETS[offsetIndex];
            int dy = SPREAD_OFFSETS[offsetIndex + 1];
            int dz = SPREAD_OFFSETS[offsetIndex + 2];
            BlockPos candidate = source.position.offset(dx, dy, dz);
            if (!isLoaded(level, candidate) || state.cells.containsKey(candidate.asLong())) continue;
            FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(candidate));
            if (!profile.flammable() || !exposed(level, candidate)
                || state.wetness.getOrDefault(candidate.asLong(), 0.0F) > 0.30F) continue;

            Vec3 offset = new Vec3(dx, 0.0, dz);
            double alignment = offset.lengthSqr() > 0.0 && windLength > 0.0
                ? offset.normalize().dot(windDirection) : 0.0;
            double verticalBias = dy > 0 ? 0.24 : dy < 0 ? -0.22 : 0.0;
            double chance = profile.ignition() * source.heat
                * (0.13 + source.intensity * 0.11 + verticalBias
                    + Math.max(-0.08, alignment * Math.min(0.30, windLength * 0.20)));
            if (random.nextDouble() < Mth.clamp(chance, 0.01, 0.72)
                && igniteInternal(level, state, candidate,
                    Math.max(0.30F, source.intensity * 0.82F),
                    Math.max(55, profile.burnTicks()),
                    Math.max(7, source.spreadIntervalTicks + 2),
                    mix(source.seed ^ candidate.asLong() ^ now), false)) {
                state.newIgnitionsThisTick++;
                ignited++;
            }
        }

        if (source.intensity >= 0.66F && windLength > 0.075
            && state.newIgnitionsThisTick < MAX_NEW_IGNITIONS_PER_TICK
            && random.nextDouble() < Math.min(0.38, windLength * source.heat * 0.24)) {
            int distance = random.nextInt(3, source.intensity >= 0.9F ? 11 : 7);
            BlockPos landing = BlockPos.containing(Vec3.atCenterOf(source.position)
                .add(windDirection.scale(distance)).add(0.0, random.nextDouble(-1.0, 3.5), 0.0));
            tryEmberLanding(level, state, source, landing, now, random);
        }
    }

    private static void tryEmberLanding(final ServerLevel level, final LevelState state,
        final Cell source, final BlockPos landing, final long now, final SplittableRandom random) {
        for (int attempt = 0; attempt < 18; attempt++) {
            BlockPos candidate = landing.offset(random.nextInt(-1, 2), random.nextInt(-1, 2),
                random.nextInt(-1, 2));
            if (!isLoaded(level, candidate) || state.cells.containsKey(candidate.asLong())) continue;
            FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(candidate));
            if (!profile.flammable() || !exposed(level, candidate)
                || state.wetness.getOrDefault(candidate.asLong(), 0.0F) > 0.25F) continue;
            if (igniteInternal(level, state, candidate, Math.max(0.30F, source.intensity * 0.68F),
                profile.burnTicks(), Math.max(9, source.spreadIntervalTicks + 3),
                mix(source.seed ^ candidate.asLong() ^ now ^ 0x454D424552L), false)) {
                state.newIgnitionsThisTick++;
            }
            return;
        }
    }

    private static boolean igniteInternal(final ServerLevel level, final LevelState state,
        final BlockPos position, final float intensity, final int surfaceBurnTicks,
        final int spreadIntervalTicks, final long seed, final boolean allowSurfaceFlame) {
        if (!isLoaded(level, position) || !level.getWorldBorder().isWithinBounds(position)
            || level.getFluidState(position).is(FluidTags.WATER)
            || state.wetness.getOrDefault(position.asLong(), 0.0F) > 0.35F) return false;
        FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(position));
        if (!profile.flammable() && !allowSurfaceFlame) return false;
        long key = position.asLong();
        Cell existing = state.cells.get(key);
        if (existing != null) {
            existing.intensity = Math.max(existing.intensity, intensity);
            existing.heat = Math.max(existing.heat, intensity);
            existing.fuel = Math.max(existing.fuel, 0.50F);
            return true;
        }
        if (state.cells.size() >= MAX_ACTIVE_CELLS) return false;
        boolean surface = !profile.flammable() || !profile.consumable();
        int burnTicks = surface ? surfaceBurnTicks : Math.max(40, profile.burnTicks());
        long now = level.getGameTime();
        Cell cell = new Cell(position.immutable(), Mth.clamp(intensity, 0.20F, 1.0F),
            Mth.clamp(intensity, 0.20F, 1.0F), 1.0F, burnTicks,
            Math.max(4, spreadIntervalTicks), seed, now, surface);
        state.cells.put(key, cell);
        state.workQueue.addLast(key);
        return true;
    }

    private static boolean exposed(final ServerLevel level, final BlockPos position) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = position.relative(direction);
            if (isLoaded(level, neighbour) && level.getBlockState(neighbour).isAir()) return true;
        }
        return false;
    }

    private static boolean visibleFrom(final ServerLevel level, final ServerPlayer source,
        final Vec3 start, final Vec3 target) {
        HitResult hit = level.clip(new ClipContext(start, target, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, source));
        return hit.getType() == HitResult.Type.MISS
            || hit.getLocation().distanceToSqr(target) <= 0.80;
    }

    private static void sendSnapshot(final ServerLevel level, final LevelState state) {
        List<FireCellSnapshot> snapshots = new ArrayList<>(state.cells.size());
        for (Cell cell : state.cells.values()) {
            Vec3 wind = FireWindEngine.windAt(level, Vec3.atCenterOf(cell.position));
            snapshots.add(new FireCellSnapshot(cell.position, cell.intensity, cell.heat,
                cell.phase, cell.seed, wind));
        }
        FireNetworking.sendSnapshot(level, List.copyOf(snapshots));
    }

    private static void playCrackle(final ServerLevel level, final LevelState state, final long now) {
        int selected = (int) Math.floorMod(mix(now ^ state.cells.size()), state.cells.size());
        Iterator<Cell> iterator = state.cells.values().iterator();
        Cell cell = iterator.next();
        for (int index = 0; index < selected && iterator.hasNext(); index++) cell = iterator.next();
        level.playSound(null, cell.position, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
            0.35F + cell.intensity * 0.35F, 0.82F + (float) unit(cell.seed ^ now) * 0.35F);
    }

    private static void decayWetness(final LevelState state) {
        Iterator<Map.Entry<Long, Float>> iterator = state.wetness.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Float> entry = iterator.next();
            float remaining = entry.getValue() - 0.012F;
            if (remaining <= 0.0F) iterator.remove();
            else entry.setValue(remaining);
        }
    }

    private static void addWetness(final LevelState state, final long key, final float amount,
        final boolean priority) {
        Float existing = state.wetness.get(key);
        if (existing == null && state.wetness.size() >= MAX_WET_POSITIONS) {
            if (!priority) return;
            Iterator<Long> iterator = state.wetness.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        state.wetness.put(key, Math.min(1.5F, (existing == null ? 0.0F : existing) + amount));
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(final long value) { return (mix(value) >>> 11) * 0x1.0p-53; }

    private static final class LevelState {
        private final LinkedHashMap<Long, Cell> cells = new LinkedHashMap<>();
        private final ArrayDeque<Long> workQueue = new ArrayDeque<>();
        private final HashMap<Long, Float> wetness = new HashMap<>();
        private int newIgnitionsThisTick;
    }

    private static final class Cell {
        private final BlockPos position;
        private float intensity;
        private float heat;
        private float fuel;
        private final int burnTicks;
        private final int spreadIntervalTicks;
        private final long seed;
        private long lastUpdateTick;
        private long nextSpreadTick;
        private FirePhase phase = FirePhase.FLAMING;
        private final boolean surfaceFlame;
        private boolean consumed;

        private Cell(final BlockPos position, final float intensity, final float heat,
            final float fuel, final int burnTicks, final int spreadIntervalTicks,
            final long seed, final long now, final boolean surfaceFlame) {
            this.position = position;
            this.intensity = intensity;
            this.heat = heat;
            this.fuel = fuel;
            this.burnTicks = burnTicks;
            this.spreadIntervalTicks = spreadIntervalTicks;
            this.seed = seed;
            this.lastUpdateTick = now;
            this.nextSpreadTick = now + spreadIntervalTicks;
            this.surfaceFlame = surfaceFlame;
        }
    }
}
