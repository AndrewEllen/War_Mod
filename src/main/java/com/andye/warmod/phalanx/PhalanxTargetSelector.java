package com.andye.warmod.phalanx;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public final class PhalanxTargetSelector {
    private PhalanxTargetSelector() {
    }

    /**
     * Chooses a target for rotation and spin-up.
     *
     * Tracking has no distance or altitude limit. Firing range is checked
     * separately by {@link #withinFiringCylinder(Vec3, PhalanxTargetSnapshot)}.
     */
    public static Optional<PhalanxTargetSnapshot> select(
        final Vec3 centre,
        final Vec3 muzzle,
        final List<PhalanxTargetSnapshot> candidates
    ) {
        return candidates.stream()
            .filter(
                PhalanxTargetSelector::trackable
            )
            .min(
                Comparator
                    /*
                     * Prefer missiles whose predicted impact is inside the
                     * defended 400-block cylinder.
                     */
                    .comparingInt(
                        (PhalanxTargetSnapshot target) ->
                            horizontal(
                                centre,
                                target.predictedImpact()
                            )
                                    <= PhalanxConstants
                                        .HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS
                                ? 0
                                : 1
                    )
                    .thenComparingDouble(
                        PhalanxTargetSnapshot::ticksToImpact
                    )
                    .thenComparingInt(
                        PhalanxTargetSelector::kindPriority
                    )
                    .thenComparingDouble(target ->
                        horizontal(
                            muzzle,
                            target.position()
                        )
                    )
                    .thenComparing(target ->
                        target.targetId()
                            .toString()
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

    /**
     * The firing range is a vertical cylinder: only X/Z distance is tested.
     */
    public static boolean withinFiringCylinder(
        final Vec3 muzzle,
        final PhalanxTargetSnapshot target
    ) {
        return trackable(target)
            && horizontal(
                muzzle,
                target.position()
            )
                <= PhalanxConstants
                    .HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS;
    }

    /**
     * Compatibility alias for older callers.
     */
    public static boolean valid(
        final Vec3 centre,
        final Vec3 muzzle,
        final PhalanxTargetSnapshot target
    ) {
        return withinFiringCylinder(
            muzzle,
            target
        );
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

    private static int kindPriority(
        final PhalanxTargetSnapshot target
    ) {
        return switch (target.kind()) {
            case ICBM_CARRIER -> 0;
            case CLUSTER_SUBMUNITION -> 1;

            case TERMINAL_WARHEAD, DIRECT_WARHEAD ->
                target.payloadType()
                        .orElse(null)
                    == WarheadPayloadType.NUCLEAR
                        ? 2
                        : 3;

            case MK_I_FALLBACK -> 4;
        };
    }
}
