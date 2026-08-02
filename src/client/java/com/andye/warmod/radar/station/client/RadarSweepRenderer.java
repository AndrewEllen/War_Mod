package com.andye.warmod.radar.station.client;

import com.andye.warmod.radar.client.gui.RadarMapTransform;
import com.andye.warmod.radar.client.gui.RadarPolylineRenderer;
import com.andye.warmod.radar.station.RadarSweepMath;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RadarSweepRenderer {
    private RadarSweepRenderer() { }

    public static void render(final GuiGraphicsExtractor graphics,
        final ClientRadarStationState state, final RadarMapTransform transform,
        final double now, final int left, final int top, final int width, final int height) {
        if (state.centre() == null) return;
        double centerX = transform.screenX(state.centre().getX() + 0.5, left, width);
        double centerY = transform.screenY(state.centre().getZ() + 0.5, top, height);
        double angle = Math.toRadians(RadarSweepMath.angleDegrees(now, state.phaseOffset()));
        double radius = Math.hypot(width, height);
        double endX = centerX + Math.sin(angle) * radius;
        double endY = centerY - Math.cos(angle) * radius;
        RadarPolylineRenderer.drawSegment(graphics, centerX, centerY, endX, endY,
            left, top, width, height, 0xaa62f2a5, 1);
        for (int index = 1; index <= 5; index++) {
            double wedge = angle + Math.toRadians((index - 3) * 0.8);
            RadarPolylineRenderer.drawSegment(graphics, centerX, centerY,
                centerX + Math.sin(wedge) * radius,
                centerY - Math.cos(wedge) * radius,
                left, top, width, height, 0x1845d98b, 1);
        }
        RadarPolylineRenderer.drawRing(graphics, (int)centerX, (int)centerY,
            state.fireRadius() / transform.blocksPerPixel(), left, top, width, height,
            state.redstoneSignal() == 15 ? 0xff50e7ff : 0x8872b7c4, 1);        RadarPolylineRenderer.drawRing(graphics, (int)centerX, (int)centerY,
            state.warningRadius() / transform.blocksPerPixel(), left, top, width, height,
            state.warningActive() ? 0xffff3b35 : 0x99c58b35, 1);
        graphics.fill((int)centerX - 3, (int)centerY - 3,
            (int)centerX + 4, (int)centerY + 4, 0xffffc45a);
        for (ClientRadarBlip blip : state.blips()) {
            RadarStationTrackRenderer.render(graphics, blip, transform, now,
                left, top, width, height, state.sweepPeriod());
        }
    }
}