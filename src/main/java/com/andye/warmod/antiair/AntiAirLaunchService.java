package com.andye.warmod.antiair;

import com.andye.warmod.WarMod;
import com.andye.warmod.antiair.network.AntiAirNetworking;
import com.andye.warmod.antiair.network.ClientboundAntiAirLaunchPayload;
import com.andye.warmod.defence.DefenceOwnershipSnapshot;
import com.andye.warmod.defence.MissileAffiliation;
import com.andye.warmod.radar.RadarInterceptorPlanSnapshot;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.silo.MissileSiloCollisionContext;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class AntiAirLaunchService {
    private static final long NO_TARGET_OFFSET_SALT =
        0x4E4F5F5441524745L;

    private static final long NO_TARGET_DISTANCE_SALT =
        0x4152435F4F464653L;

    private AntiAirLaunchService() {
    }

    public static Vec3 estimatedBurnout(
        final ServerLevel level,
        final Vec3 origin
    ) {
        return new Vec3(
            origin.x,
            Math.max(origin.y, level.getSeaLevel())
                + AntiAirConstants.VERTICAL_ASCENT_ABOVE_SEA_OR_LAUNCH_BLOCKS,
            origin.z
        );
    }

    public static Optional<AntiAirLaunchResult> launchFromSilo(
        final ServerLevel level,
        final @Nullable UUID owner,
        final MissileAffiliation affiliation,
        final String name,
        final UUID siloId,
        final BlockPos centre,
        final Vec3 origin,
        final AntiAirMissileVariant variant,
        final AntiAirLaunchDecision decision,
        final int tier,
        final MissileSiloCollisionContext collision,
        final DefenceOwnershipSnapshot ownership
    ) {
        return launch(
            level,
            owner,
            affiliation,
            name,
            siloId,
            centre,
            origin,
            estimatedBurnout(level, origin),
            variant,
            decision,
            tier,
            false,
            collision,
            ownership
        );
    }

    public static Optional<AntiAirLaunchResult> launchDebug(
        final ServerLevel level,
        final @Nullable UUID owner,
        final String name,
        final Vec3 origin,
        final AntiAirMissileVariant variant,
        final AntiAirLaunchDecision decision
    ) {
        Vec3 burnout =
            decision.mode()
                    == AntiAirLaunchMode.NO_TARGET_ASCENT
                ? new Vec3(
                    origin.x,
                    origin.y
                            >= AntiAirConstants
                                .DEBUG_NO_TARGET_CEILING_Y
                        ? origin.y + 128.0
                        : AntiAirConstants
                            .DEBUG_NO_TARGET_CEILING_Y,
                    origin.z
                )
                : estimatedBurnout(
                    level,
                    origin
                );

        return launch(
            level,
            owner,
            MissileAffiliation.ofOwner(owner),
            name,
            null,
            null,
            origin,
            burnout,
            variant,
            decision,
            3,
            decision.mode()
                == AntiAirLaunchMode.NO_TARGET_ASCENT,
            new MissileSiloCollisionContext(
                UUID.randomUUID(),
                BlockPos.containing(origin),
                Set.of(),
                0.35,
                2.2
            ),
            DefenceOwnershipSnapshot.unclaimed()
        );
    }

    private static Optional<AntiAirLaunchResult> launch(
        final ServerLevel level,
        final @Nullable UUID owner,
        final MissileAffiliation affiliation,
        final String name,
        final @Nullable UUID siloId,
        final @Nullable BlockPos centre,
        final Vec3 origin,
        final Vec3 nominalBurnout,
        final AntiAirMissileVariant variant,
        AntiAirLaunchDecision decision,
        final int tier,
        final boolean debugNoTargetFlight,
        final MissileSiloCollisionContext collision,
        final DefenceOwnershipSnapshot ownership
    ) {
        if (
            !decision.valid()
            || !AntiAirFlightControllerManager.canAccept(level)
            || !origin.isFinite()
            || !nominalBurnout.isFinite()
        ) {
            return Optional.empty();
        }

        UUID id =
            UUID.randomUUID();

        if (
            decision.mode()
                != AntiAirLaunchMode.NO_TARGET_ASCENT
        ) {
            AntiAirTargetSelectionResult selected =
                AntiAirTargetClaimRegistry.selectAndClaim(
                    level,
                    id,
                    origin,
                    nominalBurnout,
                    tier,
                    ownership
                ).orElse(null);

            if (selected == null) {
                /*
                 * The target may have left the bounded projection between the
                 * initial UI/redstone plan and the final claim. Preserve the
                 * launch as a dumb ascent instead of returning a route-geometry
                 * failure.
                 */
                decision =
                    new AntiAirLaunchDecision(
                        AntiAirLaunchMode.NO_TARGET_ASCENT,
                        null,
                        null,
                        "target_changed_before_claim"
                    );
            } else {
                decision =
                    new AntiAirLaunchDecision(
                        selected.mode(),
                        selected.selection(),
                        selected.solution(),
                        selected.solution().rangeLimited()
                            ? "claim_balanced_attempt"
                            : "claim_balanced_exact"
                    );
            }
        }

        long seed =
            id.getMostSignificantBits()
                ^ Long.rotateLeft(
                    id.getLeastSignificantBits(),
                    19
                );

        Vec3 noTargetOffset =
            decision.mode()
                    == AntiAirLaunchMode.NO_TARGET_ASCENT
                ? noTargetOffset(id, seed)
                : Vec3.ZERO;

        Vec3 burnout = nominalBurnout;

        AntiAirTargetLock lock =
            decision.targetSelection() == null
                ? null
                : decision.targetSelection()
                    .targetLock();

        AntiAirInterceptSolution solution =
            decision.solution();

        AntiAirFlightPlan plan =
            new AntiAirFlightPlan(
                id,
                owner,
                affiliation,
                siloId,
                centre,
                variant,
                lock == null
                    ? null
                    : lock.rootTrackId(),
                origin,
                burnout,
                noTargetOffset,
                lock,
                solution,
                decision.mode(),
                level.getGameTime(),
                AntiAirConstants.IGNITION_TICKS,
                AntiAirConstants.BOOST_TICKS,
                seed,
                tier,
                debugNoTargetFlight
            );

        if (
            !AntiAirFlightControllerManager.add(
                level,
                plan,
                collision
            )
        ) {
            AntiAirTargetClaimRegistry.releaseInterceptor(
                level,
                id,
                "launch_rejected"
            );

            return Optional.empty();
        }

        RadarInterceptorPlanSnapshot radar =
            new RadarInterceptorPlanSnapshot(
                variant,
                Optional.ofNullable(
                    plan.targetRootTrackId()
                ),
                origin,
                burnout,
                noTargetOffset,
                plan.launchGameTime(),
                plan.ignitionTicks(),
                plan.boostTicks(),
                tier,
                AntiAirGuidanceResolver.maximumMiss(tier),
                Optional.empty(),
                Optional.empty()
            );

        RadarTrackingService.registerInterceptor(
            level,
            id,
            owner,
            affiliation,
            name,
            radar
        );

        AntiAirNetworking.send(
            level,
            new ClientboundAntiAirLaunchPayload(
                id,
                owner,
                variant,
                plan.targetRootTrackId(),
                origin,
                burnout,
                noTargetOffset,
                decision.mode(),
                plan.launchGameTime(),
                plan.ignitionTicks(),
                plan.boostTicks(),
                seed,
                tier,
                debugNoTargetFlight
            )
        );

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            logDecision(
                plan,
                decision
            );
        }

        return Optional.of(
            new AntiAirLaunchResult(plan)
        );
    }

    private static Vec3 noTargetOffset(
        final UUID id,
        final long visualSeed
    ) {
        long seed =
            visualSeed
                ^ id.getMostSignificantBits()
                ^ Long.rotateLeft(
                    id.getLeastSignificantBits(),
                    17
                )
                ^ NO_TARGET_OFFSET_SALT;

        double angle =
            unitDouble(seed)
                * Math.PI
                * 2.0;

        double distance =
            4.0
                + unitDouble(
                    seed
                        ^ NO_TARGET_DISTANCE_SALT
                )
                    * 2.0;

        return new Vec3(
            Math.cos(angle) * distance,
            0.0,
            Math.sin(angle) * distance
        );
    }

    private static double unitDouble(
        final long input
    ) {
        long value = input;

        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;

        return (value >>> 11) * 0x1.0p-53;
    }

    private static void logDecision(
        final AntiAirFlightPlan plan,
        final AntiAirLaunchDecision decision
    ) {
        if (
            decision.mode()
                == AntiAirLaunchMode.NO_TARGET_ASCENT
        ) {
            WarMod.LOGGER.info(
                "Anti-air route: mode=no_target, "
                    + "offset=<{},{}>, variant={}, reason={}",
                plan.noTargetHorizontalOffset().x,
                plan.noTargetHorizontalOffset().z,
                plan.variant().serializedName(),
                decision.diagnosticReason()
            );

            return;
        }

        AntiAirInterceptSolution solution =
            decision.solution();

        WarMod.LOGGER.info(
            "Anti-air route: mode={}, arcLength={}, "
                + "interceptTime={}, target={}, "
                + "rangeLimited={}, guidanceTier={}, maxMiss={}",
            solution.rangeLimited()
                ? "bounded_attempt"
                : "exact",
            solution.poweredArcLength(),
            solution.interceptGameTime(),
            plan.targetRootTrackId(),
            solution.rangeLimited(),
            plan.capturedGuidanceTier(),
            AntiAirGuidanceResolver.maximumMiss(
                plan.capturedGuidanceTier()
            )
        );
    }
}
