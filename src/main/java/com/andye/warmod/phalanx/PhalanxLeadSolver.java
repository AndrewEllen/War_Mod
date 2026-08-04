package com.andye.warmod.phalanx;

import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class PhalanxLeadSolver {
    public record Solution(
        Vec3 direction,
        double flightTicks,
        double elevationDegrees
    ) {
    }

    private PhalanxLeadSolver() {
    }

    public static Optional<Solution> solve(
        final Vec3 muzzle,
        final Vec3 target,
        final Vec3 velocity
    ) {
        Vec3 relative =
            target.subtract(muzzle);

        double speed =
            PhalanxConstants
                .BULLET_SPEED_BLOCKS_PER_TICK;

        double a =
            velocity.lengthSqr()
                - speed * speed;

        double b =
            2.0 * relative.dot(velocity);

        double c =
            relative.lengthSqr();

        double flightTicks;

        if (Math.abs(a) < 1.0E-9) {
            if (Math.abs(b) < 1.0E-9) {
                return Optional.empty();
            }

            flightTicks = -c / b;
        } else {
            double discriminant =
                b * b - 4.0 * a * c;

            if (discriminant < 0.0) {
                return Optional.empty();
            }

            double root =
                Math.sqrt(discriminant);

            double first =
                (-b - root) / (2.0 * a);

            double second =
                (-b + root) / (2.0 * a);

            if (first > 0.0 && second > 0.0) {
                flightTicks =
                    Math.min(first, second);
            } else {
                flightTicks =
                    Math.max(first, second);
            }
        }

        /*
         * Do not compare flightTicks against one global bullet lifetime.
         *
         * Each successful shot receives a lifetime based on this particular
         * interception solution.
         */
        if (!Double.isFinite(flightTicks)
            || flightTicks <= 0.0) {
            return Optional.empty();
        }

        Vec3 aim =
            target
                .add(
                    velocity.scale(
                        flightTicks
                    )
                )
                .add(
                    0.0,
                    0.5
                        * PhalanxConstants
                            .BULLET_GRAVITY_PER_TICK_SQUARED
                        * flightTicks
                        * flightTicks,
                    0.0
                )
                .subtract(muzzle);

        if (!aim.isFinite()
            || aim.lengthSqr() < 1.0E-8) {
            return Optional.empty();
        }

        Vec3 direction =
            aim.normalize();

        double elevation =
            Math.toDegrees(
                Math.atan2(
                    direction.y,
                    direction.horizontalDistance()
                )
            );

        if (elevation
                < PhalanxConstants
                    .MIN_ELEVATION_DEGREES
            || elevation
                > PhalanxConstants
                    .MAX_ELEVATION_DEGREES) {
            return Optional.empty();
        }

        return Optional.of(
            new Solution(
                direction,
                flightTicks,
                elevation
            )
        );
    }

    public static Vec3 spread(
        final Vec3 direction,
        final UUID turret,
        final long sequence,
        final long gameTime,
        final double degrees
    ) {
        long seed =
            turret.getMostSignificantBits()
                ^ Long.rotateLeft(
                    turret.getLeastSignificantBits(),
                    17
                )
                ^ sequence * 0x9E3779B97F4A7C15L
                ^ gameTime
                ^ 0x5048414C414E58L;

        SplittableRandom random =
            new SplittableRandom(seed);

        Vec3 axis =
            Math.abs(direction.y) < 0.9
                ? new Vec3(0.0, 1.0, 0.0)
                : new Vec3(1.0, 0.0, 0.0);

        Vec3 first =
            direction.cross(axis).normalize();

        Vec3 second =
            direction.cross(first).normalize();

        double radius =
            Math.tan(
                Math.toRadians(degrees)
            ) * Math.sqrt(random.nextDouble());

        double angle =
            random.nextDouble(
                Math.PI * 2.0
            );

        return direction
            .add(
                first.scale(
                    Math.cos(angle) * radius
                )
            )
            .add(
                second.scale(
                    Math.sin(angle) * radius
                )
            )
            .normalize();
    }
}