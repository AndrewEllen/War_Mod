package com.andye.warmod.icbm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Authoritative carrier route with a continuous powered-ascent-to-coast arc. */
public final class IcbmTrajectory {
    private static final double COAST_INITIAL_HORIZONTAL_FRACTION = 0.18;
    private static final double COAST_INITIAL_MINIMUM_HORIZONTAL_LEAD = 48.0;
    private static final double COAST_INITIAL_MAXIMUM_HORIZONTAL_LEAD = 320.0;
    private static final double BOOST_TURN_START_WORLD_Y = 112.0;
    private static final double BOOST_MINIMUM_CLIMB_BEFORE_TURN = 48.0;
    private static final double BOOST_MINIMUM_CLIMB_AFTER_TURN = 72.0;
    private static final double BOOST_TURN_MINIMUM_FRACTION = 0.30;
    private static final double BOOST_TURN_MAXIMUM_FRACTION = 0.58;
    private static final double LAUNCH_VERTICAL_SPEED = 0.55;

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
        double turnFraction = poweredTurnFraction(plan);
        Vec3 start = plan.launchPosition().add(0, .5, 0);
        Vec3 turn = poweredTurnPosition(plan);
        Vec3 burnout = plan.burnoutPosition();
        Vec3 launchVelocity = new Vec3(0.0, LAUNCH_VERTICAL_SPEED, 0.0);
        Vec3 turnVelocity = new Vec3(0.0, poweredTurnVerticalSpeed(plan, turnFraction), 0.0);
        if (u <= turnFraction) {
            double segmentTicks = plan.boostTicks() * turnFraction;
            return hermitePosition(start, turn, launchVelocity, turnVelocity,
                segmentTicks, u / turnFraction);
        }
        double segmentFraction = 1.0 - turnFraction;
        double segmentTicks = plan.boostTicks() * segmentFraction;
        return hermitePosition(turn, burnout, turnVelocity, coastInitialVelocity(plan),
            segmentTicks, (u - turnFraction) / segmentFraction);
    }

    private static Vec3 poweredVelocity(final IcbmFlightPlan plan, final double raw) {
        double u = Mth.clamp(raw, 0.0, 1.0);
        double turnFraction = poweredTurnFraction(plan);
        Vec3 start = plan.launchPosition().add(0, .5, 0);
        Vec3 turn = poweredTurnPosition(plan);
        Vec3 burnout = plan.burnoutPosition();
        Vec3 launchVelocity = new Vec3(0.0, LAUNCH_VERTICAL_SPEED, 0.0);
        Vec3 turnVelocity = new Vec3(0.0, poweredTurnVerticalSpeed(plan, turnFraction), 0.0);
        if (u <= turnFraction) {
            double segmentTicks = plan.boostTicks() * turnFraction;
            return hermiteVelocity(start, turn, launchVelocity, turnVelocity,
                segmentTicks, u / turnFraction);
        }
        double segmentFraction = 1.0 - turnFraction;
        double segmentTicks = plan.boostTicks() * segmentFraction;
        return hermiteVelocity(turn, burnout, turnVelocity, coastInitialVelocity(plan),
            segmentTicks, (u - turnFraction) / segmentFraction);
    }

    /**
     * Start banking only after the carrier has cleared normal terrain. A fixed
     * world-Y threshold gives ordinary surface launches the requested cloud-base
     * cue, while the minimum relative climb prevents deep or underground silos
     * from turning immediately. The remaining climb keeps the bank broad rather
     * than spending the final boost ticks snapping horizontal.
     */
    private static Vec3 poweredTurnPosition(final IcbmFlightPlan plan) {
        Vec3 start = plan.launchPosition().add(0, .5, 0);
        Vec3 burnout = plan.burnoutPosition();
        double desiredY = Math.max(BOOST_TURN_START_WORLD_Y,
            start.y + BOOST_MINIMUM_CLIMB_BEFORE_TURN);
        double maximumY = burnout.y - BOOST_MINIMUM_CLIMB_AFTER_TURN;
        double turnY = Math.min(desiredY, maximumY);
        turnY = Math.max(start.y + 1.0, turnY);
        return new Vec3(start.x, turnY, start.z);
    }

    private static double poweredTurnFraction(final IcbmFlightPlan plan) {
        Vec3 start = plan.launchPosition().add(0, .5, 0);
        Vec3 turn = poweredTurnPosition(plan);
        double totalClimb = Math.max(1.0, plan.burnoutPosition().y - start.y);
        double verticalFraction = Mth.clamp((turn.y - start.y) / totalClimb, 0.0, 1.0);
        return Mth.clamp(0.24 + verticalFraction * 0.55,
            BOOST_TURN_MINIMUM_FRACTION, BOOST_TURN_MAXIMUM_FRACTION);
    }

    private static double poweredTurnVerticalSpeed(final IcbmFlightPlan plan,
        final double turnFraction) {
        Vec3 turn = poweredTurnPosition(plan);
        double remainingTicks = Math.max(1.0,
            plan.boostTicks() * (1.0 - turnFraction));
        double averageVerticalSpeed = Math.max(0.0,
            plan.burnoutPosition().y - turn.y) / remainingTicks;
        return Mth.clamp(averageVerticalSpeed * 0.68, 1.25, 5.0);
    }

    private static Vec3 hermitePosition(final Vec3 start, final Vec3 end,
        final Vec3 startVelocity, final Vec3 endVelocity,
        final double segmentTicks, final double raw) {
        double u = Mth.clamp(raw, 0.0, 1.0);
        double u2 = u * u;
        double u3 = u2 * u;
        Vec3 startTangent = startVelocity.scale(segmentTicks);
        Vec3 endTangent = endVelocity.scale(segmentTicks);
        return start.scale(2 * u3 - 3 * u2 + 1)
            .add(startTangent.scale(u3 - 2 * u2 + u))
            .add(end.scale(-2 * u3 + 3 * u2))
            .add(endTangent.scale(u3 - u2));
    }

    private static Vec3 hermiteVelocity(final Vec3 start, final Vec3 end,
        final Vec3 startVelocity, final Vec3 endVelocity,
        final double segmentTicks, final double raw) {
        double u = Mth.clamp(raw, 0.0, 1.0);
        double u2 = u * u;
        Vec3 startTangent = startVelocity.scale(segmentTicks);
        Vec3 endTangent = endVelocity.scale(segmentTicks);
        return start.scale(6 * u2 - 6 * u)
            .add(startTangent.scale(3 * u2 - 4 * u + 1))
            .add(end.scale(-6 * u2 + 6 * u))
            .add(endTangent.scale(3 * u2 - 2 * u))
            .scale(1.0 / Math.max(1.0E-6, segmentTicks));
    }

    private static Vec3 coast(final IcbmFlightPlan plan, final double raw) {
        double u = Mth.clamp(raw, 0, 1);
        double u2 = u * u;
        double u3 = u2 * u;
        Vec3 p0 = plan.burnoutPosition();
        Vec3 p1 = coastControlOne(plan.burnoutPosition(), plan.separationPosition());
        Vec3 p2 = coastControlTwo(plan.burnoutPosition(), plan.separationPosition());
        Vec3 p3 = plan.separationPosition();
        return p0.scale(1 - 3 * u + 3 * u2 - u3)
            .add(p1.scale(3 * u - 6 * u2 + 3 * u3))
            .add(p2.scale(3 * u2 - 3 * u3))
            .add(p3.scale(u3));
    }

    private static Vec3 coastVelocity(final IcbmFlightPlan plan, final double raw) {
        double u = Mth.clamp(raw, 0, 1);
        Vec3 p0 = plan.burnoutPosition();
        Vec3 p1 = coastControlOne(plan.burnoutPosition(), plan.separationPosition());
        Vec3 p2 = coastControlTwo(plan.burnoutPosition(), plan.separationPosition());
        Vec3 p3 = plan.separationPosition();
        return p1.subtract(p0).scale(3 * (1 - u) * (1 - u))
            .add(p2.subtract(p1).scale(6 * (1 - u) * u))
            .add(p3.subtract(p2).scale(3 * u * u))
            .scale(1.0 / plan.coastTicks());
    }

    /** The first coast control point continues the upper-boost bank down-range. */
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

    private static Vec3 coastDerivativePerUnit(final Vec3 burnout,
        final Vec3 separation, final double rawU) {
        double u = Mth.clamp(rawU, 0.0, 1.0);
        double inverse = 1 - u;
        Vec3 p0 = burnout;
        Vec3 p1 = coastControlOne(burnout, separation);
        Vec3 p2 = coastControlTwo(burnout, separation);
        Vec3 p3 = separation;
        return p1.subtract(p0).scale(3 * inverse * inverse)
            .add(p2.subtract(p1).scale(6 * inverse * u))
            .add(p3.subtract(p2).scale(3 * u * u));
    }

    public static double estimatedPeakCoastDerivative(final Vec3 burnout,
        final Vec3 separation) {
        double maximum = 0;
        for (int index = 0; index <= 4096; index++) {
            maximum = Math.max(maximum,
                coastDerivativePerUnit(burnout, separation, index / 4096.0).length());
        }
        return maximum * 1.005;
    }

    public static int requiredCoastTicks(final Vec3 burnout, final Vec3 separation) {
        double peak = estimatedPeakCoastDerivative(burnout, separation);
        if (!Double.isFinite(peak)) return -1;
        int ticks = Math.max(IcbmConstants.MINIMUM_COAST_TICKS,
            Math.max((int) Math.ceil(peak / IcbmConstants.MAXIMUM_CARRIER_SPEED_BLOCKS_PER_TICK),
                (int) Math.ceil(peak / IcbmConstants.PREFERRED_CARRIER_SPEED_BLOCKS_PER_TICK)));
        if (ticks > IcbmConstants.MAXIMUM_COAST_TICKS) return -1;
        while (peak / ticks > IcbmConstants.MAXIMUM_CARRIER_SPEED_BLOCKS_PER_TICK
            && ticks < IcbmConstants.MAXIMUM_COAST_TICKS) ticks++;
        return peak / ticks <= IcbmConstants.MAXIMUM_CARRIER_SPEED_BLOCKS_PER_TICK + .001
            ? ticks : -1;
    }

    public static double estimatedPeakCoastSpeed(final IcbmFlightPlan plan) {
        return estimatedPeakCoastDerivative(plan.burnoutPosition(),
            plan.separationPosition()) / plan.coastTicks();
    }
}
