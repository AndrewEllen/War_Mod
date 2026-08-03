package com.andye.warmod.antiair;

import com.andye.warmod.WarMod;
import com.andye.warmod.antiair.network.AntiAirNetworking;
import com.andye.warmod.antiair.network.ClientboundAntiAirLaunchPayload;
import com.andye.warmod.radar.RadarInterceptorPlanSnapshot;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.silo.MissileSiloCollisionContext;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class AntiAirLaunchService {
    private static final long NO_TARGET_OFFSET_SALT = 0x4E4F5F5441524745L;
    private static final long NO_TARGET_DISTANCE_SALT = 0x4152435F4F464653L;

    private AntiAirLaunchService() { }

    public static Vec3 estimatedBurnout(ServerLevel level, Vec3 origin) {
        double cloud = origin.y;
        try { cloud = level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, origin).doubleValue(); }
        catch (RuntimeException ignored) { }
        return new Vec3(origin.x, Math.max(origin.y + 96.0, cloud + 48.0), origin.z);
    }

    public static Optional<AntiAirLaunchResult> launchFromSilo(ServerLevel level, @Nullable UUID owner, String name,
        UUID siloId, BlockPos centre, Vec3 origin, AntiAirMissileVariant variant, AntiAirLaunchDecision decision,
        int tier, MissileSiloCollisionContext collision) {
        return launch(level, owner, name, siloId, centre, origin, estimatedBurnout(level, origin), variant, decision,
            tier, false, collision);
    }

    public static Optional<AntiAirLaunchResult> launchDebug(ServerLevel level, @Nullable UUID owner, String name,
        Vec3 origin, AntiAirMissileVariant variant, AntiAirLaunchDecision decision) {
        Vec3 burnout = decision.mode() == AntiAirLaunchMode.NO_TARGET_ASCENT
            ? new Vec3(origin.x, origin.y >= AntiAirConstants.DEBUG_NO_TARGET_CEILING_Y ? origin.y + 128.0
                : AntiAirConstants.DEBUG_NO_TARGET_CEILING_Y, origin.z)
            : estimatedBurnout(level, origin);
        return launch(level, owner, name, null, null, origin, burnout, variant, decision, 3,
            decision.mode() == AntiAirLaunchMode.NO_TARGET_ASCENT,
            new MissileSiloCollisionContext(UUID.randomUUID(), BlockPos.containing(origin), Set.of(), 0.35, 2.2));
    }

    private static Optional<AntiAirLaunchResult> launch(ServerLevel level, @Nullable UUID owner, String name,
        @Nullable UUID siloId, @Nullable BlockPos centre, Vec3 origin, Vec3 nominalBurnout, AntiAirMissileVariant variant,
        AntiAirLaunchDecision decision, int tier, boolean debugNoTargetFlight, MissileSiloCollisionContext collision) {
        if (!decision.valid() || !AntiAirFlightControllerManager.canAccept(level) || !origin.isFinite()
            || !nominalBurnout.isFinite()) return Optional.empty();
        UUID id = UUID.randomUUID();
        long seed = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 19);
        Vec3 noTargetOffset = decision.mode() == AntiAirLaunchMode.NO_TARGET_ASCENT ? noTargetOffset(id, seed) : Vec3.ZERO;
        Vec3 burnout = decision.mode() == AntiAirLaunchMode.NO_TARGET_ASCENT ? nominalBurnout.add(noTargetOffset) : nominalBurnout;
        AntiAirTargetLock lock = decision.targetSelection() == null ? null : decision.targetSelection().targetLock();
        AntiAirInterceptSolution solution = decision.solution();
        AntiAirFlightPlan plan = new AntiAirFlightPlan(id, owner, siloId, centre, variant,
            lock == null ? null : lock.rootTrackId(), origin, burnout, noTargetOffset, lock, solution, decision.mode(),
            level.getGameTime(), AntiAirConstants.IGNITION_TICKS, AntiAirConstants.BOOST_TICKS, seed, tier,
            debugNoTargetFlight);
        if (!AntiAirFlightControllerManager.add(level, plan, collision)) return Optional.empty();
        RadarInterceptorPlanSnapshot radar = new RadarInterceptorPlanSnapshot(variant,
            Optional.ofNullable(plan.targetRootTrackId()), origin, burnout, noTargetOffset, plan.launchGameTime(),
            plan.ignitionTicks(), plan.boostTicks(), tier, AntiAirGuidanceResolver.maximumMiss(tier), Optional.empty(),
            Optional.empty());
        RadarTrackingService.registerInterceptor(level, id, owner, name, radar);
        AntiAirNetworking.send(level, new ClientboundAntiAirLaunchPayload(id, owner, variant, plan.targetRootTrackId(),
            origin, burnout, noTargetOffset, decision.mode(), plan.launchGameTime(), plan.ignitionTicks(), plan.boostTicks(),
            seed, tier, debugNoTargetFlight));
        if (SharedConstants.IS_RUNNING_IN_IDE) logDecision(plan, decision);
        return Optional.of(new AntiAirLaunchResult(plan));
    }

    private static Vec3 noTargetOffset(UUID id, long visualSeed) {
        long seed = visualSeed ^ id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17)
            ^ NO_TARGET_OFFSET_SALT;
        double angle = unitDouble(seed) * Math.PI * 2.0;
        double distance = 4.0 + unitDouble(seed ^ NO_TARGET_DISTANCE_SALT) * 2.0;
        return new Vec3(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
    }

    private static double unitDouble(long input) {
        long value = input;
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static void logDecision(AntiAirFlightPlan plan, AntiAirLaunchDecision decision) {
        if (decision.mode() == AntiAirLaunchMode.NO_TARGET_ASCENT) {
            WarMod.LOGGER.info("Anti-air route: mode=no_target, offset=<{},{}>, variant={}",
                plan.noTargetHorizontalOffset().x, plan.noTargetHorizontalOffset().z, plan.variant().serializedName());
        } else if (decision.mode() == AntiAirLaunchMode.BEST_EFFORT_INTERCEPT) {
            AntiAirInterceptSolution solution = decision.solution();
            WarMod.LOGGER.info("Anti-air route: mode=best_effort, originalArcLength={}, poweredArcLength={}, rangeLimited={}, target={}",
                solution.originalArcLength(), solution.poweredArcLength(), solution.rangeLimited(), plan.targetRootTrackId());
        } else {
            AntiAirInterceptSolution solution = decision.solution();
            WarMod.LOGGER.info("Anti-air route: mode=tracked, arcLength={}, interceptTime={}, target={}",
                solution.poweredArcLength(), solution.interceptGameTime(), plan.targetRootTrackId());
        }
    }
}