package com.andye.warmod.antiair;

import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.radar.RadarTerminalPlanSnapshot;
import com.andye.warmod.radar.RadarTrackKind;
import com.andye.warmod.radar.RadarTrackPhase;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadTrajectory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Projects server-authoritative ICBM carrier and terminal paths into a defended horizontal region. */
public final class AntiAirTargetSelector {
    private AntiAirTargetSelector() { }

    public static Optional<AntiAirTargetSelection> acquire(ServerLevel level, Vec3 origin) {
        long now = level.getGameTime();
        List<AntiAirTargetSelection> candidates = new ArrayList<>();
        for (RadarTrackingService.RadarTrackTelemetry telemetry : RadarTrackingService.currentTelemetry(level, now)) {
            if (telemetry.kind() != RadarTrackKind.ICBM || telemetry.phase() == RadarTrackPhase.IMPACT
                || telemetry.strategicPayloadType().isEmpty() || telemetry.snapshot().carrierPlan().isEmpty()) continue;
            project(telemetry, origin, now).ifPresent(candidates::add);
        }
        return candidates.stream().min(Comparator
            .comparingLong((AntiAirTargetSelection selection) -> selection.projection().firstEntryGameTime())
            .thenComparingLong(selection -> selection.projection().estimatedImpactGameTime())
            .thenComparingDouble(selection -> selection.projection().closestHorizontalDistance())
            .thenComparingInt(selection -> selection.targetLock().payloadType() == WarheadPayloadType.NUCLEAR ? 0 : 1)
            .thenComparing(selection -> selection.targetLock().rootTrackId().toString()));
    }

    private static Optional<AntiAirTargetSelection> project(RadarTrackingService.RadarTrackTelemetry telemetry,
        Vec3 origin, long now) {
        IcbmFlightPlan carrier = carrierPlan(telemetry);
        @Nullable RadarTerminalPlanSnapshot terminal = telemetry.snapshot().terminalPlan().orElse(null);
        long separation = carrier.launchGameTime() + carrier.separationTick();
        long impact = expectedImpactGameTime(carrier, terminal);
        if (impact < now) return Optional.empty();
        AntiAirTargetLock lock = new AntiAirTargetLock(telemetry.trackId(), telemetry.strategicPayloadType().orElseThrow(),
            carrier, terminal, now, separation, impact);
        Vec3 firstEntry = null;
        long firstEntryTime = Long.MAX_VALUE;
        Vec3 closest = null;
        long closestTime = Long.MAX_VALUE;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (long time = now; ; time = Math.min(impact, time + AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS)) {
            Vec3 position = positionAt(lock, time);
            if (position != null && position.isFinite()) {
                double distance = horizontal(origin, position);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = position;
                    closestTime = time;
                }
                if (firstEntry == null && distance <= AntiAirConstants.DEFENDED_TRAJECTORY_RADIUS_BLOCKS) {
                    firstEntry = position;
                    firstEntryTime = time;
                }
            }
            if (time >= impact) break;
        }
        if (firstEntry == null || closest == null) return Optional.empty();
        AntiAirThreatProjection projection = new AntiAirThreatProjection(lock.rootTrackId(), firstEntry, firstEntryTime,
            closest, closestTime, closestDistance, predictedImpactPosition(lock), impact,
            horizontal(origin, positionAt(lock, now)) <= AntiAirConstants.DEFENDED_TRAJECTORY_RADIUS_BLOCKS,
            terminal != null && now >= terminal.launchGameTime());
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            System.getLogger(AntiAirTargetSelector.class.getName()).log(System.Logger.Level.INFO,
                "Anti-air projection: target={0}, firstEntry={1}, closestDistance={2}, closestTime={3}, impactTime={4}",
                lock.rootTrackId(), firstEntryTime, closestDistance, closestTime, impact);
        }
        return Optional.of(new AntiAirTargetSelection(lock, projection));
    }

    private static IcbmFlightPlan carrierPlan(RadarTrackingService.RadarTrackTelemetry telemetry) {
        var plan = telemetry.snapshot().carrierPlan().orElseThrow();
        return new IcbmFlightPlan(telemetry.trackId(), telemetry.snapshot().ownerPlayerId(), plan.launchPosition(),
            plan.burnoutPosition(), plan.separationPosition(), plan.intendedTarget(), plan.launchGameTime(),
            plan.ignitionTicks(), plan.boostTicks(), plan.coastTicks(), plan.visualSeed(),
            telemetry.strategicPayloadType().orElseThrow());
    }

    public static long expectedImpactGameTime(IcbmFlightPlan carrier, @Nullable RadarTerminalPlanSnapshot terminal) {
        if (terminal != null) return terminal.launchGameTime() + terminal.flightTicks();
        return carrier.launchGameTime() + carrier.separationTick() + expectedTerminalFlightTicks(carrier);
    }

    public static Vec3 positionAt(AntiAirTargetLock lock, long gameTime) {
        long terminalLaunch = terminalLaunchGameTime(lock);
        if (gameTime < terminalLaunch) {
            return IcbmTrajectory.position(lock.carrierPlan(), gameTime - lock.carrierPlan().launchGameTime());
        }
        RadarTerminalPlanSnapshot terminal = lock.terminalPlan();
        Vec3 start = terminal == null ? lock.carrierPlan().separationPosition() : terminal.startPosition();
        Vec3 target = terminal == null ? lock.carrierPlan().intendedTarget() : terminal.targetPosition();
        int ticks = terminal == null ? expectedTerminalFlightTicks(lock.carrierPlan()) : terminal.flightTicks();
        return WarheadTrajectory.position(start, target, gameTime - terminalLaunch, ticks);
    }

    public static Vec3 velocityAt(AntiAirTargetLock lock, long gameTime) {
        long terminalLaunch = terminalLaunchGameTime(lock);
        if (gameTime < terminalLaunch) {
            return IcbmTrajectory.velocity(lock.carrierPlan(), gameTime - lock.carrierPlan().launchGameTime());
        }
        RadarTerminalPlanSnapshot terminal = lock.terminalPlan();
        Vec3 start = terminal == null ? lock.carrierPlan().separationPosition() : terminal.startPosition();
        Vec3 target = terminal == null ? lock.carrierPlan().intendedTarget() : terminal.targetPosition();
        int ticks = terminal == null ? expectedTerminalFlightTicks(lock.carrierPlan()) : terminal.flightTicks();
        return WarheadTrajectory.velocity(start, target, gameTime - terminalLaunch, ticks);
    }

    public static Vec3 predictedImpactPosition(AntiAirTargetLock lock) {
        return lock.terminalPlan() == null ? lock.carrierPlan().intendedTarget() : lock.terminalPlan().targetPosition();
    }

    private static long terminalLaunchGameTime(AntiAirTargetLock lock) {
        return lock.terminalPlan() == null ? lock.separationGameTime() : lock.terminalPlan().launchGameTime();
    }

    private static int expectedTerminalFlightTicks(IcbmFlightPlan carrier) {
        int requested = (int)Math.ceil(carrier.separationPosition().distanceTo(carrier.intendedTarget())
            / WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK);
        return Math.max(IcbmConstants.MINIMUM_TERMINAL_TICKS, Math.min(IcbmConstants.MAXIMUM_TERMINAL_TICKS, requested));
    }

    public static double horizontal(Vec3 a, Vec3 b) {
        if (a == null || b == null) return Double.POSITIVE_INFINITY;
        return Math.hypot(a.x - b.x, a.z - b.z);
    }
}