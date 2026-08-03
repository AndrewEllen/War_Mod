package com.andye.warmod.antiair;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Builds a once-locked route from a sampled carrier-or-terminal trajectory point. */
public final class AntiAirInterceptSolver {
    private AntiAirInterceptSolver() { }

    public static Optional<AntiAirInterceptSolution> solve(Vec3 burnout, long burnoutGameTime,
        AntiAirTargetSelection selection) {
        long impact = selection.projection().estimatedImpactGameTime();
        for (long targetTime = burnoutGameTime + AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS;
            targetTime <= impact; targetTime += AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS) {
            AntiAirInterceptSolution solution = routeFor(burnout, burnoutGameTime, selection, targetTime, true);
            if (solution != null) return Optional.of(solution);
        }
        return Optional.empty();
    }

    /** Uses the most useful projected point but never increases speed or continuously homes. */
    public static Optional<AntiAirInterceptSolution> bestEffort(Vec3 burnout, long burnoutGameTime,
        AntiAirTargetSelection selection) {
        AntiAirThreatProjection projection = selection.projection();
        long[] candidates = { projection.firstEntryGameTime(), projection.closestApproachGameTime(),
            Math.max(burnoutGameTime + AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS,
                projection.estimatedImpactGameTime() - AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS) };
        for (long targetTime : candidates) {
            if (targetTime < burnoutGameTime + AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS
                || targetTime > projection.estimatedImpactGameTime()) continue;
            AntiAirInterceptSolution solution = routeFor(burnout, burnoutGameTime, selection, targetTime, false);
            if (solution != null) return Optional.of(solution);
        }
        return Optional.empty();
    }

    private static AntiAirInterceptSolution routeFor(Vec3 burnout, long burnoutGameTime,
        AntiAirTargetSelection selection, long targetTime, boolean requireArrivalByTargetTime) {
        Vec3 target = AntiAirTargetSelector.positionAt(selection.targetLock(), targetTime);
        Vec3 velocity = AntiAirTargetSelector.velocityAt(selection.targetLock(), targetTime);
        if (!burnout.isFinite() || target == null || velocity == null || !target.isFinite() || !velocity.isFinite()) return null;
        Vec3 approach = velocity.lengthSqr() < 1.0E-8 ? target.subtract(burnout) : velocity;
        approach = approach.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : approach.normalize();
        double control = Math.min(120.0, Math.max(24.0, burnout.distanceTo(target) * 0.28));
        AntiAirRoute geometry = new AntiAirRoute(burnout, burnout.add(0.0, Math.min(96.0, control), 0.0),
            target.subtract(approach.scale(control)), target, 1);
        double originalLength = geometry.arcLength();
        if (!Double.isFinite(originalLength) || originalLength <= 0.0) return null;
        if (requireArrivalByTargetTime) {
            int duration = (int)(targetTime - burnoutGameTime);
            if (duration < AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS
                || originalLength > AntiAirConstants.MAX_POWERED_INTERCEPT_ARC_BLOCKS) return null;
            AntiAirRoute route = geometry.withDuration(duration);
            double average = route.arcLength() / duration;
            double peak = peakSpeed(route);
            if (average > AntiAirConstants.MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK
                || peak > AntiAirConstants.MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK) return null;
            return new AntiAirInterceptSolution(targetTime, target, velocity, route, average, peak,
                true, false, false, originalLength, originalLength);
        }
        AntiAirRoute capped = originalLength > AntiAirConstants.MAX_POWERED_INTERCEPT_ARC_BLOCKS
            ? geometry.truncatedAtArcLength(AntiAirConstants.MAX_POWERED_INTERCEPT_ARC_BLOCKS) : geometry;
        int duration = Math.max(AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS,
            capped.minimumDurationForSpeed(AntiAirConstants.MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK));
        AntiAirRoute route = capped.withDuration(duration);
        double average = route.arcLength() / duration;
        double peak = peakSpeed(route);
        if (average > AntiAirConstants.MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK + 1.0E-8
            || peak > AntiAirConstants.MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK + 1.0E-8) return null;
        return new AntiAirInterceptSolution(targetTime, target, velocity, route, average, peak,
            false, true, originalLength > AntiAirConstants.MAX_POWERED_INTERCEPT_ARC_BLOCKS,
            originalLength, route.arcLength());
    }

    private static double peakSpeed(AntiAirRoute route) {
        double peak = 0.0;
        for (int sample = 0; sample <= 64; sample++)
            peak = Math.max(peak, route.velocity(route.durationTicks() * sample / 64.0).length());
        return peak;
    }
}