package com.andye.warmod.radar.display.client;

import com.andye.warmod.radar.display.RadarDisplayOfflineReason;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

public final class RadarDisplayMapRenderer {
    private static final int BACKGROUND = 0xFF04100D;
    private static final int OFFLINE_BACKGROUND = 0xFF080B0C;
    private static final int FRAME = 0xFF202B2F;
    private static final int MINOR_GRID = 0x3A245044;
    private static final int MAJOR_GRID = 0x692D7760;
    private static final int CENTRE_AXIS = 0x8A3E9577;
    private static final int SWEEP = 0xE85CF3A1;
    private static final int SWEEP_FADE = 0x2445D98B;
    private static final int WARNING_CLEAR = 0x99C58B35;
    private static final int WARNING_ACTIVE = 0xFFFF3B35;
    private static final int FIRE_CLEAR = 0x8872B7C4;
    private static final int FIRE_ACTIVE = 0xFF50E7FF;
    private static final int CENTRE_MARKER = 0xFFFFC45A;

    private RadarDisplayMapRenderer() {
    }

    public static void render(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayRenderState state
    ) {
        RadarDisplayPlaneTransform plane =
            new RadarDisplayPlaneTransform(state.facing);

        int size = Math.max(1, state.size);

        RadarDisplayPrimitiveBuilder.fill(
            pose,
            buffer,
            plane,
            0.0,
            0.0,
            size,
            size,
            0.0,
            state.online
                ? BACKGROUND
                : OFFLINE_BACKGROUND
        );

        drawOuterFrame(
            pose,
            buffer,
            plane,
            size
        );

        if (!state.structureValid) {
            drawFailureMark(
                pose,
                buffer,
                plane,
                size,
                0xCCFF3737
            );

            return;
        }

        if (!state.online) {
            int colour = state.syncing
                ? 0xD0FFB84A
                : state.offlineReason
                    == RadarDisplayOfflineReason.UNLINKED
                        ? 0xD0D6A94A
                        : 0xD0FF4747;

            drawFailureMark(
                pose,
                buffer,
                plane,
                size,
                colour
            );

            return;
        }

        RadarDisplayMapTransform transform =
            new RadarDisplayMapTransform(
                state.radarCentre,
                state.displayRadius,
                size
            );

        renderGrid(
            pose,
            buffer,
            plane,
            transform,
            state
        );

        renderRanges(
            pose,
            buffer,
            plane,
            transform,
            state
        );

        renderSweep(
            pose,
            buffer,
            plane,
            transform,
            state
        );

        renderObservations(
            pose,
            buffer,
            plane,
            transform,
            state
        );

        RadarDisplayMapTransform.Point centre =
            transform.map(state.radarCentre);

        RadarDisplayPrimitiveBuilder.diamond(
            pose,
            buffer,
            plane,
            centre.x(),
            centre.y(),
            Math.max(0.025, size * 0.014),
            0.007,
            CENTRE_MARKER
        );
    }

    private static void drawOuterFrame(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final int size
    ) {
        double width = Math.max(
            0.028,
            size * 0.008
        );

        RadarDisplayPrimitiveBuilder.line(
            pose,
            buffer,
            plane,
            0,
            0,
            size,
            0,
            width,
            0.009,
            FRAME
        );

        RadarDisplayPrimitiveBuilder.line(
            pose,
            buffer,
            plane,
            size,
            0,
            size,
            size,
            width,
            0.009,
            FRAME
        );

        RadarDisplayPrimitiveBuilder.line(
            pose,
            buffer,
            plane,
            size,
            size,
            0,
            size,
            width,
            0.009,
            FRAME
        );

        RadarDisplayPrimitiveBuilder.line(
            pose,
            buffer,
            plane,
            0,
            size,
            0,
            0,
            width,
            0.009,
            FRAME
        );
    }

    private static void drawFailureMark(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final int size,
        final int colour
    ) {
        double margin = size * 0.22;
        double width = Math.max(
            0.025,
            size * 0.009
        );

        RadarDisplayPrimitiveBuilder.line(
            pose,
            buffer,
            plane,
            margin,
            margin,
            size - margin,
            size - margin,
            width,
            0.006,
            colour
        );

        RadarDisplayPrimitiveBuilder.line(
            pose,
            buffer,
            plane,
            margin,
            size - margin,
            size - margin,
            margin,
            width,
            0.006,
            colour
        );
    }

    private static void renderGrid(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayMapTransform transform,
        final RadarDisplayRenderState state
    ) {
        double spacing = niceGridSpacing(
            state.displayRadius
        );

        double minimumWorldX =
            state.radarCentre.x - state.displayRadius;

        double maximumWorldX =
            state.radarCentre.x + state.displayRadius;

        double minimumWorldZ =
            state.radarCentre.z - state.displayRadius;

        double maximumWorldZ =
            state.radarCentre.z + state.displayRadius;

        double firstX =
            Math.floor(minimumWorldX / spacing) * spacing;

        double firstZ =
            Math.floor(minimumWorldZ / spacing) * spacing;

        int lineIndex = 0;

        for (double worldX = firstX;
            worldX <= maximumWorldX + 0.001;
            worldX += spacing) {
            RadarDisplayMapTransform.Point bottom =
                transform.map(worldX, maximumWorldZ);

            RadarDisplayMapTransform.Point top =
                transform.map(worldX, minimumWorldZ);

            boolean major = lineIndex % 5 == 0;

            RadarDisplayPrimitiveBuilder.clippedLine(
                pose,
                buffer,
                plane,
                transform,
                bottom,
                top,
                major ? 0.012 : 0.006,
                0.002,
                major ? MAJOR_GRID : MINOR_GRID
            );

            lineIndex++;
        }

        lineIndex = 0;

        for (double worldZ = firstZ;
            worldZ <= maximumWorldZ + 0.001;
            worldZ += spacing) {
            RadarDisplayMapTransform.Point left =
                transform.map(minimumWorldX, worldZ);

            RadarDisplayMapTransform.Point right =
                transform.map(maximumWorldX, worldZ);

            boolean major = lineIndex % 5 == 0;

            RadarDisplayPrimitiveBuilder.clippedLine(
                pose,
                buffer,
                plane,
                transform,
                left,
                right,
                major ? 0.012 : 0.006,
                0.002,
                major ? MAJOR_GRID : MINOR_GRID
            );

            lineIndex++;
        }

        RadarDisplayMapTransform.Point west =
            transform.map(
                state.radarCentre.x - state.displayRadius,
                state.radarCentre.z
            );

        RadarDisplayMapTransform.Point east =
            transform.map(
                state.radarCentre.x + state.displayRadius,
                state.radarCentre.z
            );

        RadarDisplayMapTransform.Point south =
            transform.map(
                state.radarCentre.x,
                state.radarCentre.z + state.displayRadius
            );

        RadarDisplayMapTransform.Point north =
            transform.map(
                state.radarCentre.x,
                state.radarCentre.z - state.displayRadius
            );

        RadarDisplayPrimitiveBuilder.clippedLine(
            pose,
            buffer,
            plane,
            transform,
            west,
            east,
            0.014,
            0.003,
            CENTRE_AXIS
        );

        RadarDisplayPrimitiveBuilder.clippedLine(
            pose,
            buffer,
            plane,
            transform,
            south,
            north,
            0.014,
            0.003,
            CENTRE_AXIS
        );
    }

    private static void renderRanges(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayMapTransform transform,
        final RadarDisplayRenderState state
    ) {
        RadarDisplayMapTransform.Point centre =
            transform.map(state.radarCentre);

        int fireColour = state.redstoneSignal == 15
            ? FIRE_ACTIVE
            : FIRE_CLEAR;

        int warningColour = state.redstoneSignal > 0
            ? WARNING_ACTIVE
            : WARNING_CLEAR;

        RadarDisplayPrimitiveBuilder.ring(
            pose,
            buffer,
            plane,
            centre.x(),
            centre.y(),
            transform.worldRadiusToLocal(
                state.fireRadius
            ),
            128,
            0.012,
            0.004,
            fireColour
        );

        RadarDisplayPrimitiveBuilder.ring(
            pose,
            buffer,
            plane,
            centre.x(),
            centre.y(),
            transform.worldRadiusToLocal(
                state.warningRadius
            ),
            128,
            0.012,
            0.004,
            warningColour
        );
    }

    private static void renderSweep(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayMapTransform transform,
        final RadarDisplayRenderState state
    ) {
        RadarDisplayMapTransform.Point centre =
            transform.map(state.radarCentre);

        double angle =
            Math.toRadians(state.sweepAngleDegrees);

        double radius =
            transform.extent() * 0.72;

        double endX =
            centre.x() + Math.sin(angle) * radius;

        double endY =
            centre.y() + Math.cos(angle) * radius;

        RadarDisplayPrimitiveBuilder.clippedLine(
            pose,
            buffer,
            plane,
            transform,
            centre,
            new RadarDisplayMapTransform.Point(
                endX,
                endY
            ),
            0.018,
            0.006,
            SWEEP
        );

        for (int index = 1; index <= 6; index++) {
            double trailing =
                angle - Math.toRadians(index * 1.1);

            RadarDisplayPrimitiveBuilder.clippedLine(
                pose,
                buffer,
                plane,
                transform,
                centre,
                new RadarDisplayMapTransform.Point(
                    centre.x()
                        + Math.sin(trailing) * radius,
                    centre.y()
                        + Math.cos(trailing) * radius
                ),
                0.010,
                0.005,
                withAlpha(
                    SWEEP_FADE,
                    Math.max(4, 34 - index * 5)
                )
            );
        }
    }

    private static void renderObservations(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayMapTransform transform,
        final RadarDisplayRenderState state
    ) {
        for (RadarDisplayRenderObservation observation
            : state.observations) {
            if (observation.alpha() <= 0.0F) {
                continue;
            }

            int completedColour = withAlpha(
                observation.rgb(),
                (int)(255.0F * observation.alpha())
            );

            int projectedColour = withAlpha(
                observation.rgb(),
                (int)(112.0F * observation.alpha())
            );

            for (RadarDisplayRouteSection route
                : observation.routes()) {
                RadarDisplayPrimitiveBuilder.route(
                    pose,
                    buffer,
                    plane,
                    transform,
                    route.points(),
                    route.completedSegments(),
                    completedColour,
                    projectedColour,
                    0.019,
                    0.010,
                    0.005
                );
            }

            RadarDisplayMapTransform.Point observed =
                transform.map(
                    observation.observedPosition()
                );

            RadarDisplayPrimitiveBuilder.diamond(
                pose,
                buffer,
                plane,
                observed.x(),
                observed.y(),
                Math.max(0.022, state.size * 0.012),
                0.008,
                completedColour
            );

            RadarDisplayMapTransform.Point impact =
                transform.map(
                    observation.predictedImpactPosition()
                );

            RadarDisplayPrimitiveBuilder.cross(
                pose,
                buffer,
                plane,
                impact.x(),
                impact.y(),
                Math.max(0.025, state.size * 0.013),
                0.009,
                0.008,
                completedColour
            );
        }
    }

    private static double niceGridSpacing(
        final double radius
    ) {
        double requested =
            radius * 2.0 / 10.0;

        double magnitude =
            Math.pow(
                10.0,
                Math.floor(Math.log10(
                    Math.max(1.0, requested)
                ))
            );

        double normalized =
            requested / magnitude;

        double nice;

        if (normalized <= 1.0) {
            nice = 1.0;
        } else if (normalized <= 2.0) {
            nice = 2.0;
        } else if (normalized <= 5.0) {
            nice = 5.0;
        } else {
            nice = 10.0;
        }

        return nice * magnitude;
    }

    private static int withAlpha(
        final int rgb,
        final int alpha
    ) {
        return (Math.max(0, Math.min(255, alpha)) << 24)
            | (rgb & 0x00FFFFFF);
    }
}
