package com.andye.warmod.phalanx;

import com.andye.warmod.antiair.AntiAirFallbackTrajectory;
import com.andye.warmod.antiair.AntiAirFlightControllerManager;
import com.andye.warmod.entity.IncomingWarheadEntity;
import com.andye.warmod.icbm.IcbmFlightControllerManager;
import com.andye.warmod.warhead.IncomingWarheadRegistry;
import com.andye.warmod.warhead.WarheadTrajectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class PhalanxTargetService {
    private PhalanxTargetService() { }
    public static List<PhalanxTargetSnapshot> snapshot(final ServerLevel level) {
        ArrayList<PhalanxTargetSnapshot> output = new ArrayList<>();
        long now = level.getGameTime();
        for (var carrier : IcbmFlightControllerManager.pointDefenceSnapshots(level, now)) output.add(new PhalanxTargetSnapshot(carrier.missileId(), carrier.missileId(), PhalanxTargetKind.ICBM_CARRIER, Optional.of(carrier.payloadType()), carrier.position(), carrier.velocity(), carrier.intendedTarget(), carrier.ticksToImpact(), 0.90, carrier.ownerPlayerId(), false));
        for (IncomingWarheadEntity warhead : IncomingWarheadRegistry.activeWarheads(level)) {
            double age = Math.max(0.0, now - warhead.launchGameTime());
            Vec3 position = WarheadTrajectory.position(warhead.startPosition(), warhead.intendedTarget(), age,
                warhead.flightTicks(), warhead.clusterIndex(), warhead.clusterCount());
            Vec3 velocity = WarheadTrajectory.velocity(warhead.startPosition(), warhead.intendedTarget(), age,
                warhead.flightTicks(), warhead.clusterIndex(), warhead.clusterCount());
            double remaining = Math.max(0.0, warhead.flightTicks() - age);
            PhalanxTargetKind kind = warhead.clusterCount() > 1 ? PhalanxTargetKind.CLUSTER_SUBMUNITION : PhalanxTargetKind.TERMINAL_WARHEAD;
            output.add(new PhalanxTargetSnapshot(warhead.warheadId(), warhead.radarRootTrackId(), kind, Optional.of(warhead.payloadType()), position, velocity, warhead.intendedTarget(), remaining, warhead.clusterCount() > 1 ? 0.48 : 0.62, warhead.ownerPlayerId(), false));
        }
        for (var interceptor : AntiAirFlightControllerManager.pointDefenceSnapshots(level)) {
            if (!interceptor.active()) continue;
            boolean fallback = interceptor.phase() == com.andye.warmod.antiair.AntiAirFlightPhase.FALLBACK;
            Vec3 impact = fallback
                ? AntiAirFallbackTrajectory.predictImpact(level, interceptor.currentPosition(), interceptor.currentVelocity(), 200)
                    .orElse(interceptor.projectedPosition(80.0))
                : interceptor.projectedPosition(80.0);
            double remaining = fallback
                ? estimateTicks(interceptor.currentPosition(), interceptor.currentVelocity(), impact)
                : 80.0;
            output.add(new PhalanxTargetSnapshot(interceptor.interceptorId(), interceptor.interceptorId(),
                fallback ? PhalanxTargetKind.MK_I_FALLBACK : PhalanxTargetKind.ACTIVE_ANTI_AIR,
                Optional.empty(), interceptor.currentPosition(), interceptor.currentVelocity(), impact,
                remaining, 0.8, interceptor.ownerPlayerId(), interceptor.forcedHostile()));
        }
        return List.copyOf(output);
    }
    private static double estimateTicks(final Vec3 position, final Vec3 velocity, final Vec3 impact) { return Math.min(200.0, Math.max(1.0, position.distanceTo(impact) / Math.max(0.1, velocity.length()))); }
}
