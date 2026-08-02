package com.andye.warmod.radar.client.gui;

import com.andye.warmod.radar.client.ClientRadarImpact;
import com.andye.warmod.radar.client.ClientRadarNetworking;
import com.andye.warmod.radar.client.ClientRadarState;
import com.andye.warmod.radar.client.ClientRadarTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class RadarScreen extends Screen {
	private final RadarMapWidget map = new RadarMapWidget();
	private final ClientRadarState state = ClientRadarState.INSTANCE;
	private int sidebarScroll;
	private boolean initializedMap, closing;
	public RadarScreen() { super(Component.literal("Missile Radar")); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void tick() { double now = state.clock().now(0); ClientRadarTrack selected = state.selected(); if (state.followSelectedTrack() && selected != null) { Vec3 position = selected.position(now); map.transform().center(position.x, position.z); } }
	@Override public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) { int top = 24, bottom = 22, mapWidth = Math.max(1, (int)(width * .74)), mapHeight = Math.max(1, height - top - bottom), sidebarLeft = mapWidth; double now = state.clock().now(partialTick); graphics.fill(0, 0, width, height, 0xff070b0d); RadarMapRenderer.render(graphics, state, map.transform(), now, 0, top, mapWidth, mapHeight); RadarSidebar.render(graphics, font, state, now, sidebarLeft, top, width - sidebarLeft, mapHeight, sidebarScroll); graphics.fill(0, 0, width, top, 0xff11191d); graphics.text(font, Component.literal("MISSILE RADAR"), 8, 8, 0xffffc45a); String dimension = state.dimensionId() == null ? "unknown" : state.dimensionId().toString(); String status = dimension + "   Active: " + state.tracks().size() + "   Scale: " + String.format(Locale.ROOT, "%.2f blocks/px", map.transform().blocksPerPixel()) + "   Mode: Strategic Grid"; graphics.text(font, Component.literal(status), 110, 8, 0xffc5d5dc); graphics.fill(0, height - bottom, width, height, 0xff11191d); graphics.text(font, Component.literal("Drag: Pan   Wheel: Zoom   Click: Select   F: Follow   Home: Fit All   R: Centre Player   Esc: Close"), 8, height - 15, 0xffa9bdc5); ClientRadarTrack hovered = nearest(mouseX, mouseY, 12); if (hovered != null) RadarTooltip.render(graphics, font, hovered, mouseX, mouseY); }
	@Override protected void init() { if (!initializedMap) { centerPlayer(); initializedMap = true; fitAll(); } }
	@Override public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) { int mapWidth = (int)(width * .74); if (map.contains(event.x(), event.y(), 0, 24, mapWidth, height - 46) && event.button() == 0) { ClientRadarTrack hit = nearest(event.x(), event.y(), 12); if (hit != null) { state.select(hit.id()); return true; } map.beginDrag(); return true; } return super.mouseClicked(event, doubleClick); }
	@Override public boolean mouseReleased(final MouseButtonEvent event) { map.endDrag(); return super.mouseReleased(event); }
	@Override public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) { if (map.dragging()) { map.transform().panPixels(dx, dy); state.disableFollow(); return true; } return super.mouseDragged(event, dx, dy); }
	@Override public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) { int mapWidth = (int)(width * .74); if (map.contains(x, y, 0, 24, mapWidth, height - 46)) { map.transform().zoomAt(scrollY, x, y, 0, 24, mapWidth, height - 46); state.disableFollow(); return true; } sidebarScroll = Math.max(0, sidebarScroll - (int)(scrollY * 18)); return true; }
	@Override public boolean keyPressed(final KeyEvent event) { if (event.key() == 70) { state.toggleFollow(); return true; } if (event.key() == 268) { fitAll(); return true; } if (event.key() == 82) { centerPlayer(); return true; } return super.keyPressed(event); }
	@Override public void removed() { if (!closing) { closing = true; ClientRadarNetworking.close(); } }
	private void centerPlayer() { if (minecraft.player != null) { Vec3 position = minecraft.player.position(); map.transform().center(position.x, position.z); } }
	private void fitAll() { int mapWidth = Math.max(1, (int)(width * .74)), mapHeight = Math.max(1, height - 46); List<Vec3> points = new ArrayList<>(); if (minecraft.player != null) points.add(minecraft.player.position()); for (ClientRadarTrack track : state.tracks()) { points.add(track.launch()); points.add(track.target()); points.add(track.position(state.clock().now(0))); } for (ClientRadarImpact impact : state.impacts()) points.add(impact.snapshot().impactPosition()); if (points.isEmpty()) return; double minimumX = points.stream().mapToDouble(point -> point.x).min().orElse(0.0), maximumX = points.stream().mapToDouble(point -> point.x).max().orElse(0.0), minimumZ = points.stream().mapToDouble(point -> point.z).min().orElse(0.0), maximumZ = points.stream().mapToDouble(point -> point.z).max().orElse(0.0); map.transform().fit(minimumX, minimumZ, maximumX, maximumZ, mapWidth, mapHeight); }
	private ClientRadarTrack nearest(final double x, final double y, final double radius) { int mapWidth = (int)(width * .74), mapHeight = height - 46; ClientRadarTrack best = null; double bestDistance = radius * radius, now = state.clock().now(0); for (ClientRadarTrack track : state.tracks()) { Vec3 position = track.position(now); double dx = map.transform().screenX(position.x, 0, mapWidth) - x, dy = map.transform().screenY(position.z, 24, mapHeight) - y, distance = dx * dx + dy * dy; if (distance < bestDistance) { bestDistance = distance; best = track; } } return best; }
}