package com.andye.warmod.radar.client.gui;

import com.andye.warmod.radar.client.ClientRadarImpact;
import com.andye.warmod.radar.client.ClientRadarNetworking;
import com.andye.warmod.radar.client.ClientRadarState;
import com.andye.warmod.radar.client.ClientRadarTrack;
import com.andye.warmod.radar.station.RadarRedstoneMode;
import com.andye.warmod.radar.station.client.ClientRadarStationNetworking;
import com.andye.warmod.radar.station.client.ClientRadarStationState;
import com.andye.warmod.radar.station.client.RadarSweepRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class RadarScreen extends Screen {
    private final RadarMapWidget map = new RadarMapWidget();
    private final ClientRadarState state = ClientRadarState.INSTANCE;
    private final ClientRadarStationState station = ClientRadarStationState.INSTANCE;
    private final RadarScreenMode mode;
    private int sidebarScroll;
    private boolean initializedMap;
    private boolean closing;
    private boolean redstoneModeDirty;
    private EditBox radiusField;
    private EditBox fireRadiusField;
    private Button redstoneModeButton;
    private RadarRedstoneMode pendingRedstoneMode = RadarRedstoneMode.ANALOG_DISTANCE;

    public RadarScreen() { this(RadarScreenMode.GLOBAL); }
    public RadarScreen(RadarScreenMode mode) {
        super(Component.literal(mode == RadarScreenMode.STATION ? "Radar Station" : "Missile Radar"));
        this.mode = mode;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }

    @Override
    public void tick() {
        double now = state.clock().now(0);
        if (mode == RadarScreenMode.STATION) {
            station.prune(minecraft.level == null ? 0.0 : minecraft.level.getGameTime());
            if (!redstoneModeDirty && redstoneModeButton != null && pendingRedstoneMode != station.redstoneMode()) {
                pendingRedstoneMode = station.redstoneMode();
                redstoneModeButton.setMessage(redstoneModeButtonText());
            }
            return;
        }
        state.pruneImpacts(now);
        ClientRadarTrack selected = state.selected();
        if (state.followSelectedTrack() && selected != null) {
            Vec3 position = selected.position(now);
            map.transform().center(position.x, position.z);
        }
    }

    @Override
    protected void init() {
        if (!initializedMap) {
            if (mode == RadarScreenMode.STATION && station.centre() != null)
                map.transform().center(station.centre().getX() + .5, station.centre().getZ() + .5);
            else {
                centerPlayer();
                fitAll();
            }
            initializedMap = true;
        }
        if (mode != RadarScreenMode.STATION) return;
        int left = mapWidth() + 12;
        int controlWidth = Math.max(100, sidebarWidth() - 24);
        radiusField = new EditBox(font, left, 134, controlWidth, 18, Component.literal("Warning radius"));
        radiusField.setValue(Integer.toString((int)station.warningRadius()));
        radiusField.setMaxLength(4);
        addRenderableWidget(radiusField);
        addRenderableWidget(Button.builder(Component.literal("-16"), button -> adjust(radiusField, -16))
            .bounds(left, 156, (controlWidth - 6) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+16"), button -> adjust(radiusField, 16))
            .bounds(left + (controlWidth + 6) / 2, 156, (controlWidth - 6) / 2, 20).build());
        fireRadiusField = new EditBox(font, left, 196, controlWidth, 18, Component.literal("Fire radius"));
        fireRadiusField.setValue(Integer.toString((int)station.fireRadius()));
        fireRadiusField.setMaxLength(4);
        addRenderableWidget(fireRadiusField);
        addRenderableWidget(Button.builder(Component.literal("-16"), button -> adjust(fireRadiusField, -16))
            .bounds(left, 218, (controlWidth - 6) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+16"), button -> adjust(fireRadiusField, 16))
            .bounds(left + (controlWidth + 6) / 2, 218, (controlWidth - 6) / 2, 20).build());
        pendingRedstoneMode = station.redstoneMode();
        redstoneModeDirty = false;
        redstoneModeButton = addRenderableWidget(Button.builder(redstoneModeButtonText(), button -> toggleRedstoneMode())
            .bounds(left, 264, controlWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply settings"), button -> applySettings())
            .bounds(left, 290, controlWidth, 20).build());
    }

    private void adjust(EditBox field, int delta) {
        try { field.setValue(Integer.toString(Integer.parseInt(field.getValue()) + delta)); }
        catch (NumberFormatException ignored) { }
    }

    private void toggleRedstoneMode() {
        pendingRedstoneMode = pendingRedstoneMode == RadarRedstoneMode.ANALOG_DISTANCE
            ? RadarRedstoneMode.INTERCEPT_TRIGGER_ONLY : RadarRedstoneMode.ANALOG_DISTANCE;
        redstoneModeDirty = true;
        redstoneModeButton.setMessage(redstoneModeButtonText());
    }

    private Component redstoneModeButtonText() {
        return Component.literal("Only output redstone inside fire radius: "
            + (pendingRedstoneMode == RadarRedstoneMode.INTERCEPT_TRIGGER_ONLY ? "ON" : "OFF"));
    }

    private void applySettings() {
        try {
            ClientRadarStationNetworking.configure(Double.parseDouble(radiusField.getValue()),
                Double.parseDouble(fireRadiusField.getValue()), pendingRedstoneMode);
            redstoneModeDirty = false;
        } catch (NumberFormatException ignored) { }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        int top = 24;
        int bottom = 22;
        int mapWidth = mapWidth();
        int mapHeight = Math.max(1, height - top - bottom);
        int side = mapWidth;
        graphics.fill(0, 0, width, height, 0xff070b0d);
        if (mode == RadarScreenMode.STATION) {
            double now = minecraft.level == null ? 0.0 : minecraft.level.getGameTime() + partial;
            RadarMapRenderer.renderGrid(graphics, map.transform(), 0, top, mapWidth, mapHeight);
            graphics.enableScissor(0, top, mapWidth, top + mapHeight);
            RadarSweepRenderer.render(graphics, station, map.transform(), now, 0, top, mapWidth, mapHeight);
            graphics.disableScissor();
            graphics.fill(side, top, width, top + mapHeight, 0xff0d1519);
            int x = side + 12;
            graphics.text(font, Component.literal("RADAR STATION"), x, top + 10, 0xffffc45a);
            graphics.text(font, Component.literal(station.radarId() == null ? "offline" : station.radarId().toString().substring(0, 8)),
                x, top + 25, 0xffc5d5dc);
            graphics.text(font, Component.literal("DETECTION RANGE"), x, top + 46, 0xff7f969d);
            graphics.text(font, Component.literal((int)station.detectionRange() + " blocks"), x, top + 59, 0xffffffff);
            graphics.text(font, Component.literal("WARNING RADIUS"), x, top + 78, 0xff7f969d);
            graphics.text(font, Component.literal((int)station.warningRadius() + " blocks"), x, top + 91, 0xffffffff);
            graphics.text(font, Component.literal("FIRE RADIUS"), x, top + 140, 0xff7f969d);
            graphics.text(font, Component.literal((int)station.fireRadius() + " blocks"), x, top + 153, 0xffd7f7ff);
            graphics.text(font, Component.literal("REDSTONE MODE"), x, top + 224, 0xff7f969d);
            graphics.text(font, Component.literal(station.redstoneMode() == RadarRedstoneMode.ANALOG_DISTANCE
                ? "Analogue distance" : "Interceptor trigger only"), x, top + 237, 0xffffffff);
            graphics.text(font, Component.literal("Analogue mode: Outputs 1-15 as threats approach"), x, top + 292, 0xffa9bdc5);
            graphics.text(font, Component.literal("Trigger mode: Outputs only 0 or 15"), x, top + 305, 0xffa9bdc5);
            int signalColour = station.redstoneSignal() == 15 ? 0xff50e7ff
                : station.redstoneSignal() > 0 ? 0xffffc45a : 0xff8fd7a6;
            graphics.text(font, Component.literal("REDSTONE SIGNAL  " + station.redstoneSignal() + " / 15"), x,
                top + 330, signalColour);
            graphics.text(font, Component.literal("PRIMARY THREAT  " + (station.primaryThreatId() == null ? "CLEAR"
                : station.primaryThreatId().toString().substring(0, 8))), x, top + 344,
                station.primaryThreatId() == null ? 0xff8fd7a6 : 0xffff8b62);
            graphics.text(font, Component.literal("PRIMARY DISTANCE  "
                + (Double.isFinite(station.primaryThreatDistance()) ? (int)station.primaryThreatDistance() + " blocks" : "--")),
                x, top + 358, 0xffc5d5dc);
            graphics.text(font, Component.literal("SWEEP  " + String.format(Locale.ROOT, "%.1f s", station.sweepPeriod() / 20.0)
                + "   CONTACTS " + station.contacts() + "   THREATS " + station.threats()), x, top + 372, 0xffc5d5dc);
        } else {
            double now = state.clock().now(partial);
            RadarMapRenderer.render(graphics, state, map.transform(), now, 0, top, mapWidth, mapHeight);
            RadarSidebar.render(graphics, font, state, now, side, top, width - side, mapHeight, sidebarScroll);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        graphics.fill(0, 0, width, top, 0xff11191d);
        graphics.text(font, Component.literal(mode == RadarScreenMode.STATION ? "RADAR STATION SWEEP" : "MISSILE RADAR"), 8, 8,
            0xffffc45a);
        String status = (mode == RadarScreenMode.STATION ? (station.dimension() == null ? "unknown" : station.dimension().toString())
            : state.dimensionId() == null ? "unknown" : state.dimensionId().toString()) + "   Scale: "
            + String.format(Locale.ROOT, "%.2f blocks/px", map.transform().blocksPerPixel()) + "   Mode: "
            + (mode == RadarScreenMode.STATION ? "Station Sweep" : "Strategic Grid");
        graphics.text(font, Component.literal(status), 150, 8, 0xffc5d5dc);
        graphics.fill(0, height - bottom, width, height, 0xff11191d);
        graphics.text(font, Component.literal("Drag: Pan   Wheel: Zoom   "
            + (mode == RadarScreenMode.GLOBAL ? "Click: Select   F: Follow   Home: Fit All   R: Centre Player   " : "")
            + "Esc: Close"), 8, height - 15, 0xffa9bdc5);
        if (mode == RadarScreenMode.GLOBAL) {
            ClientRadarTrack hovered = nearest(mouseX, mouseY, 12);
            if (hovered != null) RadarTooltip.render(graphics, font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mapWidth = mapWidth();
        if (map.contains(event.x(), event.y(), 0, 24, mapWidth, height - 46) && event.button() == 0) {
            if (mode == RadarScreenMode.GLOBAL) {
                ClientRadarTrack hit = nearest(event.x(), event.y(), 12);
                if (hit != null) { state.select(hit.id()); return true; }
            }
            map.beginDrag();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        map.endDrag();
        return super.mouseReleased(event);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (map.dragging()) { map.transform().panPixels(deltaX, deltaY); state.disableFollow(); return true; }
        return super.mouseDragged(event, deltaX, deltaY);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int mapWidth = mapWidth();
        if (map.contains(mouseX, mouseY, 0, 24, mapWidth, height - 46)) {
            map.transform().zoomAt(verticalAmount, mouseX, mouseY, 0, 24, mapWidth, height - 46);
            state.disableFollow();
            return true;
        }
        sidebarScroll = Math.max(0, sidebarScroll - (int)(verticalAmount * 18));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (mode == RadarScreenMode.GLOBAL) {
            if (event.key() == 70) { state.toggleFollow(); return true; }
            if (event.key() == 268) { fitAll(); return true; }
            if (event.key() == 82) { centerPlayer(); return true; }
        }
        return super.keyPressed(event);
    }
    @Override public void removed() {
        if (!closing) {
            closing = true;
            if (mode == RadarScreenMode.STATION) ClientRadarStationNetworking.close();
            else ClientRadarNetworking.close();
        }
    }

    private int sidebarWidth() { return Math.min(300, Math.max(220, (int)Math.round(width * .28))); }
    private int mapWidth() { return Math.max(1, width - sidebarWidth()); }
    private void centerPlayer() {
        if (minecraft.player != null) {
            Vec3 position = minecraft.player.position();
            map.transform().center(position.x, position.z);
        }
    }
    private void fitAll() {
        if (mode != RadarScreenMode.GLOBAL) return;
        int mapWidth = mapWidth();
        int mapHeight = Math.max(1, height - 46);
        List<Vec3> points = new ArrayList<>();
        if (minecraft.player != null) points.add(minecraft.player.position());
        for (ClientRadarTrack track : state.tracks()) {
            points.add(track.launch()); points.add(track.target()); points.add(track.position(state.clock().now(0)));
        }
        for (ClientRadarImpact impact : state.impacts()) points.add(impact.snapshot().impactPosition());
        if (points.isEmpty()) return;
        map.transform().fit(points.stream().mapToDouble(point -> point.x).min().orElse(0.0),
            points.stream().mapToDouble(point -> point.z).min().orElse(0.0),
            points.stream().mapToDouble(point -> point.x).max().orElse(0.0),
            points.stream().mapToDouble(point -> point.z).max().orElse(0.0), mapWidth, mapHeight);
    }
    private ClientRadarTrack nearest(double mouseX, double mouseY, double radius) {
        if (mode != RadarScreenMode.GLOBAL) return null;
        int mapWidth = mapWidth();
        int mapHeight = height - 46;
        ClientRadarTrack best = null;
        double bestDistance = radius * radius;
        double now = state.clock().now(0);
        for (ClientRadarTrack track : state.tracks()) {
            Vec3 position = track.position(now);
            double dx = map.transform().screenX(position.x, 0, mapWidth) - mouseX;
            double dy = map.transform().screenY(position.z, 24, mapHeight) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) { bestDistance = distance; best = track; }
        }
        return best;
    }
}