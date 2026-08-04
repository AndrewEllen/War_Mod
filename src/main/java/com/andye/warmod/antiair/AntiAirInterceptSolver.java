package com.andye.warmod.antiair;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/**
 * Builds one fixed, synchronized powered route.
 *
 * Automatic launches never use a truncated "best effort" route. A solution is
 * valid only when the interceptor and the target can occupy the same point at
 * the same time, inside the defended horizontal radius, within the powered
 * range and powered-flight time limits.
 */
public final class AntiAirInterceptSolver {
    private AntiAirInterceptSolver() {
    }

    public static Optional<AntiAirInterceptSolution> solve(
        final Vec3 burnout,
        final long burnoutGameTime,
        final AntiAirTargetSelection selection
    ) {
        long earliest =
            burnoutGameTime
                + AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS;

        long latest =
            Math.min(
                selection.projection()
                    .estimatedImpactGameTime(),
                burnoutGameTime
                    + AntiAirConstants
                        .MAXIMUM_POWERED_INTERCEPT_TICKS
            );

        if (latest < earliest) {
            return Optional.empty();
        }

        for (
            long targetTime = earliest;
            targetTime <= latest;
            targetTime +=
                AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS
        ) {
            AntiAirInterceptSolution solution =
                routeFor(
                    burnout,
                    burnoutGameTime,
                    selection,
                    targetTime
                );

            if (solution != null) {
                return Optional.of(solution);
            }
        }

        /*
         * Include the exact final horizon when the sample step did not land on
         * it.
         */
        if (
            (latest - earliest)
                % AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS
                != 0
        ) {
            AntiAirInterceptSolution solution =
                routeFor(
                    burnout,
                    burnoutGameTime,
                    selection,
                    latest
                );

            if (solution != null) {
                return Optional.of(solution);
            }
        }

        return Optional.empty();
    }

    /**
     * Kept for source compatibility. Automatic target selection must not
     * launch an interceptor on a route which reaches a point before or after
     * the target.
     */
    public static Optional<AntiAirInterceptSolution> bestEffort(
        final Vec3 burnout,
        final long burnoutGameTime,
        final AntiAirTargetSelection selection
    ) {
        return Optional.empty();
    }

    private static AntiAirInterceptSolution routeFor(
        final Vec3 burnout,
        final long burnoutGameTime,
        final AntiAirTargetSelection selection,
        final long targetTime
    ) {
        int duration =
            Math.toIntExact(
                targetTime - burnoutGameTime
            );

        if (duration
                < AntiAirConstants
                    .MINIMUM_INTERCEPT_LEAD_TICKS
            || duration
                > AntiAirConstants
                    .MAXIMUM_POWERED_INTERCEPT_TICKS) {
            return null;
        }

        Vec3 target =
            AntiAirTargetSelector.positionAt(
                selection.targetLock(),
                targetTime
            );

        Vec3 velocity =
            AntiAirTargetSelector.velocityAt(
                selection.targetLock(),
                targetTime
            );

        if (!burnout.isFinite()
            || target == null
            || velocity == null
            || !target.isFinite()
            || !velocity.isFinite()) {
            return null;
        }

        /*
         * The defended range is horizontal. The actual powered path is also
         * checked separately against the 500-block arc limit below.
         */
        if (
            AntiAirTargetSelector.horizontal(
                burnout,
                target
            )
                > AntiAirConstants
                    .DEFENDED_TRAJECTORY_RADIUS_BLOCKS
        ) {
            return null;
        }

        Vec3 approach =
            velocity.lengthSqr() < 1.0E-8
                ? target.subtract(burnout)
                : velocity;

        approach =
            approach.lengthSqr() < 1.0E-8
                ? new Vec3(0.0, 0.0, 1.0)
                : approach.normalize();

        double control =
            Math.min(
                120.0,
                Math.max(
                    24.0,
                    burnout.distanceTo(target) * 0.28
                )
            );

        AntiAirRoute route =
            new AntiAirRoute(
                burnout,
                burnout.add(
                    0.0,
                    Math.min(96.0, control),
                    0.0
                ),
                target.subtract(
                    approach.scale(control)
                ),
                target,
                duration
            );

        double arcLength =
            route.arcLength();

        if (!Double.isFinite(arcLength)
            || arcLength <= 0.0
            || arcLength
                > AntiAirConstants
                    .MAX_POWERED_INTERCEPT_ARC_BLOCKS) {
            return null;
        }

        double averageSpeed =
            arcLength / duration;

        double peakSpeed =
            peakSpeed(route);

        if (!Double.isFinite(averageSpeed)
            || !Double.isFinite(peakSpeed)
            || averageSpeed
                > AntiAirConstants
                    .MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK
                    + 1.0E-8
            || peakSpeed
                > AntiAirConstants
                    .MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK
                    + 1.0E-8) {
            return null;
        }

        return new AntiAirInterceptSolution(
            targetTime,
            target,
            velocity,
            route,
            averageSpeed,
            peakSpeed,
            true,
            false,
            false,
            arcLength,
            arcLength
        );
    }

    private static double peakSpeed(
        final AntiAirRoute route
    ) {
        double peak = 0.0;

        for (int sample = 0; sample <= 64; sample++) {
            peak = Math.max(
                peak,
                route.velocity(
                    route.durationTicks()
                        * sample / 64.0
                ).length()
            );
        }

        return peak;
    }
}
