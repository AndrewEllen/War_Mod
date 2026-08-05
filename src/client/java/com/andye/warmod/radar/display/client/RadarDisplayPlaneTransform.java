package com.andye.warmod.radar.display.client;

import com.andye.warmod.radar.display.RadarDisplayOrientation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class RadarDisplayPlaneTransform {
    /*
     * The display used to begin 0.065 blocks in front of the model face, then
     * added another 0.020 blocks of internal layer depth. That prevented
     * clipping but made the complete UI visibly float away from the panels.
     * The renderer now uses flat quads with depth writes, so a much smaller
     * separation remains stable without the visible gap.
     */
    private static final double FRONT_OFFSET = 0.512;

    private final Vec3 origin;
    private final Vec3 right;
    private final Vec3 normal;

    public RadarDisplayPlaneTransform(final Direction facing) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException(
                "Radar Display facing must be horizontal"
            );
        }

        normal = vector(facing);
        right = vector(RadarDisplayOrientation.screenRight(facing));
        origin = new Vec3(0.5, 0.0, 0.5)
            .add(normal.scale(FRONT_OFFSET))
            .subtract(right.scale(0.5));
    }

    public Vec3 point(
        final double localX,
        final double localY,
        final double depth
    ) {
        return origin
            .add(right.scale(localX))
            .add(0.0, localY, 0.0)
            .add(normal.scale(depth));
    }

    public Vec3 normal() {
        return normal;
    }

    private static Vec3 vector(final Direction direction) {
        return new Vec3(
            direction.getStepX(),
            direction.getStepY(),
            direction.getStepZ()
        );
    }
}
