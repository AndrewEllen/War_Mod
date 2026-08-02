package com.andye.warmod.radar.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class RadarPlayerRenderer {
	private RadarPlayerRenderer() { }

	public static void render(final GuiGraphicsExtractor graphics, final Font font, final LocalPlayer player,
		final RadarMapTransform transform, final int left, final int top, final int width, final int height) {
		if (player == null) return;
		Vec3 position = player.position(), facing = player.getLookAngle();
		double length = Math.hypot(facing.x, facing.z);
		double dx = length < 1.0E-6 ? 0.0 : facing.x / length;
		double dy = length < 1.0E-6 ? -1.0 : facing.z / length;
		int x = (int)Math.round(transform.screenX(position.x, left, width));
		int y = (int)Math.round(transform.screenY(position.z, top, height));
		if (x < left + 6 || x >= left + width - 6 || y < top + 6 || y >= top + height - 6) return;
		double sideX = -dy, sideY = dx;
		double tipX = x + dx * 5.0, tipY = y + dy * 5.0;
		double leftX = x - dx * 3.0 + sideX * 4.0, leftY = y - dy * 3.0 + sideY * 4.0;
		double rightX = x - dx * 3.0 - sideX * 4.0, rightY = y - dy * 3.0 - sideY * 4.0;
		RadarPolylineRenderer.drawSegment(graphics, tipX, tipY, leftX, leftY, left, top, width, height, 0xffffffff, 2);
		RadarPolylineRenderer.drawSegment(graphics, leftX, leftY, rightX, rightY, left, top, width, height, 0xffffffff, 2);
		RadarPolylineRenderer.drawSegment(graphics, rightX, rightY, tipX, tipY, left, top, width, height, 0xffffffff, 2);
		RadarPolylineRenderer.drawSegment(graphics, tipX, tipY + 1, leftX, leftY + 1, left, top, width, height, 0xff70e8ff, 1);
		RadarPolylineRenderer.drawSegment(graphics, leftX, leftY + 1, rightX, rightY + 1, left, top, width, height, 0xff70e8ff, 1);
		graphics.text(font, net.minecraft.network.chat.Component.literal("YOU"), x + 7, y - 4, 0xffdffaff);
	}
}