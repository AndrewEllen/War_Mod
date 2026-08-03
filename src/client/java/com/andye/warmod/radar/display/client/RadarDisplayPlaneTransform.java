package com.andye.warmod.radar.display.client;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Converts display-local coordinates into controller-block local coordinates.
 *
 * Local x:
 *   Left to right as seen from the front.
 *
 * Local y:
 *   Bottom to top.
 */
public final class RadarDisplayPlaneTransform {
    private static final double FRONT_OFFSET = 0.503;

    private final Vec3 origin;
    private final Vec3 right;
    private final Vec3 normal;

    public RadarDisplayPlaneTransform(
        final Direction facing
    ) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException(
                "Radar Display facing must be horizontal"
            );
        }

        normal = vector(facing);
        right = vector(facing.getCounterClockWise());

        Vec3 controllerCentre =
            new Vec3(0.5, 0.0, 0.5);

        // The controller is the bottom-left panel.
        origin = controllerCentre
            .add(normal.scale(FRONT_OFFSET))
            .subtract(right.scale(0.5));
    }

    public Vec3 point(
        final double localX,
        final double localY,
        final double outwardDepth
    ) {
        return origin
            .add(right.scale(localX))
            .add(0.0, localY, 0.0)
            .add(normal.scale(outwardDepth));
    }

    public Vec3 normal() {
        return normal;
    }

    private static Vec3 vector(
        final Direction direction
    ) {
        return new Vec3(
            direction.getStepX(),
            direction.getStepY(),
            direction.getStepZ()
        );
    }
}
