package com.andye.warmod.radar.display.client;

import net.minecraft.world.phys.Vec3;

public final class RadarDisplayMapTransform {
    private final Vec3 radarCentre;
    private final double displayRadius;
    private final double minimum;
    private final double maximum;
    private final double extent;

    public RadarDisplayMapTransform(
        final Vec3 radarCentre,
        final double displayRadius,
        final int displaySize
    ) {
        this.radarCentre = radarCentre;
        this.displayRadius = Math.max(1.0, displayRadius);

        double margin = Math.max(
            0.045,
            displaySize * 0.025
        );

        minimum = margin;
        maximum = displaySize - margin;
        extent = Math.max(0.01, maximum - minimum);
    }

    public Point map(final Vec3 worldPosition) {
        return map(worldPosition.x, worldPosition.z);
    }

    public Point map(
        final double worldX,
        final double worldZ
    ) {
        double normalizedX =
            0.5
            + (worldX - radarCentre.x)
                / (displayRadius * 2.0);

        double normalizedY =
            0.5
            - (worldZ - radarCentre.z)
                / (displayRadius * 2.0);

        return new Point(
            minimum + normalizedX * extent,
            minimum + normalizedY * extent
        );
    }

    public double worldRadiusToLocal(
        final double worldRadius
    ) {
        return worldRadius
            / (displayRadius * 2.0)
            * extent;
    }

    public double minimum() {
        return minimum;
    }

    public double maximum() {
        return maximum;
    }

    public double extent() {
        return extent;
    }

    /**
     * Liang-Barsky clipping against the active map rectangle.
     */
    public @org.jspecify.annotations.Nullable Segment clip(
        final Point start,
        final Point end
    ) {
        double deltaX = end.x() - start.x();
        double deltaY = end.y() - start.y();

        double lower = 0.0;
        double upper = 1.0;

        double[] p = {
            -deltaX,
            deltaX,
            -deltaY,
            deltaY
        };

        double[] q = {
            start.x() - minimum,
            maximum - start.x(),
            start.y() - minimum,
            maximum - start.y()
        };

        for (int index = 0; index < 4; index++) {
            if (Math.abs(p[index]) < 1.0E-9) {
                if (q[index] < 0.0) {
                    return null;
                }

                continue;
            }

            double ratio = q[index] / p[index];

            if (p[index] < 0.0) {
                lower = Math.max(lower, ratio);
            } else {
                upper = Math.min(upper, ratio);
            }

            if (lower > upper) {
                return null;
            }
        }

        return new Segment(
            new Point(
                start.x() + deltaX * lower,
                start.y() + deltaY * lower
            ),
            new Point(
                start.x() + deltaX * upper,
                start.y() + deltaY * upper
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
