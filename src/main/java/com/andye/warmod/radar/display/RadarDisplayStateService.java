package com.andye.warmod.radar.display;

import com.andye.warmod.block.RadarDisplayPanelBlock;
import com.andye.warmod.block.RadarStationStructure;
import com.andye.warmod.block.entity.RadarDisplayPanelBlockEntity;
import com.andye.warmod.block.entity.RadarStationBlockEntity;
import com.andye.warmod.radar.RadarCarrierPlanSnapshot;
import com.andye.warmod.radar.RadarInterceptorPlanSnapshot;
import com.andye.warmod.radar.RadarTerminalPlanSnapshot;
import com.andye.warmod.radar.RadarTrackSnapshot;
import com.andye.warmod.radar.station.RadarStationConstants;
import com.andye.warmod.radar.station.RadarStationObservation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class RadarDisplayStateService {
    private RadarDisplayStateService() {
    }

    public static RadarDisplaySnapshot snapshot(
        final ServerLevel level,
        final RadarDisplayPanelBlockEntity display
    ) {
        UUID displayId = display.displayId();

        if (displayId == null) {
            displayId = UUID.nameUUIDFromBytes(
                (
                    level.dimension().identifier()
                    + ":"
                    + display.getBlockPos()
                ).getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        Direction facing = display.getBlockState()
            .getValue(RadarDisplayPanelBlock.FACING);

        int width = display.width();
        int height = display.height();
        double horizontalRadius = display.horizontalRadius();
        double verticalRadius = display.verticalRadius();

        if (!display.valid() || !display.controllerPanel()) {
            return offline(
                level,
                displayId,
                display.controller(),
                facing,
                width,
                height,
                horizontalRadius,
                verticalRadius,
                false,
                RadarDisplayOfflineReason.INVALID_STRUCTURE
            );
        }

        RadarDisplayLink link = display.link();

        if (link == null) {
            return offline(
                level,
                displayId,
                display.controller(),
                facing,
                width,
                height,
                horizontalRadius,
                verticalRadius,
                true,
                RadarDisplayOfflineReason.UNLINKED
            );
        }

        if (!link.dimension().equals(level.dimension())) {
            return offline(
                level,
                displayId,
                display.controller(),
                facing,
                width,
                height,
                horizontalRadius,
                verticalRadius,
                true,
                RadarDisplayOfflineReason.WRONG_DIMENSION
            );
        }

        double allowedDistanceSquared =
            RadarDisplayConstants.LINK_DISTANCE_BLOCKS
            * RadarDisplayConstants.LINK_DISTANCE_BLOCKS;

        if (Vec3.atCenterOf(display.controller()).distanceToSqr(
            Vec3.atCenterOf(link.centre())
        ) > allowedDistanceSquared) {
            return offline(
                level,
                displayId,
                display.controller(),
                facing,
                width,
                height,
                horizontalRadius,
                verticalRadius,
                true,
                RadarDisplayOfflineReason.OUT_OF_LINK_RANGE
            );
        }

        if (!(level.getBlockEntity(link.centre())
            instanceof RadarStationBlockEntity station)) {
            return offline(
                level,
                displayId,
                display.controller(),
                facing,
                width,
                height,
                horizontalRadius,
                verticalRadius,
                true,
                RadarDisplayOfflineReason.STATION_MISSING
            );
        }

        if (!station.radarId().equals(link.radarId())) {
            return offline(
                level,
                displayId,
                display.controller(),
                facing,
                width,
                height,
                horizontalRadius,
                verticalRadius,
                true,
                RadarDisplayOfflineReason.STATION_REPLACED
            );
        }

        if (!RadarStationStructure.complete(
            level,
            station.getBlockPos(),
            station.facing()
        )) {
            return offline(
                level,
                displayId,
                display.controller(),
                facing,
                width,
                height,
                horizontalRadius,
                verticalRadius,
                true,
                RadarDisplayOfflineReason.STATION_STRUCTURE_INVALID
            );
        }

        Vec3 radarCentre = Vec3.atCenterOf(station.getBlockPos());


        List<RadarStationObservation> observations = new ArrayList<>();

        for (RadarStationObservation observation : station.observations()) {
            if (!touchesViewport(
                observation,
                radarCentre,
                horizontalRadius,
                verticalRadius
            )) {
                continue;
            }

            observations.add(observation);

            if (observations.size()
                >= RadarDisplayConstants.MAX_OBSERVED_TRACKS) {
                break;
            }
        }

        return new RadarDisplaySnapshot(
            displayId,
            level.dimension().identifier(),
            display.controller(),
            facing,
            width,
            height,
            horizontalRadius,
            verticalRadius,
            true,
            true,
            RadarDisplayOfflineReason.NONE,
            station.radarId(),
            station.getBlockPos(),
            level.getGameTime(),
            station.phaseOffset(),
            RadarStationConstants.SWEEP_PERIOD_TICKS,
            station.warningRadius(),
            station.fireRadius(),
            station.redstoneSignal(),
            observations
        );
    }

    private static RadarDisplaySnapshot offline(
        final ServerLevel level,
        final UUID displayId,
        final BlockPos controller,
        final Direction facing,
        final int width,
        final int height,
        final double horizontalRadius,
        final double verticalRadius,
        final boolean structureValid,
        final RadarDisplayOfflineReason reason
    ) {
        return new RadarDisplaySnapshot(
            displayId,
            level.dimension().identifier(),
            controller,
            facing,
            Math.max(0, width),
            Math.max(0, height),
            Math.max(0.0, horizontalRadius),
            Math.max(0.0, verticalRadius),
            structureValid,
            false,
            reason,
            null,
            null,
            level.getGameTime(),
            0L,
            RadarStationConstants.SWEEP_PERIOD_TICKS,
            0.0,
            0.0,
            0,
            List.of()
        );
    }

    private static boolean touchesViewport(
        final RadarStationObservation observation,
        final Vec3 centre,
        final double horizontalRadius,
        final double verticalRadius
    ) {
        if (insideViewport(observation.observedPosition(), centre, horizontalRadius, verticalRadius)
            || insideViewport(
                observation.predictedImpactPosition(),
                centre,
                horizontalRadius,
                verticalRadius
            )) {
            return true;
        }

        RadarTrackSnapshot snapshot = observation.trackSnapshot();

        if (snapshot.carrierPlan().isPresent()) {
            RadarCarrierPlanSnapshot carrier =
                snapshot.carrierPlan().get();

            if (insideViewport(carrier.launchPosition(), centre, horizontalRadius, verticalRadius)
                || insideViewport(carrier.burnoutPosition(), centre, horizontalRadius, verticalRadius)
                || insideViewport(
                    carrier.separationPosition(),
                    centre,
                    horizontalRadius,
                verticalRadius
                )
                || insideViewport(carrier.intendedTarget(), centre, horizontalRadius, verticalRadius)) {
                return true;
            }
        }

        for (RadarTerminalPlanSnapshot terminal
            : snapshot.terminalPlans()) {
            if (insideViewport(terminal.startPosition(), centre, horizontalRadius, verticalRadius)
                || insideViewport(
                    terminal.targetPosition(),
                    centre,
                    horizontalRadius,
                verticalRadius
                )) {
                return true;
            }
        }

        if (snapshot.interceptorPlan().isPresent()) {
            RadarInterceptorPlanSnapshot interceptor =
                snapshot.interceptorPlan().get();

            if (insideViewport(
                    interceptor.launchPosition(),
                    centre,
                    horizontalRadius,
                verticalRadius
                )
                || insideViewport(
                    interceptor.burnoutPosition(),
                    centre,
                    horizontalRadius,
                verticalRadius
                )
                || interceptor.route().map(route -> insideViewport(
                    route.resolvedInterceptPosition(),
                    centre,
                    horizontalRadius,
                verticalRadius
                )).orElse(false)) {
                return true;
            }
        }

        return false;
    }

    private static boolean insideViewport(final Vec3 point, final Vec3 centre, final double horizontalRadius, final double verticalRadius) { return Math.abs(point.x - centre.x) <= horizontalRadius && Math.abs(point.z - centre.z) <= verticalRadius; }
}
