package com.andye.warmod.fire;

import com.andye.warmod.fire.FireSurfaceAnchor.SurfaceKey;
import com.andye.warmod.fire.network.FireNetworking;
import com.andye.warmod.fire.wind.FireWindEngine;
import com.andye.warmod.item.component.FireDebugConfig;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded surface-patch combustion. Fire is an authoritative heat/fuel field;
 * no vanilla fire blocks or Minecraft Particle instances are used.
 */
public final class FireSimulationManager {
    private static final int MAX_ACTIVE_PATCHES = 4_096;
    private static final int MAX_PATCH_UPDATES_PER_TICK = 512;
    private static final int MAX_NEW_IGNITIONS_PER_TICK = 48;
    private static final int MAX_PREHEAT_SURFACES = 8_192;
    private static final int MAX_WET_POSITIONS = 8_192;
    private static final int MAX_EMBERS = 256;
    private static final int NETWORK_INTERVAL_TICKS = 6;
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
        if (level == null || primary == null || config == null) return 0;
        LevelState state = LEVELS.computeIfAbsent(level, ignored -> new LevelState());
        if (!validSurface(level, primary, true)) return 0;
        int placed = igniteInternal(level, state, primary, config.intensity(), seed,
            true, true) ? 1 : 0;
        if (config.size() <= 1 || placed == 0) return placed;

        double radius = (config.size() - 1) * 0.72;
        int reach = Mth.ceil(radius);
        int maximum = Math.min(384, 1 + config.size() * config.size() * 2);
        Vec3 origin = primary.position();
        List<RankedSurface> candidates = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos host = primary.host().offset(dx, dy, dz);
                    if (!isLoaded(level, host) || host.equals(primary.host())) continue;
                    FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(host));
                    if (!profile.flammable()) continue;
                    Direction face = bestExposedFace(level, host, origin);
                    if (face == null) continue;
                    FireSurfaceAnchor anchor = FireSurfaceAnchor.center(host, face);
                    double distance = anchor.position().distanceTo(origin);
                    if (distance <= radius + 0.35) candidates.add(new RankedSurface(anchor,
                        distance, mix(seed ^ host.asLong() ^ face.ordinal())));
                }
            }
        }
        candidates.sort((left, right) -> {
            int distance = Double.compare(left.distance(), right.distance());
            return distance != 0 ? distance : Long.compare(left.rank(), right.rank());
        });
        for (RankedSurface candidate : candidates) {
            if (placed >= maximum || state.patches.size() >= MAX_ACTIVE_PATCHES) break;
            float falloff = (float) Mth.clamp(1.0 - candidate.distance()
                / Math.max(0.1, radius + 0.35), 0.0, 1.0);
            float intensity = config.intensity() * (0.52F + falloff * 0.48F);
            if (igniteInternal(level, state, candidate.anchor(), intensity,
                candidate.rank(), true, false)) placed++;
        }
        return placed;
    }

    public static synchronized int suppress(final ServerLevel level, final ServerPlayer source,
        final Vec3 center, final double radius, final float amount) {
        LevelState state = LEVELS.get(level);
        if (state == null || center == null || !center.isFinite() || radius <= 0.0
            || amount <= 0.0F) return 0;
        double radiusSquared = radius * radius;
        int affected = 0;
        for (Patch patch : state.patches.values()) {
            Vec3 position = patch.anchor.position();
            if (position.distanceToSqr(center) > radiusSquared) continue;
            if (source != null && !visibleFrom(level, source, center, position)) continue;
            patch.heat = Math.max(0.0F, patch.heat - amount);
            patch.coverage = Math.max(0.02F, patch.coverage - amount * 0.16F);
            if (patch.heat < 0.24F) patch.phase = FirePhase.SMOLDERING;
            addWetness(state, patch.anchor.host().asLong(), amount * 1.35F, true);
            affected++;
        }
        int reach = Mth.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos host = origin.offset(dx, dy, dz);
                    if (!isLoaded(level, host)
                        || Vec3.atCenterOf(host).distanceToSqr(center) > radiusSquared) continue;
                    if (FireFuelProfile.of(level.getBlockState(host)).flammable())
                        addWetness(state, host.asLong(), amount, false);
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
        boolean hadPatches = !state.patches.isEmpty();
        state.newIgnitionsThisTick = 0;
        decayWetness(state);
        if (now % 20L == 0L) decayPreheat(state, now);
        tickEmbers(level, state, now);

        int updates = Math.min(MAX_PATCH_UPDATES_PER_TICK, state.workQueue.size());
        for (int index = 0; index < updates; index++) {
            long id = state.workQueue.removeFirst();
            Patch patch = state.patches.get(id);
            if (patch == null) continue;
            updatePatch(level, state, patch, now);
            if (state.patches.containsKey(id)) state.workQueue.addLast(id);
        }

        if (hadPatches && state.patches.isEmpty()) sendSnapshot(level, state);
        else if (now % NETWORK_INTERVAL_TICKS == 0L) sendSnapshot(level, state);
        if (now % 24L == 0L && !state.patches.isEmpty()) playCrackle(level, state, now);
        if (state.patches.isEmpty() && state.wetness.isEmpty()
            && state.preheat.isEmpty() && state.embers.isEmpty()) {
            checkpoint(level, null);
            LEVELS.remove(level);
        } else if (now % 100L == 0L) checkpoint(level, state);
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
        if (water) {
            addWetness(state, host.asLong(), 1.5F, true);
            patch.heat -= 0.18F * elapsed;
        } else if (wetness > 0.0F) {
            patch.heat -= (0.006F + wetness * 0.025F) * elapsed;
        }

        long age = now - patch.ignitionGameTime;
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
                    * Math.min(1.0F, elapsed * (0.018F + patch.targetIntensity * 0.018F));
                patch.coverage += elapsed * (0.0022F + patch.targetIntensity * 0.0036F);
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
                patch.fuel -= elapsed / (float) Math.max(400, patch.burnTicks);
                if (patch.fuel < 0.28F) patch.phase = FirePhase.DECAYING;
            }
            case DECAYING -> {
                patch.fuel -= elapsed / (float) Math.max(300, patch.burnTicks);
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

        if ((patch.phase == FirePhase.GROWING || patch.phase == FirePhase.FLAMING)
            && patch.heat > 0.075F && now >= patch.nextTransferTick
            && state.newIgnitionsThisTick < MAX_NEW_IGNITIONS_PER_TICK) {
            transferHeat(level, state, patch, profile, now);
            patch.nextTransferTick = now + Math.max(7,
                21 - (int) (patch.targetIntensity * 12.0F));
        }
        if (patch.phase == FirePhase.FLAMING && patch.coverage > 0.65F
            && state.embers.size() < MAX_EMBERS
            && unit(mix(patch.seed ^ now)) < patch.targetIntensity * profile.emberSusceptibility() * 0.055) {
            spawnEmber(level, state, patch, now);
        }
    }

    private static void transferHeat(final ServerLevel level, final LevelState state,
        final Patch source, final FireFuelProfile sourceProfile, final long now) {
        Vec3 origin = source.anchor.position();
        Vec3 wind = FireWindEngine.windAt(level, origin);
        double windLength = Math.sqrt(wind.x * wind.x + wind.z * wind.z);
        Vec3 windDirection = windLength > 1.0E-5
            ? new Vec3(wind.x / windLength, 0.0, wind.z / windLength) : Vec3.ZERO;
        SplittableRandom random = new SplittableRandom(mix(source.seed ^ now));

        for (int[] offset : SURFACE_OFFSETS) {
            FireSurfaceAnchor target = source.anchor.gridOffset(offset[0], offset[1]);
            if (target != null) depositAlongSurface(level, state, source,
                sourceProfile, target, now);
        }

        for (Direction direction : DIRECTIONS) {
            BlockPos candidate = source.anchor.host().relative(direction);
            depositToBlock(level, state, source, sourceProfile, candidate, origin, windDirection,
                windLength, now, 1.0F);
        }
        int radius = 2 + Mth.ceil(source.targetIntensity * 4.0F);
        int samples = 8 + Mth.ceil(source.targetIntensity * 14.0F);
        for (int index = 0; index < samples; index++) {
            int dx = random.nextInt(-radius, radius + 1);
            int dy = random.nextInt(-Math.max(1, radius / 2), radius + 1);
            int dz = random.nextInt(-radius, radius + 1);
            if (dx == 0 && dy == 0 && dz == 0) continue;
            BlockPos candidate = source.anchor.host().offset(dx, dy, dz);
            float sampling = dy > 0 ? 1.28F : 0.62F;
            depositToBlock(level, state, source, sourceProfile, candidate, origin,
                windDirection, windLength, now, sampling);
        }
    }

    private static void depositAlongSurface(final ServerLevel level, final LevelState state,
        final Patch source, final FireFuelProfile profile, final FireSurfaceAnchor target,
        final long now) {
        if (state.surfaceIndex.containsKey(target.key())) return;
        float dose = 0.014F + source.heat * source.coverage * profile.heatRelease()
            * (0.065F + source.targetIntensity * 0.085F);
        if (dose <= 0.003F) return;
        addPreheat(level, state, target, dose, source.targetIntensity,
            source.seed, now, profile);
    }

    private static void depositToBlock(final ServerLevel level, final LevelState state,
        final Patch source, final FireFuelProfile sourceProfile, final BlockPos candidate,
        final Vec3 origin, final Vec3 windDirection, final double windLength,
        final long now, final float samplingScale) {
        if (!isLoaded(level, candidate) || candidate.equals(source.anchor.host())) return;
        FireFuelProfile targetProfile = FireFuelProfile.of(level.getBlockState(candidate));
        if (!targetProfile.flammable()
            || state.wetness.getOrDefault(candidate.asLong(), 0.0F) > 0.35F) return;
        Direction face = bestExposedFace(level, candidate, origin);
        if (face == null) return;
        FireSurfaceAnchor target = FireSurfaceAnchor.center(candidate, face);
        if (state.surfaceIndex.containsKey(target.key()) || !clearHeatPath(level, origin,
            target.position())) return;
        Vec3 delta = target.position().subtract(origin);
        double distance = Math.max(0.65, delta.length());
        Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
        double alignment = horizontal.lengthSqr() > 1.0E-5 && windLength > 0.0
            ? horizontal.normalize().dot(windDirection) : 0.0;
        double convection = delta.y > 0.0 ? Math.min(1.25, delta.y * 0.32) : -0.18;
        double windBias = alignment * Math.min(0.85, windLength * 2.1);
        double distanceFalloff = 1.0 / Math.max(1.0, distance * distance * 0.72);
        float dose = (float) (source.heat * source.coverage * sourceProfile.heatRelease()
            * (0.16 + source.targetIntensity * 0.22 + convection + windBias)
            * distanceFalloff * samplingScale);
        if (dose <= 0.004F) return;
        addPreheat(level, state, target, dose, source.targetIntensity, source.seed, now,
            targetProfile);
    }

    private static void addPreheat(final ServerLevel level, final LevelState state,
        final FireSurfaceAnchor target, final float amount, final float sourceIntensity,
        final long sourceSeed, final long now, final FireFuelProfile profile) {
        Exposure exposure = state.preheat.get(target.key());
        float existing = exposure == null ? 0.0F : Math.max(0.0F,
            exposure.dose - (now - exposure.lastTouched) * 0.00065F);
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
        state.surfaceIndex.put(anchor.key(), id);
        state.workQueue.addLast(id);
        return true;
    }

    private static boolean validSurface(final ServerLevel level,
        final FireSurfaceAnchor anchor, final boolean allowSurfaceFlame) {
        if (!isLoaded(level, anchor.host())
            || !level.getWorldBorder().isWithinBounds(anchor.host())
            || level.getFluidState(anchor.host()).is(FluidTags.WATER)) return false;
        FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(anchor.host()));
        if (!profile.flammable() && !allowSurfaceFlame) return false;
        BlockPos outside = anchor.host().relative(anchor.face());
        return isLoaded(level, outside) && level.getBlockState(outside).isAir();
    }

    private static Direction bestExposedFace(final ServerLevel level, final BlockPos host,
        final Vec3 source) {
        Direction best = null;
        double bestScore = -Double.MAX_VALUE;
        Vec3 center = Vec3.atCenterOf(host);
        Vec3 towardSource = source.subtract(center);
        for (Direction face : DIRECTIONS) {
            BlockPos outside = host.relative(face);
            if (!isLoaded(level, outside) || !level.getBlockState(outside).isAir()) continue;
            double score = towardSource.x * face.getStepX() + towardSource.y * face.getStepY()
                + towardSource.z * face.getStepZ();
            if (score > bestScore) { bestScore = score; best = face; }
        }
        return best;
    }

    private static void spawnEmber(final ServerLevel level, final LevelState state,
        final Patch patch, final long now) {
        Vec3 wind = FireWindEngine.windAt(level, patch.anchor.position());
        long seed = mix(patch.seed ^ now ^ state.embers.size());
        SplittableRandom random = new SplittableRandom(seed);
        Vec3 velocity = new Vec3(wind.x * random.nextDouble(0.55, 1.25)
            + random.nextDouble(-0.035, 0.035), random.nextDouble(0.055, 0.13),
            wind.z * random.nextDouble(0.55, 1.25) + random.nextDouble(-0.035, 0.035));
        state.embers.addLast(new Ember(patch.anchor.position().add(0.0, 0.12, 0.0),
            velocity, patch.targetIntensity, seed, now, random.nextInt(38, 86)));
    }

    private static void tickEmbers(final ServerLevel level, final LevelState state, final long now) {
        Iterator<Ember> iterator = state.embers.iterator();
        while (iterator.hasNext()) {
            Ember ember = iterator.next();
            if (now - ember.startTick >= ember.lifetime) { iterator.remove(); continue; }
            Vec3 wind = FireWindEngine.windAt(level, ember.position);
            ember.velocity = ember.velocity.scale(0.97).add(wind.scale(0.032)).add(0.0, -0.006, 0.0);
            Vec3 next = ember.position.add(ember.velocity);
            BlockPos host = BlockPos.containing(next);
            if (!isLoaded(level, host)) { iterator.remove(); continue; }
            BlockState stateAt = level.getBlockState(host);
            if (!stateAt.isAir()) {
                FireFuelProfile profile = FireFuelProfile.of(stateAt);
                if (profile.flammable()) {
                    Direction face = oppositeDominant(ember.velocity);
                    FireSurfaceAnchor anchor = FireSurfaceAnchor.center(host, face);
                    addPreheat(level, state, anchor,
                        0.18F + ember.intensity * profile.emberSusceptibility() * 0.48F,
                        ember.intensity, ember.seed, now, profile);
                }
                iterator.remove();
            } else ember.position = next;
        }
    }

    private static Direction oppositeDominant(final Vec3 velocity) {
        double ax = Math.abs(velocity.x), ay = Math.abs(velocity.y), az = Math.abs(velocity.z);
        if (ay >= ax && ay >= az) return velocity.y > 0.0 ? Direction.DOWN : Direction.UP;
        if (ax >= az) return velocity.x > 0.0 ? Direction.WEST : Direction.EAST;
        return velocity.z > 0.0 ? Direction.NORTH : Direction.SOUTH;
    }

    private static void removePatch(final LevelState state, final Patch patch) {
        state.patches.remove(patch.id);
        state.surfaceIndex.remove(patch.anchor.key());
    }

    private static void removeHostPatches(final LevelState state, final BlockPos host) {
        List<Patch> matches = new ArrayList<>();
        for (Patch candidate : state.patches.values())
            if (candidate.anchor.host().equals(host)) matches.add(candidate);
        for (Patch candidate : matches) removePatch(state, candidate);
    }

    private static void sendSnapshot(final ServerLevel level, final LevelState state) {
        List<FireCellSnapshot> snapshots = new ArrayList<>(state.patches.size());
        for (Patch patch : state.patches.values()) {
            FireFuelProfile profile = FireFuelProfile.of(level.getBlockState(patch.anchor.host()));
            float smoke = smokeProduction(patch, profile);
            snapshots.add(new FireCellSnapshot(patch.id, patch.anchor,
                patch.targetIntensity, patch.heat, patch.coverage, smoke, patch.phase,
                patch.seed, patch.ignitionGameTime,
                FireWindEngine.windAt(level, patch.anchor.position())));
        }
        FireNetworking.sendSnapshot(level, List.copyOf(snapshots));
    }

    private static void checkpoint(final ServerLevel level, final LevelState state) {
        if (state == null || state.patches.isEmpty()) {
            if (!FireSavedData.get(level).entries().isEmpty())
                FireSavedData.get(level).replace(List.of());
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
        FireSavedData.get(level).replace(entries);
    }

    private static void restore(final ServerLevel level) {
        List<FireSavedData.Entry> saved = FireSavedData.get(level).entries();
        if (saved.isEmpty()) return;
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
            state.workQueue.addLast(id);
        }
        if (!state.patches.isEmpty()) LEVELS.put(level, state);
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
        level.playSound(null, patch.anchor.host(), SoundEvents.CAMPFIRE_CRACKLE,
            SoundSource.BLOCKS, 0.20F + patch.targetIntensity * 0.42F,
            0.84F + (float) unit(patch.seed ^ now) * 0.34F);
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

    private static void decayWetness(final LevelState state) {
        Iterator<Map.Entry<Long, Float>> iterator = state.wetness.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Float> entry = iterator.next();
            float remaining = entry.getValue() - 0.006F;
            if (remaining <= 0.0F) iterator.remove(); else entry.setValue(remaining);
        }
    }

    private static void decayPreheat(final LevelState state, final long now) {
        Iterator<Map.Entry<SurfaceKey, Exposure>> iterator = state.preheat.entrySet().iterator();
        while (iterator.hasNext()) {
            Exposure exposure = iterator.next().getValue();
            if (now - exposure.lastTouched > 240L || exposure.dose <= 0.005F) iterator.remove();
        }
    }

    private static void addWetness(final LevelState state, final long key, final float amount,
        final boolean priority) {
        Float existing = state.wetness.get(key);
        if (existing == null && state.wetness.size() >= MAX_WET_POSITIONS) {
            if (!priority) return;
            Iterator<Long> iterator = state.wetness.keySet().iterator();
            if (iterator.hasNext()) { iterator.next(); iterator.remove(); }
        }
        state.wetness.put(key, Math.min(1.5F, (existing == null ? 0.0F : existing) + amount));
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
        private final ArrayDeque<Long> workQueue = new ArrayDeque<>();
        private final HashMap<Long, Float> wetness = new HashMap<>();
        private final HashMap<SurfaceKey, Exposure> preheat = new HashMap<>();
        private final ArrayDeque<Ember> embers = new ArrayDeque<>();
        private long nextId = 1L;
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
        private Vec3 position;
        private Vec3 velocity;
        private final float intensity;
        private final long seed;
        private final long startTick;
        private final int lifetime;
        private Ember(final Vec3 position, final Vec3 velocity, final float intensity,
            final long seed, final long startTick, final int lifetime) {
            this.position = position; this.velocity = velocity; this.intensity = intensity;
            this.seed = seed; this.startTick = startTick; this.lifetime = lifetime;
        }
    }

    private record Exposure(float dose, long lastTouched) { }
    private record RankedSurface(FireSurfaceAnchor anchor, double distance, long rank) { }
}
