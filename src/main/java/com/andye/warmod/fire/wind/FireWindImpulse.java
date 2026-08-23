package com.andye.warmod.fire.wind;

import net.minecraft.world.phys.Vec3;

/** Immutable pressure pulse shared by authoritative fire and client visual advection. */
public record FireWindImpulse(Vec3 center, double radius, double strength,
    long startTick, int durationTicks) {
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
        double pulseWidth = Math.max(5.0, Math.min(14.0, durationTicks * 0.13));
        double returnStart = Math.max(travelTicks + 6.0, durationTicks * 0.56);
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
