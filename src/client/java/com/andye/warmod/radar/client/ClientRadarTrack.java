package com.andye.warmod.radar.client;

import com.andye.warmod.antiair.AntiAirFallbackTrajectory;
import com.andye.warmod.antiair.AntiAirRoute;
import com.andye.warmod.antiair.AntiAirTrajectory;
import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.radar.RadarCarrierPlanSnapshot;
import com.andye.warmod.radar.RadarInterceptorPlanSnapshot;
import com.andye.warmod.radar.RadarInterceptorRouteSnapshot;
import com.andye.warmod.radar.RadarTerminalPlanSnapshot;
import com.andye.warmod.radar.RadarTrackPhase;
import com.andye.warmod.radar.RadarTrackSnapshot;
import com.andye.warmod.warhead.WarheadTrajectory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class ClientRadarTrack {
    private RadarTrackSnapshot snapshot;
    private List<Vec3> carrierRoute = List.of();
    private List<Vec3> terminalRoute = List.of();
    private double carrierDuration;
    private double terminalDuration;

    public ClientRadarTrack(final RadarTrackSnapshot snapshot) {
        this.snapshot = snapshot;
        rebuildRoutes();
    }

    public void update(final RadarTrackSnapshot updated) {
        snapshot = updated;
        rebuildRoutes();
    }

    public RadarTrackSnapshot snapshot() { return snapshot; }
    public UUID id() { return snapshot.trackId(); }
    public List<Vec3> carrierRoute() { return carrierRoute; }
    public List<Vec3> terminalRoute() { return terminalRoute; }
    public double carrierDuration() { return carrierDuration; }
    public double terminalDuration() { return terminalDuration; }

    public Vec3 position(final double time) {
        if (snapshot.phase() == RadarTrackPhase.IMPACT) return target();
        if (snapshot.interceptorPlan().isPresent())
            return interceptorPosition(snapshot.interceptorPlan().get(), time);
        if (snapshot.terminalPlan().isPresent()) {
            RadarTerminalPlanSnapshot plan = snapshot.terminalPlan().get();
            return WarheadTrajectory.position(plan.startPosition(), plan.targetPosition(),
                Math.max(0, time - plan.launchGameTime()), plan.flightTicks(),
                plan.clusterIndex(), plan.clusterCount());
        }
        if (snapshot.carrierPlan().isPresent()) {
            RadarCarrierPlanSnapshot plan = snapshot.carrierPlan().get();
            return IcbmTrajectory.position(asPlan(plan),
                Math.max(0, time - plan.launchGameTime()));
        }
        return Vec3.ZERO;
    }

    public Vec3 velocity(final double time) {
        if (snapshot.interceptorPlan().isPresent())
            return position(time + .25).subtract(position(time - .25)).scale(2);
        if (snapshot.terminalPlan().isPresent()) {
            RadarTerminalPlanSnapshot plan = snapshot.terminalPlan().get();
            return WarheadTrajectory.velocity(plan.startPosition(), plan.targetPosition(),
                Math.max(0, time - plan.launchGameTime()), plan.flightTicks(),
                plan.clusterIndex(), plan.clusterCount());
        }
        if (snapshot.carrierPlan().isPresent()) {
            RadarCarrierPlanSnapshot plan = snapshot.carrierPlan().get();
            return IcbmTrajectory.velocity(asPlan(plan),
                Math.max(0, time - plan.launchGameTime()));
        }
        return Vec3.ZERO;
    }

    public double carrierElapsed(final double time) {
        return snapshot.interceptorPlan()
            .map(plan -> Math.max(0, time - plan.launchGameTime()))
            .orElseGet(() -> snapshot.carrierPlan()
                .map(plan -> Math.max(0, time - plan.launchGameTime())).orElse(0.0));
    }

    public double terminalElapsed(final double time) {
        return snapshot.terminalPlan()
            .map(plan -> Math.max(0, time - plan.launchGameTime())).orElse(0.0);
    }

    public Vec3 target() {
        return snapshot.interceptorPlan()
            .map(plan -> plan.route().map(RadarInterceptorRouteSnapshot::resolvedInterceptPosition)
                .orElse(plan.burnoutPosition()))
            .orElseGet(() -> snapshot.terminalPlan()
                .map(RadarTerminalPlanSnapshot::targetPosition)
                .orElseGet(() -> snapshot.carrierPlan()
                    .map(RadarCarrierPlanSnapshot::intendedTarget).orElse(Vec3.ZERO)));
    }

    public Vec3 launch() {
        return snapshot.interceptorPlan().map(RadarInterceptorPlanSnapshot::launchPosition)
            .orElseGet(() -> snapshot.carrierPlan().map(RadarCarrierPlanSnapshot::launchPosition)
                .orElseGet(() -> snapshot.terminalPlan()
                    .map(RadarTerminalPlanSnapshot::startPosition).orElse(Vec3.ZERO)));
    }

    private Vec3 interceptorPosition(final RadarInterceptorPlanSnapshot plan, final double time) {
        long boostEnd = plan.launchGameTime() + plan.ignitionTicks() + plan.boostTicks();
        if (plan.fallback().isPresent()
            && time >= plan.fallback().get().transitionGameTime()) {
            var fallback = plan.fallback().get();
            return AntiAirFallbackTrajectory.positionAt(fallback.transitionPosition(),
                fallback.transitionVelocity(), time - fallback.transitionGameTime());
        }
        if (time <= boostEnd || plan.route().isEmpty()) {
            return AntiAirTrajectory.boostPosition(plan.launchPosition(), plan.burnoutPosition(),
                plan.noTargetHorizontalOffset(), plan.ignitionTicks(), plan.boostTicks(),
                time - plan.launchGameTime());
        }
        RadarInterceptorRouteSnapshot routeSnapshot = plan.route().get();
        AntiAirRoute route = new AntiAirRoute(plan.burnoutPosition(),
            routeSnapshot.controlPoint1(), routeSnapshot.controlPoint2(),
            routeSnapshot.resolvedInterceptPosition(), routeSnapshot.interceptTicks());
        return route.position(time - routeSnapshot.routeLockGameTime());
    }

    private void rebuildRoutes() {
        terminalRoute = snapshot.terminalPlan().map(this::sampleTerminal).orElseGet(List::of);
        if (snapshot.interceptorPlan().isPresent())
            carrierRoute = sampleInterceptor(snapshot.interceptorPlan().get());
        else carrierRoute = snapshot.carrierPlan().map(this::sampleCarrier).orElseGet(List::of);
    }

    private List<Vec3> sampleInterceptor(final RadarInterceptorPlanSnapshot plan) {
        carrierDuration = plan.ignitionTicks() + plan.boostTicks()
            + plan.route().map(RadarInterceptorRouteSnapshot::interceptTicks).orElse(0);
        ArrayList<Vec3> result = new ArrayList<>();
        for (int index = 0; index <= 96; index++)
            result.add(interceptorPosition(plan,
                plan.launchGameTime() + carrierDuration * index / 96.0));
        return List.copyOf(result);
    }

    private List<Vec3> sampleCarrier(final RadarCarrierPlanSnapshot snapshotPlan) {
        IcbmFlightPlan plan = asPlan(snapshotPlan);
        carrierDuration = plan.separationTick();
        ArrayList<Vec3> result = new ArrayList<>();
        for (int index = 0; index <= 96; index++)
            result.add(IcbmTrajectory.position(plan, carrierDuration * index / 96));
        return List.copyOf(result);
    }

    private List<Vec3> sampleTerminal(final RadarTerminalPlanSnapshot plan) {
        terminalDuration = plan.flightTicks();
        ArrayList<Vec3> result = new ArrayList<>();
        for (int index = 0; index <= 48; index++)
            result.add(WarheadTrajectory.position(plan.startPosition(), plan.targetPosition(),
                terminalDuration * index / 48, plan.flightTicks(),
                plan.clusterIndex(), plan.clusterCount()));
        return List.copyOf(result);
    }

    private IcbmFlightPlan asPlan(final RadarCarrierPlanSnapshot plan) {
        return new IcbmFlightPlan(snapshot.trackId(), snapshot.ownerPlayerId(),
            plan.launchPosition(), plan.burnoutPosition(), plan.separationPosition(),
            plan.intendedTarget(), plan.launchGameTime(), plan.ignitionTicks(), plan.boostTicks(),
            plan.coastTicks(), plan.visualSeed(), snapshot.strategicPayloadType().orElseThrow());
    }
}
