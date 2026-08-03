package com.andye.warmod.antiair;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record AntiAirFlightPlan(UUID interceptorId, @Nullable UUID ownerPlayerId, @Nullable UUID sourceSiloId,
    @Nullable BlockPos sourceSiloCentre, AntiAirMissileVariant variant, @Nullable UUID targetRootTrackId,
    Vec3 launchPosition, Vec3 burnoutPosition, Vec3 noTargetHorizontalOffset, @Nullable AntiAirTargetLock targetLock,
    @Nullable AntiAirInterceptSolution nominalSolution, AntiAirLaunchMode launchMode, long launchGameTime,
    int ignitionTicks, int boostTicks, long visualSeed, int capturedGuidanceTier, boolean debugNoTargetFlight) {
    public AntiAirFlightPlan {
        boolean routeRequired = launchMode == AntiAirLaunchMode.TRACKED_INTERCEPT
            || launchMode == AntiAirLaunchMode.BEST_EFFORT_INTERCEPT;
        if ((routeRequired && (targetLock == null || nominalSolution == null || targetRootTrackId == null))
            || (!routeRequired && (targetLock != null || nominalSolution != null || targetRootTrackId != null))
            || launchPosition == null || burnoutPosition == null || noTargetHorizontalOffset == null
            || !launchPosition.isFinite() || !burnoutPosition.isFinite() || !noTargetHorizontalOffset.isFinite()
            || Math.abs(noTargetHorizontalOffset.y) > 1.0E-6
            || (routeRequired && noTargetHorizontalOffset.lengthSqr() > 1.0E-8)) {
            throw new IllegalArgumentException("Invalid anti-air flight plan");
        }
    }
}