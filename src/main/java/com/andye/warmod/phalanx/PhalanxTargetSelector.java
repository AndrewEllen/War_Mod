package com.andye.warmod.phalanx;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PhalanxTargetSelector {
    private PhalanxTargetSelector() {
    }

    public static Optional<PhalanxTargetSnapshot> select(
        final ServerLevel level,
        final UUID turretId,
        final @Nullable UUID currentTargetId,
        final Vec3 centre,
        final Vec3 muzzle,
        final List<PhalanxTargetSnapshot> candidates
    ) {
        long now = level.getGameTime();

        return candidates.stream()
            .filter(target -> withinTrackingCylinder(centre, target))
            .min(
                Comparator
                    /* Protect this installation before unrelated flyovers. */
                    .comparingInt((PhalanxTargetSnapshot target) ->
                        horizontal(centre, target.predictedImpact())
                                <= PhalanxConstants
                                    .HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS
                            ? 0
                            : 1
                    )
                    /* A target already inside firing range has immediate value. */
                    .thenComparingInt(target ->
                        withinFiringCylinder(muzzle, target)
                            ? 0
                            : 1
                    )
                    /* Group similar ETAs so claims can balance within a band. */
                    .thenComparingInt(
                        PhalanxTargetSelector::urgencyBand
                    )
                    /* Spread comparable threats across available turrets. */
                    .thenComparingInt(target ->
                        PhalanxTargetClaimRegistry.claimCountExcluding(
                            level,
                            target.targetId(),
                            turretId,
                            now
                        )
                    )
                    /* Avoid needless oscillation when two options are equal. */
                    .thenComparingInt(target ->
                        target.targetId().equals(currentTargetId)
                            ? 0
                            : 1
                    )
                    .thenComparingInt(
                        PhalanxTargetSelector::kindPriority
                    )
                    .thenComparingDouble(
                        PhalanxTargetSnapshot::ticksToImpact
                    )
                    .thenComparingDouble(target ->
                        horizontal(muzzle, target.position())
                    )
                    .thenComparing(target ->
                        target.targetId().toString()
                    )
            );
    }

    public static boolean trackable(
        final PhalanxTargetSnapshot target
    ) {
        return target != null
            && target.position() != null
            && target.velocity() != null
            && target.predictedImpact() != null
            && target.position().isFinite()
            && target.velocity().isFinite()
            && target.predictedImpact().isFinite()
            && Double.isFinite(target.ticksToImpact());
    }

    public static boolean withinTrackingCylinder(
        final Vec3 centre,
        final PhalanxTargetSnapshot target
    ) {
        return trackable(target)
            && horizontal(centre, target.position())
                <= PhalanxConstants.HORIZONTAL_TRACKING_RADIUS_BLOCKS;
    }

    public static boolean withinFiringCylinder(
        final Vec3 muzzle,
        final PhalanxTargetSnapshot target
    ) {
        return trackable(target)
            && horizontal(muzzle, target.position())
                <= PhalanxConstants.HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS;
    }

    /** Compatibility alias for existing callers. */
    public static boolean valid(
        final Vec3 centre,
        final Vec3 muzzle,
        final PhalanxTargetSnapshot target
    ) {
        return withinTrackingCylinder(centre, target);
    }

    public static double horizontal(
        final Vec3 first,
        final Vec3 second
    ) {
        if (first == null || second == null) {
            return Double.POSITIVE_INFINITY;
        }

        return Math.hypot(
            first.x - second.x,
            first.z - second.z
        );
    }

    private static int urgencyBand(
        final PhalanxTargetSnapshot target
    ) {
        return (int)Math.max(
            0.0,
            Math.min(
                32.0,
                Math.floor(target.ticksToImpact() / 40.0)
            )
        );
    }

    private static int kindPriority(
        final PhalanxTargetSnapshot target
    ) {
        return switch (target.kind()) {
            case ICBM_CARRIER -> 0;
            case CLUSTER_SUBMUNITION -> 1;
            case TERMINAL_WARHEAD, DIRECT_WARHEAD ->
                target.payloadType().orElse(null) == WarheadPayloadType.NUCLEAR
                    ? 2
                    : 3;
            case MK_I_FALLBACK -> 4;
        };
    }
}
