package com.andye.warmod.radar.display.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

public final class RadarDisplayPrimitiveBuilder {
    private RadarDisplayPrimitiveBuilder() {
    }

    public static void fill(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final double minimumX,
        final double minimumY,
        final double maximumX,
        final double maximumY,
        final double depth,
        final int argb
    ) {
        vertex(pose, buffer, plane, minimumX, minimumY, depth, argb);
        vertex(pose, buffer, plane, maximumX, minimumY, depth, argb);
        vertex(pose, buffer, plane, maximumX, maximumY, depth, argb);
        vertex(pose, buffer, plane, minimumX, maximumY, depth, argb);
    }

    /**
     * Emits one flat quad. The previous implementation extruded every line into
     * a six-faced translucent prism; upload sorting could then interleave the
     * prism faces with rings, markers and the sweep line, causing angle-dependent
     * squares and missing arc sections.
     */
    public static void line(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final double startX,
        final double startY,
        final double endX,
        final double endY,
        final double width,
        final double depth,
        final int argb
    ) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double length = Math.hypot(deltaX, deltaY);

        if (!Double.isFinite(length)
            || length < 1.0E-7
            || !Double.isFinite(width)
            || width <= 0.0) {
            return;
        }

        double perpendicularX = -deltaY / length * width * 0.5;
        double perpendicularY = deltaX / length * width * 0.5;

        vertex(
            pose,
            buffer,
            plane,
            startX + perpendicularX,
            startY + perpendicularY,
            depth,
            argb
        );
        vertex(
            pose,
            buffer,
            plane,
            startX - perpendicularX,
            startY - perpendicularY,
            depth,
            argb
        );
        vertex(
            pose,
            buffer,
            plane,
            endX - perpendicularX,
            endY - perpendicularY,
            depth,
            argb
        );
        vertex(
            pose,
            buffer,
            plane,
            endX + perpendicularX,
            endY + perpendicularY,
            depth,
            argb
        );
    }

    public static void clippedLine(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayMapTransform transform,
        final RadarDisplayMapTransform.Point start,
        final RadarDisplayMapTransform.Point end,
        final double width,
        final double depth,
        final int argb
    ) {
        RadarDisplayMapTransform.Segment segment = transform.clip(start, end);

        if (segment == null) {
            return;
        }

        line(
            pose,
            buffer,
            plane,
            segment.start().x(),
            segment.start().y(),
            segment.end().x(),
            segment.end().y(),
            width,
            depth,
            argb
        );
    }

    public static void route(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayMapTransform transform,
        final List<Vec3> points,
        final int completedSegments,
        final int completedColour,
        final int projectedColour,
        final double completedWidth,
        final double projectedWidth,
        final double depth
    ) {
        for (int index = 0; index + 1 < points.size(); index++) {
            boolean completed = index < completedSegments;

            if (!completed && (index & 1) != 0) {
                continue;
            }

            clippedLine(
                pose,
                buffer,
                plane,
                transform,
                transform.map(points.get(index)),
                transform.map(points.get(index + 1)),
                completed ? completedWidth : projectedWidth,
                depth,
                completed ? completedColour : projectedColour
            );
        }
    }

    public static void ring(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final double centreX,
        final double centreY,
        final double radius,
        final int segments,
        final double width,
        final double depth,
        final int argb
    ) {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            return;
        }

        int count = Math.max(24, Math.min(256, segments));

        for (int index = 0; index < count; index++) {
            double first = Math.PI * 2.0 * index / count;
            double second = Math.PI * 2.0 * (index + 1) / count;

            line(
                pose,
                buffer,
                plane,
                centreX + Math.cos(first) * radius,
                centreY + Math.sin(first) * radius,
                centreX + Math.cos(second) * radius,
                centreY + Math.sin(second) * radius,
                width,
                depth,
                argb
            );
        }
    }

    public static void diamond(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final double centreX,
        final double centreY,
        final double radius,
        final double depth,
        final int argb
    ) {
        vertex(pose, buffer, plane, centreX, centreY + radius, depth, argb);
        vertex(pose, buffer, plane, centreX + radius, centreY, depth, argb);
        vertex(pose, buffer, plane, centreX, centreY - radius, depth, argb);
        vertex(pose, buffer, plane, centreX - radius, centreY, depth, argb);
    }

    public static void cross(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final double centreX,
        final double centreY,
        final double radius,
        final double width,
        final double depth,
        final int argb
    ) {
        line(
            pose,
            buffer,
            plane,
            centreX - radius,
            centreY,
            centreX + radius,
            centreY,
            width,
            depth,
            argb
        );
        line(
            pose,
            buffer,
            plane,
            centreX,
            centreY - radius,
            centreX,
            centreY + radius,
            width,
            depth,
            argb
        );
    }

    public static void globalLineForTile(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayMapTransform.Point start,
        final RadarDisplayMapTransform.Point end,
        final int tileX,
        final int tileY,
        final double width,
        final double depth,
        final int colour
    ) {
        RadarDisplayTileClip.Segment segment = RadarDisplayTileClip.line(
            start,
            end,
            tileX,
            tileY
        );

        if (segment == null) {
            return;
        }

        line(
            pose,
            buffer,
            plane,
            segment.start().x(),
            segment.start().y(),
            segment.end().x(),
            segment.end().y(),
            width,
            depth,
            colour
        );
    }

    public static void globalMarkerForTile(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final RadarDisplayMapTransform.Point point,
        final int tileX,
        final int tileY,
        final double radius,
        final double depth,
        final int colour,
        final boolean crossMarker
    ) {
        if (!RadarDisplayTileClip.contains(point, tileX, tileY)) {
            return;
        }

        RadarDisplayTileClip.Point local = RadarDisplayTileClip.local(
            point,
            tileX,
            tileY
        );

        if (crossMarker) {
            cross(
                pose,
                buffer,
                plane,
                local.x(),
                local.y(),
                radius,
                radius * 0.36,
                depth,
                colour
            );
        } else {
            diamond(
                pose,
                buffer,
                plane,
                local.x(),
                local.y(),
                radius,
                depth,
                colour
            );
        }
    }

    public static void globalRingForTile(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final double centreX,
        final double centreY,
        final double radius,
        final int segments,
        final int tileX,
        final int tileY,
        final double lineWidth,
        final double depth,
        final int colour
    ) {
        int count = Math.max(96, Math.min(256, segments));

        for (int index = 0; index < count; index++) {
            double first = Math.PI * 2.0 * index / count;
            double second = Math.PI * 2.0 * (index + 1) / count;

            globalLineForTile(
                pose,
                buffer,
                plane,
                new RadarDisplayMapTransform.Point(
                    centreX + Math.cos(first) * radius,
                    centreY + Math.sin(first) * radius
                ),
                new RadarDisplayMapTransform.Point(
                    centreX + Math.cos(second) * radius,
                    centreY + Math.sin(second) * radius
                ),
                tileX,
                tileY,
                lineWidth,
                depth,
                colour
            );
        }
    }

    private static void vertex(
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final RadarDisplayPlaneTransform plane,
        final double x,
        final double y,
        final double depth,
        final int argb
    ) {
        Vec3 point = plane.point(x, y, depth);
        Vec3 normal = plane.normal();

        int alpha = argb >>> 24 & 0xFF;
        int red = argb >>> 16 & 0xFF;
        int green = argb >>> 8 & 0xFF;
        int blue = argb & 0xFF;

        buffer.addVertex(
            pose,
            (float)point.x,
            (float)point.y,
            (float)point.z
        ).setColor(
            red,
            green,
            blue,
            alpha
        ).setUv(
            0.5F,
            0.5F
        ).setOverlay(
            OverlayTexture.NO_OVERLAY
        ).setLight(
            0xF000F0
        ).setNormal(
            pose,
            (float)normal.x,
            (float)normal.y,
            (float)normal.z
        );
    }
}
