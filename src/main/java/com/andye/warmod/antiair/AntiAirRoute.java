package com.andye.warmod.antiair;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** A fixed cubic Bezier powered route with deterministic arc-length operations. */
public record AntiAirRoute(Vec3 start, Vec3 controlPoint1, Vec3 controlPoint2, Vec3 end, int durationTicks) {
    private static final int ARC_SAMPLES = 64;

    public AntiAirRoute {
        Objects.requireNonNull(start); Objects.requireNonNull(controlPoint1);
        Objects.requireNonNull(controlPoint2); Objects.requireNonNull(end);
        if (!start.isFinite() || !controlPoint1.isFinite() || !controlPoint2.isFinite() || !end.isFinite()
            || durationTicks <= 0) throw new IllegalArgumentException("Invalid anti-air route");
    }

    public Vec3 position(double elapsed) { return point(clamp(elapsed / durationTicks)); }

    public Vec3 velocity(double elapsed) { return derivative(clamp(elapsed / durationTicks)).scale(1.0 / durationTicks); }

    public double arcLength() { return arcLengthTo(1.0); }

    public double arcLengthTo(double normalizedParameter) {
        double endParameter = clamp(normalizedParameter);
        if (endParameter <= 0.0) return 0.0;
        Vec3 previous = point(0.0);
        double length = 0.0;
        for (int sample = 1; sample <= ARC_SAMPLES; sample++) {
            Vec3 next = point(endParameter * sample / ARC_SAMPLES);
            length += previous.distanceTo(next);
            previous = next;
        }
        return length;
    }

    public double parameterAtArcLength(double requestedLength) {
        if (!Double.isFinite(requestedLength) || requestedLength <= 0.0) return 0.0;
        double total = arcLength();
        if (requestedLength >= total) return 1.0;
        double low = 0.0, high = 1.0;
        for (int iteration = 0; iteration < 12; iteration++) {
            double middle = (low + high) * 0.5;
            if (arcLengthTo(middle) < requestedLength) low = middle;
            else high = middle;
        }
        return (low + high) * 0.5;
    }

    /** Returns the left De Casteljau segment, preserving the original initial tangent. */
    public AntiAirRoute truncatedAtArcLength(double maximumLength) {
        if (!Double.isFinite(maximumLength) || maximumLength <= 0.0)
            throw new IllegalArgumentException("Invalid anti-air route length");
        if (maximumLength >= arcLength()) return this;
        double parameter = parameterAtArcLength(maximumLength);
        Vec3 p01 = start.lerp(controlPoint1, parameter);
        Vec3 p12 = controlPoint1.lerp(controlPoint2, parameter);
        Vec3 p23 = controlPoint2.lerp(end, parameter);
        Vec3 p012 = p01.lerp(p12, parameter);
        Vec3 p123 = p12.lerp(p23, parameter);
        Vec3 p0123 = p012.lerp(p123, parameter);
        return new AntiAirRoute(start, p01, p012, p0123, Math.max(1, (int)Math.ceil(durationTicks * parameter)));
    }

    public AntiAirRoute withDuration(int duration) {
        return new AntiAirRoute(start, controlPoint1, controlPoint2, end, duration);
    }

    public int minimumDurationForSpeed(double maximumSpeed) {
        if (!Double.isFinite(maximumSpeed) || maximumSpeed <= 0.0)
            throw new IllegalArgumentException("Invalid maximum speed");
        double peakPerTickAtOne = 0.0;
        for (int sample = 0; sample <= ARC_SAMPLES; sample++)
            peakPerTickAtOne = Math.max(peakPerTickAtOne, derivative(sample / (double)ARC_SAMPLES).length());
        return Math.max(1, (int)Math.ceil(Math.max(arcLength(), peakPerTickAtOne) / maximumSpeed));
    }

    private Vec3 point(double u) {
        double v = 1.0 - u;
        return start.scale(v * v * v)
            .add(controlPoint1.scale(3.0 * v * v * u))
            .add(controlPoint2.scale(3.0 * v * u * u))
            .add(end.scale(u * u * u));
    }

    private Vec3 derivative(double u) {
        double v = 1.0 - u;
        return controlPoint1.subtract(start).scale(3.0 * v * v)
            .add(controlPoint2.subtract(controlPoint1).scale(6.0 * v * u))
            .add(end.subtract(controlPoint2).scale(3.0 * u * u));
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}