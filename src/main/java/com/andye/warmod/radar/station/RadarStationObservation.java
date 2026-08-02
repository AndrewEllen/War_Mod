package com.andye.warmod.radar.station;

import com.andye.warmod.radar.RadarTrackSnapshot;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record RadarStationObservation(
    UUID trackId,
    RadarTrackSnapshot trackSnapshot,
    Vec3 observedPosition,
    Vec3 observedVelocity,
    Vec3 predictedImpactPosition,
    long observationGameTime,
    double observedRouteTime,
    boolean threatensWarningZone
) { }