package com.andye.warmod.antiair;

import com.andye.warmod.defence.DefenceOwnershipSnapshot;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative selector and fixed-route planner for every anti-air
 * launch source.
 */
public final class AntiAirLaunchPlanner {
    private AntiAirLaunchPlanner() {
    }

    public static Optional<AntiAirLaunchDecision> plan(
        final ServerLevel level,
        final Vec3 origin,
        final Vec3 burnout
    ) {
        return plan(level, origin, burnout, DefenceOwnershipSnapshot.unclaimed());
    }

    public static Optional<AntiAirLaunchDecision> plan(
        final ServerLevel level,
        final Vec3 origin,
        final Vec3 burnout,
        final DefenceOwnershipSnapshot ownership
    ) {
        if (
            origin == null
            || burnout == null
            || !origin.isFinite()
            || !burnout.isFinite()
        ) {
            return Optional.empty();
        }

        AntiAirTargetSelection selection =
            AntiAirTargetSelector.acquire(
                level,
                origin,
                ownership
            ).orElse(null);

        if (selection == null) {
            return Optional.of(
                noTargetDecision(
                    "no_projected_threat"
                )
            );
        }

        long burnoutTime =
            level.getGameTime()
                + AntiAirConstants.IGNITION_TICKS
                + AntiAirConstants.BOOST_TICKS;

        AntiAirInterceptSolution exact =
            AntiAirInterceptSolver.solve(
                burnout,
                burnoutTime,
                selection
            ).orElse(null);

        if (exact != null) {
            return Optional.of(
                new AntiAirLaunchDecision(
                    AntiAirLaunchMode.TRACKED_INTERCEPT,
                    selection,
                    exact,
                    "exact_intercept"
                )
            );
        }

        AntiAirInterceptSolution attempt =
            AntiAirInterceptSolver.bestEffort(
                burnout,
                burnoutTime,
                selection
            ).orElse(null);

        if (attempt != null) {
            return Optional.of(
                new AntiAirLaunchDecision(
                    AntiAirLaunchMode.TRACKED_INTERCEPT,
                    selection,
                    attempt,
                    "bounded_low_probability_attempt"
                )
            );
        }

        /*
         * A target projection existed but no safe finite powered route could
         * be constructed. Do not reject the launch. Use the established
         * no-target ascent and variant-specific fallback instead.
         */
        return Optional.of(
            noTargetDecision(
                "no_safe_attempt_route"
            )
        );
    }

    private static AntiAirLaunchDecision noTargetDecision(
        final String reason
    ) {
        return new AntiAirLaunchDecision(
            AntiAirLaunchMode.NO_TARGET_ASCENT,
            null,
            null,
            reason
        );
    }
}
