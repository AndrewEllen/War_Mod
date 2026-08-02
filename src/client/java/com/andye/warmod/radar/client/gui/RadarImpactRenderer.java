package com.andye.warmod.radar.client.gui;

import com.andye.warmod.radar.client.ClientRadarImpact;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

public final class RadarImpactRenderer {
	private RadarImpactRenderer() { }
	public static void render(final GuiGraphicsExtractor graphics, final ClientRadarImpact impact,
		final RadarMapTransform transform, final double now, final int left, final int top,
		final int width, final int height) {
		var snapshot = impact.snapshot();
		double age = Math.max(0.0, now - snapshot.impactGameTime());
		double radius = WarheadVisualMath.airShockwaveRadius(age) / transform.blocksPerPixel();
		Vec3 position = snapshot.impactPosition();
		int centerX = (int)Math.round(transform.screenX(position.x, left, width));
		int centerY = (int)Math.round(transform.screenY(position.z, top, height));
		boolean nuclear = snapshot.payloadType() == WarheadPayloadType.NUCLEAR;
		int leading = nuclear ? 0xffff7040 : 0xffffc45a;
		int trailing = (leading & 0x00ffffff) | (nuclear ? 0x99000000 : 0x66000000);
		graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, leading);
		RadarPolylineRenderer.drawRing(graphics, centerX, centerY, radius, left, top, width, height,
			leading, nuclear ? 2 : 1);
		RadarPolylineRenderer.drawRing(graphics, centerX, centerY, Math.max(0.0, radius - (nuclear ? 4.0 : 3.0)),
			left, top, width, height, trailing, 1);
	}
}