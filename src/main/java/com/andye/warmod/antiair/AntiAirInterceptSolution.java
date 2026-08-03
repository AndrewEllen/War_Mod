package com.andye.warmod.antiair;

import net.minecraft.world.phys.Vec3;

/** Immutable fixed-route result selected before an interceptor launches. */
public record AntiAirInterceptSolution(
    long interceptGameTime,
    Vec3 perfectInterceptPosition,
    Vec3 targetVelocityAtIntercept,
    AntiAirRoute nominalRoute,
    double requiredAverageSpeed,
    double requiredPeakSpeed,
    boolean feasible,
    boolean bestEffort,
    boolean rangeLimited,
    double originalArcLength,
    double poweredArcLength
) {
    public AntiAirInterceptSolution {
        if (!perfectInterceptPosition.isFinite() || !targetVelocityAtIntercept.isFinite()
            || !Double.isFinite(requiredAverageSpeed) || !Double.isFinite(requiredPeakSpeed)
            || !Double.isFinite(originalArcLength) || !Double.isFinite(poweredArcLength)
            || originalArcLength < poweredArcLength || poweredArcLength <= 0.0 || feasible == bestEffort)
            throw new IllegalArgumentException("Invalid anti-air interception solution");
    }
}