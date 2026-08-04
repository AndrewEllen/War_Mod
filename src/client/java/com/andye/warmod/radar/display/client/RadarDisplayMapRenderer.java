package com.andye.warmod.radar.display.client;

import com.andye.warmod.radar.display.RadarDisplayConstants;
import com.andye.warmod.radar.display.RadarDisplayOfflineReason;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class RadarDisplayMapRenderer {
    private static final int BACKGROUND =
        0xFF04100D;

    private static final int OFFLINE_BACKGROUND =
        0xFF080B0C;

    private static final int FRAME =
        0xFF202B2F;

    private static final int GRID =
        0x442D7760;

    private static final int SWEEP =
        0xE85CF3A1;

    private static final int WARNING =
        0x99C58B35;

    private static final int FIRE =
        0x8872B7C4;

    private static final int CENTRE =
        0xFFFFC45A;

    private RadarDisplayMapRenderer() {
    }

    public static void renderTile(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayRenderState state
    ) {
        RadarDisplayPlaneTransform plane =
            new RadarDisplayPlaneTransform(
                state.facing
            );

        /*
         * Do not overlap neighbouring panel surfaces. The old -0.002/1.002
         * bounds caused coplanar overlap along large displays.
         */
        RadarDisplayPrimitiveBuilder.fill(
            pose,
            buffer,
            plane,
            0.0,
            0.0,
            1.0,
            1.0,
            0.0,
            state.online
                ? BACKGROUND
                : OFFLINE_BACKGROUND
        );

        outer(
            pose,
            buffer,
            plane,
            state
        );

        if (!state.online) {
            status(
                pose,
                buffer,
                plane,
                state
            );

            return;
        }

        RadarDisplayMapTransform map =
            new RadarDisplayMapTransform(
                state.radarCentre,
                state.width,
                state.height,
                state.horizontalRadius,
                state.verticalRadius
            );

        grid(
            pose,
            buffer,
            plane,
            state
        );

        RadarDisplayMapTransform.Point centre =
            map.centre();

        RadarDisplayPrimitiveBuilder
            .globalRingForTile(
                pose,
                buffer,
                plane,
                centre.x(),
                centre.y(),
                map.worldRadiusToLocal(
                    state.warningRadius
                ),
                128,
                state.tileX,
                state.tileY,
                0.012,
                0.006,
                WARNING
            );

        RadarDisplayPrimitiveBuilder
            .globalRingForTile(
                pose,
                buffer,
                plane,
                centre.x(),
                centre.y(),
                map.worldRadiusToLocal(
                    state.fireRadius
                ),
                128,
                state.tileX,
                state.tileY,
                0.012,
                0.007,
                FIRE
            );

        double sweepRadius =
            RadarDisplayConstants.sweepRadius(
                state.width,
                state.height
            );

        double angle =
            Math.toRadians(
                state.sweepAngleDegrees
            );

        RadarDisplayMapTransform.Point sweepEnd =
            new RadarDisplayMapTransform.Point(
                centre.x()
                    + Math.sin(angle)
                        * sweepRadius,
                centre.y()
                    + Math.cos(angle)
                        * sweepRadius
            );

        RadarDisplayPrimitiveBuilder
            .globalRingForTile(
                pose,
                buffer,
                plane,
                centre.x(),
                centre.y(),
                sweepRadius,
                128,
                state.tileX,
                state.tileY,
                0.007,
                0.008,
                0x6645D98B
            );

        RadarDisplayPrimitiveBuilder
            .globalLineForTile(
                pose,
                buffer,
                plane,
                centre,
                sweepEnd,
                state.tileX,
                state.tileY,
                0.020,
                0.010,
                SWEEP
            );

        for (
            RadarDisplayRenderObservation observation
                : state.observations
        ) {
            /*
             * RadarDisplayRenderObservation stores 24-bit RGB. The old
             * renderer passed it directly to an ARGB vertex writer, producing
             * alpha zero and making every missile route/contact invisible.
             */
            int colour =
                withAlpha(
                    observation.rgb(),
                    observation.alpha()
                );

            for (
                RadarDisplayRouteSection route
                    : observation.routes()
            ) {
                for (
                    int index = 0;
                    index + 1
                        < route.points().size();
                    index++
                ) {
                    boolean completed =
                        index
                            < route.completedSegments();

                    if (
                        !completed
                        && (index & 1) != 0
                    ) {
                        continue;
                    }

                    RadarDisplayPrimitiveBuilder
                        .globalLineForTile(
                            pose,
                            buffer,
                            plane,
                            map.map(
                                route.points()
                                    .get(index)
                            ),
                            map.map(
                                route.points()
                                    .get(index + 1)
                            ),
                            state.tileX,
                            state.tileY,
                            completed
                                ? 0.024
                                : 0.014,
                            0.012,
                            colour
                        );
                }
            }

            RadarDisplayPrimitiveBuilder
                .globalMarkerForTile(
                    pose,
                    buffer,
                    plane,
                    map.map(
                        observation
                            .observedPosition()
                    ),
                    state.tileX,
                    state.tileY,
                    0.040,
                    0.014,
                    colour,
                    false
                );

            RadarDisplayPrimitiveBuilder
                .globalMarkerForTile(
                    pose,
                    buffer,
                    plane,
                    map.map(
                        observation
                            .predictedImpactPosition()
                    ),
                    state.tileX,
                    state.tileY,
                    0.034,
                    0.014,
                    colour,
                    true
                );
        }

        RadarDisplayPrimitiveBuilder
            .globalMarkerForTile(
                pose,
                buffer,
                plane,
                centre,
                state.tileX,
                state.tileY,
                0.030,
                0.015,
                CENTRE,
                false
            );

        status(
            pose,
            buffer,
            plane,
            state
        );
    }

    private static int withAlpha(
        final int rgb,
        final float requestedAlpha
    ) {
        int alpha =
            Math.max(
                32,
                Math.min(
                    255,
                    Math.round(
                        requestedAlpha
                            * 255.0F
                    )
                )
            );

        return alpha << 24
            | rgb & 0x00FFFFFF;
    }

    private static void outer(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayRenderState state
    ) {
        double lineWidth =
            0.024;

        if (state.tileX == 0) {
            RadarDisplayPrimitiveBuilder.line(
                pose,
                buffer,
                plane,
                0.01,
                0.0,
                0.01,
                1.0,
                lineWidth,
                0.016,
                FRAME
            );
        }

        if (
            state.tileX
                == state.width - 1
        ) {
            RadarDisplayPrimitiveBuilder.line(
                pose,
                buffer,
                plane,
                0.99,
                0.0,
                0.99,
                1.0,
                lineWidth,
                0.016,
                FRAME
            );
        }

        if (state.tileY == 0) {
            RadarDisplayPrimitiveBuilder.line(
                pose,
                buffer,
                plane,
                0.0,
                0.01,
                1.0,
                0.01,
                lineWidth,
                0.016,
                FRAME
            );
        }

        if (
            state.tileY
                == state.height - 1
        ) {
            RadarDisplayPrimitiveBuilder.line(
                pose,
                buffer,
                plane,
                0.0,
                0.99,
                1.0,
                0.99,
                lineWidth,
                0.016,
                FRAME
            );
        }
    }

    private static void grid(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayRenderState state
    ) {
        /*
         * Draw a small local grid on every panel. Lines end at panel edges but
         * use identical coordinates, producing one continuous display without
         * multi-block geometry from a single block entity.
         */
        for (int index = 1; index < 4; index++) {
            double unit =
                index / 4.0;

            RadarDisplayPrimitiveBuilder.line(
                pose,
                buffer,
                plane,
                unit,
                0.0,
                unit,
                1.0,
                0.004,
                0.004,
                GRID
            );

            RadarDisplayPrimitiveBuilder.line(
                pose,
                buffer,
                plane,
                0.0,
                unit,
                1.0,
                unit,
                0.004,
                0.004,
                GRID
            );
        }
    }

    private static void status(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayRenderState state
    ) {
        if (
            state.tileX
                != state.width - 1
            || state.tileY
                != state.height - 1
        ) {
            return;
        }

        if (
            state.online
            && !state.stale
        ) {
            return;
        }

        int colour =
            state.online
                ? 0xFFFFB84A
                : state.syncing
                    ? 0xFFFFB84A
                    : state.offlineReason
                            == RadarDisplayOfflineReason
                                .UNLINKED
                        ? 0xFFD6A94A
                        : 0xFFFF4747;

        RadarDisplayPrimitiveBuilder.fill(
            pose,
            buffer,
            plane,
            0.91,
            0.91,
            0.97,
            0.97,
            0.020,
            colour
        );
    }
}
