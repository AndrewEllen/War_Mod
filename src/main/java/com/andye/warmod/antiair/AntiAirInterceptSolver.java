package com.andye.warmod.antiair;

import com.andye.warmod.icbm.IcbmTrajectory;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public final class AntiAirInterceptSolver {
    private AntiAirInterceptSolver() { }

    public static Optional<AntiAirInterceptSolution> solve(Vec3 burnout, long burnoutTime, AntiAirTargetLock lock) {
        long last = Math.min(burnoutTime + AntiAirConstants.MAXIMUM_INTERCEPT_LOOKAHEAD_TICKS,
            lock.separationGameTime() + AntiAirConstants.SEPARATION_INTERCEPT_GRACE_TICKS);
        for (long time = burnoutTime + AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS; time <= last;
            time += AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS) {
            AntiAirInterceptSolution solution = routeFor(burnout, burnoutTime, lock, time, true);
            if (solution != null) return Optional.of(solution);
        }
        return Optional.empty();
    }

    /** Builds a speed-limited fixed route even after the normal intercept window has closed. */
    public static Optional<AntiAirInterceptSolution> bestEffort(Vec3 burnout, long burnoutTime, AntiAirTargetLock lock) {
        long captured = Math.max(burnoutTime + AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS,
            Math.min(lock.separationGameTime(), burnoutTime + AntiAirConstants.MAXIMUM_INTERCEPT_LOOKAHEAD_TICKS));
        return Optional.ofNullable(routeFor(burnout, burnoutTime, lock, captured, false));
    }

    private static AntiAirInterceptSolution routeFor(Vec3 burnout, long burnoutTime, AntiAirTargetLock lock,
        long targetTime, boolean requireArrivalByTargetTime) {
        double targetElapsed = targetTime - lock.carrierPlan().launchGameTime();
        Vec3 target = IcbmTrajectory.position(lock.carrierPlan(), targetElapsed);
        Vec3 velocity = IcbmTrajectory.velocity(lock.carrierPlan(), targetElapsed);
        if (!burnout.isFinite() || !target.isFinite() || !velocity.isFinite()) return null;
        Vec3 approach = velocity.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : velocity.normalize();
        double control = Math.min(120, Math.max(24, burnout.distanceTo(target) * .28));
        AntiAirRoute unitRoute = new AntiAirRoute(burnout, burnout.add(0, Math.min(96, control), 0),
            target.subtract(approach.scale(control)), target, 1);
        double peakAtOneTick = 0;
        for (int i = 0; i <= 16; i++) peakAtOneTick = Math.max(peakAtOneTick, unitRoute.velocity(i / 16.0).length());
        int speedLimitedDuration = Math.max(AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS,
            (int)Math.ceil(Math.max(unitRoute.arcLength(), peakAtOneTick)
                / AntiAirConstants.MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK));
        int duration = requireArrivalByTargetTime ? (int)(targetTime - burnoutTime) : speedLimitedDuration;
        if (duration < AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS) return null;
        AntiAirRoute route = new AntiAirRoute(burnout, burnout.add(0, Math.min(96, control), 0),
            target.subtract(approach.scale(control)), target, duration);
        double average = route.arcLength() / duration, peak = 0;
        for (int i = 0; i <= 16; i++) peak = Math.max(peak, route.velocity(duration * i / 16.0).length());
        if (average > AntiAirConstants.MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK
            || peak > AntiAirConstants.MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK) return null;
        return new AntiAirInterceptSolution(targetTime, target, velocity, route, average, peak);
    }
}