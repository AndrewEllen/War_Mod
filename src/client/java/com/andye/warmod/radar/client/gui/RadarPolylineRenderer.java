package com.andye.warmod.radar.client.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/** Clipped radar polylines with batched pixel runs to reduce GUI draw nodes. */
public final class RadarPolylineRenderer {
    private static final int MAX_STEPS = 2048;

    private RadarPolylineRenderer() {
    }

    public static void drawSolidPolyline(
        final GuiGraphicsExtractor graphics,
        final List<Vec3> points,
        final RadarMapTransform transform,
        final int left,
        final int top,
        final int width,
        final int height,
        final int color,
        final int thickness
    ) {
        drawRouteRange(
            graphics,
            points,
            1.0,
            0.0,
            1.0,
            transform,
            left,
            top,
            width,
            height,
            color,
            thickness,
            false
        );
    }

    public static void drawDashedPolyline(
        final GuiGraphicsExtractor graphics,
        final List<Vec3> points,
        final RadarMapTransform transform,
        final int left,
        final int top,
        final int width,
        final int height,
        final int color,
        final int thickness
    ) {
        drawRouteRange(
            graphics,
            points,
            1.0,
            0.0,
            1.0,
            transform,
            left,
            top,
            width,
            height,
            color,
            thickness,
            true
        );
    }

    public static void drawRouteRange(
        final GuiGraphicsExtractor graphics,
        final List<Vec3> points,
        final double duration,
        final double from,
        final double to,
        final RadarMapTransform transform,
        final int left,
        final int top,
        final int width,
        final int height,
        final int color,
        final int thickness,
        final boolean dashed
    ) {
        if (points.size() < 2 || duration <= 0.0 || to <= from) return;
        double start = Math.max(0.0, from);
        double end = Math.min(duration, to);
        if (end <= start) return;

        int segments = points.size() - 1;
        int dashPhase = 0;
        for (int index = 0; index < segments; index++) {
            double segmentStart = duration * index / segments;
            double segmentEnd = duration * (index + 1) / segments;
            double visibleStart = Math.max(start, segmentStart);
            double visibleEnd = Math.min(end, segmentEnd);
            if (visibleEnd <= visibleStart) continue;

            Vec3 first = points.get(index);
            Vec3 second = points.get(index + 1);
            double startFraction = (visibleStart - segmentStart)
                / (segmentEnd - segmentStart);
            double endFraction = (visibleEnd - segmentStart)
                / (segmentEnd - segmentStart);
            double startX = first.x + (second.x - first.x) * startFraction;
            double startZ = first.z + (second.z - first.z) * startFraction;
            double endX = first.x + (second.x - first.x) * endFraction;
            double endZ = first.z + (second.z - first.z) * endFraction;

            dashPhase = drawSegment(
                graphics,
                transform.screenX(startX, left, width),
                transform.screenY(startZ, top, height),
                transform.screenX(endX, left, width),
                transform.screenY(endZ, top, height),
                left,
                top,
                width,
                height,
                color,
                thickness,
                dashed,
                dashPhase
            );
        }
    }

    public static void drawSegment(
        final GuiGraphicsExtractor graphics,
        final double x0,
        final double y0,
        final double x1,
        final double y1,
        final int left,
        final int top,
        final int width,
        final int height,
        final int color,
        final int thickness
    ) {
        drawSegment(
            graphics,
            x0,
            y0,
            x1,
            y1,
            left,
            top,
            width,
            height,
            color,
            thickness,
            false,
            0
        );
    }

    private static int drawSegment(
        final GuiGraphicsExtractor graphics,
        double x0,
        double y0,
        final double x1,
        final double y1,
        final int left,
        final int top,
        final int width,
        final int height,
        final int color,
        final int thickness,
        final boolean dashed,
        int dashPhase
    ) {
        double deltaX = x1 - x0;
        double deltaY = y1 - y0;
        double lower = 0.0;
        double upper = 1.0;
        double ratio;

        if (deltaX == 0.0) {
            if (x0 < left || x0 > left + width - 1) return dashPhase;
        } else {
            ratio = (left - x0) / deltaX;
            if (deltaX > 0.0) lower = Math.max(lower, ratio);
            else upper = Math.min(upper, ratio);
            ratio = (left + width - 1 - x0) / deltaX;
            if (deltaX > 0.0) upper = Math.min(upper, ratio);
            else lower = Math.max(lower, ratio);
            if (lower > upper) return dashPhase;
        }

        if (deltaY == 0.0) {
            if (y0 < top || y0 > top + height - 1) return dashPhase;
        } else {
            ratio = (top - y0) / deltaY;
            if (deltaY > 0.0) lower = Math.max(lower, ratio);
            else upper = Math.min(upper, ratio);
            ratio = (top + height - 1 - y0) / deltaY;
            if (deltaY > 0.0) upper = Math.min(upper, ratio);
            else lower = Math.max(lower, ratio);
            if (lower > upper) return dashPhase;
        }

        int startX = (int)Math.round(x0 + lower * deltaX);
        int startY = (int)Math.round(y0 + lower * deltaY);
        int endX = (int)Math.round(x0 + upper * deltaX);
        int endY = (int)Math.round(y0 + upper * deltaY);
        int absoluteX = Math.abs(endX - startX);
        int stepX = startX < endX ? 1 : -1;
        int negativeAbsoluteY = -Math.abs(endY - startY);
        int stepY = startY < endY ? 1 : -1;
        int error = absoluteX + negativeAbsoluteY;
        int radius = Math.max(0, thickness - 1);
        boolean horizontalDominant = absoluteX >= -negativeAbsoluteY;

        PixelRun run = new PixelRun(horizontalDominant, radius, color);
        int x = startX;
        int y = startY;
        int steps = 0;

        while (steps++ < MAX_STEPS) {
            boolean visible = !dashed || Math.floorMod(dashPhase, 9) < 5;
            if (visible) run.add(graphics, x, y);
            else run.flush(graphics);
            dashPhase++;

            if (x == endX && y == endY) break;
            int twice = error * 2;
            if (twice >= negativeAbsoluteY) {
                error += negativeAbsoluteY;
                x += stepX;
            }
            if (twice <= absoluteX) {
                error += absoluteX;
                y += stepY;
            }
        }
        run.flush(graphics);
        return dashPhase;
    }

    public static void drawRing(
        final GuiGraphicsExtractor graphics,
        final int centerX,
        final int centerY,
        final double radius,
        final int left,
        final int top,
        final int width,
        final int height,
        final int color,
        final int thickness,
        final int maximumSegments
    ) {
        if (!Double.isFinite(radius) || radius <= 1.0) return;
        int segments = Math.max(
            16,
            Math.min(
                maximumSegments,
                (int)Math.ceil(Math.PI * 2.0 * radius / 3.0)
            )
        );
        double previousX = centerX + radius;
        double previousY = centerY;
        for (int index = 1; index <= segments; index++) {
            double angle = Math.PI * 2.0 * index / segments;
            double x = centerX + Math.cos(angle) * radius;
            double y = centerY + Math.sin(angle) * radius;
            drawSegment(
                graphics,
                previousX,
                previousY,
                x,
                y,
                left,
                top,
                width,
                height,
                color,
                thickness
            );
            previousX = x;
            previousY = y;
        }
    }

    public static void drawRing(
        final GuiGraphicsExtractor graphics,
        final int centerX,
        final int centerY,
        final double radius,
        final int left,
        final int top,
        final int width,
        final int height,
        final int color,
        final int thickness
    ) {
        drawRing(
            graphics,
            centerX,
            centerY,
            radius,
            left,
            top,
            width,
            height,
            color,
            thickness,
            128
        );
    }

    /** Batches adjacent Bresenham pixels into one GUI fill rectangle. */
    private static final class PixelRun {
        private final boolean horizontal;
        private final int radius;
        private final int color;
        private boolean active;
        private int firstX;
        private int firstY;
        private int lastX;
        private int lastY;

        private PixelRun(
            final boolean horizontal,
            final int radius,
            final int color
        ) {
            this.horizontal = horizontal;
            this.radius = radius;
            this.color = color;
        }

        private void add(
            final GuiGraphicsExtractor graphics,
            final int x,
            final int y
        ) {
            boolean extendsRun = active && (horizontal
                ? y == lastY && Math.abs(x - lastX) == 1
                : x == lastX && Math.abs(y - lastY) == 1);
            if (!extendsRun) {
                flush(graphics);
                active = true;
                firstX = lastX = x;
                firstY = lastY = y;
                return;
            }
            lastX = x;
            lastY = y;
        }

        private void flush(final GuiGraphicsExtractor graphics) {
            if (!active) return;
            graphics.fill(
                Math.min(firstX, lastX) - radius,
                Math.min(firstY, lastY) - radius,
                Math.max(firstX, lastX) + radius + 1,
                Math.max(firstY, lastY) + radius + 1,
                color
            );
            active = false;
        }
    }
}
