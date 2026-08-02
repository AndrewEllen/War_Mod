package com.andye.warmod.radar.client.gui;

import com.andye.warmod.radar.client.ClientRadarImpact;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

public final class RadarImpactRenderer {
    private static final double RING_FADE_TAIL_TICKS = 60.0;
    private static final double RING_VISUAL_LIFETIME =
        WarheadVisualMath.AIR_SHOCKWAVE_DURATION_TICKS + RING_FADE_TAIL_TICKS;

    private RadarImpactRenderer() { }

    public static void render(final GuiGraphicsExtractor graphics, final ClientRadarImpact impact,
        final RadarMapTransform transform, final double now, final int left, final int top,
        final int width, final int height) {
        var snapshot = impact.snapshot();
        double age = Math.max(0.0, now - snapshot.impactGameTime());
        Vec3 position = snapshot.impactPosition();
        int centerX = (int)Math.round(transform.screenX(position.x, left, width));
        int centerY = (int)Math.round(transform.screenY(position.z, top, height));
        boolean nuclear = snapshot.payloadType() == WarheadPayloadType.NUCLEAR;
        int leading = nuclear ? 0xffff7040 : 0xffffc45a;
        graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, leading);
        if (age >= RING_VISUAL_LIFETIME) return;

        double propagationAge = Math.min(age, WarheadVisualMath.AIR_SHOCKWAVE_DURATION_TICKS);
        double radius = WarheadVisualMath.airShockwaveRadius(propagationAge)
            / transform.blocksPerPixel();
        double fade = age <= WarheadVisualMath.AIR_SHOCKWAVE_DURATION_TICKS ? 1.0
            : 1.0 - (age - WarheadVisualMath.AIR_SHOCKWAVE_DURATION_TICKS)
                / RING_FADE_TAIL_TICKS;
        int alpha = (int)Math.round(255.0 * Math.max(0.0, fade));
        int ringColor = (alpha << 24) | (leading & 0x00ffffff);
        int trailing = ((int)(alpha * (nuclear ? 0.60 : 0.40)) << 24)
            | (leading & 0x00ffffff);
        RadarPolylineRenderer.drawRing(graphics, centerX, centerY, radius,
            left, top, width, height, ringColor, nuclear ? 2 : 1, nuclear ? 384 : 256);
        RadarPolylineRenderer.drawRing(graphics, centerX, centerY,
            Math.max(0.0, radius - (nuclear ? 4.0 : 3.0)),
            left, top, width, height, trailing, 1, nuclear ? 384 : 256);
    }
}