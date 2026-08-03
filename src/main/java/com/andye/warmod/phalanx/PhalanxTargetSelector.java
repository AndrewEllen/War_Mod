package com.andye.warmod.phalanx;

import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public final class PhalanxTargetSelector {
    private PhalanxTargetSelector() { }
    public static Optional<PhalanxTargetSnapshot> select(final Vec3 centre, final Vec3 muzzle, final List<PhalanxTargetSnapshot> candidates) {
        return candidates.stream().filter(target -> valid(centre, muzzle, target)).min(Comparator.comparingInt(PhalanxTargetSelector::kindPriority).thenComparingDouble(PhalanxTargetSnapshot::ticksToImpact).thenComparingDouble(target -> horizontal(centre, target.predictedImpact())).thenComparingDouble(target -> centre.distanceToSqr(target.position())).thenComparing(target -> target.targetId().toString()));
    }
    private static int kindPriority(final PhalanxTargetSnapshot target) { return switch (target.kind()) { case ICBM_CARRIER -> 0; case CLUSTER_SUBMUNITION -> 1; case TERMINAL_WARHEAD, DIRECT_WARHEAD -> target.payloadType().orElse(null) == WarheadPayloadType.NUCLEAR ? 2 : 3; case MK_I_FALLBACK -> 4; }; }
    public static boolean valid(final Vec3 centre, final Vec3 muzzle, final PhalanxTargetSnapshot target) {
        if (target == null || !target.position().isFinite() || !target.velocity().isFinite() || !target.predictedImpact().isFinite()) return false;
        if (horizontal(centre, target.predictedImpact()) > PhalanxConstants.PROTECTED_RADIUS_BLOCKS || muzzle.distanceTo(target.position()) > PhalanxConstants.MAX_ENGAGEMENT_RANGE_BLOCKS) return false;
        double horizontal = Math.hypot(target.position().x - muzzle.x, target.position().z - muzzle.z);
        double elevation = Math.toDegrees(Math.atan2(target.position().y - muzzle.y, horizontal));
        return elevation >= PhalanxConstants.MIN_ELEVATION_DEGREES && elevation <= PhalanxConstants.MAX_ELEVATION_DEGREES;
    }
    public static double horizontal(final Vec3 a, final Vec3 b) { return Math.hypot(a.x - b.x, a.z - b.z); }
}