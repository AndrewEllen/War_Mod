package com.andye.warmod.radar;

import com.andye.warmod.antiair.AntiAirMissileVariant;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record RadarInterceptorPlanSnapshot(AntiAirMissileVariant variant, Optional<UUID> targetRootTrackId,
    Vec3 launchPosition, Vec3 burnoutPosition, Vec3 noTargetHorizontalOffset, long launchGameTime,
    int ignitionTicks, int boostTicks, int guidanceTier, double maximumMissDistance,
    Optional<RadarInterceptorRouteSnapshot> route, Optional<RadarInterceptorFallbackSnapshot> fallback) { }