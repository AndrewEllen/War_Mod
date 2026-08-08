package com.andye.warmod.artillery;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Analytic low-arc solver followed by integer-tick correction to hit the requested target exactly. */
public final class ArtilleryBallistics {
    private static final double SPEED_EPSILON = 1.0E-4;

    private ArtilleryBallistics() {
    }

    public static Optional<Solution> solve(final ServerLevel level, final Vec3 muzzle,
        final Vec3 target) {
        if (level == null || muzzle == null || target == null
            || !muzzle.isFinite() || !target.isFinite()) return Optional.empty();

        double dx = target.x - muzzle.x;
        double dz = target.z - muzzle.z;
        double horizontal = Math.hypot(dx, dz);
        double vertical = target.y - muzzle.y;
        if (!Double.isFinite(horizontal)
            || horizontal < ArtilleryConstants.MINIMUM_HORIZONTAL_RANGE_BLOCKS
            || horizontal > ArtilleryConstants.MAXIMUM_RANGE_BLOCKS + 1.0E-6) {
            return Optional.empty();
        }

        double speed = ArtilleryConstants.MAXIMUM_MUZZLE_SPEED_BLOCKS_PER_TICK;
        double gravity = ArtilleryConstants.GRAVITY_BLOCKS_PER_TICK_SQUARED;
        double speedSquared = speed * speed;
        double discriminant = speedSquared * speedSquared
            - gravity * (gravity * horizontal * horizontal + 2.0 * vertical * speedSquared);
        if (!Double.isFinite(discriminant) || discriminant < 0.0) return Optional.empty();

        // Prefer the low solution. Close targets therefore use a flatter trajectory while
        // long shots naturally approach 45 degrees and the configured maximum apex.
        double tanTheta = (speedSquared - Math.sqrt(discriminant)) / (gravity * horizontal);
        double angle = Math.atan(tanTheta);
        double horizontalVelocity = speed * Math.cos(angle);
        if (!Double.isFinite(horizontalVelocity) || horizontalVelocity <= 1.0E-6) {
            return Optional.empty();
        }

        int flightTicks = Math.max(1, (int)Math.ceil(horizontal / horizontalVelocity));
        double maximumWorldY = level.dimensionType().minY() + level.dimensionType().height() - 2.0;
        double allowedApex = Math.min(maximumWorldY,
            muzzle.y + ArtilleryConstants.MAXIMUM_APEX_ABOVE_MUZZLE_BLOCKS);
        double ux = dx / horizontal;
        double uz = dz / horizontal;

        // Integer ticks can differ from the continuous solution by floating-point noise at
        // the exact 1000-block boundary. The epsilon is 0.002% of the 5 block/tick cap.
        for (int attempt = 0; attempt < 24
            && flightTicks <= ArtilleryConstants.MAXIMUM_FLIGHT_TICKS; attempt++, flightTicks++) {
            double t = flightTicks;
            double vxz = horizontal / t;
            double vy = (vertical + 0.5 * gravity * t * t) / t;
            double correctedSpeed = Math.sqrt(vxz * vxz + vy * vy);
            double apex = vy > 0.0 ? muzzle.y + vy * vy / (2.0 * gravity) : muzzle.y;
            if (!Double.isFinite(correctedSpeed) || !Double.isFinite(apex)) continue;
            if (correctedSpeed > speed + SPEED_EPSILON || apex > allowedApex + 1.0E-6) continue;

            Vec3 velocity = new Vec3(ux * vxz, vy, uz * vxz);
            double correctedAngle = Math.toDegrees(Math.atan2(vy, vxz));
            return Optional.of(new Solution(velocity, flightTicks, correctedAngle,
                correctedSpeed, apex, horizontal));
        }
        return Optional.empty();
    }

    public static Vec3 position(final Vec3 start, final Vec3 initialVelocity,
        final double elapsedTicks) {
        double t = Math.max(0.0, elapsedTicks);
        double gravity = ArtilleryConstants.GRAVITY_BLOCKS_PER_TICK_SQUARED;
        return new Vec3(
            start.x + initialVelocity.x * t,
            start.y + initialVelocity.y * t - 0.5 * gravity * t * t,
            start.z + initialVelocity.z * t
        );
    }

    public static Vec3 velocity(final Vec3 initialVelocity, final double elapsedTicks) {
        double t = Math.max(0.0, elapsedTicks);
        return new Vec3(initialVelocity.x,
            initialVelocity.y - ArtilleryConstants.GRAVITY_BLOCKS_PER_TICK_SQUARED * t,
            initialVelocity.z);
    }

    public record Solution(Vec3 initialVelocity, int flightTicks, double angleDegrees,
        double muzzleSpeed, double apexY, double horizontalRange) {
    }
}
