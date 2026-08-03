package com.andye.warmod.antiair;

import net.minecraft.world.phys.Vec3;

public final class AntiAirTrajectory {
    private AntiAirTrajectory() { }

    public static Vec3 boostPosition(AntiAirFlightPlan plan, double elapsed) {
        return boostPosition(plan.launchPosition(), plan.burnoutPosition(), plan.noTargetHorizontalOffset(),
            plan.ignitionTicks(), plan.boostTicks(), elapsed);
    }

    public static Vec3 boostVelocity(AntiAirFlightPlan plan, double elapsed) {
        return boostVelocity(plan.launchPosition(), plan.burnoutPosition(), plan.noTargetHorizontalOffset(),
            plan.ignitionTicks(), plan.boostTicks(), elapsed);
    }

    public static Vec3 boostPosition(Vec3 launch, Vec3 burnout, Vec3 noTargetOffset, int ignitionTicks,
        int boostTicks, double elapsed) {
        double relative = Math.max(0.0, Math.min(boostTicks, elapsed - ignitionTicks));
        if (noTargetOffset.lengthSqr() > 1.0E-8) return noTargetAscentRoute(launch, burnout, boostTicks).position(relative);
        double u = relative / boostTicks;
        u = u * u * (3.0 - 2.0 * u);
        return launch.lerp(burnout, u);
    }

    public static Vec3 boostVelocity(Vec3 launch, Vec3 burnout, Vec3 noTargetOffset, int ignitionTicks,
        int boostTicks, double elapsed) {
        if (noTargetOffset.lengthSqr() > 1.0E-8)
            return noTargetAscentRoute(launch, burnout, boostTicks).velocity(elapsed - ignitionTicks);
        return boostPosition(launch, burnout, noTargetOffset, ignitionTicks, boostTicks, elapsed + 0.25)
            .subtract(boostPosition(launch, burnout, noTargetOffset, ignitionTicks, boostTicks, elapsed - 0.25)).scale(2.0);
    }

    /** A slight off-axis ascent: vertical initial tangent, gradual drift, and a fixed offset apex. */
    public static AntiAirRoute noTargetAscentRoute(Vec3 launch, Vec3 burnout, int boostTicks) {
        double vertical = Math.max(16.0, burnout.y - launch.y);
        Vec3 offset = new Vec3(burnout.x - launch.x, 0.0, burnout.z - launch.z);
        Vec3 first = launch.add(0.0, vertical * 0.72, 0.0);
        Vec3 second = burnout.subtract(offset.scale(0.18)).add(0.0, -vertical * 0.24, 0.0);
        return new AntiAirRoute(launch, first, second, burnout, boostTicks);
    }
}