package com.andye.warmod.artillery;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Solves a gravity-only trajectory, preferring the highest legal arc. */
public final class ArtilleryTrajectory {
    private ArtilleryTrajectory() { }

    public static Optional<Vec3> solve(final Vec3 origin, final Vec3 target) {
        if (origin == null || target == null || !origin.isFinite() || !target.isFinite()) return Optional.empty();
        Vec3 horizontal = new Vec3(target.x - origin.x, 0.0, target.z - origin.z);
        double distance = horizontal.horizontalDistance();
        double dy = target.y - origin.y;
        if (distance < 0.001 || distance > ArtilleryConstants.MAX_RANGE_BLOCKS) return Optional.empty();
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

    public static double apexAboveMuzzle(final Vec3 velocity) {
        return velocity.y <= 0.0 ? 0.0 : velocity.y * velocity.y / (2.0 * ArtilleryConstants.GRAVITY_PER_TICK);
    }
}
