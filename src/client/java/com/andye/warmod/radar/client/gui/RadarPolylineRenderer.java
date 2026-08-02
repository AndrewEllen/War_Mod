package com.andye.warmod.radar.client.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/** Clipped, bounded pixel polylines for the radar extraction GUI. */
public final class RadarPolylineRenderer {
	private static final int MAX_STEPS = 4096;
	private RadarPolylineRenderer() { }

	public static void drawSolidPolyline(final GuiGraphicsExtractor graphics, final List<Vec3> points,
		final RadarMapTransform transform, final int left, final int top, final int width, final int height,
		final int color, final int thickness) {
		for (int index = 1; index < points.size(); index++) {
			Vec3 a = points.get(index - 1), b = points.get(index);
			drawSegment(graphics, transform.screenX(a.x, left, width), transform.screenY(a.z, top, height),
				transform.screenX(b.x, left, width), transform.screenY(b.z, top, height),
				left, top, width, height, color, thickness, false, 0);
		}
	}

	public static void drawDashedPolyline(final GuiGraphicsExtractor graphics, final List<Vec3> points,
		final RadarMapTransform transform, final int left, final int top, final int width, final int height,
		final int color, final int thickness) {
		int phase = 0;
		for (int index = 1; index < points.size(); index++) {
			Vec3 a = points.get(index - 1), b = points.get(index);
			phase = drawSegment(graphics, transform.screenX(a.x, left, width), transform.screenY(a.z, top, height),
				transform.screenX(b.x, left, width), transform.screenY(b.z, top, height),
				left, top, width, height, color, thickness, true, phase);
		}
	}

	public static void drawSegment(final GuiGraphicsExtractor graphics, final double x0, final double y0,
		final double x1, final double y1, final int left, final int top, final int width, final int height,
		final int color, final int thickness) {
		drawSegment(graphics, x0, y0, x1, y1, left, top, width, height, color, thickness, false, 0);
	}

	private static int drawSegment(final GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1,
		final int left, final int top, final int width, final int height, final int color, final int thickness,
		final boolean dashed, int dashPhase) {
		double[] clipped = clip(x0, y0, x1, y1, left, top, left + width - 1, top + height - 1);
		if (clipped == null) return dashPhase;
		int ax = (int)Math.round(clipped[0]), ay = (int)Math.round(clipped[1]);
		int bx = (int)Math.round(clipped[2]), by = (int)Math.round(clipped[3]);
		int dx = Math.abs(bx - ax), sx = ax < bx ? 1 : -1, dy = -Math.abs(by - ay), sy = ay < by ? 1 : -1;
		int error = dx + dy, steps = 0, radius = Math.max(0, thickness - 1);
		while (steps++ < MAX_STEPS) {
			if (!dashed || (dashPhase % 9) < 5) graphics.fill(ax - radius, ay - radius, ax + radius + 1, ay + radius + 1, color);
			dashPhase++;
			if (ax == bx && ay == by) break;
			int twice = error * 2;
			if (twice >= dy) { error += dy; ax += sx; }
			if (twice <= dx) { error += dx; ay += sy; }
		}
		return dashPhase;
	}

	public static void drawRing(final GuiGraphicsExtractor graphics, final int centerX, final int centerY,
		final double radius, final int left, final int top, final int width, final int height,
		final int color, final int thickness) {
		if (!Double.isFinite(radius) || radius <= 1.0 || radius > Math.max(width, height) * 2.0) return;
		int segments = Math.max(48, Math.min(512, (int)Math.ceil(Math.PI * 2.0 * radius / 3.0)));
		double previousX = centerX + radius, previousY = centerY;
		for (int index = 1; index <= segments; index++) {
			double angle = Math.PI * 2.0 * index / segments;
			double x = centerX + Math.cos(angle) * radius, y = centerY + Math.sin(angle) * radius;
			drawSegment(graphics, previousX, previousY, x, y, left, top, width, height, color, thickness);
			previousX = x; previousY = y;
		}
	}

	private static double[] clip(double x0, double y0, double x1, double y1,
		final double minimumX, final double minimumY, final double maximumX, final double maximumY) {
		double dx = x1 - x0, dy = y1 - y0;
		double[] p = {-dx, dx, -dy, dy};
		double[] q = {x0 - minimumX, maximumX - x0, y0 - minimumY, maximumY - y0};
		double start = 0.0, end = 1.0;
		for (int index = 0; index < 4; index++) {
			if (p[index] == 0.0) { if (q[index] < 0.0) return null; continue; }
			double ratio = q[index] / p[index];
			if (p[index] < 0.0) start = Math.max(start, ratio); else end = Math.min(end, ratio);
			if (start > end) return null;
		}
		return new double[] {x0 + start * dx, y0 + start * dy, x0 + end * dx, y0 + end * dy};
	}
}