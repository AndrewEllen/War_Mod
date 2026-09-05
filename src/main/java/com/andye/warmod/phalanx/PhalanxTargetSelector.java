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
    /** Two seconds at the normal 20 TPS server rate. */
    private static final double IMMINENT_IMPACT_TICKS = 40.0;

    /** A target this close to the gun is already in the last defensive layer. */
    private static final double IMMINENT_HORIZONTAL_DISTANCE_BLOCKS = 96.0;

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
        List<PhalanxTargetSnapshot> trackable = candidates.stream()
            .filter(target -> withinTrackingCylinder(centre, target))
            .toList();

        if (trackable.isEmpty()) {
            return Optional.empty();
        }

        /*
         * A base defence never gives an unrelated fly-over priority over a
         * missile predicted to land inside its defended area.
         */
        List<PhalanxTargetSnapshot> localThreats = trackable.stream()
            .filter(target -> threatensInstallation(centre, target))
            .toList();
        List<PhalanxTargetSnapshot> eligible = localThreats.isEmpty()
            ? trackable
            : localThreats;

        /*
         * Once a local threat reaches the last defensive window, coverage
         * balancing is intentionally suspended and every turret may focus it.
         */
        Optional<PhalanxTargetSnapshot> imminent = eligible.stream()
            .filter(target -> threatensInstallation(centre, target))
            .filter(target -> isImminent(muzzle, target))
            .min(imminentComparator(centre, muzzle));

        if (imminent.isPresent()) {
            return imminent;
        }

        PhalanxTargetSnapshot current = find(eligible, currentTargetId);
        Comparator<PhalanxTargetSnapshot> priority = priorityComparator(
            centre,
            muzzle,
            !localThreats.isEmpty()
        );
        int minimumClaims = eligible.stream()
            .mapToInt(target -> claimCountExcluding(
                level,
                target,
                turretId,
                now
            ))
            .min()
            .orElse(0);

        if (current != null) {
            int currentClaimsExcludingTurret = claimCountExcluding(
                level,
                current,
                turretId,
                now
            );

            if (currentClaimsExcludingTurret == minimumClaims) {
                /*
                 * Retain the lock when coverage is balanced. The only allowed
                 * same-tier move puts an unavoidable remainder on the highest
                 * priority threat rather than leaving it on a farther impact.
                 * Sequential server ticks make at most one turret move before
                 * the claim counts become balanced again.
                 */
                PhalanxTargetSnapshot preferredRemainder = eligible.stream()
                    .filter(target -> claimCountExcluding(
                        level,
                        target,
                        turretId,
                        now
                    ) == minimumClaims)
                    .min(priority)
                    .orElse(current);

                if (priority.compare(current, preferredRemainder) <= 0) {
                    return Optional.of(current);
                }
            }
        }

        return eligible.stream().min(
            Comparator
                /* Even coverage first; any remainder goes to the best threat. */
                .comparingInt((PhalanxTargetSnapshot target) ->
                    claimCountExcluding(level, target, turretId, now)
                )
                .thenComparing(priority)
        );
    }

    public static boolean trackable(final PhalanxTargetSnapshot target) {
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

    public static double horizontal(final Vec3 first, final Vec3 second) {
        if (first == null || second == null) {
            return Double.POSITIVE_INFINITY;
        }

        return Math.hypot(first.x - second.x, first.z - second.z);
    }

    private static boolean threatensInstallation(
        final Vec3 centre,
        final PhalanxTargetSnapshot target
    ) {
        return horizontal(centre, target.predictedImpact())
            <= PhalanxConstants.HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS;
    }

    private static boolean isImminent(
        final Vec3 muzzle,
        final PhalanxTargetSnapshot target
    ) {
        return target.ticksToImpact() <= IMMINENT_IMPACT_TICKS
            || horizontal(muzzle, target.position())
                <= IMMINENT_HORIZONTAL_DISTANCE_BLOCKS;
    }

    private static int claimCountExcluding(
        final ServerLevel level,
        final PhalanxTargetSnapshot target,
        final UUID turretId,
        final long gameTime
    ) {
        return PhalanxTargetClaimRegistry.claimCountExcluding(
            level,
            target.targetId(),
            turretId,
            gameTime
        );
    }

    private static Comparator<PhalanxTargetSnapshot> priorityComparator(
        final Vec3 centre,
        final Vec3 muzzle,
        final boolean localThreatsOnly
    ) {
        Comparator<PhalanxTargetSnapshot> comparator;

        if (localThreatsOnly) {
            comparator = Comparator
                /* Defend the closest predicted impact before farther impacts. */
                .comparingDouble((PhalanxTargetSnapshot target) ->
                    horizontal(centre, target.predictedImpact())
                )
                .thenComparingInt(target ->
                    withinFiringCylinder(muzzle, target) ? 0 : 1
                );
        } else {
            comparator = Comparator
                /* With no local impact, engage the closest useful fly-over. */
                .comparingInt((PhalanxTargetSnapshot target) ->
                    withinFiringCylinder(muzzle, target) ? 0 : 1
                )
                .thenComparingDouble(target ->
                    horizontal(muzzle, target.position())
                );
        }

        return comparator
            .thenComparingInt(PhalanxTargetSelector::kindPriority)
            .thenComparingDouble(PhalanxTargetSnapshot::ticksToImpact)
            .thenComparingDouble(target ->
                horizontal(muzzle, target.position())
            )
            .thenComparing(target -> target.targetId().toString());
    }

    private static Comparator<PhalanxTargetSnapshot> imminentComparator(
        final Vec3 centre,
        final Vec3 muzzle
    ) {
        return Comparator
            .comparingDouble(PhalanxTargetSnapshot::ticksToImpact)
            .thenComparingDouble(target ->
                horizontal(muzzle, target.position())
            )
            .thenComparingDouble(target ->
                horizontal(centre, target.predictedImpact())
            )
            .thenComparingInt(PhalanxTargetSelector::kindPriority)
            .thenComparing(target -> target.targetId().toString());
    }

    private static @Nullable PhalanxTargetSnapshot find(
        final List<PhalanxTargetSnapshot> candidates,
        final @Nullable UUID targetId
    ) {
        if (targetId == null) {
            return null;
        }

        for (PhalanxTargetSnapshot candidate : candidates) {
            if (candidate.targetId().equals(targetId)) {
                return candidate;
            }
        }

        return null;
    }

    private static int kindPriority(final PhalanxTargetSnapshot target) {
        return switch (target.kind()) {
            case ICBM_CARRIER -> 0;
            case CLUSTER_SUBMUNITION -> 1;
            case TERMINAL_WARHEAD, DIRECT_WARHEAD ->
                target.payloadType().orElse(null) == WarheadPayloadType.NUCLEAR
                    ? 2
                    : 3;
            case ACTIVE_ANTI_AIR -> 4;
            case MK_I_FALLBACK -> 5;
        };
    }
}
