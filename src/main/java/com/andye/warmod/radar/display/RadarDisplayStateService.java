package com.andye.warmod.radar.display;

import com.andye.warmod.block.RadarDisplayPanelBlock;
import com.andye.warmod.block.RadarStationStructure;
import com.andye.warmod.block.entity.RadarDisplayPanelBlockEntity;
import com.andye.warmod.block.entity.RadarStationBlockEntity;
import com.andye.warmod.radar.RadarTrackPhase;
import com.andye.warmod.radar.station.RadarStationConstants;
import com.andye.warmod.radar.station.RadarStationObservation;
import java.nio.charset.StandardCharsets;
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
            displayId = UUID.nameUUIDFromBytes((
                level.dimension().identifier() + ":" + display.getBlockPos()
            ).getBytes(StandardCharsets.UTF_8));
        }

        Direction facing = display.getBlockState().getValue(RadarDisplayPanelBlock.FACING);
        int width = display.width();
        int height = display.height();
        double offlineHorizontalRadius = display.horizontalRadius();
        double offlineVerticalRadius = display.verticalRadius();

        if (!display.valid() || !display.controllerPanel()) {
            return offline(level, displayId, display.controller(), facing, width, height,
                offlineHorizontalRadius, offlineVerticalRadius, false,
                RadarDisplayOfflineReason.INVALID_STRUCTURE);
        }

        RadarDisplayLink link = display.link();
        if (link == null) {
            return offline(level, displayId, display.controller(), facing, width, height,
                offlineHorizontalRadius, offlineVerticalRadius, true,
                RadarDisplayOfflineReason.UNLINKED);
        }
        if (!link.dimension().equals(level.dimension())) {
            return offline(level, displayId, display.controller(), facing, width, height,
                offlineHorizontalRadius, offlineVerticalRadius, true,
                RadarDisplayOfflineReason.WRONG_DIMENSION);
        }

        double allowedDistanceSquared = RadarDisplayConstants.LINK_DISTANCE_BLOCKS
            * RadarDisplayConstants.LINK_DISTANCE_BLOCKS;
        if (Vec3.atCenterOf(display.controller()).distanceToSqr(
            Vec3.atCenterOf(link.centre())) > allowedDistanceSquared) {
            return offline(level, displayId, display.controller(), facing, width, height,
                offlineHorizontalRadius, offlineVerticalRadius, true,
                RadarDisplayOfflineReason.OUT_OF_LINK_RANGE);
        }

        if (!(level.getBlockEntity(link.centre()) instanceof RadarStationBlockEntity station)) {
            return offline(level, displayId, display.controller(), facing, width, height,
                offlineHorizontalRadius, offlineVerticalRadius, true,
                RadarDisplayOfflineReason.STATION_MISSING);
        }
        if (!station.radarId().equals(link.radarId())) {
            return offline(level, displayId, display.controller(), facing, width, height,
                offlineHorizontalRadius, offlineVerticalRadius, true,
                RadarDisplayOfflineReason.STATION_REPLACED);
        }
        if (!RadarStationStructure.complete(
            level, station.getBlockPos(), station.facing())) {
            return offline(level, displayId, display.controller(), facing, width, height,
                offlineHorizontalRadius, offlineVerticalRadius, true,
                RadarDisplayOfflineReason.STATION_STRUCTURE_INVALID);
        }

        /*
         * Every panel represents a fixed world area. A 1x1 display therefore
         * shows a useful close view, while additional panels reveal additional
         * terrain at the same scale. On a 5x5 wall the 500-block fire circle is
         * approximately the size of the centre panel rather than a few pixels.
         */
        double horizontalRadius = RadarDisplayConstants.horizontalRadius(width);
        double verticalRadius = RadarDisplayConstants.verticalRadius(height);

        List<RadarStationObservation> observations = new ArrayList<>();
        for (RadarStationObservation observation : station.observations()) {
            if (observation.trackSnapshot().phase() == RadarTrackPhase.IMPACT) continue;
            observations.add(observation);
            if (observations.size() >= RadarDisplayConstants.MAX_OBSERVED_TRACKS) break;
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
}
