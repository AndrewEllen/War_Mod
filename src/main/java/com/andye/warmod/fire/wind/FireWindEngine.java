package com.andye.warmod.fire.wind;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Spatial wind queried only by the custom fire subsystem. */
public final class FireWindEngine {
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
        /* A slowly evolving prevailing field gives smoke enough coherent travel
           time to show direction. Local gusts vary strength without rotating the
           plume every few seconds. Values are blocks/tick for fire advection. */
        double time = level.getGameTime() * 0.00042;
        double spatial = position.x * 0.0016 + position.z * 0.0012;
        double angle = time + Math.sin(spatial) * 0.72
            + Math.cos(position.z * 0.0011 - time * 0.31) * 0.26;
        double gust = 0.5 + 0.5 * Math.sin(position.x * 0.0032
            - position.z * 0.0027 + level.getGameTime() * 0.0034);
        double speed = 0.14 + 0.15 * gust;
        Vec3 result = new Vec3(Math.cos(angle) * speed, 0.0,
            Math.sin(angle) * speed);

        ArrayDeque<WindImpulse> impulses = IMPULSES.get(level);
        if (impulses != null) {
            long now = level.getGameTime();
            for (WindImpulse impulse : impulses) result = result.add(impulse.sample(position, now));
        }
        double length = result.length();
        return length > 2.5 ? result.scale(2.5 / length) : result;
    }

    private record WindImpulse(Vec3 center, double radius, double strength,
        long startTick, int durationTicks) {
        private boolean expired(final long now) { return now > startTick + durationTicks; }

        private Vec3 sample(final Vec3 position, final long now) {
            Vec3 delta = position.subtract(center);
            double distance = delta.length();
            if (distance < 0.05 || distance >= radius) return Vec3.ZERO;
            double age = Mth.clamp((now - startTick) / (double) durationTicks, 0.0, 1.0);
            double temporal;
            if (age < 0.58) {
                double outward = 1.0 - age / 0.58;
                temporal = outward * outward;
            } else {
                double returning = 1.0 - (age - 0.58) / 0.42;
                temporal = -0.34 * Math.max(0.0, returning);
            }
            double falloff = 1.0 - distance / radius;
            falloff *= falloff;
            return delta.scale(1.0 / distance).scale(strength * temporal * falloff);
        }
    }
}
