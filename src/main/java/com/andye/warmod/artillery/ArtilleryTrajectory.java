package com.andye.warmod.artillery;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Solves the discrete, gravity-only high arc used by artillery shells. */
public final class ArtilleryTrajectory {
    private ArtilleryTrajectory() { }

    public static Optional<Vec3> solve(final Vec3 origin, final Vec3 target) {
        if (origin == null || target == null || !origin.isFinite() || !target.isFinite()) return Optional.empty();
        Vec3 horizontal = new Vec3(target.x - origin.x, 0.0, target.z - origin.z);
        double distance = horizontal.horizontalDistance();
        double dy = target.y - origin.y;
        if (distance < 0.001 || distance > ArtilleryConstants.MAX_RANGE_BLOCKS) return Optional.empty();
        double gravity = ArtilleryConstants.GRAVITY_PER_TICK;
        double desiredApex = Math.min(300.0, 58.0 + distance * 0.24);
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        /* Solve from integer flight time because the entity integrates velocity
         * before gravity. This lets us choose the cinematic high arc explicitly
         * without sacrificing exact arrival at the selected block. */
        for (int ticks = 12; ticks <= 900; ticks++) {
            double horizontalSpeed = distance / ticks;
            double verticalSpeed = (dy + gravity * ticks * (ticks - 1) * 0.5) / ticks;
            double speedSquared = horizontalSpeed * horizontalSpeed
                + verticalSpeed * verticalSpeed;
            if (verticalSpeed <= 0.0 || !Double.isFinite(speedSquared)
                || speedSquared > ArtilleryConstants.MAX_MUZZLE_SPEED
                    * ArtilleryConstants.MAX_MUZZLE_SPEED) continue;
            double apex = verticalSpeed * verticalSpeed / (2.0 * gravity);
            if (apex > ArtilleryConstants.MAX_APEX_ABOVE_MUZZLE) continue;
            double error = apex >= desiredApex ? apex - desiredApex
                : (desiredApex - apex) * 1.75;
            double score = error + ticks * 0.010;
            if (score < bestScore) {
                bestScore = score;
                best = horizontal.normalize().scale(horizontalSpeed)
                    .add(0.0, verticalSpeed, 0.0);
            }
        }
        return Optional.ofNullable(best);
    }

    /** Solves the physical muzzle and velocity together so renderer and server share one aim. */
    public static Optional<LaunchSolution> solveFromCannon(final Vec3 pivot, final Vec3 target) {
        Vec3 pivotVelocity = solve(pivot, target).orElse(null);
        if (pivotVelocity == null || pivotVelocity.lengthSqr() < 1.0E-8) return Optional.empty();
        Vec3 muzzle = pivot.add(pivotVelocity.normalize().scale(ArtilleryConstants.BARREL_MUZZLE_OFFSET));
        Vec3 muzzleVelocity = solve(muzzle, target).orElse(null);
        return muzzleVelocity == null ? Optional.empty() : Optional.of(new LaunchSolution(muzzle, muzzleVelocity));
    }

    public static double apexAboveMuzzle(final Vec3 velocity) {
        return velocity.y <= 0.0 ? 0.0 : velocity.y * velocity.y / (2.0 * ArtilleryConstants.GRAVITY_PER_TICK);
    }

    /** Number of physics ticks required to cover the horizontal part of a solved arc. */
    public static int flightTicks(final Vec3 origin, final Vec3 target, final Vec3 velocity) {
        if (origin == null || target == null || velocity == null || !origin.isFinite()
            || !target.isFinite() || !velocity.isFinite()) return 1;
        double horizontalSpeed = velocity.horizontalDistance();
        if (!Double.isFinite(horizontalSpeed) || horizontalSpeed < 1.0E-6) return 1;
        double distance = target.subtract(origin).horizontalDistance();
        return Math.max(1, (int) Math.ceil(distance / horizontalSpeed));
    }

    public record LaunchSolution(Vec3 muzzle, Vec3 velocity) { }
}
