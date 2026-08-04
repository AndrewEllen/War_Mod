package com.andye.warmod.radar.display.client;

import com.andye.warmod.radar.display.RadarDisplayOrientation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class RadarDisplayPlaneTransform {
    /*
     * The old 0.510 offset was close enough to the block face to z-fight at
     * oblique angles and at distance. This remains visually flush while being
     * safely in front of the static block model.
     */
    private static final double FRONT_OFFSET = 0.535;

    private final Vec3 origin;
    private final Vec3 right;
    private final Vec3 normal;

    public RadarDisplayPlaneTransform(
        final Direction facing
    ) {
        if (
            !facing.getAxis()
                .isHorizontal()
        ) {
            throw new IllegalArgumentException(
                "Radar Display facing must be horizontal"
            );
        }

        normal =
            vector(facing);

        right =
            vector(
                RadarDisplayOrientation
                    .screenRight(facing)
            );

        origin =
            new Vec3(
                0.5,
                0.0,
                0.5
            )
                .add(
                    normal.scale(
                        FRONT_OFFSET
                    )
                )
                .subtract(
                    right.scale(0.5)
                );
    }

    public Vec3 point(
        final double localX,
        final double localY,
        final double depth
    ) {
        return origin
            .add(
                right.scale(localX)
            )
            .add(
                0.0,
                localY,
                0.0
            )
            .add(
                normal.scale(depth)
            );
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
