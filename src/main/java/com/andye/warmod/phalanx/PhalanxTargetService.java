package com.andye.warmod.phalanx;

import com.andye.warmod.antiair.AntiAirFallbackTrajectory;
import com.andye.warmod.antiair.AntiAirFlightControllerManager;
import com.andye.warmod.antiair.AntiAirMissileVariant;
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
        for (var carrier : IcbmFlightControllerManager.pointDefenceSnapshots(level, now)) output.add(new PhalanxTargetSnapshot(carrier.missileId(), carrier.missileId(), PhalanxTargetKind.ICBM_CARRIER, Optional.of(carrier.payloadType()), carrier.position(), carrier.velocity(), carrier.intendedTarget(), carrier.ticksToImpact(), 0.90));
        for (IncomingWarheadEntity warhead : IncomingWarheadRegistry.activeWarheads(level)) {
            double age = Math.max(0.0, now - warhead.launchGameTime());
            Vec3 position = WarheadTrajectory.position(warhead.startPosition(), warhead.intendedTarget(), age, warhead.flightTicks());
            Vec3 velocity = WarheadTrajectory.velocity(warhead.startPosition(), warhead.intendedTarget(), age, warhead.flightTicks());
            double remaining = Math.max(0.0, warhead.flightTicks() - age);
            PhalanxTargetKind kind = warhead.clusterCount() > 1 ? PhalanxTargetKind.CLUSTER_SUBMUNITION : PhalanxTargetKind.TERMINAL_WARHEAD;
            output.add(new PhalanxTargetSnapshot(warhead.warheadId(), warhead.radarRootTrackId(), kind, Optional.of(warhead.payloadType()), position, velocity, warhead.intendedTarget(), remaining, warhead.clusterCount() > 1 ? 0.48 : 0.62));
        }
        for (var fallback : AntiAirFlightControllerManager.fallbackSnapshots(level)) {
            if (fallback.variant() != AntiAirMissileVariant.MK_I || !fallback.active()) continue;
            AntiAirFallbackTrajectory.predictImpact(level, fallback.currentPosition(), fallback.currentVelocity(), 200).ifPresent(impact -> output.add(new PhalanxTargetSnapshot(fallback.interceptorId(), fallback.interceptorId(), PhalanxTargetKind.MK_I_FALLBACK, Optional.empty(), fallback.currentPosition(), fallback.currentVelocity(), impact, estimateTicks(fallback.currentPosition(), fallback.currentVelocity(), impact), 0.8)));
        }
        return List.copyOf(output);
    }
    private static double estimateTicks(final Vec3 position, final Vec3 velocity, final Vec3 impact) { return Math.min(200.0, Math.max(1.0, position.distanceTo(impact) / Math.max(0.1, velocity.length()))); }
}