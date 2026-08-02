package com.andye.warmod.radar.client.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/** Clipped, bounded pixel polylines for the radar extraction GUI. */
public final class RadarPolylineRenderer {
	private static final int MAX_STEPS = 2048;
	private RadarPolylineRenderer() { }
	public static void drawSolidPolyline(final GuiGraphicsExtractor graphics, final List<Vec3> points, final RadarMapTransform transform, final int left, final int top, final int width, final int height, final int color, final int thickness) { drawRouteRange(graphics, points, 1.0, 0.0, 1.0, transform, left, top, width, height, color, thickness, false); }
	public static void drawDashedPolyline(final GuiGraphicsExtractor graphics, final List<Vec3> points, final RadarMapTransform transform, final int left, final int top, final int width, final int height, final int color, final int thickness) { drawRouteRange(graphics, points, 1.0, 0.0, 1.0, transform, left, top, width, height, color, thickness, true); }
	public static void drawRouteRange(final GuiGraphicsExtractor graphics, final List<Vec3> points, final double duration, final double from, final double to, final RadarMapTransform transform, final int left, final int top, final int width, final int height, final int color, final int thickness, final boolean dashed) {
		if (points.size() < 2 || duration <= 0.0 || to <= from) return;
		double start = Math.max(0.0, from), end = Math.min(duration, to); if (end <= start) return;
		int segments = points.size() - 1, dashPhase = 0;
		for (int index = 0; index < segments; index++) { double segmentStart = duration * index / segments, segmentEnd = duration * (index + 1) / segments; double visibleStart = Math.max(start, segmentStart), visibleEnd = Math.min(end, segmentEnd); if (visibleEnd <= visibleStart) continue; Vec3 a = points.get(index), b = points.get(index + 1); double startFraction = (visibleStart - segmentStart) / (segmentEnd - segmentStart), endFraction = (visibleEnd - segmentStart) / (segmentEnd - segmentStart); double ax = a.x + (b.x - a.x) * startFraction, az = a.z + (b.z - a.z) * startFraction, bx = a.x + (b.x - a.x) * endFraction, bz = a.z + (b.z - a.z) * endFraction; dashPhase = drawSegment(graphics, transform.screenX(ax, left, width), transform.screenY(az, top, height), transform.screenX(bx, left, width), transform.screenY(bz, top, height), left, top, width, height, color, thickness, dashed, dashPhase); }
	}
	public static void drawSegment(final GuiGraphicsExtractor graphics, final double x0, final double y0, final double x1, final double y1, final int left, final int top, final int width, final int height, final int color, final int thickness) { drawSegment(graphics, x0, y0, x1, y1, left, top, width, height, color, thickness, false, 0); }
	private static int drawSegment(final GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1, final int left, final int top, final int width, final int height, final int color, final int thickness, final boolean dashed, int dashPhase) {
		double dx = x1 - x0, dy = y1 - y0, start = 0.0, end = 1.0, ratio;
		if (dx == 0.0) { if (x0 < left || x0 > left + width - 1) return dashPhase; } else { ratio = (left - x0) / dx; if (dx > 0.0) start = Math.max(start, ratio); else end = Math.min(end, ratio); ratio = (left + width - 1 - x0) / dx; if (dx > 0.0) end = Math.min(end, ratio); else start = Math.max(start, ratio); if (start > end) return dashPhase; }
		if (dy == 0.0) { if (y0 < top || y0 > top + height - 1) return dashPhase; } else { ratio = (top - y0) / dy; if (dy > 0.0) start = Math.max(start, ratio); else end = Math.min(end, ratio); ratio = (top + height - 1 - y0) / dy; if (dy > 0.0) end = Math.min(end, ratio); else start = Math.max(start, ratio); if (start > end) return dashPhase; }
		int ax = (int)Math.round(x0 + start * dx), ay = (int)Math.round(y0 + start * dy), bx = (int)Math.round(x0 + end * dx), by = (int)Math.round(y0 + end * dy); int deltaX = Math.abs(bx - ax), stepX = ax < bx ? 1 : -1, deltaY = -Math.abs(by - ay), stepY = ay < by ? 1 : -1, error = deltaX + deltaY, steps = 0, radius = Math.max(0, thickness - 1);
		while (steps++ < MAX_STEPS) { if (!dashed || (dashPhase % 9) < 5) graphics.fill(ax - radius, ay - radius, ax + radius + 1, ay + radius + 1, color); dashPhase++; if (ax == bx && ay == by) break; int twice = error * 2; if (twice >= deltaY) { error += deltaY; ax += stepX; } if (twice <= deltaX) { error += deltaX; ay += stepY; } }
		return dashPhase;
	}
	public static void drawRing(final GuiGraphicsExtractor graphics, final int centerX, final int centerY, final double radius, final int left, final int top, final int width, final int height, final int color, final int thickness, final int maximumSegments) { if (!Double.isFinite(radius) || radius <= 1.0) return; int segments = Math.max(16, Math.min(maximumSegments, (int)Math.ceil(Math.PI * 2.0 * radius / 3.0))); double previousX = centerX + radius, previousY = centerY; for (int index = 1; index <= segments; index++) { double angle = Math.PI * 2.0 * index / segments, x = centerX + Math.cos(angle) * radius, y = centerY + Math.sin(angle) * radius; drawSegment(graphics, previousX, previousY, x, y, left, top, width, height, color, thickness); previousX = x; previousY = y; } }
	public static void drawRing(final GuiGraphicsExtractor graphics, final int centerX, final int centerY, final double radius, final int left, final int top, final int width, final int height, final int color, final int thickness) { drawRing(graphics, centerX, centerY, radius, left, top, width, height, color, thickness, 128); }
}