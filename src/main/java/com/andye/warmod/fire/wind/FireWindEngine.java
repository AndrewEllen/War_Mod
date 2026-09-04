package com.andye.warmod.fire.wind;

import com.andye.warmod.fire.network.FireNetworking;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Spatial wind queried only by the custom fire subsystem. */
public final class FireWindEngine {
    private static final int MACRO_WIND_CELL_SIZE = 96;
    private static final int LOCAL_WIND_CELL_SIZE = 40;
    private static final long MACRO_WIND_EPOCH_TICKS = 1_200L;
    private static final long LOCAL_WIND_EPOCH_TICKS = 420L;
    private static final Map<ServerLevel, ArrayDeque<FireWindImpulse>> IMPULSES =
        new IdentityHashMap<>();
    private static boolean registered;

    private FireWindEngine() { }

    public static synchronized void registerLifecycle() {
        if (registered) return;
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            synchronized (FireWindEngine.class) { IMPULSES.remove(level); }
        });
        registered = true;
    }

    public static synchronized void clearAll() { IMPULSES.clear(); }

    public static synchronized void tick(final ServerLevel level) {
        ArrayDeque<FireWindImpulse> impulses = IMPULSES.get(level);
        if (impulses == null) return;
        long now = level.getGameTime();
        Iterator<FireWindImpulse> iterator = impulses.iterator();
        while (iterator.hasNext()) if (iterator.next().expired(now)) iterator.remove();
        if (impulses.isEmpty()) IMPULSES.remove(level);
    }

    public static synchronized void addExplosionImpulse(final ServerLevel level,
        final Vec3 center, final double radius, final double strength,
        final int durationTicks) {
        if (level == null || center == null || !center.isFinite() || radius <= 0.0
            || strength <= 0.0 || durationTicks <= 0) return;
        ArrayDeque<FireWindImpulse> impulses = IMPULSES.computeIfAbsent(level,
            ignored -> new ArrayDeque<>());
        while (impulses.size() >= 32) impulses.removeFirst();
        FireWindImpulse impulse = new FireWindImpulse(center, radius, strength,
            level.getGameTime(), durationTicks);
        impulses.addLast(impulse);
        FireNetworking.sendWindImpulse(level, impulse);
    }

    public static synchronized Vec3 windAt(final ServerLevel level, final Vec3 position) {
        if (level == null || position == null || !position.isFinite()) return Vec3.ZERO;
        Vec3 result = ambientWindAt(level, position);
        ArrayDeque<FireWindImpulse> impulses = IMPULSES.get(level);
        if (impulses != null) {
            long now = level.getGameTime();
            for (FireWindImpulse impulse : impulses) result = result.add(impulse.sample(position, now));
        }
        double length = result.length();
        return length > 2.5 ? result.scale(2.5 / length) : result;
    }

    /** Snapshots carry ambient wind; timestamped impulses are applied once at the client. */
    public static Vec3 ambientWindAt(final ServerLevel level, final Vec3 position) {
        if (level == null || position == null || !position.isFinite()) return Vec3.ZERO;
        /* Wind is spatially local rather than one world-wide rotating vector.
           Each broad cell owns a persistent direction, blending gently into its
           next random state only every ninety seconds.  Bilinear interpolation
           keeps neighbouring forests related while allowing different valleys
           to blow in genuinely different directions. */
        long time = level.getGameTime();
        Vec3 macro = temporalField(level.getSeed(), position, time,
            MACRO_WIND_CELL_SIZE, MACRO_WIND_EPOCH_TICKS, 0x4D4143524F5F5749L);
        Vec3 local = temporalField(level.getSeed(), position, time,
            LOCAL_WIND_CELL_SIZE, LOCAL_WIND_EPOCH_TICKS, 0x4C4F43414C5F5749L);
        return macro.scale(0.72).add(local.scale(0.58));
    }

    /** Only the inner outward blast can strip a flame from its fuel surface. */
    public static synchronized double blowoutAt(final ServerLevel level, final Vec3 position) {
        ArrayDeque<FireWindImpulse> impulses = IMPULSES.get(level);
        if (impulses == null) return 0.0;
        double exposure = 0.0;
        for (FireWindImpulse impulse : impulses) {
            Vec3 delta = position.subtract(impulse.center());
            if (delta.lengthSqr() > impulse.radius() * impulse.radius() * 0.0225) continue;
            Vec3 pressure = impulse.sample(position, level.getGameTime());
            if (pressure.dot(delta) > 0.0) exposure = Math.max(exposure, pressure.length());
        }
        return exposure;
    }

    private static Vec3 temporalField(final long levelSeed, final Vec3 position,
        final long time, final int cellSize, final long epochTicks, final long salt) {
        long epoch = Math.floorDiv(time, epochTicks);
        double progress = (time - epoch * (double) epochTicks) / epochTicks;
        Vec3 present = sampledField(levelSeed ^ salt, position, epoch, cellSize);
        Vec3 future = sampledField(levelSeed ^ salt, position, epoch + 1L, cellSize);
        return present.lerp(future, smoothstep(progress));
    }

    private static Vec3 sampledField(final long levelSeed, final Vec3 position,
        final long epoch, final int cellSize) {
        double cellX = position.x / cellSize;
        double cellZ = position.z / cellSize;
        int baseX = (int) Math.floor(cellX);
        int baseZ = (int) Math.floor(cellZ);
        double x = smoothstep(cellX - baseX);
        double z = smoothstep(cellZ - baseZ);
        Vec3 southWest = cellWind(levelSeed, baseX, baseZ, epoch);
        Vec3 southEast = cellWind(levelSeed, baseX + 1, baseZ, epoch);
        Vec3 northWest = cellWind(levelSeed, baseX, baseZ + 1, epoch);
        Vec3 northEast = cellWind(levelSeed, baseX + 1, baseZ + 1, epoch);
        return southWest.lerp(southEast, x).lerp(northWest.lerp(northEast, x), z);
    }

    private static Vec3 cellWind(final long levelSeed, final int cellX, final int cellZ,
        final long epoch) {
        long value = mix(levelSeed ^ ((long) cellX * 0x9E3779B97F4A7C15L)
            ^ ((long) cellZ * 0xC2B2AE3D27D4EB4FL)
            ^ (epoch * 0xD1B54A32D192ED03L));
        double angle = unit(value) * Math.PI * 2.0;
        double speed = 0.24 + unit(value ^ 0x475553545F535045L) * 0.42;
        return new Vec3(Math.cos(angle) * speed, 0.0, Math.sin(angle) * speed);
    }

    private static double smoothstep(final double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double unit(final long value) {
        return (mix(value) >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

}
