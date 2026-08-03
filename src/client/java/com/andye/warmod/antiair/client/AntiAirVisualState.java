package com.andye.warmod.antiair.client;

import com.andye.warmod.antiair.AntiAirFallbackTrajectory;
import com.andye.warmod.antiair.AntiAirFlightPhase;
import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.antiair.AntiAirRoute;
import com.andye.warmod.antiair.AntiAirTrajectory;
import com.andye.warmod.antiair.network.ClientboundAntiAirLaunchPayload;
import com.andye.warmod.antiair.network.ClientboundAntiAirPhasePayload;
import com.andye.warmod.antiair.network.ClientboundAntiAirRouteLockPayload;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class AntiAirVisualState {
    private final ClientboundAntiAirLaunchPayload launch;
    private AntiAirRoute route;
    private long routeLockTime;
    private long fallbackStartGameTime;
    private Vec3 fallbackStartPosition;
    private Vec3 fallbackStartVelocity;
    private AntiAirFlightPhase phase = AntiAirFlightPhase.IGNITION;

    public AntiAirVisualState(ClientboundAntiAirLaunchPayload payload) { launch = payload; }
    public UUID id() { return launch.interceptorId(); }
    public AntiAirMissileVariant variant() { return launch.variant(); }
    public long seed() { return launch.visualSeed(); }
    public AntiAirFlightPhase phase() { return phase; }

    public void lock(ClientboundAntiAirRouteLockPayload payload) {
        route = new AntiAirRoute(launch.burnoutPosition(), payload.controlPoint1(), payload.controlPoint2(),
            payload.resolvedInterceptPosition(), payload.interceptTicks());
        routeLockTime = payload.routeLockGameTime();
        phase = AntiAirFlightPhase.INTERCEPT;
    }

    public void phase(ClientboundAntiAirPhasePayload payload) {
        phase = payload.phase();
        if (phase == AntiAirFlightPhase.FALLBACK && payload.transitionPosition() != null && payload.transitionVelocity() != null) {
            fallbackStartGameTime = payload.gameTime();
            fallbackStartPosition = payload.transitionPosition();
            fallbackStartVelocity = payload.transitionVelocity();
        }
    }

    public Vec3 position(long time, double partial) {
        double now = time + partial;
        if (phase == AntiAirFlightPhase.FALLBACK && fallbackStartPosition != null && fallbackStartVelocity != null)
            return AntiAirFallbackTrajectory.positionAt(fallbackStartPosition, fallbackStartVelocity, now - fallbackStartGameTime);
        if (route != null && now >= routeLockTime) return route.position(now - routeLockTime);
        return AntiAirTrajectory.boostPosition(launch.launchPosition(), launch.burnoutPosition(),
            launch.noTargetHorizontalOffset(), launch.ignitionTicks(), launch.boostTicks(), now - launch.launchGameTime());
    }

    public Vec3 velocity(long time, double partial) {
        if (phase == AntiAirFlightPhase.FALLBACK && fallbackStartVelocity != null)
            return AntiAirFallbackTrajectory.velocityAt(fallbackStartVelocity, time + partial - fallbackStartGameTime);
        return position(time, partial + .1).subtract(position(time, partial - .1)).scale(5.0);
    }

    public double fallbackAge(long time, double partial) {
        return fallbackStartPosition == null ? 0.0 : Math.max(0.0, time + partial - fallbackStartGameTime);
    }
    public boolean fallback() { return phase == AntiAirFlightPhase.FALLBACK && fallbackStartPosition != null; }
    public boolean thrust() { return !fallback() && phase != AntiAirFlightPhase.SELF_DESTRUCT; }
}