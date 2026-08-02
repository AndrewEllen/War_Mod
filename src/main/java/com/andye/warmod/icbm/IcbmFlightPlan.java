package com.andye.warmod.icbm;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.*;
import net.minecraft.world.phys.Vec3;

public record IcbmFlightPlan(UUID missileId, UUID ownerPlayerId, Vec3 launchPosition, Vec3 burnoutPosition,
    Vec3 separationPosition, Vec3 intendedTarget, long launchGameTime, int ignitionTicks, int boostTicks,
    int coastTicks, long visualSeed, WarheadPayloadType payloadType) {
    public IcbmFlightPlan {
        Objects.requireNonNull(missileId); Objects.requireNonNull(ownerPlayerId); Objects.requireNonNull(launchPosition);
        Objects.requireNonNull(burnoutPosition); Objects.requireNonNull(separationPosition); Objects.requireNonNull(intendedTarget);
        Objects.requireNonNull(payloadType);
        double boostHorizontal = Math.hypot(burnoutPosition.x - launchPosition.x, burnoutPosition.z - launchPosition.z);
        if (!launchPosition.isFinite() || !burnoutPosition.isFinite() || !separationPosition.isFinite() || !intendedTarget.isFinite()
            || launchGameTime < 0 || ignitionTicks < 1 || ignitionTicks > 20 || boostTicks < 1 || boostTicks > 200
            || coastTicks < IcbmConstants.MINIMUM_COAST_TICKS || coastTicks > IcbmConstants.MAXIMUM_COAST_TICKS
            || boostHorizontal > IcbmConstants.MAXIMUM_BOOST_HORIZONTAL_DRIFT_BLOCKS
            || burnoutPosition.y <= launchPosition.y || launchPosition.distanceTo(intendedTarget) > IcbmConstants.MAXIMUM_COMMAND_ROUTE_LENGTH
            || launchPosition.distanceTo(burnoutPosition) > IcbmConstants.MAXIMUM_COMMAND_ROUTE_LENGTH
            || burnoutPosition.distanceTo(separationPosition) > IcbmConstants.MAXIMUM_COMMAND_ROUTE_LENGTH) {
            throw new IllegalArgumentException("Invalid ICBM flight plan");
        }
    }
    public int separationTick() { return ignitionTicks + boostTicks + coastTicks; }
    public int totalCarrierTicks() { return separationTick(); }
}