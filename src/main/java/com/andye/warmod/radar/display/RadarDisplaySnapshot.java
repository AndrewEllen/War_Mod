package com.andye.warmod.radar.display;

import com.andye.warmod.radar.station.RadarStationObservation;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record RadarDisplaySnapshot(
    UUID displayId,
    Identifier dimension,
    BlockPos controller,
    Direction facing,
    int width,
    int height,
    double horizontalRadius,
    double verticalRadius,
    boolean structureValid,
    boolean online,
    RadarDisplayOfflineReason offlineReason,
    @Nullable UUID radarId,
    @Nullable BlockPos radarCentre,
    long serverGameTime,
    long phaseOffset,
    int sweepPeriodTicks,
    double warningRadius,
    double fireRadius,
    int redstoneSignal,
    List<RadarStationObservation> observations
) {
    public RadarDisplaySnapshot {
        if (displayId == null) {
            throw new IllegalArgumentException("displayId");
        }

        if (dimension == null) {
            throw new IllegalArgumentException("dimension");
        }

        if (controller == null) {
            throw new IllegalArgumentException("controller");
        }

        if (facing == null || !facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Invalid display facing");
        }

        if (width < 0 || width > RadarDisplayConstants.MAX_WIDTH) { throw new IllegalArgumentException("Invalid display width: " + width); }
        if (height < 0 || height > RadarDisplayConstants.MAX_HEIGHT) { throw new IllegalArgumentException("Invalid display height: " + height); }

        redstoneSignal = Math.max(0, Math.min(15, redstoneSignal));

        observations = List.copyOf(
            observations.subList(
                0,
                Math.min(
                    observations.size(),
                    RadarDisplayConstants.MAX_OBSERVED_TRACKS
                )
            )
        );
    }
}
