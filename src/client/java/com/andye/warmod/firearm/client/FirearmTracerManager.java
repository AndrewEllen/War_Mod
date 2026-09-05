package com.andye.warmod.firearm.client;

import com.andye.warmod.firearm.FirearmType;
import com.andye.warmod.firearm.network.ClientboundFirearmShotPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class FirearmTracerManager {
    public record Tracer(UUID id, FirearmType type, Vec3 origin, Vec3 velocity,
        Vec3 acceleration, long seed, int maximumAge, double startTime,
        Vec3 impact, double impactTime) {
        public Vec3 position(final double age) {
            double t = Mth.clamp(age, 0.0, maximumAge);
            return origin.add(velocity.scale(t)).add(acceleration.scale(t * (t - 1.0) * 0.5));
        }
    }
    private static final int MAX_TRACERS = 4_096;
    private static final Map<UUID, Tracer> ACTIVE = new LinkedHashMap<>();
    private FirearmTracerManager() { }
    public static synchronized void shot(final ClientboundFirearmShotPayload payload,
        final double now) {
        while (ACTIVE.size() >= MAX_TRACERS)
            ACTIVE.remove(ACTIVE.keySet().iterator().next());
        int ordinal = Mth.clamp(payload.firearmType(), 0,
            FirearmType.values().length - 1);
        ACTIVE.put(payload.shotId(), new Tracer(payload.shotId(),
            FirearmType.values()[ordinal], payload.origin(), payload.velocity(),
            payload.acceleration(), payload.visualSeed(), payload.maximumAge(), now,
            null, Double.NaN));
    }
    public static synchronized void impact(final UUID id, final Vec3 position,
        final double now) {
        Tracer tracer = ACTIVE.get(id);
        if (tracer != null) ACTIVE.put(id, new Tracer(tracer.id, tracer.type,
            tracer.origin, tracer.velocity, tracer.acceleration, tracer.seed,
            tracer.maximumAge, tracer.startTime, position, now));
    }
    public static synchronized List<Tracer> snapshot(final double now) {
        ACTIVE.values().removeIf(tracer -> tracer.impact != null
            ? now - tracer.impactTime > 4.0
            : now - tracer.startTime > tracer.maximumAge + 2.0);
        return List.copyOf(ACTIVE.values());
    }
    public static synchronized void clear() { ACTIVE.clear(); }
}
