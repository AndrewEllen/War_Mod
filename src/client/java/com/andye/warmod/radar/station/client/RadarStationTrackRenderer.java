package com.andye.warmod.radar.station.client;

import com.andye.warmod.radar.client.ClientRadarTrack;
import com.andye.warmod.radar.client.gui.RadarMapTransform;
import com.andye.warmod.radar.client.gui.RadarPolylineRenderer;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RadarStationTrackRenderer {
    private RadarStationTrackRenderer() {
    }

    public static void render(
        final GuiGraphicsExtractor graphics,
        final ClientRadarBlip blip,
        final RadarMapTransform transform,
        final double now,
        final int left,
        final int top,
        final int width,
        final int height,
        final int sweepPeriod
    ) {
        var observation = blip.observation();
        ClientRadarTrack track = blip.track();
        double alpha = Mth.clamp(blip.alpha(now, sweepPeriod), 0.0, 1.0);
        int base = observation.threatensWarningZone()
            ? 0xFF3F32
            : observation.trackSnapshot().strategicPayloadType().orElse(null)
                    == WarheadPayloadType.NUCLEAR
                ? 0xFF663D
                : 0xFFB43B;
        int completed = ((int)(255 * alpha) << 24) | base;
        int projected = ((int)(120 * alpha) << 24) | base;

        /*
         * A rotating-search radar is sample-and-hold: the sweep itself moves
         * continuously, but a contact remains at the last observed position
         * until the beam crosses it again. Do not extrapolate it every frame.
         */
        if (!track.carrierRoute().isEmpty()) {
            drawRoute(
                graphics,
                track.carrierRoute(),
                track.carrierDuration(),
                blip.carrierProgress(),
                transform,
                left,
                top,
                width,
                height,
                completed,
                projected
            );
        }

        for (ClientRadarBlip.TerminalRender terminal : blip.terminals()) {
            drawRoute(
                graphics,
                terminal.route(),
                terminal.plan().flightTicks(),
                terminal.progress(),
                transform,
                left,
                top,
                width,
                height,
                completed,
                projected
            );
            marker(
                graphics,
                transform,
                terminal.observedPosition(),
                left,
                top,
                width,
                height,
                completed,
                false
            );
            marker(
                graphics,
                transform,
                terminal.plan().targetPosition(),
                left,
                top,
                width,
                height,
                completed,
                true
            );
        }

        marker(
            graphics,
            transform,
            track.launch(),
            left,
            top,
            width,
            height,
            ((int)(180 * alpha) << 24) | 0x66D9FF,
            false
        );

        if (blip.terminals().isEmpty()) {
            marker(
                graphics,
                transform,
                observation.observedPosition(),
                left,
                top,
                width,
                height,
                completed,
                false
            );
            marker(
                graphics,
                transform,
                observation.predictedImpactPosition(),
                left,
                top,
                width,
                height,
                completed,
                true
            );
        }
    }

    private static void drawRoute(
        final GuiGraphicsExtractor graphics,
        final List<Vec3> points,
        final double duration,
        final double current,
        final RadarMapTransform transform,
        final int left,
        final int top,
        final int width,
        final int height,
        final int completedColour,
        final int projectedColour
    ) {
        RadarPolylineRenderer.drawRouteRange(
            graphics,
            points,
            duration,
            0.0,
            current,
            transform,
            left,
            top,
            width,
            height,
            completedColour,
            2,
            false
        );
        if (current < duration) {
            RadarPolylineRenderer.drawRouteRange(
                graphics,
                points,
                duration,
                current,
                duration,
                transform,
                left,
                top,
                width,
                height,
                projectedColour,
                1,
                true
            );
        }
    }

    private static void marker(
        final GuiGraphicsExtractor graphics,
        final RadarMapTransform transform,
        final Vec3 position,
        final int left,
        final int top,
        final int width,
        final int height,
        final int color,
        final boolean cross
    ) {
        int x = (int)Math.round(transform.screenX(position.x, left, width));
        int y = (int)Math.round(transform.screenY(position.z, top, height));
        if (cross) {
            RadarPolylineRenderer.drawSegment(
                graphics, x - 4, y, x + 4, y,
                left, top, width, height, color, 1
            );
            RadarPolylineRenderer.drawSegment(
                graphics, x, y - 4, x, y + 4,
                left, top, width, height, color, 1
            );
        } else {
            graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
        }
    }
}
