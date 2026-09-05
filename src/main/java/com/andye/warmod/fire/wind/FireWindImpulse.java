package com.andye.warmod.fire.wind;

import net.minecraft.world.phys.Vec3;

/** Immutable pressure pulse shared by authoritative fire and client visual advection. */
public record FireWindImpulse(Vec3 center, double radius, double strength,
    long startTick, int durationTicks, boolean nuclear) {
    public FireWindImpulse(Vec3 center, double radius, double strength,
        long startTick, int durationTicks) {
        this(center, radius, strength, startTick, durationTicks, false);
    }
    public FireWindImpulse {
        if (center == null || !center.isFinite() || !Double.isFinite(radius)
            || !Double.isFinite(strength) || radius <= 0.0 || strength <= 0.0
            || durationTicks <= 0) throw new IllegalArgumentException("Invalid fire wind impulse");
    }

    public boolean expired(final long now) {
        return now > startTick + effectiveDuration();
    }

    public long effectiveDuration() {
        double travelTicks = radius / 17.15;
        double pulseWidth = pulseWidth();
        if (!nuclear) return (long)Math.ceil(travelTicks + pulseWidth);
        double returnStart = Math.max(travelTicks + pulseWidth, durationTicks * 0.62);
        return (long) Math.ceil(Math.max(durationTicks,
            returnStart + travelTicks + pulseWidth * 1.18));
    }

    public Vec3 sample(final Vec3 position, final double now) {
        if (position == null || !position.isFinite()) return Vec3.ZERO;
        Vec3 delta = position.subtract(center);
        double distance = delta.length();
        if (distance < 0.05 || distance >= radius) return Vec3.ZERO;
        double elapsed = now - startTick;
        double shockSpeed = 17.15;
        double travelTicks = radius / shockSpeed;
        double pulseWidth = pulseWidth();
        double outwardAge = elapsed - distance / shockSpeed;
        double temporal = pulse(outwardAge, pulseWidth);
        double returnStart = Math.max(travelTicks + pulseWidth, durationTicks * 0.62);
        double returnAge = elapsed - returnStart - (radius - distance) / shockSpeed;
        if (nuclear) temporal -= pulse(returnAge, pulseWidth * 1.18) * 0.56;
        if (Math.abs(temporal) < 1.0E-5) return Vec3.ZERO;
        double falloff = 0.35 + 0.65 * (1.0 - distance / radius);
        return delta.scale(1.0 / distance).scale(strength * temporal * falloff);
    }

    private double pulseWidth() {
        return nuclear ? Math.max(100.0, Math.min(180.0, durationTicks * 0.48))
            : Math.max(60.0, Math.min(120.0, durationTicks));
    }

    private static double pulse(final double age, final double width) {
        if (age < 0.0 || age >= width) return 0.0;
        double normalized = age / width;
        return Math.sin(normalized * Math.PI) * (1.0 - normalized * 0.35);
    }
}
