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

        int size = display.size();
        int radius = display.radius();

        if (!display.valid() || !display.controllerPanel()) {
            return offline(
                level,
                displayId,
                display.controller(),
                facing,
                size,
                radius,
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
                size,
                radius,
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
                size,
                radius,
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
                size,
                radius,
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
                size,
                radius,
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
                size,
                radius,
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
                size,
                radius,
                true,
                RadarDisplayOfflineReason.STATION_STRUCTURE_INVALID
            );
        }

        Vec3 radarCentre = Vec3.atCenterOf(station.getBlockPos());
        double radiusSquared = radius * (double)radius;

        List<RadarStationObservation> observations = new ArrayList<>();

        for (RadarStationObservation observation : station.observations()) {
            if (!touchesViewport(
                observation,
                radarCentre,
                radiusSquared
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
            size,
            radius,
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
        final int size,
        final int radius,
        final boolean structureValid,
        final RadarDisplayOfflineReason reason
    ) {
        return new RadarDisplaySnapshot(
            displayId,
            level.dimension().identifier(),
            controller,
            facing,
            Math.max(0, size),
            Math.max(0, radius),
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
        final double radiusSquared
    ) {
        if (inside(observation.observedPosition(), centre, radiusSquared)
            || inside(
                observation.predictedImpactPosition(),
                centre,
                radiusSquared
            )) {
            return true;
        }

        RadarTrackSnapshot snapshot = observation.trackSnapshot();

        if (snapshot.carrierPlan().isPresent()) {
            RadarCarrierPlanSnapshot carrier =
                snapshot.carrierPlan().get();

            if (inside(carrier.launchPosition(), centre, radiusSquared)
                || inside(carrier.burnoutPosition(), centre, radiusSquared)
                || inside(
                    carrier.separationPosition(),
                    centre,
                    radiusSquared
                )
                || inside(carrier.intendedTarget(), centre, radiusSquared)) {
                return true;
            }
        }

        for (RadarTerminalPlanSnapshot terminal
            : snapshot.terminalPlans()) {
            if (inside(terminal.startPosition(), centre, radiusSquared)
                || inside(
                    terminal.targetPosition(),
                    centre,
                    radiusSquared
                )) {
                return true;
            }
        }

        if (snapshot.interceptorPlan().isPresent()) {
            RadarInterceptorPlanSnapshot interceptor =
                snapshot.interceptorPlan().get();

            if (inside(
                    interceptor.launchPosition(),
                    centre,
                    radiusSquared
                )
                || inside(
                    interceptor.burnoutPosition(),
                    centre,
                    radiusSquared
                )
                || interceptor.route().map(route -> inside(
                    route.resolvedInterceptPosition(),
                    centre,
                    radiusSquared
                )).orElse(false)) {
                return true;
            }
        }

        return false;
    }

    private static boolean inside(
        final Vec3 point,
        final Vec3 centre,
        final double radiusSquared
    ) {
        double deltaX = point.x - centre.x;
        double deltaZ = point.z - centre.z;

        return deltaX * deltaX + deltaZ * deltaZ <= radiusSquared;
    }
}
