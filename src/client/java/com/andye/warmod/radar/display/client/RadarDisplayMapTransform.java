package com.andye.warmod.radar.display.client;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RadarDisplayMapTransform {
    private final Vec3 radarCentre;

    private final int width;
    private final int height;

    private final double horizontalRadius;
    private final double verticalRadius;

    private final double scaleX;
    private final double scaleY;

    public RadarDisplayMapTransform(
        final Vec3 radarCentre,
        final int width,
        final int height,
        final double horizontalRadius,
        final double verticalRadius
    ) {
        this.radarCentre =
            radarCentre;

        this.width =
            Math.max(
                1,
                width
            );

        this.height =
            Math.max(
                1,
                height
            );

        this.horizontalRadius =
            Math.max(
                1.0,
                horizontalRadius
            );

        this.verticalRadius =
            Math.max(
                1.0,
                verticalRadius
            );

        scaleX =
            this.width
                / (
                    this.horizontalRadius
                        * 2.0
                );

        scaleY =
            this.height
                / (
                    this.verticalRadius
                        * 2.0
                );
    }

    public Point map(
        final Vec3 world
    ) {
        return map(
            world.x,
            world.z
        );
    }

    public Point map(
        final double worldX,
        final double worldZ
    ) {
        return new Point(
            width * 0.5
                + (
                    worldX
                        - radarCentre.x
                ) * scaleX,
            height * 0.5
                - (
                    worldZ
                        - radarCentre.z
                ) * scaleY
        );
    }

    public Point centre() {
        return new Point(
            width * 0.5,
            height * 0.5
        );
    }

    /**
     * Uniform local scale for a world-space circle.
     */
    public double worldRadiusToLocal(
        final double worldRadius
    ) {
        return worldRadius
            * Math.min(
                scaleX,
                scaleY
            );
    }

    public @Nullable Segment clip(
        final Point start,
        final Point end
    ) {
        double deltaX =
            end.x() - start.x();

        double deltaY =
            end.y() - start.y();

        double lower =
            0.0;

        double upper =
            1.0;

        double[] p = {
            -deltaX,
            deltaX,
            -deltaY,
            deltaY
        };

        double[] q = {
            start.x(),
            width - start.x(),
            start.y(),
            height - start.y()
        };

        for (int index = 0; index < 4; index++) {
            if (
                Math.abs(p[index])
                    < 1.0E-9
            ) {
                if (q[index] < 0.0) {
                    return null;
                }

                continue;
            }

            double ratio =
                q[index] / p[index];

            if (p[index] < 0.0) {
                lower =
                    Math.max(
                        lower,
                        ratio
                    );
            } else {
                upper =
                    Math.min(
                        upper,
                        ratio
                    );
            }

            if (lower > upper) {
                return null;
            }
        }

        return new Segment(
            new Point(
                start.x()
                    + deltaX * lower,
                start.y()
                    + deltaY * lower
            ),
            new Point(
                start.x()
                    + deltaX * upper,
                start.y()
                    + deltaY * upper
            )
        );
    }

    public record Point(
        double x,
        double y
    ) {
    }

    public record Segment(
        Point start,
        Point end
    ) {
    }
}
