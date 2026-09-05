package com.andye.warmod.antiair;

import java.util.UUID;
import com.andye.warmod.defence.MissileAffiliation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record AntiAirPointDefenceSnapshot(
    UUID interceptorId,
    @Nullable UUID ownerPlayerId,
    MissileAffiliation affiliation,
    Vec3 currentPosition,
    Vec3 currentVelocity,
    AntiAirFlightPhase phase,
    AntiAirMissileVariant variant,
    boolean active,
    AntiAirFlightPlan flightPlan,
    @Nullable AntiAirRoute lockedRoute,
    long routeLockGameTime,
    long capturedGameTime
) {
    public boolean forcedHostile() {
        return phase == AntiAirFlightPhase.FALLBACK;
    }

    public Vec3 projectedPosition(final double ticks) {
        double bounded = Math.max(0.0, ticks);
        if (phase == AntiAirFlightPhase.FALLBACK) {
            return AntiAirFallbackTrajectory.positionAt(currentPosition, currentVelocity, bounded);
        }
        long gameTime = capturedGameTime + (long)Math.ceil(bounded);
        if (phase == AntiAirFlightPhase.IGNITION || phase == AntiAirFlightPhase.BOOST) {
            double elapsed = Math.max(0.0, gameTime - flightPlan.launchGameTime());
            double burnout = flightPlan.ignitionTicks() + flightPlan.boostTicks();
            if (elapsed <= burnout)
                return AntiAirTrajectory.boostPosition(flightPlan, elapsed);
            if (flightPlan.nominalSolution() != null)
                return flightPlan.nominalSolution().nominalRoute().position(elapsed - burnout);
        }
        if (lockedRoute != null)
            return lockedRoute.position(Math.max(0.0, gameTime - routeLockGameTime));
        return currentPosition.add(currentVelocity.scale(bounded));
    }
}
