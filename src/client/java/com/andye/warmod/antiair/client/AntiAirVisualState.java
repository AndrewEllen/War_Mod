package com.andye.warmod.antiair.client;

import com.andye.warmod.antiair.*;
import com.andye.warmod.antiair.network.*;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class AntiAirVisualState {
    private final ClientboundAntiAirLaunchPayload launch;
    private AntiAirRoute route;
    private long routeLockTime, fallbackStartGameTime;
    private Vec3 fallbackStartPosition, fallbackStartVelocity;
    private AntiAirFlightPhase phase = AntiAirFlightPhase.IGNITION;
    public AntiAirVisualState(ClientboundAntiAirLaunchPayload payload) { launch = payload; }
    public UUID id() { return launch.interceptorId(); } public AntiAirMissileVariant variant() { return launch.variant(); }
    public long seed() { return launch.visualSeed(); } public AntiAirFlightPhase phase() { return phase; }
    public void lock(ClientboundAntiAirRouteLockPayload payload) {
        route = new AntiAirRoute(launch.burnoutPosition(), payload.controlPoint1(), payload.controlPoint2(),
            payload.resolvedInterceptPosition(), payload.interceptTicks()); routeLockTime = payload.routeLockGameTime();
        phase = AntiAirFlightPhase.INTERCEPT;
    }
    public void phase(ClientboundAntiAirPhasePayload payload) {
        phase = payload.phase();
        if (phase == AntiAirFlightPhase.FALLBACK && payload.transitionPosition() != null && payload.transitionVelocity() != null) {
            fallbackStartGameTime = payload.gameTime(); fallbackStartPosition = payload.transitionPosition();
            fallbackStartVelocity = payload.transitionVelocity();
        }
    }
    public Vec3 position(long time, double partial) {
        double now = time + partial;
        if (phase == AntiAirFlightPhase.FALLBACK && fallbackStartPosition != null && fallbackStartVelocity != null)
            return AntiAirFallbackTrajectory.positionAt(fallbackStartPosition, fallbackStartVelocity, now - fallbackStartGameTime);
        if (route != null && now >= routeLockTime) return route.position(now - routeLockTime);
        double u = Math.max(0, Math.min(1, (now - launch.launchGameTime() - launch.ignitionTicks()) / launch.boostTicks()));
        u = u * u * (3 - 2 * u); return launch.launchPosition().lerp(launch.burnoutPosition(), u);
    }
    public Vec3 velocity(long time, double partial) {
        if (phase == AntiAirFlightPhase.FALLBACK && fallbackStartVelocity != null)
            return AntiAirFallbackTrajectory.velocityAt(fallbackStartVelocity, time + partial - fallbackStartGameTime);
        return position(time, partial + .1).subtract(position(time, partial - .1)).scale(5);
    }
    public double fallbackAge(long time, double partial) { return fallbackStartPosition == null ? 0 : Math.max(0, time + partial - fallbackStartGameTime); }
    public boolean fallback() { return phase == AntiAirFlightPhase.FALLBACK && fallbackStartPosition != null; }
    public boolean thrust() { return !fallback() && phase != AntiAirFlightPhase.SELF_DESTRUCT; }
}