package com.andye.warmod.phalanx;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public final class PhalanxTargetSelector {
    private PhalanxTargetSelector() {
    }

    public static Optional<PhalanxTargetSnapshot> select(
        final Vec3 centre,
        final Vec3 muzzle,
        final List<PhalanxTargetSnapshot> candidates
    ) {
        return candidates.stream()
            .filter(target ->
                valid(
                    centre,
                    muzzle,
                    target
                )
            )
            .min(
                Comparator
                    .comparingInt(
                        PhalanxTargetSelector::kindPriority
                    )
                    .thenComparingDouble(
                        PhalanxTargetSnapshot::ticksToImpact
                    )
                    /*
                     * Prefer threats currently closest in the horizontal
                     * engagement cylinder. Do not use three-dimensional
                     * distance here.
                     */
                    .thenComparingDouble(target ->
                        horizontal(
                            muzzle,
                            target.position()
                        )
                    )
                    /*
                     * Predicted impact remains a priority signal, but is no
                     * longer a validity requirement. A missile passing through
                     * the defended cylinder may be engaged regardless of where
                     * its eventual target is.
                     */
                    .thenComparingDouble(target ->
                        horizontal(
                            centre,
                            target.predictedImpact()
                        )
                    )
                    .thenComparing(target ->
                        target.targetId().toString()
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
                target.payloadType().orElse(null)
                    == WarheadPayloadType.NUCLEAR
                        ? 2
                        : 3;

            case MK_I_FALLBACK -> 4;
        };
    }

    public static boolean valid(
        final Vec3 centre,
        final Vec3 muzzle,
        final PhalanxTargetSnapshot target
    ) {
        if (target == null
            || !target.position().isFinite()
            || !target.velocity().isFinite()
            || !target.predictedImpact().isFinite()) {
            return false;
        }

        /*
         * Range is an X/Z cylinder, not a three-dimensional sphere.
         *
         * A missile at X/Z distance 100 remains in range whether it is
         * 20 blocks high or 1,000 blocks high.
         */
        double horizontalDistance =
            horizontal(
                muzzle,
                target.position()
            );

        if (!Double.isFinite(horizontalDistance)
            || horizontalDistance
                > PhalanxConstants
                    .HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS) {
            return false;
        }

        /*
         * Vertical distance has no separate limit. Only the physical gun
         * elevation arc applies.
         */
        double vertical =
            target.position().y - muzzle.y;

        double elevation =
            Math.toDegrees(
                Math.atan2(
                    vertical,
                    horizontalDistance
                )
            );

        return elevation
                >= PhalanxConstants
                    .MIN_ELEVATION_DEGREES
            && elevation
                <= PhalanxConstants
                    .MAX_ELEVATION_DEGREES;
    }

    public static double horizontal(
        final Vec3 first,
        final Vec3 second
    ) {
        return Math.hypot(
            first.x - second.x,
            first.z - second.z
        );
    }
}