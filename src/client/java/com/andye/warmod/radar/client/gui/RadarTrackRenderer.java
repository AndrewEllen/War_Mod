package com.andye.warmod.radar.client.gui;

import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.radar.RadarCarrierPlanSnapshot;
import com.andye.warmod.radar.RadarTerminalPlanSnapshot;
import com.andye.warmod.radar.RadarTrackKind;
import com.andye.warmod.radar.RadarTrackSnapshot;
import com.andye.warmod.radar.client.ClientRadarTrack;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadTrajectory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RadarTrackRenderer {
	private RadarTrackRenderer() { }

	public static void render(final GuiGraphicsExtractor graphics, final ClientRadarTrack track,
		final RadarMapTransform transform, final double now, final int left, final int top,
		final int width, final int height, final boolean selected) {
		boolean nuclear = track.snapshot().payloadType() == WarheadPayloadType.NUCLEAR;
		int completed = nuclear ? 0xffff5a32 : 0xffffad32;
		int projected = nuclear ? 0x886b281f : 0x8870521d;
		drawRoutes(graphics, track, transform, now, left, top, width, height, completed, projected, selected);
		drawLaunch(graphics, transform, track.launch(), left, top, width, height);
		drawTarget(graphics, transform, track.target(), left, top, width, height, completed);
		drawMissile(graphics, track, transform, now, left, top, width, height, completed, selected);
	}

	private static void drawRoutes(final GuiGraphicsExtractor graphics, final ClientRadarTrack track,
		final RadarMapTransform transform, final double now, final int left, final int top, final int width,
		final int height, final int completed, final int projected, final boolean selected) {
		RadarTrackSnapshot snapshot = track.snapshot();
		if (snapshot.carrierPlan().isPresent()) {
			RadarCarrierPlanSnapshot carrier = snapshot.carrierPlan().get();
			IcbmFlightPlan plan = plan(snapshot, carrier);
			double end = plan.separationTick();
			if (snapshot.terminalPlan().isPresent()) {
				List<Vec3> route = carrierRoute(plan, 0.0, end);
				RadarPolylineRenderer.drawSolidPolyline(graphics, route, transform, left, top, width, height,
					(completed & 0x00ffffff) | 0x99000000, 1);
			} else {
				double current = Mth.clamp(track.carrierElapsed(now), 0.0, end);
				drawCompleted(graphics, carrierRoute(plan, 0.0, current), transform, left, top, width, height,
					completed, selected);
				RadarPolylineRenderer.drawDashedPolyline(graphics, carrierRoute(plan, current, end), transform,
					left, top, width, height, projected, 1);
			}
		}
		if (snapshot.terminalPlan().isPresent()) {
			RadarTerminalPlanSnapshot terminal = snapshot.terminalPlan().get();
			double current = Mth.clamp(track.terminalElapsed(now), 0.0, terminal.flightTicks());
			drawCompleted(graphics, terminalRoute(terminal, 0.0, current), transform, left, top, width, height,
				completed, selected);
			RadarPolylineRenderer.drawDashedPolyline(graphics,
				terminalRoute(terminal, current, terminal.flightTicks()), transform,
				left, top, width, height, projected, 1);
		}
	}

	private static void drawCompleted(final GuiGraphicsExtractor graphics, final List<Vec3> points,
		final RadarMapTransform transform, final int left, final int top, final int width, final int height,
		final int color, final boolean selected) {
		if (selected) RadarPolylineRenderer.drawSolidPolyline(graphics, points, transform, left, top, width, height,
			0x66ffffff, 3);
		RadarPolylineRenderer.drawSolidPolyline(graphics, points, transform, left, top, width, height, color, 2);
	}

	private static List<Vec3> carrierRoute(final IcbmFlightPlan plan, final double start, final double end) {
		if (end <= start) return List.of(IcbmTrajectory.position(plan, start));
		int samples = Math.max(2, Math.min(96, (int)Math.ceil((end - start) / 5.0)));
		List<Vec3> points = new ArrayList<>(samples + 1);
		for (int index = 0; index <= samples; index++)
			points.add(IcbmTrajectory.position(plan, Mth.lerp(index / (double)samples, start, end)));
		return points;
	}

	private static List<Vec3> terminalRoute(final RadarTerminalPlanSnapshot plan, final double start, final double end) {
		if (end <= start) return List.of(WarheadTrajectory.position(plan.startPosition(), plan.targetPosition(), start, plan.flightTicks()));
		int samples = Math.max(2, Math.min(48, (int)Math.ceil((end - start) / 3.0)));
		List<Vec3> points = new ArrayList<>(samples + 1);
		for (int index = 0; index <= samples; index++) {
			double elapsed = Mth.lerp(index / (double)samples, start, end);
			points.add(WarheadTrajectory.position(plan.startPosition(), plan.targetPosition(), elapsed, plan.flightTicks()));
		}
		return points;
	}

	private static void drawMissile(final GuiGraphicsExtractor graphics, final ClientRadarTrack track,
		final RadarMapTransform transform, final double now, final int left, final int top, final int width,
		final int height, final int color, final boolean selected) {
		Vec3 position = track.position(now), velocity = track.velocity(now);
		double x = transform.screenX(position.x, left, width), y = transform.screenY(position.z, top, height);
		if (x < left || x >= left + width || y < top || y >= top + height) {
			x = Math.max(left + 4, Math.min(left + width - 5, x));
			y = Math.max(top + 4, Math.min(top + height - 5, y));
		}
		double length = Math.hypot(velocity.x, velocity.z), dx = length < 1.0E-6 ? 0.0 : velocity.x / length;
		double dy = length < 1.0E-6 ? 1.0 : velocity.z / length, sideX = -dy, sideY = dx;
		boolean terminal = track.snapshot().terminalPlan().isPresent() || track.snapshot().kind() == RadarTrackKind.DIRECT_WARHEAD;
		double nose = terminal ? 4.0 : 5.0, tail = terminal ? 2.5 : 3.5, halfWidth = terminal ? 2.5 : 3.5;
		double tipX = x + dx * nose, tipY = y + dy * nose;
		double aX = x - dx * tail + sideX * halfWidth, aY = y - dy * tail + sideY * halfWidth;
		double bX = x - dx * tail - sideX * halfWidth, bY = y - dy * tail - sideY * halfWidth;
		if (selected) {
			RadarPolylineRenderer.drawSegment(graphics, tipX, tipY, aX, aY, left, top, width, height, 0xffffffff, 2);
			RadarPolylineRenderer.drawSegment(graphics, aX, aY, bX, bY, left, top, width, height, 0xffffffff, 2);
			RadarPolylineRenderer.drawSegment(graphics, bX, bY, tipX, tipY, left, top, width, height, 0xffffffff, 2);
		}
		RadarPolylineRenderer.drawSegment(graphics, tipX, tipY, aX, aY, left, top, width, height, color, 1);
		RadarPolylineRenderer.drawSegment(graphics, aX, aY, bX, bY, left, top, width, height, color, 1);
		RadarPolylineRenderer.drawSegment(graphics, bX, bY, tipX, tipY, left, top, width, height, color, 1);
	}

	private static void drawLaunch(final GuiGraphicsExtractor graphics, final RadarMapTransform transform,
		final Vec3 position, final int left, final int top, final int width, final int height) {
		int x = (int)Math.round(transform.screenX(position.x, left, width));
		int y = (int)Math.round(transform.screenY(position.z, top, height));
		RadarPolylineRenderer.drawRing(graphics, x, y, 3.0, left, top, width, height, 0xff66d9ff, 1);
	}

	private static void drawTarget(final GuiGraphicsExtractor graphics, final RadarMapTransform transform,
		final Vec3 position, final int left, final int top, final int width, final int height, final int color) {
		int x = (int)Math.round(transform.screenX(position.x, left, width));
		int y = (int)Math.round(transform.screenY(position.z, top, height));
		RadarPolylineRenderer.drawSegment(graphics, x - 5, y, x + 5, y, left, top, width, height, color, 1);
		RadarPolylineRenderer.drawSegment(graphics, x, y - 5, x, y + 5, left, top, width, height, color, 1);
	}

	private static IcbmFlightPlan plan(final RadarTrackSnapshot snapshot, final RadarCarrierPlanSnapshot plan) {
		return new IcbmFlightPlan(snapshot.trackId(), snapshot.ownerPlayerId(), plan.launchPosition(),
			plan.burnoutPosition(), plan.separationPosition(), plan.intendedTarget(), plan.launchGameTime(),
			plan.ignitionTicks(), plan.boostTicks(), plan.coastTicks(), plan.visualSeed(), snapshot.payloadType());
	}
}