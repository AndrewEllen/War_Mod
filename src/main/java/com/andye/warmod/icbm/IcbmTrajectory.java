package com.andye.warmod.icbm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Authoritative carrier route with a continuous powered-ascent-to-coast arc. */
public final class IcbmTrajectory {
    private IcbmTrajectory() { }

    public static IcbmFlightPhase phase(final IcbmFlightPlan plan, final double ticks) {
        if (ticks < plan.ignitionTicks()) return IcbmFlightPhase.IGNITION;
        if (ticks < plan.ignitionTicks() + plan.boostTicks()) {
            return IcbmFlightPhase.POWERED_ASCENT;
        }
        if (ticks < plan.separationTick()) return IcbmFlightPhase.BALLISTIC_COAST;
        return IcbmFlightPhase.SEPARATED;
    }

    public static boolean thrustActive(final IcbmFlightPlan plan, final double ticks) {
        return ticks >= 0 && ticks < plan.ignitionTicks() + plan.boostTicks();
    }

    public static Vec3 position(final IcbmFlightPlan plan, final double elapsed) {
        double ticks = Math.max(0, Math.min(elapsed, plan.separationTick()));
        if (ticks < plan.ignitionTicks()) {
            double u = ticks / plan.ignitionTicks();
            return plan.launchPosition().add(0, ignitionRise(plan) * u * u, 0);
        }
        if (ticks < plan.ignitionTicks() + plan.boostTicks()) {
            return powered(plan, (ticks - plan.ignitionTicks()) / plan.boostTicks());
        }
        return coast(plan,
            (ticks - plan.ignitionTicks() - plan.boostTicks()) / plan.coastTicks());
    }

    public static Vec3 coastInitialVelocity(final IcbmFlightPlan plan) {
        return coastVelocity(plan, 0.0);
    }

    public static Vec3 velocity(final IcbmFlightPlan plan, final double elapsed) {
        double ticks = Math.max(0, Math.min(elapsed, plan.separationTick()));
        if (ticks < plan.ignitionTicks()) {
            double u = ticks / plan.ignitionTicks();
            return new Vec3(0, 2.0 * ignitionRise(plan) * u / plan.ignitionTicks(), 0);
        }
        if (ticks < plan.ignitionTicks() + plan.boostTicks()) {
            return poweredVelocity(plan,
                (ticks - plan.ignitionTicks()) / plan.boostTicks());
        }
        return coastVelocity(plan,
            (ticks - plan.ignitionTicks() - plan.boostTicks()) / plan.coastTicks());
    }

    /**
     * Fit a circular bend whose entry tangent is vertical and whose exit tangent
     * is the initial ballistic velocity. Unlike an unconstrained spline, its
     * heading can only turn down-range: there is no internal inflection.
     */
    static Vec3 alignedBurnout(final Vec3 launch, final Vec3 requested,
        final Vec3 separation) {
        Vec3 horizontal = new Vec3(separation.x - launch.x, 0, separation.z - launch.z);
        double distance = horizontal.length();
        if (distance < 1.0E-6) return new Vec3(launch.x, requested.y, launch.z);
        Vec3 direction = horizontal.scale(1.0 / distance);
        double rise = requested.y - curveStartY(launch, requested);
        double lead = 0.0;
        for (int iteration = 0; iteration < 32; iteration++) {
            Vec3 candidate = new Vec3(launch.x + direction.x * lead, requested.y,
                launch.z + direction.z * lead);
            double remaining = Math.max(1.0E-6, distance - lead);
            double slope = (separation.y - candidate.y
                + 4.0 * ballisticArcHeight(candidate, separation)) / remaining;
            lead = Math.min(distance * 0.95,
                rise / (Math.sqrt(1.0 + slope * slope) + slope));
        }
        return new Vec3(launch.x + direction.x * lead, requested.y,
            launch.z + direction.z * lead);
    }

    private static double curveStartY(final Vec3 launch, final Vec3 burnout) {
        return Math.min(burnout.y - 1.0, Math.max(
            IcbmConstants.BOOST_CURVE_START_MINIMUM_WORLD_Y,
            launch.y + IcbmConstants.BOOST_CURVE_START_MINIMUM_HEIGHT_ABOVE_LAUNCH));
    }

    private static Vec3 powered(final IcbmFlightPlan plan, final double raw) {
        PoweredArc arc = poweredArc(plan);
        double ticks = Mth.clamp(raw, 0.0, 1.0) * plan.boostTicks();
        if (ticks <= arc.verticalTicks) {
            double height = arc.initialSpeed * ticks
                + 0.5 * arc.verticalAcceleration * ticks * ticks;
            return plan.launchPosition().add(0.0, ignitionRise(plan) + height, 0.0);
        }
        double curvedTime = ticks - arc.verticalTicks;
        double distance = arc.joinSpeed * curvedTime
            + 0.5 * arc.curvedAcceleration * curvedTime * curvedTime;
        double angle = distance / arc.radius;
        double horizontal = arc.radius * (1.0 - Math.cos(angle));
        return new Vec3(plan.launchPosition().x + arc.direction.x * horizontal,
            arc.startY + arc.radius * Math.sin(angle),
            plan.launchPosition().z + arc.direction.z * horizontal);
    }

    private static Vec3 poweredVelocity(final IcbmFlightPlan plan, final double raw) {
        PoweredArc arc = poweredArc(plan);
        double ticks = Mth.clamp(raw, 0.0, 1.0) * plan.boostTicks();
        if (ticks <= arc.verticalTicks)
            return new Vec3(0.0, arc.initialSpeed + arc.verticalAcceleration * ticks, 0.0);
        double curvedTime = ticks - arc.verticalTicks;
        double distance = arc.joinSpeed * curvedTime
            + 0.5 * arc.curvedAcceleration * curvedTime * curvedTime;
        double angle = distance / arc.radius;
        double speed = arc.joinSpeed + arc.curvedAcceleration * curvedTime;
        return new Vec3(arc.direction.x * Math.sin(angle) * speed,
            Math.cos(angle) * speed, arc.direction.z * Math.sin(angle) * speed);
    }

    private static PoweredArc poweredArc(final IcbmFlightPlan plan) {
        Vec3 start = plan.launchPosition().add(0.0, ignitionRise(plan), 0.0);
        Vec3 burnout = plan.burnoutPosition();
        Vec3 endVelocity = coastInitialVelocity(plan);
        Vec3 lateral = new Vec3(burnout.x - start.x, 0.0, burnout.z - start.z);
        double lead = Math.max(1.0E-9, lateral.length());
        Vec3 direction = lateral.scale(1.0 / lead);
        double startY = curveStartY(plan.launchPosition(), burnout);
        double rise = burnout.y - startY;
        double angle = 2.0 * Math.atan2(lead, rise);
        double radius = (lead * lead + rise * rise) / (2.0 * lead);
        double arcLength = radius * angle;
        double verticalHeight = startY - start.y;
        double initialSpeed = 2.0 * ignitionRise(plan) / plan.ignitionTicks();
        double endSpeed = endVelocity.length();
        double duration = plan.boostTicks();
        // Solve T = 2H/(v0+vJoin) + 2S/(vJoin+vEnd). Both segments
        // use constant tangential acceleration and share exactly one speed.
        double b = duration * (initialSpeed + endSpeed)
            - 2.0 * (verticalHeight + arcLength);
        double c = duration * initialSpeed * endSpeed
            - 2.0 * (verticalHeight * endSpeed + arcLength * initialSpeed);
        double joinSpeed = (-b + Math.sqrt(b * b - 4.0 * duration * c))
            / (2.0 * duration);
        double verticalTicks = 2.0 * verticalHeight / (initialSpeed + joinSpeed);
        double curvedTicks = duration - verticalTicks;
        return new PoweredArc(direction, startY, radius, initialSpeed, joinSpeed,
            verticalTicks, (joinSpeed - initialSpeed) / verticalTicks,
            (endSpeed - joinSpeed) / curvedTicks);
    }

    private static double ignitionRise(final IcbmFlightPlan plan) {
        return plan.ignitionTicks() == IcbmConstants.SILO_IGNITION_TICKS
            ? IcbmConstants.SILO_IGNITION_RISE_BLOCKS : 0.5;
    }

    private record PoweredArc(Vec3 direction, double startY, double radius,
        double initialSpeed, double joinSpeed, double verticalTicks,
        double verticalAcceleration, double curvedAcceleration) { }
    private static Vec3 coast(final IcbmFlightPlan plan, final double raw) {
        double u = Mth.clamp(raw, 0, 1);
        double ticks = u * plan.coastTicks();
        Vec3 initial = ballisticInitialVelocity(plan.burnoutPosition(),
            plan.separationPosition(), plan.coastTicks());
        return plan.burnoutPosition().add(initial.scale(ticks))
            .add(0.0, -0.5 * ballisticGravity(plan.burnoutPosition(),
                plan.separationPosition(), plan.coastTicks())
                * ticks * ticks, 0.0);
    }

    private static Vec3 coastVelocity(final IcbmFlightPlan plan, final double raw) {
        double u = Mth.clamp(raw, 0, 1);
        double ticks = u * plan.coastTicks();
        return ballisticInitialVelocity(plan.burnoutPosition(), plan.separationPosition(),
            plan.coastTicks()).add(0.0,
                -ballisticGravity(plan.burnoutPosition(), plan.separationPosition(),
                    plan.coastTicks()) * ticks, 0.0);
    }

    private static Vec3 ballisticInitialVelocity(final Vec3 burnout,
        final Vec3 separation, final int ticks) {
        return separation.subtract(burnout)
            .add(0.0, 0.5 * ballisticGravity(burnout, separation, ticks)
                * ticks * ticks, 0.0)
            .scale(1.0 / ticks);
    }

    private static double ballisticGravity(final Vec3 burnout, final Vec3 separation,
        final int ticks) {
        return 8.0 * ballisticArcHeight(burnout, separation) / (ticks * (double) ticks);
    }

    private static double ballisticArcHeight(final Vec3 burnout, final Vec3 separation) {
        double horizontal = Math.hypot(separation.x - burnout.x,
            separation.z - burnout.z);
        double arcHeight = Mth.clamp(
            horizontal * IcbmConstants.COAST_ARC_HEIGHT_PER_HORIZONTAL_BLOCK,
            IcbmConstants.MINIMUM_COAST_ARC_HEIGHT_BLOCKS,
            IcbmConstants.MAXIMUM_COAST_ARC_HEIGHT_BLOCKS);
        // A high-altitude launch must also leave burnout climbing. Otherwise the
        // powered bend would have to turn past its apex before thrust cuts out.
        return Math.max(arcHeight, (burnout.y - separation.y) * 0.25 + 120.0);
    }

    public static double estimatedPeakCoastDerivative(final Vec3 burnout,
        final Vec3 separation) {
        int ticks = requiredCoastTicks(burnout, separation);
        if (ticks < 0) return Double.POSITIVE_INFINITY;
        return peakBallisticSpeed(burnout, separation, ticks) * ticks;
    }

    public static int requiredCoastTicks(final Vec3 burnout, final Vec3 separation) {
        Vec3 horizontal = new Vec3(separation.x - burnout.x, 0.0,
            separation.z - burnout.z);
        int preferred = Math.max(IcbmConstants.MINIMUM_COAST_TICKS,
            (int) Math.ceil(horizontal.length()
                / IcbmConstants.PREFERRED_CARRIER_SPEED_BLOCKS_PER_TICK));
        int best = -1;
        double bestSpeed = Double.POSITIVE_INFINITY;
        for (int ticks = IcbmConstants.MINIMUM_COAST_TICKS;
            ticks <= IcbmConstants.MAXIMUM_COAST_TICKS; ticks++) {
            double peak = peakBallisticSpeed(burnout, separation, ticks);
            if (!Double.isFinite(peak)) return -1;
            if (peak <= IcbmConstants.MAXIMUM_CARRIER_SPEED_BLOCKS_PER_TICK + .001) {
                if (ticks >= preferred) return ticks;
                if (peak < bestSpeed) {
                    best = ticks;
                    bestSpeed = peak;
                }
            }
        }
        return best;
    }

    public static double estimatedPeakCoastSpeed(final IcbmFlightPlan plan) {
        return peakBallisticSpeed(plan.burnoutPosition(), plan.separationPosition(),
            plan.coastTicks());
    }

    private static double peakBallisticSpeed(final Vec3 burnout, final Vec3 separation,
        final int ticks) {
        Vec3 initial = ballisticInitialVelocity(burnout, separation, ticks);
        Vec3 terminal = initial.add(0.0,
            -ballisticGravity(burnout, separation, ticks) * ticks, 0.0);
        return Math.max(initial.length(), terminal.length());
    }
}
