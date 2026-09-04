package com.andye.warmod.icbm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Authoritative carrier route with a continuous powered-ascent-to-coast arc. */
public final class IcbmTrajectory {
    private static final double COAST_INITIAL_HORIZONTAL_FRACTION = 0.14;
    private static final double COAST_INITIAL_MINIMUM_HORIZONTAL_LEAD = 24.0;
    private static final double COAST_INITIAL_MAXIMUM_HORIZONTAL_LEAD = 192.0;
    private static final double BOOST_CURVE_MINIMUM_FRACTION = 0.18;
    private static final double BOOST_CURVE_MAXIMUM_FRACTION = 0.78;

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
            return plan.launchPosition().add(0, .5 * u * u, 0);
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
            return new Vec3(0, u / plan.ignitionTicks(), 0);
        }
        if (ticks < plan.ignitionTicks() + plan.boostTicks()) {
            return poweredVelocity(plan,
                (ticks - plan.ignitionTicks()) / plan.boostTicks());
        }
        return coastVelocity(plan,
            (ticks - plan.ignitionTicks() - plan.boostTicks()) / plan.coastTicks());
    }

    private static Vec3 powered(final IcbmFlightPlan plan, final double raw) {
        double u = Mth.clamp(raw, 0.0, 1.0);
        Vec3 start = plan.launchPosition().add(0, .5, 0);
        Vec3 burnout = plan.burnoutPosition();
        double curveStartFraction = boostCurveStartFraction(plan, start, burnout);
        Vec3 curveStart = new Vec3(start.x,
            Mth.lerp(curveStartFraction, start.y, burnout.y), start.z);
        double curveStartTicks = Math.max(1.0, plan.boostTicks() * curveStartFraction);
        double curvedTicks = Math.max(1.0, plan.boostTicks() - curveStartTicks);
        Vec3 curveStartVelocity = boostCurveStartVelocity(plan, curveStart, burnout, curvedTicks);

        if (u <= curveStartFraction) {
            double local = curveStartFraction <= 1.0E-8 ? 1.0 : u / curveStartFraction;
            Vec3 launchVelocity = new Vec3(0.0, 1.0 / plan.ignitionTicks(), 0.0);
            return hermitePosition(start, curveStart,
                launchVelocity.scale(curveStartTicks),
                curveStartVelocity.scale(curveStartTicks), local);
        }

        double local = (u - curveStartFraction) / Math.max(1.0E-8, 1.0 - curveStartFraction);
        Vec3 burnoutVelocity = coastInitialVelocity(plan);
        return hermitePosition(curveStart, burnout,
            curveStartVelocity.scale(curvedTicks),
            burnoutVelocity.scale(curvedTicks), local);
    }

    private static Vec3 poweredVelocity(final IcbmFlightPlan plan, final double raw) {
        double u = Mth.clamp(raw, 0.0, 1.0);
        Vec3 start = plan.launchPosition().add(0, .5, 0);
        Vec3 burnout = plan.burnoutPosition();
        double curveStartFraction = boostCurveStartFraction(plan, start, burnout);
        Vec3 curveStart = new Vec3(start.x,
            Mth.lerp(curveStartFraction, start.y, burnout.y), start.z);
        double curveStartTicks = Math.max(1.0, plan.boostTicks() * curveStartFraction);
        double curvedTicks = Math.max(1.0, plan.boostTicks() - curveStartTicks);
        Vec3 curveStartVelocity = boostCurveStartVelocity(plan, curveStart, burnout, curvedTicks);

        if (u <= curveStartFraction) {
            double local = curveStartFraction <= 1.0E-8 ? 1.0 : u / curveStartFraction;
            Vec3 launchVelocity = new Vec3(0.0, 1.0 / plan.ignitionTicks(), 0.0);
            return hermiteDerivative(start, curveStart,
                launchVelocity.scale(curveStartTicks),
                curveStartVelocity.scale(curveStartTicks), local)
                .scale(1.0 / curveStartTicks);
        }

        double local = (u - curveStartFraction) / Math.max(1.0E-8, 1.0 - curveStartFraction);
        Vec3 burnoutVelocity = coastInitialVelocity(plan);
        return hermiteDerivative(curveStart, burnout,
            curveStartVelocity.scale(curvedTicks),
            burnoutVelocity.scale(curvedTicks), local)
            .scale(1.0 / curvedTicks);
    }

    private static double boostCurveStartFraction(final IcbmFlightPlan plan,
        final Vec3 start, final Vec3 burnout) {
        double verticalGain = Math.max(1.0, burnout.y - start.y);
        double desiredStartY = Math.max(
            IcbmConstants.BOOST_CURVE_START_MINIMUM_WORLD_Y,
            plan.launchPosition().y + IcbmConstants.BOOST_CURVE_START_MINIMUM_HEIGHT_ABOVE_LAUNCH);
        double rawFraction = (desiredStartY - start.y) / verticalGain;
        return Mth.clamp(rawFraction, BOOST_CURVE_MINIMUM_FRACTION,
            BOOST_CURVE_MAXIMUM_FRACTION);
    }

    private static Vec3 boostCurveStartVelocity(final IcbmFlightPlan plan,
        final Vec3 curveStart, final Vec3 burnout, final double curvedTicks) {
        double verticalSecant = Math.max(0.01, (burnout.y - curveStart.y) / curvedTicks);
        double verticalVelocity = Mth.clamp(verticalSecant * 1.30, 0.70, 4.50);
        return new Vec3(0.0, verticalVelocity, 0.0);
    }

    private static Vec3 hermitePosition(final Vec3 p0, final Vec3 p1,
        final Vec3 m0, final Vec3 m1, final double rawU) {
        double u = Mth.clamp(rawU, 0.0, 1.0);
        double u2 = u * u;
        double u3 = u2 * u;
        return p0.scale(2 * u3 - 3 * u2 + 1)
            .add(m0.scale(u3 - 2 * u2 + u))
            .add(p1.scale(-2 * u3 + 3 * u2))
            .add(m1.scale(u3 - u2));
    }

    private static Vec3 hermiteDerivative(final Vec3 p0, final Vec3 p1,
        final Vec3 m0, final Vec3 m1, final double rawU) {
        double u = Mth.clamp(rawU, 0.0, 1.0);
        double u2 = u * u;
        return p0.scale(6 * u2 - 6 * u)
            .add(m0.scale(3 * u2 - 4 * u + 1))
            .add(p1.scale(-6 * u2 + 6 * u))
            .add(m1.scale(3 * u2 - 2 * u));
    }

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

    /**
     * Keep the coast tangent moving down-range while it is still climbing, so
     * burnout joins the ballistic arc without a second visible corner.
     */
    private static Vec3 coastControlOne(final Vec3 burnout, final Vec3 separation) {
        Vec3 delta = separation.subtract(burnout);
        Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
        if (horizontal.lengthSqr() < 1.0E-8) {
            return burnout.add(0, IcbmConstants.BOOST_ASCENT_CONTROL_DISTANCE, 0);
        }
        double lead = Mth.clamp(
            horizontal.length() * COAST_INITIAL_HORIZONTAL_FRACTION,
            COAST_INITIAL_MINIMUM_HORIZONTAL_LEAD,
            COAST_INITIAL_MAXIMUM_HORIZONTAL_LEAD);
        return burnout.add(horizontal.normalize().scale(lead))
            .add(0, IcbmConstants.BOOST_ASCENT_CONTROL_DISTANCE, 0);
    }

    private static Vec3 coastControlTwo(final Vec3 burnout, final Vec3 separation) {
        Vec3 delta = separation.subtract(burnout);
        Vec3 horizontal = new Vec3(delta.x, 0, delta.z);
        Vec3 approach = horizontal.lengthSqr() < 1.0E-8 ? Vec3.ZERO
            : horizontal.normalize().scale(Math.min(
                IcbmConstants.COAST_APPROACH_CONTROL_DISTANCE,
                horizontal.length() * .20));
        return separation.subtract(approach)
            .add(0, IcbmConstants.COAST_TERMINAL_CONTROL_HEIGHT, 0);
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
        double horizontal = Math.hypot(separation.x - burnout.x,
            separation.z - burnout.z);
        double arcHeight = Mth.clamp(
            horizontal * IcbmConstants.COAST_ARC_HEIGHT_PER_HORIZONTAL_BLOCK,
            IcbmConstants.MINIMUM_COAST_ARC_HEIGHT_BLOCKS,
            IcbmConstants.MAXIMUM_COAST_ARC_HEIGHT_BLOCKS);
        return 8.0 * arcHeight / (ticks * (double) ticks);
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
