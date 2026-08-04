package com.andye.warmod.antiair;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/**
 * Builds bounded powered routes for anti-air missiles.
 *
 * Exact solutions place the interceptor and target at the same point at the
 * same time. When that is not possible but the target is inside the defended
 * near-term projection, a bounded attempt route is still returned. Attempt
 * routes always end after a finite duration and therefore cannot loiter.
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
                selection.projection().estimatedImpactGameTime(),
                burnoutGameTime
                    + AntiAirConstants.MAXIMUM_POWERED_INTERCEPT_TICKS
            );

        if (latest < earliest) {
            return Optional.empty();
        }

        for (
            long targetTime = earliest;
            targetTime <= latest;
            targetTime += AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS
        ) {
            AntiAirInterceptSolution solution =
                exactRouteFor(
                    burnout,
                    burnoutGameTime,
                    selection,
                    targetTime
                );

            if (solution != null) {
                return Optional.of(solution);
            }
        }

        if (
            (latest - earliest)
                % AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS
                != 0
        ) {
            AntiAirInterceptSolution solution =
                exactRouteFor(
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
     * Produces the best finite powered attempt available inside the same
     * near-term interception horizon.
     *
     * The returned route itself is physically valid: no more than 500 blocks,
     * no faster than six blocks per tick and no longer than the powered-flight
     * timeout. It may end short of the projected target point, which is why the
     * chance of interception can be low. The live fuse check can still score a
     * hit anywhere along the route.
     */
    public static Optional<AntiAirInterceptSolution> bestEffort(
        final Vec3 burnout,
        final long burnoutGameTime,
        final AntiAirTargetSelection selection
    ) {
        long earliest =
            burnoutGameTime
                + AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS;

        long latest =
            Math.min(
                selection.projection().estimatedImpactGameTime(),
                burnoutGameTime
                    + AntiAirConstants.MAXIMUM_POWERED_INTERCEPT_TICKS
            );

        if (latest < earliest) {
            return Optional.empty();
        }

        Attempt best = null;

        for (
            long targetTime = earliest;
            targetTime <= latest;
            targetTime += AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS
        ) {
            Attempt attempt =
                boundedAttemptFor(
                    burnout,
                    burnoutGameTime,
                    selection,
                    targetTime
                );

            if (
                attempt != null
                && (
                    best == null
                    || attempt.missDistance() < best.missDistance()
                    || (
                        attempt.missDistance() == best.missDistance()
                        && attempt.targetTime() < best.targetTime()
                    )
                )
            ) {
                best = attempt;
            }
        }

        if (
            (latest - earliest)
                % AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS
                != 0
        ) {
            Attempt attempt =
                boundedAttemptFor(
                    burnout,
                    burnoutGameTime,
                    selection,
                    latest
                );

            if (
                attempt != null
                && (
                    best == null
                    || attempt.missDistance() < best.missDistance()
                    || (
                        attempt.missDistance() == best.missDistance()
                        && attempt.targetTime() < best.targetTime()
                    )
                )
            ) {
                best = attempt;
            }
        }

        if (best == null) {
            return Optional.empty();
        }

        /*
         * "feasible" here means the powered route is physically flyable. The
         * rangeLimited flag records that this is not an exact synchronization.
         * Returning it as a normal tracked route lets the existing controller
         * retain guidance inaccuracy, live fuse checks and finite fallback.
         */
        return Optional.of(
            new AntiAirInterceptSolution(
                best.targetTime(),
                best.route().end(),
                best.targetVelocity(),
                best.route(),
                best.route().arcLength()
                    / best.route().durationTicks(),
                peakSpeed(best.route()),
                true,
                false,
                true,
                best.originalArcLength(),
                best.route().arcLength()
            )
        );
    }

    private static AntiAirInterceptSolution exactRouteFor(
        final Vec3 burnout,
        final long burnoutGameTime,
        final AntiAirTargetSelection selection,
        final long targetTime
    ) {
        int duration =
            Math.toIntExact(
                targetTime - burnoutGameTime
            );

        if (
            duration < AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS
            || duration
                > AntiAirConstants.MAXIMUM_POWERED_INTERCEPT_TICKS
        ) {
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

        if (
            !burnout.isFinite()
            || target == null
            || velocity == null
            || !target.isFinite()
            || !velocity.isFinite()
        ) {
            return null;
        }

        if (
            AntiAirTargetSelector.horizontal(
                burnout,
                target
            ) > AntiAirConstants.DEFENDED_TRAJECTORY_RADIUS_BLOCKS
        ) {
            return null;
        }

        AntiAirRoute route =
            rawRoute(
                burnout,
                target,
                velocity,
                duration
            );

        double arcLength =
            route.arcLength();

        if (
            !Double.isFinite(arcLength)
            || arcLength <= 0.0
            || arcLength
                > AntiAirConstants.MAX_POWERED_INTERCEPT_ARC_BLOCKS
        ) {
            return null;
        }

        double averageSpeed =
            arcLength / duration;

        double peakSpeed =
            peakSpeed(route);

        if (
            !Double.isFinite(averageSpeed)
            || !Double.isFinite(peakSpeed)
            || averageSpeed
                > AntiAirConstants
                    .MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK
                    + 1.0E-8
            || peakSpeed
                > AntiAirConstants
                    .MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK
                    + 1.0E-8
        ) {
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

    private static Attempt boundedAttemptFor(
        final Vec3 burnout,
        final long burnoutGameTime,
        final AntiAirTargetSelection selection,
        final long targetTime
    ) {
        int duration =
            Math.toIntExact(
                targetTime - burnoutGameTime
            );

        if (
            duration < AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS
            || duration
                > AntiAirConstants.MAXIMUM_POWERED_INTERCEPT_TICKS
        ) {
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

        if (
            target == null
            || velocity == null
            || !burnout.isFinite()
            || !target.isFinite()
            || !velocity.isFinite()
        ) {
            return null;
        }

        if (
            AntiAirTargetSelector.horizontal(
                burnout,
                target
            ) > AntiAirConstants.DEFENDED_TRAJECTORY_RADIUS_BLOCKS
        ) {
            return null;
        }

        AntiAirRoute raw =
            rawRoute(
                burnout,
                target,
                velocity,
                duration
            );

        double originalArcLength =
            raw.arcLength();

        if (
            !Double.isFinite(originalArcLength)
            || originalArcLength <= 0.0
        ) {
            return null;
        }

        double maximumTravel =
            Math.min(
                AntiAirConstants.MAX_POWERED_INTERCEPT_ARC_BLOCKS,
                duration
                    * AntiAirConstants
                        .MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK
            );

        if (
            !Double.isFinite(maximumTravel)
            || maximumTravel <= 0.0
        ) {
            return null;
        }

        AntiAirRoute route = null;

        /*
         * Cubic control points can produce a peak speed above the average
         * speed. Repeatedly shorten the route until both limits are satisfied.
         */
        for (int attempt = 0; attempt < 16; attempt++) {
            double requestedLength =
                Math.min(
                    originalArcLength,
                    maximumTravel
                );

            AntiAirRoute candidate =
                requestedLength
                        >= originalArcLength - 1.0E-6
                    ? raw.withDuration(duration)
                    : raw
                        .truncatedAtArcLength(
                            requestedLength
                        )
                        .withDuration(duration);

            double candidateLength =
                candidate.arcLength();

            double candidatePeak =
                peakSpeed(candidate);

            if (
                Double.isFinite(candidateLength)
                && Double.isFinite(candidatePeak)
                && candidateLength > 0.0
                && candidateLength
                    <= AntiAirConstants
                        .MAX_POWERED_INTERCEPT_ARC_BLOCKS
                        + 1.0E-6
                && candidatePeak
                    <= AntiAirConstants
                        .MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK
                        + 1.0E-8
            ) {
                route = candidate;
                break;
            }

            maximumTravel *= 0.82;
        }

        if (route == null) {
            return null;
        }

        return new Attempt(
            targetTime,
            target,
            velocity,
            route,
            originalArcLength,
            route.end().distanceTo(target)
        );
    }

    private static AntiAirRoute rawRoute(
        final Vec3 burnout,
        final Vec3 target,
        final Vec3 targetVelocity,
        final int duration
    ) {
        Vec3 approach =
            targetVelocity.lengthSqr() < 1.0E-8
                ? target.subtract(burnout)
                : targetVelocity;

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

        return new AntiAirRoute(
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

    private record Attempt(
        long targetTime,
        Vec3 targetPosition,
        Vec3 targetVelocity,
        AntiAirRoute route,
        double originalArcLength,
        double missDistance
    ) {
    }
}
