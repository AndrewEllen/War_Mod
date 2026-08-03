package com.andye.warmod.antiair;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative selector and fixed-route planner for every anti-air launch source. */
public final class AntiAirLaunchPlanner {
    private AntiAirLaunchPlanner() { }

    public static Optional<AntiAirLaunchDecision> plan(ServerLevel level, Vec3 origin, Vec3 burnout) {
        if (!origin.isFinite() || !burnout.isFinite()) return Optional.empty();
        AntiAirTargetSelection selection = AntiAirTargetSelector.acquire(level, origin).orElse(null);
        if (selection == null) return Optional.of(new AntiAirLaunchDecision(
            AntiAirLaunchMode.NO_TARGET_ASCENT, null, null, "no_projected_threat"));
        long burnoutTime = level.getGameTime() + AntiAirConstants.IGNITION_TICKS + AntiAirConstants.BOOST_TICKS;
        AntiAirInterceptSolution feasible = AntiAirInterceptSolver.solve(burnout, burnoutTime, selection).orElse(null);
        if (feasible != null) return Optional.of(new AntiAirLaunchDecision(
            AntiAirLaunchMode.TRACKED_INTERCEPT, selection, feasible, "feasible_intercept"));
        AntiAirInterceptSolution bestEffort = AntiAirInterceptSolver.bestEffort(burnout, burnoutTime, selection).orElse(null);
        if (bestEffort == null) return Optional.empty();
        return Optional.of(new AntiAirLaunchDecision(
            AntiAirLaunchMode.BEST_EFFORT_INTERCEPT, selection, bestEffort, "best_effort_route"));
    }
}