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
        /* Coherent regional wind: direction evolves over roughly a minute while
           neighbouring fires still share a readable plume direction. */
        double time = level.getGameTime() * 0.0032;
        double spatial = position.x * 0.0090 + position.z * 0.0070;
        double angle = time + Math.sin(spatial) * 0.78
            + Math.cos(position.z * 0.0060 - time * 0.47) * 0.34;
        double gust = 0.5 + 0.5 * Math.sin(position.x * 0.012
            - position.z * 0.009 + level.getGameTime() * 0.017);
        double speed = 0.15 + 0.17 * gust;
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
