package com.andye.warmod.antiair;

import com.andye.warmod.antiair.network.*;
import com.andye.warmod.radar.*;
import com.andye.warmod.silo.MissileSiloCollisionContext;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class AntiAirLaunchService {
    private AntiAirLaunchService() { }
    public static Vec3 estimatedBurnout(ServerLevel level, Vec3 origin) {
        double cloud = origin.y;
        try { cloud = level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, origin).doubleValue(); }
        catch (RuntimeException ignored) { }
        return new Vec3(origin.x, Math.max(origin.y + 96, cloud + 48), origin.z);
    }
    public static Optional<AntiAirLaunchResult> launchFromSilo(ServerLevel level, @Nullable UUID owner, String name,
        UUID siloId, BlockPos centre, Vec3 origin, AntiAirMissileVariant variant, @Nullable AntiAirTargetLock lock,
        @Nullable AntiAirInterceptSolution solution, AntiAirLaunchMode mode, int tier,
        MissileSiloCollisionContext collision) {
        return launch(level, owner, name, siloId, centre, origin, estimatedBurnout(level, origin), variant, lock,
            solution, mode, tier, false, collision);
    }
    public static Optional<AntiAirLaunchResult> launchDebug(ServerLevel level, @Nullable UUID owner, String name,
        Vec3 origin, AntiAirMissileVariant variant, @Nullable AntiAirTargetLock lock,
        @Nullable AntiAirInterceptSolution solution) {
        Vec3 burnout = lock == null
            ? new Vec3(origin.x, origin.y >= AntiAirConstants.DEBUG_NO_TARGET_CEILING_Y ? origin.y + 128
                : AntiAirConstants.DEBUG_NO_TARGET_CEILING_Y, origin.z)
            : estimatedBurnout(level, origin);
        AntiAirLaunchMode mode = lock == null ? AntiAirLaunchMode.NO_TARGET_ASCENT : AntiAirLaunchMode.TRACKED_INTERCEPT;
        return launch(level, owner, name, null, null, origin, burnout, variant, lock, solution, mode, 3, true,
            new MissileSiloCollisionContext(UUID.randomUUID(), BlockPos.containing(origin), Set.of(), .35, 2.2));
    }
    private static Optional<AntiAirLaunchResult> launch(ServerLevel level, @Nullable UUID owner, String name,
        @Nullable UUID siloId, @Nullable BlockPos centre, Vec3 origin, Vec3 burnout, AntiAirMissileVariant variant,
        @Nullable AntiAirTargetLock lock, @Nullable AntiAirInterceptSolution solution, AntiAirLaunchMode mode,
        int tier, boolean debugNoTargetFlight, MissileSiloCollisionContext collision) {
        boolean needsRoute = mode != AntiAirLaunchMode.NO_TARGET_ASCENT;
        if (!AntiAirFlightControllerManager.canAccept(level) || !origin.isFinite() || !burnout.isFinite()
            || needsRoute != (lock != null && solution != null)) return Optional.empty();
        UUID id = UUID.randomUUID();
        long seed = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 19);
        AntiAirFlightPlan plan = new AntiAirFlightPlan(id, owner, siloId, centre, variant,
            lock == null ? null : lock.rootTrackId(), origin, burnout, lock, solution, mode, level.getGameTime(),
            AntiAirConstants.IGNITION_TICKS, AntiAirConstants.BOOST_TICKS, seed, tier, debugNoTargetFlight);
        if (!AntiAirFlightControllerManager.add(level, plan, collision)) return Optional.empty();
        RadarInterceptorPlanSnapshot radar = new RadarInterceptorPlanSnapshot(variant,
            Optional.ofNullable(plan.targetRootTrackId()), origin, burnout, plan.launchGameTime(),
            plan.ignitionTicks(), plan.boostTicks(), tier, AntiAirGuidanceResolver.maximumMiss(tier), Optional.empty(),
            Optional.empty());
        RadarTrackingService.registerInterceptor(level, id, owner, name, radar);
        AntiAirNetworking.send(level, new ClientboundAntiAirLaunchPayload(id, owner, variant, plan.targetRootTrackId(),
            origin, burnout, mode, plan.launchGameTime(), plan.ignitionTicks(), plan.boostTicks(), seed, tier,
            debugNoTargetFlight));
        return Optional.of(new AntiAirLaunchResult(plan));
    }
}