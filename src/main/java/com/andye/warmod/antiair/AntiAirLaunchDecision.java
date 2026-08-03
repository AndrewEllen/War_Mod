package com.andye.warmod.antiair;

import org.jspecify.annotations.Nullable;

/** The only supported launch-mode contract shared by all interceptor entry points. */
public record AntiAirLaunchDecision(
    AntiAirLaunchMode mode,
    @Nullable AntiAirTargetSelection targetSelection,
    @Nullable AntiAirInterceptSolution solution,
    String diagnosticReason
) {
    public boolean valid() {
        return switch (mode) {
            case TRACKED_INTERCEPT -> targetSelection != null && solution != null && solution.feasible() && !solution.bestEffort();
            case BEST_EFFORT_INTERCEPT -> targetSelection != null && solution != null && solution.bestEffort() && !solution.feasible();
            case NO_TARGET_ASCENT -> targetSelection == null && solution == null;
        };
    }
}