package com.andye.warmod.icbm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Authoritative carrier route: vertical ignition/boost followed by a bounded smooth midcourse curve. */
public final class IcbmTrajectory {
    private IcbmTrajectory() { }
    public static IcbmFlightPhase phase(IcbmFlightPlan plan, double ticks) {
        if (ticks < plan.ignitionTicks()) return IcbmFlightPhase.IGNITION;
        if (ticks < plan.ignitionTicks() + plan.boostTicks()) return IcbmFlightPhase.POWERED_ASCENT;
        if (ticks < plan.separationTick()) return IcbmFlightPhase.BALLISTIC_COAST;
        return IcbmFlightPhase.SEPARATED;
    }
    public static boolean thrustActive(IcbmFlightPlan plan, double ticks) { return ticks >= 0 && ticks < plan.ignitionTicks() + plan.boostTicks(); }
    public static Vec3 position(IcbmFlightPlan plan, double elapsed) {
        double ticks = Math.max(0, Math.min(elapsed, plan.separationTick()));
        if (ticks < plan.ignitionTicks()) { double u = ticks / plan.ignitionTicks(); return plan.launchPosition().add(0, .5 * u * u, 0); }
        if (ticks < plan.ignitionTicks() + plan.boostTicks()) return powered(plan, (ticks - plan.ignitionTicks()) / plan.boostTicks());
        return coast(plan, (ticks - plan.ignitionTicks() - plan.boostTicks()) / plan.coastTicks());
    }
    public static Vec3 coastInitialVelocity(IcbmFlightPlan plan) {
        return coastVelocity(plan, 0.0);
    }
    public static Vec3 velocity(IcbmFlightPlan plan, double elapsed) {
        double ticks = Math.max(0, Math.min(elapsed, plan.separationTick()));
        if (ticks < plan.ignitionTicks()) { double u = ticks / plan.ignitionTicks(); return new Vec3(0, u / plan.ignitionTicks(), 0); }
        if (ticks < plan.ignitionTicks() + plan.boostTicks()) return poweredVelocity(plan, (ticks - plan.ignitionTicks()) / plan.boostTicks());
        return coastVelocity(plan, (ticks - plan.ignitionTicks() - plan.boostTicks()) / plan.coastTicks());
    }
    private static Vec3 powered(IcbmFlightPlan plan, double raw) {
        double u = Mth.clamp(raw, 0, 1), u2 = u * u, u3 = u2 * u;
        Vec3 a = plan.launchPosition().add(0, .5, 0), b = plan.burnoutPosition();
        Vec3 m0 = new Vec3(0, .55 * plan.boostTicks(), 0), m1 = coastInitialVelocity(plan).scale(plan.boostTicks());
        return a.scale(2 * u3 - 3 * u2 + 1).add(m0.scale(u3 - 2 * u2 + u)).add(b.scale(-2 * u3 + 3 * u2)).add(m1.scale(u3 - u2));
    }
    private static Vec3 poweredVelocity(IcbmFlightPlan plan, double raw) {
        double u = Mth.clamp(raw, 0, 1), u2 = u * u;
        Vec3 a = plan.launchPosition().add(0, .5, 0), b = plan.burnoutPosition();
        Vec3 m0 = new Vec3(0, .55 * plan.boostTicks(), 0), m1 = coastInitialVelocity(plan).scale(plan.boostTicks());
        return a.scale(6 * u2 - 6 * u).add(m0.scale(3 * u2 - 4 * u + 1)).add(b.scale(-6 * u2 + 6 * u))
            .add(m1.scale(3 * u2 - 2 * u)).scale(1.0 / plan.boostTicks());
    }
    private static Vec3 coast(IcbmFlightPlan plan, double raw) {
        double u = Mth.clamp(raw, 0, 1), u2 = u * u, u3 = u2 * u;
        Vec3 p0 = plan.burnoutPosition(), p1 = coastControlOne(plan), p2 = coastControlTwo(plan), p3 = plan.separationPosition();
        return p0.scale(1 - 3 * u + 3 * u2 - u3).add(p1.scale(3 * u - 6 * u2 + 3 * u3))
            .add(p2.scale(3 * u2 - 3 * u3)).add(p3.scale(u3));
    }
    private static Vec3 coastVelocity(IcbmFlightPlan plan, double raw) {
        double u = Mth.clamp(raw, 0, 1); Vec3 p0 = plan.burnoutPosition(), p1 = coastControlOne(plan), p2 = coastControlTwo(plan), p3 = plan.separationPosition();
        return p1.subtract(p0).scale(3 * (1 - u) * (1 - u)).add(p2.subtract(p1).scale(6 * (1 - u) * u))
            .add(p3.subtract(p2).scale(3 * u * u)).scale(1.0 / plan.coastTicks());
    }
    private static Vec3 coastControlOne(IcbmFlightPlan plan) { return plan.burnoutPosition().add(0, IcbmConstants.BOOST_ASCENT_CONTROL_DISTANCE, 0); }
    private static Vec3 coastControlTwo(IcbmFlightPlan plan) {
        Vec3 delta = plan.separationPosition().subtract(plan.burnoutPosition()); Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
        Vec3 approach = horizontal.lengthSqr() < 1.0E-8 ? Vec3.ZERO : horizontal.normalize().scale(
            Math.min(IcbmConstants.COAST_APPROACH_CONTROL_DISTANCE, horizontal.length() * .20));
        return plan.separationPosition().subtract(approach).add(0, IcbmConstants.COAST_TERMINAL_CONTROL_HEIGHT, 0);
    }
}