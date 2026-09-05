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
        double u = relative / boostTicks;
        // Zero launch velocity, steady acceleration, and a non-zero vertical
        // velocity at burnout. The following route inherits a vertical tangent,
        // so there is no stop or snap when the missile begins to arc.
        double progress = u * u * (2.0 - u);
        return launch.lerp(burnout, progress);
    }

    public static Vec3 boostVelocity(Vec3 launch, Vec3 burnout, Vec3 noTargetOffset, int ignitionTicks,
        int boostTicks, double elapsed) {
        double relative = Math.max(0.0, Math.min(boostTicks, elapsed - ignitionTicks));
        double u = relative / boostTicks;
        double derivative = (4.0 * u - 3.0 * u * u) / boostTicks;
        return burnout.subtract(launch).scale(derivative);
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
