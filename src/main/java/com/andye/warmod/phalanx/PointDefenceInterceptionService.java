package com.andye.warmod.phalanx;

import com.andye.warmod.antiair.AntiAirFlightControllerManager;
import com.andye.warmod.defence.DefenceOwnershipSnapshot;
import com.andye.warmod.icbm.IcbmFlightControllerManager;
import com.andye.warmod.testtool.TestExplosionService;
import com.andye.warmod.warhead.IncomingWarheadRegistry;
import com.andye.warmod.warhead.WarheadEffectProfile;
import com.andye.warmod.warhead.WarheadImpactService;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class PointDefenceInterceptionService {
    private PointDefenceInterceptionService() {
    }

    public static boolean intercept(
        final ServerLevel level,
        final PhalanxTargetSnapshot target,
        final UUID bulletId,
        final Vec3 hitPosition,
        final DefenceOwnershipSnapshot ownership
    ) {
        boolean intercepted =
            interceptTarget(
                level,
                target,
                bulletId,
                hitPosition
            );

        if (!intercepted) {
            return false;
        }

        double chainRadiusSquared =
            PhalanxConstants
                .INTERCEPTION_CHAIN_RADIUS_BLOCKS
                * PhalanxConstants
                    .INTERCEPTION_CHAIN_RADIUS_BLOCKS;

        /*
         * Resolve nearby airborne targets before creating the physical
         * explosion. Strategic carriers are controller-backed rather than
         * ordinary damageable entities, so a vanilla explosion alone would not
         * remove them.
         */
        for (
            PhalanxTargetSnapshot nearby
                : PhalanxTargetService.snapshot(level)
        ) {
            if (
                nearby.targetId()
                    .equals(target.targetId())
                || !ownership.isHostile(nearby.ownerPlayerId(), nearby.forcedHostile())
                || nearby.position()
                    .distanceToSqr(hitPosition)
                    > chainRadiusSquared
            ) {
                continue;
            }

            interceptTarget(
                level,
                nearby,
                bulletId,
                hitPosition
            );
        }

        WarheadImpactService.detonateAntiAir(
            level,
            bulletId,
            hitPosition,
            bulletId.getMostSignificantBits()
                ^ Long.rotateLeft(
                    bulletId.getLeastSignificantBits(),
                    21
                ),
            WarheadEffectProfile
                .ANTI_AIR_INTERCEPTION
        );

        TestExplosionService.createExplosion(
            level,
            null,
            hitPosition,
            PhalanxConstants
                .INTERCEPTION_EXPLOSION_STRENGTH
        );

        level.sendParticles(
            ParticleTypes.SMOKE,
            hitPosition.x,
            hitPosition.y,
            hitPosition.z,
            18,
            0.55,
            0.55,
            0.55,
            0.035
        );

        return true;
    }

    private static boolean interceptTarget(
        final ServerLevel level,
        final PhalanxTargetSnapshot target,
        final UUID bulletId,
        final Vec3 hitPosition
    ) {
        return switch (target.kind()) {
            case ICBM_CARRIER ->
                IcbmFlightControllerManager
                    .cancelForInterception(
                        level,
                        target.rootId(),
                        bulletId,
                        hitPosition
                    );

            case MK_I_FALLBACK ->
                AntiAirFlightControllerManager
                    .cancelForPointDefence(
                        level,
                        target.targetId(),
                        bulletId,
                        hitPosition
                    );

            case ACTIVE_ANTI_AIR ->
                AntiAirFlightControllerManager
                    .cancelAsInterceptorTarget(
                        level,
                        target.targetId(),
                        bulletId,
                        hitPosition
                    );

            case CLUSTER_SUBMUNITION,
                TERMINAL_WARHEAD,
                DIRECT_WARHEAD ->
                IncomingWarheadRegistry
                    .getByWarheadId(
                        level,
                        target.targetId()
                    )
                    .map(warhead ->
                        warhead.cancelForPointDefence(
                            level,
                            bulletId,
                            hitPosition
                        )
                    )
                    .orElse(false);
        };
    }
}
