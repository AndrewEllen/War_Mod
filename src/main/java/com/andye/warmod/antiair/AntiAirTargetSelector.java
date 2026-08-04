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

/**
 * Projects server-authoritative strategic missile paths into the defended
 * horizontal region.
 *
 * Only threats which enter that region during the interceptor's bounded
 * powered-flight horizon are returned. A missile which may pass overhead much
 * later is not a launch candidate.
 */
public final class AntiAirTargetSelector {
    private AntiAirTargetSelector() {
    }

    public static Optional<AntiAirTargetSelection> acquire(
        final ServerLevel level,
        final Vec3 origin
    ) {
        return candidates(level, origin).stream()
            .min(
                Comparator
                    .comparingLong(
                        (AntiAirTargetSelection selection) ->
                            selection.projection().firstEntryGameTime()
                    )
                    .thenComparingLong(
                        selection ->
                            selection.projection().estimatedImpactGameTime()
                    )
                    .thenComparingDouble(
                        selection ->
                            selection.projection()
                                .closestHorizontalDistance()
                    )
                    .thenComparingInt(
                        selection ->
                            selection.targetLock().payloadType()
                                == WarheadPayloadType.NUCLEAR
                                    ? 0
                                    : 1
                    )
                    .thenComparing(
                        selection ->
                            selection.targetLock()
                                .rootTrackId()
                                .toString()
                    )
            );
    }

    public static List<AntiAirTargetSelection> candidates(
        final ServerLevel level,
        final Vec3 origin
    ) {
        long now = level.getGameTime();

        List<AntiAirTargetSelection> candidates =
            new ArrayList<>();

        for (
            RadarTrackingService.RadarTrackTelemetry telemetry
                : RadarTrackingService.currentTelemetry(level, now)
        ) {
            if (telemetry.kind() != RadarTrackKind.ICBM
                || telemetry.phase() == RadarTrackPhase.IMPACT
                || telemetry.strategicPayloadType().isEmpty()
                || telemetry.snapshot().carrierPlan().isEmpty()) {
                continue;
            }

            project(
                telemetry,
                origin,
                now
            ).ifPresent(candidates::add);
        }

        return List.copyOf(candidates);
    }

    private static Optional<AntiAirTargetSelection> project(
        final RadarTrackingService.RadarTrackTelemetry telemetry,
        final Vec3 origin,
        final long now
    ) {
        IcbmFlightPlan carrier =
            carrierPlan(telemetry);

        @Nullable RadarTerminalPlanSnapshot terminal =
            telemetry.snapshot()
                .terminalPlan()
                .orElse(null);

        long separation =
            carrier.launchGameTime()
                + carrier.separationTick();

        long impact =
            expectedImpactGameTime(
                carrier,
                terminal
            );

        if (impact < now) {
            return Optional.empty();
        }

        AntiAirTargetLock lock =
            new AntiAirTargetLock(
                telemetry.trackId(),
                telemetry.strategicPayloadType()
                    .orElseThrow(),
                carrier,
                terminal,
                now,
                separation,
                impact
            );

        /*
         * An interceptor cannot reach an intercept point before ignition and
         * boost have completed.
         */
        long burnoutGameTime =
            now
                + AntiAirConstants.IGNITION_TICKS
                + AntiAirConstants.BOOST_TICKS;

        long earliestIntercept =
            burnoutGameTime
                + AntiAirConstants.MINIMUM_INTERCEPT_LEAD_TICKS;

        /*
         * This is the critical anti-loiter horizon. Do not consider a path
         * entry which occurs hundreds or thousands of ticks in the future.
         */
        long latestIntercept =
            Math.min(
                impact,
                burnoutGameTime
                    + AntiAirConstants
                        .MAXIMUM_POWERED_INTERCEPT_TICKS
            );

        if (latestIntercept < earliestIntercept) {
            return Optional.empty();
        }

        Vec3 firstEntry = null;
        long firstEntryTime = Long.MAX_VALUE;

        Vec3 closest = null;
        long closestTime = Long.MAX_VALUE;
        double closestDistance =
            Double.POSITIVE_INFINITY;

        for (
            long time = earliestIntercept;
            ;
            time = Math.min(
                latestIntercept,
                time + AntiAirConstants.INTERCEPT_SAMPLE_STEP_TICKS
            )
        ) {
            Vec3 position =
                positionAt(lock, time);

            if (position != null
                && position.isFinite()) {
                double distance =
                    horizontal(
                        origin,
                        position
                    );

                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = position;
                    closestTime = time;
                }

                if (firstEntry == null
                    && distance
                        <= AntiAirConstants
                            .DEFENDED_TRAJECTORY_RADIUS_BLOCKS) {
                    firstEntry = position;
                    firstEntryTime = time;
                }
            }

            if (time >= latestIntercept) {
                break;
            }
        }

        if (firstEntry == null
            || closest == null) {
            return Optional.empty();
        }

        Vec3 currentPosition =
            positionAt(lock, now);

        AntiAirThreatProjection projection =
            new AntiAirThreatProjection(
                lock.rootTrackId(),
                firstEntry,
                firstEntryTime,
                closest,
                closestTime,
                closestDistance,
                predictedImpactPosition(lock),
                impact,
                currentPosition != null
                    && horizontal(origin, currentPosition)
                        <= AntiAirConstants
                            .DEFENDED_TRAJECTORY_RADIUS_BLOCKS,
                terminal != null
                    && now >= terminal.launchGameTime()
            );

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            System.getLogger(
                AntiAirTargetSelector.class.getName()
            ).log(
                System.Logger.Level.INFO,
                "Anti-air projection: target={0}, firstReachableEntry={1}, "
                    + "closestDistance={2}, latestIntercept={3}, "
                    + "impactTime={4}",
                lock.rootTrackId(),
                firstEntryTime,
                closestDistance,
                latestIntercept,
                impact
            );
        }

        return Optional.of(
            new AntiAirTargetSelection(
                lock,
                projection
            )
        );
    }

    private static IcbmFlightPlan carrierPlan(
        final RadarTrackingService.RadarTrackTelemetry telemetry
    ) {
        var plan =
            telemetry.snapshot()
                .carrierPlan()
                .orElseThrow();

        return new IcbmFlightPlan(
            telemetry.trackId(),
            telemetry.snapshot().ownerPlayerId(),
            plan.launchPosition(),
            plan.burnoutPosition(),
            plan.separationPosition(),
            plan.intendedTarget(),
            plan.launchGameTime(),
            plan.ignitionTicks(),
            plan.boostTicks(),
            plan.coastTicks(),
            plan.visualSeed(),
            telemetry.strategicPayloadType()
                .orElseThrow()
        );
    }

    public static long expectedImpactGameTime(
        final IcbmFlightPlan carrier,
        final @Nullable RadarTerminalPlanSnapshot terminal
    ) {
        if (terminal != null) {
            return terminal.launchGameTime()
                + terminal.flightTicks();
        }

        return carrier.launchGameTime()
            + carrier.separationTick()
            + expectedTerminalFlightTicks(carrier);
    }

    public static Vec3 positionAt(
        final AntiAirTargetLock lock,
        final long gameTime
    ) {
        long terminalLaunch =
            terminalLaunchGameTime(lock);

        if (gameTime < terminalLaunch) {
            return IcbmTrajectory.position(
                lock.carrierPlan(),
                gameTime
                    - lock.carrierPlan()
                        .launchGameTime()
            );
        }

        RadarTerminalPlanSnapshot terminal =
            lock.terminalPlan();

        Vec3 start =
            terminal == null
                ? lock.carrierPlan()
                    .separationPosition()
                : terminal.startPosition();

        Vec3 target =
            terminal == null
                ? lock.carrierPlan()
                    .intendedTarget()
                : terminal.targetPosition();

        int ticks =
            terminal == null
                ? expectedTerminalFlightTicks(
                    lock.carrierPlan()
                )
                : terminal.flightTicks();

        return WarheadTrajectory.position(
            start,
            target,
            gameTime - terminalLaunch,
            ticks
        );
    }

    public static Vec3 velocityAt(
        final AntiAirTargetLock lock,
        final long gameTime
    ) {
        long terminalLaunch =
            terminalLaunchGameTime(lock);

        if (gameTime < terminalLaunch) {
            return IcbmTrajectory.velocity(
                lock.carrierPlan(),
                gameTime
                    - lock.carrierPlan()
                        .launchGameTime()
            );
        }

        RadarTerminalPlanSnapshot terminal =
            lock.terminalPlan();

        Vec3 start =
            terminal == null
                ? lock.carrierPlan()
                    .separationPosition()
                : terminal.startPosition();

        Vec3 target =
            terminal == null
                ? lock.carrierPlan()
                    .intendedTarget()
                : terminal.targetPosition();

        int ticks =
            terminal == null
                ? expectedTerminalFlightTicks(
                    lock.carrierPlan()
                )
                : terminal.flightTicks();

        return WarheadTrajectory.velocity(
            start,
            target,
            gameTime - terminalLaunch,
            ticks
        );
    }

    public static Vec3 predictedImpactPosition(
        final AntiAirTargetLock lock
    ) {
        return lock.terminalPlan() == null
            ? lock.carrierPlan().intendedTarget()
            : lock.terminalPlan().targetPosition();
    }

    private static long terminalLaunchGameTime(
        final AntiAirTargetLock lock
    ) {
        return lock.terminalPlan() == null
            ? lock.separationGameTime()
            : lock.terminalPlan().launchGameTime();
    }

    private static int expectedTerminalFlightTicks(
        final IcbmFlightPlan carrier
    ) {
        int requested =
            (int)Math.ceil(
                carrier.separationPosition()
                    .distanceTo(
                        carrier.intendedTarget()
                    )
                    / WarheadConstants
                        .TRAJECTORY_SPEED_BLOCKS_PER_TICK
            );

        return Math.max(
            IcbmConstants.MINIMUM_TERMINAL_TICKS,
            Math.min(
                IcbmConstants.MAXIMUM_TERMINAL_TICKS,
                requested
            )
        );
    }

    public static double horizontal(
        final Vec3 first,
        final Vec3 second
    ) {
        if (first == null
            || second == null) {
            return Double.POSITIVE_INFINITY;
        }

        return Math.hypot(
            first.x - second.x,
            first.z - second.z
        );
    }
}
