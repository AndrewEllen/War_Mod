package com.andye.warmod.fire.wind;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Spatial wind queried only by the custom fire subsystem. */
public final class FireWindEngine {
    private static final int WIND_CELL_SIZE = 48;
    private static final long WIND_EPOCH_TICKS = 1_800L;
    private static final Map<ServerLevel, ArrayDeque<WindImpulse>> IMPULSES =
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
        ArrayDeque<WindImpulse> impulses = IMPULSES.get(level);
        if (impulses == null) return;
        long now = level.getGameTime();
        Iterator<WindImpulse> iterator = impulses.iterator();
        while (iterator.hasNext()) if (iterator.next().expired(now)) iterator.remove();
        if (impulses.isEmpty()) IMPULSES.remove(level);
    }

    public static synchronized void addExplosionImpulse(final ServerLevel level,
        final Vec3 center, final double radius, final double strength,
        final int durationTicks) {
        if (level == null || center == null || !center.isFinite() || radius <= 0.0
            || strength <= 0.0 || durationTicks <= 0) return;
        ArrayDeque<WindImpulse> impulses = IMPULSES.computeIfAbsent(level,
            ignored -> new ArrayDeque<>());
        while (impulses.size() >= 32) impulses.removeFirst();
        impulses.addLast(new WindImpulse(center, radius, strength,
            level.getGameTime(), durationTicks));
    }

    public static synchronized Vec3 windAt(final ServerLevel level, final Vec3 position) {
        if (level == null || position == null || !position.isFinite()) return Vec3.ZERO;
        /* Wind is spatially local rather than one world-wide rotating vector.
           Each broad cell owns a persistent direction, blending gently into its
           next random state only every ninety seconds.  Bilinear interpolation
           keeps neighbouring forests related while allowing different valleys
           to blow in genuinely different directions. */
        long epoch = Math.floorDiv(level.getGameTime(), WIND_EPOCH_TICKS);
        double epochProgress = (level.getGameTime() - epoch * (double) WIND_EPOCH_TICKS)
            / WIND_EPOCH_TICKS;
        Vec3 present = sampledField(level.getSeed(), position, epoch);
        Vec3 future = sampledField(level.getSeed(), position, epoch + 1L);
        Vec3 result = present.lerp(future, smoothstep(epochProgress));

        ArrayDeque<WindImpulse> impulses = IMPULSES.get(level);
        if (impulses != null) {
            long now = level.getGameTime();
            for (WindImpulse impulse : impulses) result = result.add(impulse.sample(position, now));
        }
        double length = result.length();
        return length > 2.5 ? result.scale(2.5 / length) : result;
    }

    private static Vec3 sampledField(final long levelSeed, final Vec3 position,
        final long epoch) {
        double cellX = position.x / WIND_CELL_SIZE;
        double cellZ = position.z / WIND_CELL_SIZE;
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
        double speed = 0.12 + unit(value ^ 0x475553545F535045L) * 0.23;
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

    private record WindImpulse(Vec3 center, double radius, double strength,
        long startTick, int durationTicks) {
        private boolean expired(final long now) {
            double travelTicks = radius / 17.15;
            double pulseWidth = Math.max(5.0, Math.min(14.0, durationTicks * 0.13));
            double returnStart = Math.max(travelTicks + 6.0, durationTicks * 0.56);
            long effectiveDuration = (long) Math.ceil(Math.max(durationTicks,
                returnStart + travelTicks + pulseWidth * 1.18));
            return now > startTick + effectiveDuration;
        }

        private Vec3 sample(final Vec3 position, final long now) {
            Vec3 delta = position.subtract(center);
            double distance = delta.length();
            if (distance < 0.05 || distance >= radius) return Vec3.ZERO;
            double elapsed = now - startTick;
            double shockSpeed = 17.15;
            double travelTicks = radius / shockSpeed;
            double pulseWidth = Math.max(5.0, Math.min(14.0, durationTicks * 0.13));
            double outwardAge = elapsed - distance / shockSpeed;
            double temporal = pulse(outwardAge, pulseWidth);
            double returnStart = Math.max(travelTicks + 6.0, durationTicks * 0.56);
            double returnAge = elapsed - returnStart - (radius - distance) / shockSpeed;
            temporal -= pulse(returnAge, pulseWidth * 1.18) * 0.42;
            if (Math.abs(temporal) < 1.0E-5) return Vec3.ZERO;
            double falloff = 0.35 + 0.65 * (1.0 - distance / radius);
            return delta.scale(1.0 / distance).scale(strength * temporal * falloff);
        }

        private static double pulse(final double age, final double width) {
            if (age < 0.0 || age >= width) return 0.0;
            double normalized = age / width;
            return Math.sin(normalized * Math.PI) * (1.0 - normalized * 0.35);
        }
    }
}
