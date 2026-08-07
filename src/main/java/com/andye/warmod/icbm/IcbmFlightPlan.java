package com.andye.warmod.icbm;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record IcbmFlightPlan(UUID missileId, UUID ownerPlayerId, Vec3 launchPosition, Vec3 burnoutPosition,
    Vec3 separationPosition, Vec3 intendedTarget, long launchGameTime, int ignitionTicks, int boostTicks,
    int coastTicks, long visualSeed, WarheadPayloadType payloadType) {
    private static final double LEGACY_VERTICAL_BOOST_TOLERANCE = 2.001;

    public IcbmFlightPlan {
        Objects.requireNonNull(missileId); Objects.requireNonNull(ownerPlayerId); Objects.requireNonNull(launchPosition);
        Objects.requireNonNull(burnoutPosition); Objects.requireNonNull(separationPosition); Objects.requireNonNull(intendedTarget);
        Objects.requireNonNull(payloadType);
        if (launchPosition.isFinite() && burnoutPosition.isFinite() && separationPosition.isFinite()) {
            burnoutPosition = addUpperBoostLead(launchPosition, burnoutPosition, separationPosition);
        }
        double boostHorizontal = Math.hypot(burnoutPosition.x - launchPosition.x, burnoutPosition.z - launchPosition.z);
        if (!launchPosition.isFinite() || !burnoutPosition.isFinite() || !separationPosition.isFinite() || !intendedTarget.isFinite()
            || launchGameTime < 0 || ignitionTicks < 1 || ignitionTicks > 20 || boostTicks < 1 || boostTicks > 200
            || coastTicks < IcbmConstants.MINIMUM_COAST_TICKS || coastTicks > IcbmConstants.MAXIMUM_COAST_TICKS
            || boostHorizontal > IcbmConstants.MAXIMUM_BOOST_HORIZONTAL_DRIFT_BLOCKS
            || burnoutPosition.y <= launchPosition.y || !IcbmRouteRules.strategicRangeValid(launchPosition, intendedTarget)) {
            throw new IllegalArgumentException("Invalid ICBM flight plan");
        }
    }

    private static Vec3 addUpperBoostLead(final Vec3 launch, final Vec3 burnout,
        final Vec3 separation) {
        double existingHorizontal = Math.hypot(burnout.x - launch.x, burnout.z - launch.z);
        if (existingHorizontal > LEGACY_VERTICAL_BOOST_TOLERANCE) return burnout;
        Vec3 horizontal = new Vec3(separation.x - launch.x, 0.0, separation.z - launch.z);
        double distance = horizontal.length();
        if (!Double.isFinite(distance) || distance < 1.0) return burnout;
        double lead = Mth.clamp(distance * IcbmConstants.BOOST_HORIZONTAL_LEAD_FRACTION,
            IcbmConstants.BOOST_HORIZONTAL_LEAD_MINIMUM_BLOCKS,
            IcbmConstants.MAXIMUM_BOOST_HORIZONTAL_DRIFT_BLOCKS);
        Vec3 direction = horizontal.scale(1.0 / distance);
        return new Vec3(launch.x + direction.x * lead, burnout.y,
            launch.z + direction.z * lead);
    }

    public int separationTick() { return ignitionTicks + boostTicks + coastTicks; }
    public int totalCarrierTicks() { return separationTick(); }
}
