package com.andye.warmod.artillery;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Solves the discrete, gravity-only trajectory used by artillery shells. */
public final class ArtilleryTrajectory {
    /*
     * Fixed-speed low arcs made close targets look as though they were being fired into the
     * ground.  Choose a visibly artillery-like elevation first, then solve the required
     * speed.  The lowest fallback still has a clear upward component.
     */
    private static final double[] PREFERRED_ELEVATIONS_DEGREES = { 54.0, 50.0, 46.0, 42.0, 38.0 };

    private ArtilleryTrajectory() { }

    public static Optional<Vec3> solve(final Vec3 origin, final Vec3 target) {
        if (origin == null || target == null || !origin.isFinite() || !target.isFinite()) return Optional.empty();
        Vec3 horizontal = new Vec3(target.x - origin.x, 0.0, target.z - origin.z);
        double distance = horizontal.horizontalDistance();
        double dy = target.y - origin.y;
        if (distance < 0.001 || distance > ArtilleryConstants.MAX_RANGE_BLOCKS) return Optional.empty();
        for (double elevationDegrees : PREFERRED_ELEVATIONS_DEGREES) {
            Vec3 velocity = solveAtElevation(horizontal, distance, dy, Math.toRadians(elevationDegrees));
            if (velocity != null) return Optional.of(velocity);
        }

        // Retain the old maximum-speed solution as a last resort for unusual high targets.
        double speed = ArtilleryConstants.MAX_MUZZLE_SPEED;
        double gravity = ArtilleryConstants.GRAVITY_PER_TICK;
        double discriminant = speed * speed * speed * speed - gravity * (gravity * distance * distance + 2.0 * dy * speed * speed);
        if (discriminant < 0.0 || !Double.isFinite(discriminant)) return Optional.empty();
        double root = Math.sqrt(discriminant);
        Vec3 best = null;
        for (double tangent : new double[] { (speed * speed + root) / (gravity * distance), (speed * speed - root) / (gravity * distance) }) {
            if (!Double.isFinite(tangent)) continue;
            double cos = 1.0 / Math.sqrt(1.0 + tangent * tangent);
            double horizontalSpeed = speed * cos;
            double verticalSpeed = horizontalSpeed * tangent;
            if (horizontalSpeed <= 0.0 || verticalSpeed * verticalSpeed / (2.0 * gravity) > ArtilleryConstants.MAX_APEX_ABOVE_MUZZLE + 1.0E-6) continue;
            Vec3 velocity = horizontal.normalize().scale(horizontalSpeed).add(0.0, verticalSpeed, 0.0);
            if (best == null || velocity.y > best.y) best = velocity;
        }
        return Optional.ofNullable(best);
    }

    /**
     * Uses the same discrete integration as {@code ArtilleryWarheadEntity}: the shell moves
     * by its present velocity and only then loses gravity for the next tick.  Solving that
     * equation prevents the barrel preview and the real shell from disagreeing at range.
     */
    private static Vec3 solveAtElevation(final Vec3 horizontal, final double distance,
        final double dy, final double elevationRadians) {
        double tangent = Math.tan(elevationRadians);
        double gravity = ArtilleryConstants.GRAVITY_PER_TICK;
        double a = 0.5 * gravity * distance * distance;
        double b = -0.5 * gravity * distance;
        double c = dy - distance * tangent;
        double discriminant = b * b - 4.0 * a * c;
        if (!Double.isFinite(discriminant) || discriminant < 0.0) return null;
        double root = Math.sqrt(discriminant);
        double bestHorizontalSpeed = Double.NEGATIVE_INFINITY;
        for (double inverseHorizontalSpeed : new double[] {
            (-b + root) / (2.0 * a), (-b - root) / (2.0 * a)
        }) {
            if (!Double.isFinite(inverseHorizontalSpeed) || inverseHorizontalSpeed <= 1.0E-8) continue;
            double horizontalSpeed = 1.0 / inverseHorizontalSpeed;
            double verticalSpeed = horizontalSpeed * tangent;
            double speedSquared = horizontalSpeed * horizontalSpeed + verticalSpeed * verticalSpeed;
            if (!Double.isFinite(speedSquared) || speedSquared > ArtilleryConstants.MAX_MUZZLE_SPEED
                * ArtilleryConstants.MAX_MUZZLE_SPEED || verticalSpeed <= 0.0
                || apexAboveMuzzle(new Vec3(0.0, verticalSpeed, 0.0))
                    > ArtilleryConstants.MAX_APEX_ABOVE_MUZZLE + 1.0E-6) continue;
            if (horizontalSpeed > bestHorizontalSpeed) bestHorizontalSpeed = horizontalSpeed;
        }
        if (bestHorizontalSpeed <= 0.0) return null;
        return horizontal.normalize().scale(bestHorizontalSpeed)
            .add(0.0, bestHorizontalSpeed * tangent, 0.0);
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
