package com.andye.warmod.radar.station.client;

import com.andye.warmod.radar.client.ClientRadarTrack;
import com.andye.warmod.radar.client.gui.RadarMapTransform;
import com.andye.warmod.radar.client.gui.RadarPolylineRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RadarStationTrackRenderer {
    private RadarStationTrackRenderer() { }

    public static void render(final GuiGraphicsExtractor graphics, final ClientRadarBlip blip,
        final RadarMapTransform transform, final double now, final int left, final int top,
        final int width, final int height, final int sweepPeriod) {
        var observation = blip.observation();
        ClientRadarTrack track = new ClientRadarTrack(observation.trackSnapshot());
        double alpha = Math.max(0.0, Math.min(1.0, blip.alpha(now, sweepPeriod)));
        int base = observation.threatensWarningZone() ? 0xff3f32
            : observation.trackSnapshot().payloadType() == com.andye.warmod.warhead.WarheadPayloadType.NUCLEAR
                ? 0xff663d : 0xffb43b;
        int completed = ((int)(255 * alpha) << 24) | base;
        int projected = ((int)(120 * alpha) << 24) | base;
        double observedTime = observation.observedRouteTime();

        if (!track.carrierRoute().isEmpty()) {
            double duration = track.carrierDuration();
            double current = track.snapshot().terminalPlan().isPresent()
                ? duration : Mth.clamp(track.carrierElapsed(observedTime), 0.0, duration);
            RadarPolylineRenderer.drawRouteRange(graphics, track.carrierRoute(), duration,
                0.0, current, transform, left, top, width, height, completed, 2, false);
            if (current < duration) {
                RadarPolylineRenderer.drawRouteRange(graphics, track.carrierRoute(), duration,
                    current, duration, transform, left, top, width, height, projected, 1, true);
            }
        }
        if (!track.terminalRoute().isEmpty()) {
            double duration = track.terminalDuration();
            double current = Mth.clamp(track.terminalElapsed(observedTime), 0.0, duration);
            RadarPolylineRenderer.drawRouteRange(graphics, track.terminalRoute(), duration,
                0.0, current, transform, left, top, width, height, completed, 2, false);
            RadarPolylineRenderer.drawRouteRange(graphics, track.terminalRoute(), duration,
                current, duration, transform, left, top, width, height, projected, 1, true);
        }

        marker(graphics, transform, track.launch(), left, top, width, height,
            ((int)(180 * alpha) << 24) | 0x66d9ff, false);
        marker(graphics, transform, track.target(), left, top, width, height,
            completed, true);
        marker(graphics, transform, observation.observedPosition(), left, top,
            width, height, completed, false);
    }

    private static void marker(final GuiGraphicsExtractor graphics, final RadarMapTransform transform,
        final Vec3 position, final int left, final int top, final int width, final int height,
        final int color, final boolean cross) {
        int x = (int)Math.round(transform.screenX(position.x, left, width));
        int y = (int)Math.round(transform.screenY(position.z, top, height));
        if (cross) {
            RadarPolylineRenderer.drawSegment(graphics, x - 4, y, x + 4, y,
                left, top, width, height, color, 1);
            RadarPolylineRenderer.drawSegment(graphics, x, y - 4, x, y + 4,
                left, top, width, height, color, 1);
        } else {
            graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
        }
    }
}