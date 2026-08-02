package com.andye.warmod.antiair;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.*;
import com.andye.warmod.antiair.network.*;
import com.andye.warmod.radar.*;
import com.andye.warmod.silo.*;
import com.andye.warmod.warhead.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class AntiAirFlightController {
    private final AntiAirFlightPlan plan; private final @Nullable MissileSiloCollisionContext collisionContext;
    private final AntiAirChunkTicketController tickets = new AntiAirChunkTicketController();
    private AntiAirFlightPhase phase = AntiAirFlightPhase.IGNITION;
    private Vec3 previousPosition, currentPosition, currentVelocity = Vec3.ZERO;
    private @Nullable Vec3 previousTarget; private @Nullable AntiAirRoute route;
    private long routeLockTime, selfDestructStart, fallbackStart;
    private boolean completed, fallbackSonicBoomEmitted;
    AntiAirFlightController(AntiAirFlightPlan plan, @Nullable MissileSiloCollisionContext collision) {
        this.plan = plan; collisionContext = collision; previousPosition = currentPosition = plan.launchPosition();
    }
    public AntiAirFlightPlan plan() { return plan; } public boolean completed() { return completed; }
    public void tick(ServerLevel level) {
        if (completed) return;
        long now = level.getGameTime(), elapsed = Math.max(0, now - plan.launchGameTime()); previousPosition = currentPosition;
        if (phase == AntiAirFlightPhase.IGNITION || phase == AntiAirFlightPhase.BOOST) {
            phase = elapsed < plan.ignitionTicks() ? AntiAirFlightPhase.IGNITION : AntiAirFlightPhase.BOOST;
            currentPosition = AntiAirTrajectory.boostPosition(plan, elapsed); currentVelocity = AntiAirTrajectory.boostVelocity(plan, elapsed);
            if (collisionContext != null) { var hit = MissileSiloCollisionDetector.findFirst(level, previousPosition, currentPosition, collisionContext);
                if (hit != null) { detonate(level, hit.impactPosition(), WarheadEffectProfile.ANTI_AIR_LAUNCH_FAILURE); return; } }
            if (elapsed >= plan.ignitionTicks() + plan.boostTicks()) {
                if (plan.debugNoTargetFlight()) beginSelfDestruct(level);
                else if (plan.launchMode() == AntiAirLaunchMode.NO_TARGET_ASCENT) targetLost(level);
                else if (plan.targetLock() == null || StrategicMissileTargetService.state(level, plan.targetRootTrackId(), now).isEmpty()) targetLost(level);
                else lockRoute(level);
            }
        } else if (phase == AntiAirFlightPhase.INTERCEPT) tickIntercept(level, now);
        else if (phase == AntiAirFlightPhase.FALLBACK) tickFallback(level, now);
        else if (phase == AntiAirFlightPhase.SELF_DESTRUCT && now - selfDestructStart >= AntiAirConstants.MK_II_SELF_DESTRUCT_DELAY_TICKS)
            detonate(level, currentPosition, WarheadEffectProfile.ANTI_AIR_SAFE_SELF_DESTRUCT);
    }
    private void lockRoute(ServerLevel level) {
        var nominal = plan.nominalSolution(); if (nominal == null) { targetLost(level); return; }
        Vec3 tangent = nominal.nominalRoute().controlPoint1().subtract(nominal.nominalRoute().start());
        var resolution = AntiAirGuidanceResolver.resolve(new AntiAirGuidanceProfile(plan.interceptorId(),
            plan.sourceSiloId() == null ? plan.interceptorId() : plan.sourceSiloId(), plan.capturedGuidanceTier(),
            nominal.perfectInterceptPosition(), nominal.targetVelocityAtIntercept(), plan.visualSeed() ^ 0x47554944414E4345L), tangent);
        AntiAirRoute nominalRoute = nominal.nominalRoute(); route = new AntiAirRoute(plan.burnoutPosition(),
            nominalRoute.controlPoint1(), nominalRoute.controlPoint2().add(resolution.offset()),
            resolution.resolvedInterceptPosition(), nominalRoute.durationTicks()); routeLockTime = level.getGameTime();
        phase = AntiAirFlightPhase.INTERCEPT;
        RadarInterceptorRouteSnapshot radarRoute = new RadarInterceptorRouteSnapshot(route.controlPoint1(), route.controlPoint2(),
            route.end(), route.durationTicks(), routeLockTime, resolution.missDistance());
        RadarTrackingService.updateInterceptorRoute(level, plan.interceptorId(), radarRoute);
        AntiAirNetworking.send(level, new ClientboundAntiAirRouteLockPayload(plan.interceptorId(), plan.targetRootTrackId(),
            route.controlPoint1(), route.controlPoint2(), route.end(), route.durationTicks(), resolution.missDistance(), routeLockTime));
        AntiAirNetworking.send(level, ClientboundAntiAirPhasePayload.normal(plan.interceptorId(), phase, routeLockTime));
    }
    private void tickIntercept(ServerLevel level, long now) {
        var target = StrategicMissileTargetService.state(level, plan.targetRootTrackId(), now);
        if (target.isEmpty() || !target.get().active()) { targetLost(level); return; }
        double age = now - routeLockTime; currentPosition = route.position(age); currentVelocity = route.velocity(age);
        Vec3 targetPosition = target.get().currentPosition(); if (previousTarget == null) previousTarget = targetPosition.subtract(target.get().currentVelocity());
        Closest closest = closest(previousPosition, currentPosition, previousTarget, targetPosition); previousTarget = targetPosition;
        if (closest.distanceSquared <= AntiAirConstants.INTERCEPT_FUSE_RADIUS_SQUARED) {
            var result = StrategicMissileInterceptionService.intercept(level, plan.targetRootTrackId(), plan.interceptorId(), closest.midpoint);
            if (result.success()) { WarheadImpactService.detonateAntiAir(level, plan.interceptorId(), closest.midpoint, plan.visualSeed(), WarheadEffectProfile.ANTI_AIR_INTERCEPTION);
                RadarTrackingService.registerInterception(level, new RadarInterceptionSnapshot(plan.interceptorId(), plan.targetRootTrackId(), closest.midpoint,
                    level.getGameTime(), plan.variant(), result.payloadType().orElseThrow())); complete(level, RadarRemovalReason.INTERCEPTED); return; }
            targetLost(level); return;
        }
        if (age >= route.durationTicks()) { if (plan.variant().ballisticFallback()) beginFallback(level); else beginSelfDestruct(level); }
    }
    private void targetLost(ServerLevel level) { if (plan.debugNoTargetFlight() || !plan.variant().ballisticFallback()) beginSelfDestruct(level); else beginFallback(level); }
    private void beginFallback(ServerLevel level) {
        phase = AntiAirFlightPhase.FALLBACK; fallbackStart = level.getGameTime();
        if (currentVelocity.lengthSqr() < 1.0E-8) currentVelocity = new Vec3(0, -.1, 0);
        RadarTrackingService.updateInterceptorFallback(level, plan.interceptorId(),
            new RadarInterceptorFallbackSnapshot(fallbackStart, currentPosition, currentVelocity));
        RadarTrackingService.updateInterceptorPhase(level, plan.interceptorId(), RadarTrackPhase.FALLBACK);
        AntiAirNetworking.send(level, new ClientboundAntiAirPhasePayload(plan.interceptorId(), phase, fallbackStart,
            currentPosition, currentVelocity));
        if (SharedConstants.IS_RUNNING_IN_IDE) WarMod.LOGGER.info("Anti-air {} fallback began: position={}, velocity={}",
            plan.interceptorId(), currentPosition, currentVelocity);
    }
    private void tickFallback(ServerLevel level, long now) {
        tickets.updateFallback(level, currentPosition); currentVelocity = AntiAirFallbackTrajectory.nextVelocity(currentVelocity);
        Vec3 next = AntiAirFallbackTrajectory.nextPosition(currentPosition, currentVelocity);
        if (!fallbackSonicBoomEmitted && currentVelocity.length() >= AntiAirConstants.FALLBACK_SONIC_BOOM_SPEED_BLOCKS_PER_TICK) {
            AcousticEngine.playSound(level, currentPosition, AcousticSounds.TERMINAL_SONIC_BOOM_ID, SoundSource.BLOCKS, .72F, 1.04F);
            fallbackSonicBoomEmitted = true;
        }
        MissileSiloCollisionContext context = new MissileSiloCollisionContext(plan.sourceSiloId() == null ? plan.interceptorId() : plan.sourceSiloId(),
            plan.sourceSiloCentre() == null ? net.minecraft.core.BlockPos.containing(currentPosition) : plan.sourceSiloCentre(), Set.of(), .35, 2.2);
        var hit = MissileSiloCollisionDetector.findFirst(level, currentPosition, next, context); currentPosition = next;
        if (hit != null || now - fallbackStart >= AntiAirConstants.MAXIMUM_FALLBACK_LIFETIME_TICKS)
            detonate(level, hit == null ? currentPosition : hit.impactPosition(), WarheadEffectProfile.ANTI_AIR_FALLBACK);
    }
    private void beginSelfDestruct(ServerLevel level) { phase = AntiAirFlightPhase.SELF_DESTRUCT; selfDestructStart = level.getGameTime();
        RadarTrackingService.updateInterceptorPhase(level, plan.interceptorId(), RadarTrackPhase.SELF_DESTRUCT);
        AntiAirNetworking.send(level, ClientboundAntiAirPhasePayload.normal(plan.interceptorId(), phase, selfDestructStart)); }
    private void detonate(ServerLevel level, Vec3 position, WarheadEffectProfile effect) { WarheadImpactService.detonateAntiAir(level,
        plan.interceptorId(), position, plan.visualSeed(), effect); complete(level, RadarRemovalReason.CANCELLED); }
    private void complete(ServerLevel level, RadarRemovalReason reason) { tickets.releaseAll(level); RadarTrackingService.removeInterceptor(level,
        plan.interceptorId(), reason); AntiAirNetworking.send(level, new ClientboundAntiAirRemovePayload(plan.interceptorId())); completed = true; phase = AntiAirFlightPhase.COMPLETED; }
    private static Closest closest(Vec3 a0, Vec3 a1, Vec3 b0, Vec3 b1) { Vec3 r0 = a0.subtract(b0), rv = a1.subtract(a0).subtract(b1.subtract(b0));
        double length = rv.lengthSqr(), t = length < 1.0E-12 ? 0 : Math.max(0, Math.min(1, -r0.dot(rv) / length));
        Vec3 a = a0.lerp(a1, t), b = b0.lerp(b1, t); return new Closest(a.distanceToSqr(b), a.add(b).scale(.5)); }
    private record Closest(double distanceSquared, Vec3 midpoint) { }
}