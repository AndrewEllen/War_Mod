package com.andye.warmod.radar.client.gui;

import com.andye.warmod.client.gui.WarModUiText;
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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class RadarScreen extends Screen {
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 22;
    private static final int SIDEBAR_PADDING = 10;
    private static final int STATION_CONTENT_HEIGHT = 366;

    private final RadarMapWidget map = new RadarMapWidget();
    private final ClientRadarState state = ClientRadarState.INSTANCE;
    private final ClientRadarStationState station =
        ClientRadarStationState.INSTANCE;
    private final RadarScreenMode mode;

    private int globalSidebarScroll;
    private int stationSidebarScroll;
    private boolean initializedMap;
    private boolean closing;
    private boolean redstoneModeDirty;

    private EditBox radiusField;
    private EditBox fireRadiusField;
    private Button warningMinusButton;
    private Button warningPlusButton;
    private Button fireMinusButton;
    private Button firePlusButton;
    private Button redstoneModeButton;
    private Button applySettingsButton;

    private RadarRedstoneMode pendingRedstoneMode =
        RadarRedstoneMode.ANALOG_DISTANCE;

    private record StationSidebarLayout(
        int left,
        int top,
        int width,
        int height,
        int contentX,
        int contentWidth,
        int scroll
    ) {
        int y(final int contentY) {
            return top + contentY - scroll;
        }

        int bottom() {
            return top + height;
        }

        boolean fullyContains(
            final int widgetY,
            final int widgetHeight
        ) {
            return widgetY >= top
                && widgetY + widgetHeight <= bottom();
        }
    }

    public RadarScreen() {
        this(RadarScreenMode.GLOBAL);
    }

    public RadarScreen(final RadarScreenMode mode) {
        super(Component.literal(
            mode == RadarScreenMode.STATION
                ? "Radar Station"
                : "Missile Radar"
        ));
        this.mode = mode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void tick() {
        double now = state.clock().now(0.0F);

        if (mode == RadarScreenMode.STATION) {
            station.prune(
                minecraft.level == null
                    ? 0.0
                    : minecraft.level.getGameTime()
            );

            if (!redstoneModeDirty
                && redstoneModeButton != null
                && pendingRedstoneMode != station.redstoneMode()) {
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
        super.init();

        if (!initializedMap) {
            if (mode == RadarScreenMode.STATION
                && station.centre() != null) {
                map.transform().center(
                    station.centre().getX() + 0.5,
                    station.centre().getZ() + 0.5
                );
            } else {
                centerPlayer();
                fitAll();
            }

            initializedMap = true;
        }

        if (mode != RadarScreenMode.STATION) {
            return;
        }

        StationSidebarLayout layout = stationLayout();
        int controlWidth = layout.contentWidth();
        int halfWidth = (controlWidth - 6) / 2;

        radiusField = addRenderableWidget(new EditBox(
            font,
            layout.contentX(),
            layout.y(88),
            controlWidth,
            18,
            Component.literal("Warning radius")
        ));
        radiusField.setValue(Integer.toString((int)station.warningRadius()));
        radiusField.setMaxLength(4);

        warningMinusButton = addRenderableWidget(Button.builder(
            Component.literal("-16"),
            button -> adjust(radiusField, -16)
        ).bounds(
            layout.contentX(),
            layout.y(110),
            halfWidth,
            20
        ).build());

        warningPlusButton = addRenderableWidget(Button.builder(
            Component.literal("+16"),
            button -> adjust(radiusField, 16)
        ).bounds(
            layout.contentX() + halfWidth + 6,
            layout.y(110),
            halfWidth,
            20
        ).build());

        fireRadiusField = addRenderableWidget(new EditBox(
            font,
            layout.contentX(),
            layout.y(154),
            controlWidth,
            18,
            Component.literal("Fire radius")
        ));
        fireRadiusField.setValue(Integer.toString((int)station.fireRadius()));
        fireRadiusField.setMaxLength(4);

        fireMinusButton = addRenderableWidget(Button.builder(
            Component.literal("-16"),
            button -> adjust(fireRadiusField, -16)
        ).bounds(
            layout.contentX(),
            layout.y(176),
            halfWidth,
            20
        ).build());

        firePlusButton = addRenderableWidget(Button.builder(
            Component.literal("+16"),
            button -> adjust(fireRadiusField, 16)
        ).bounds(
            layout.contentX() + halfWidth + 6,
            layout.y(176),
            halfWidth,
            20
        ).build());

        pendingRedstoneMode = station.redstoneMode();
        redstoneModeDirty = false;

        redstoneModeButton = addRenderableWidget(Button.builder(
            redstoneModeButtonText(),
            button -> toggleRedstoneMode()
        ).bounds(
            layout.contentX(),
            layout.y(220),
            controlWidth,
            20
        ).build());

        applySettingsButton = addRenderableWidget(Button.builder(
            Component.literal("Apply settings"),
            button -> applySettings()
        ).bounds(
            layout.contentX(),
            layout.y(246),
            controlWidth,
            20
        ).build());

        positionStationWidgets();
    }

    private StationSidebarLayout stationLayout() {
        int mapWidth = mapWidth();
        int panelHeight = Math.max(
            1,
            height - HEADER_HEIGHT - FOOTER_HEIGHT
        );
        int panelWidth = Math.max(1, width - mapWidth);

        stationSidebarScroll = Math.max(
            0,
            Math.min(
                Math.max(0, STATION_CONTENT_HEIGHT - panelHeight),
                stationSidebarScroll
            )
        );

        return new StationSidebarLayout(
            mapWidth,
            HEADER_HEIGHT,
            panelWidth,
            panelHeight,
            mapWidth + SIDEBAR_PADDING,
            Math.max(80, panelWidth - SIDEBAR_PADDING * 2),
            stationSidebarScroll
        );
    }

    private void positionStationWidgets() {
        if (mode != RadarScreenMode.STATION || radiusField == null) {
            return;
        }

        StationSidebarLayout layout = stationLayout();
        positionStationWidget(radiusField, layout.y(88), 18, layout);
        positionStationWidget(warningMinusButton, layout.y(110), 20, layout);
        positionStationWidget(warningPlusButton, layout.y(110), 20, layout);
        positionStationWidget(fireRadiusField, layout.y(154), 18, layout);
        positionStationWidget(fireMinusButton, layout.y(176), 20, layout);
        positionStationWidget(firePlusButton, layout.y(176), 20, layout);
        positionStationWidget(redstoneModeButton, layout.y(220), 20, layout);
        positionStationWidget(applySettingsButton, layout.y(246), 20, layout);
    }

    private static void positionStationWidget(
        final AbstractWidget widget,
        final int y,
        final int widgetHeight,
        final StationSidebarLayout layout
    ) {
        widget.setY(y);
        widget.visible = layout.fullyContains(y, widgetHeight);
        widget.active = widget.visible;
    }

    private void adjust(final EditBox field, final int delta) {
        try {
            field.setValue(Integer.toString(
                Integer.parseInt(field.getValue()) + delta
            ));
        } catch (NumberFormatException ignored) {
        }
    }

    private void toggleRedstoneMode() {
        pendingRedstoneMode = pendingRedstoneMode
                == RadarRedstoneMode.ANALOG_DISTANCE
            ? RadarRedstoneMode.INTERCEPT_TRIGGER_ONLY
            : RadarRedstoneMode.ANALOG_DISTANCE;
        redstoneModeDirty = true;
        redstoneModeButton.setMessage(redstoneModeButtonText());
    }

    private Component redstoneModeButtonText() {
        return Component.literal(
            pendingRedstoneMode == RadarRedstoneMode.ANALOG_DISTANCE
                ? "Output: Distance strength"
                : "Output: Fire-radius trigger"
        );
    }

    private void applySettings() {
        try {
            ClientRadarStationNetworking.configure(
                Double.parseDouble(radiusField.getValue()),
                Double.parseDouble(fireRadiusField.getValue()),
                pendingRedstoneMode
            );
            redstoneModeDirty = false;
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void extractRenderState(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partial
    ) {
        int mapWidth = mapWidth();
        int mapHeight = Math.max(
            1,
            height - HEADER_HEIGHT - FOOTER_HEIGHT
        );

        graphics.fill(0, 0, width, height, 0xFF070B0D);

        if (mode == RadarScreenMode.STATION) {
            renderStation(graphics, mapWidth, mapHeight, partial);
        } else {
            double now = state.clock().now(partial);
            RadarMapRenderer.render(
                graphics,
                state,
                map.transform(),
                now,
                0,
                HEADER_HEIGHT,
                mapWidth,
                mapHeight
            );
            RadarSidebar.render(
                graphics,
                font,
                state,
                now,
                mapWidth,
                HEADER_HEIGHT,
                width - mapWidth,
                mapHeight,
                globalSidebarScroll
            );
        }

        super.extractRenderState(graphics, mouseX, mouseY, partial);

        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xFF11191D);
        String heading = mode == RadarScreenMode.STATION
            ? "RADAR STATION SWEEP"
            : "MISSILE RADAR";
        graphics.text(
            font,
            Component.literal(heading),
            8,
            8,
            0xFFFFC45A
        );

        String status = (mode == RadarScreenMode.STATION
            ? station.dimension() == null
                ? "unknown"
                : station.dimension().toString()
            : state.dimensionId() == null
                ? "unknown"
                : state.dimensionId().toString())
            + "   Scale: "
            + String.format(
                Locale.ROOT,
                "%.2f blocks/px",
                map.transform().blocksPerPixel()
            )
            + "   Mode: "
            + (mode == RadarScreenMode.STATION
                ? "Station Sweep"
                : "Strategic Grid");

        int statusX = 8 + font.width(heading) + 18;
        graphics.text(
            font,
            Component.literal(WarModUiText.ellipsize(
                font,
                status,
                Math.max(0, width - statusX - 8)
            )),
            statusX,
            8,
            0xFFC5D5DC
        );

        graphics.fill(
            0,
            height - FOOTER_HEIGHT,
            width,
            height,
            0xFF11191D
        );
        String footer = mode == RadarScreenMode.GLOBAL
            ? "Drag: Pan  Wheel: Zoom  Click: Select  F: Follow  Home: Fit  R: Centre  Esc: Close"
            : "Drag: Pan  Wheel: Zoom  Sidebar wheel: Scroll  Esc: Close";
        graphics.text(
            font,
            Component.literal(WarModUiText.ellipsize(
                font,
                footer,
                Math.max(0, width - 16)
            )),
            8,
            height - 15,
            0xFFA9BDC5
        );

        if (mode == RadarScreenMode.GLOBAL) {
            ClientRadarTrack hovered = nearest(mouseX, mouseY, 12.0);

            if (hovered != null) {
                RadarTooltip.render(
                    graphics,
                    font,
                    hovered,
                    mouseX,
                    mouseY
                );
            }
        }
    }

    private void renderStation(
        final GuiGraphicsExtractor graphics,
        final int mapWidth,
        final int mapHeight,
        final float partial
    ) {
        double now = minecraft.level == null
            ? 0.0
            : minecraft.level.getGameTime() + partial;

        RadarMapRenderer.renderGrid(
            graphics,
            map.transform(),
            0,
            HEADER_HEIGHT,
            mapWidth,
            mapHeight
        );
        graphics.enableScissor(
            0,
            HEADER_HEIGHT,
            mapWidth,
            HEADER_HEIGHT + mapHeight
        );
        RadarSweepRenderer.render(
            graphics,
            station,
            map.transform(),
            now,
            0,
            HEADER_HEIGHT,
            mapWidth,
            mapHeight
        );
        graphics.disableScissor();

        StationSidebarLayout layout = stationLayout();
        graphics.fill(
            layout.left(),
            layout.top(),
            layout.left() + layout.width(),
            layout.bottom(),
            0xFF0D1519
        );
        graphics.enableScissor(
            layout.left(),
            layout.top(),
            layout.left() + layout.width(),
            layout.bottom()
        );

        int x = layout.contentX();
        int width = layout.contentWidth();

        stationText(graphics, "RADAR STATION", x, layout.y(8), 0xFFFFC45A, width);
        stationText(
            graphics,
            station.radarId() == null
                ? "Offline"
                : station.radarId().toString().substring(0, 8),
            x,
            layout.y(22),
            0xFFC5D5DC,
            width
        );
        stationText(
            graphics,
            "Range " + (int)station.detectionRange()
                + " | Sweep "
                + String.format(
                    Locale.ROOT,
                    "%.1fs",
                    station.sweepPeriod() / 20.0
                ),
            x,
            layout.y(42),
            0xFFFFFFFF,
            width
        );
        stationText(
            graphics,
            "Contacts " + station.contacts()
                + " | Threats " + station.threats(),
            x,
            layout.y(56),
            station.threats() > 0 ? 0xFFFF8B62 : 0xFF8FD7A6,
            width
        );

        stationText(graphics, "WARNING RADIUS", x, layout.y(76), 0xFF7F969D, width);
        stationText(
            graphics,
            "Current: " + (int)station.warningRadius() + " blocks",
            x,
            layout.y(132),
            0xFFC5D5DC,
            width
        );
        stationText(
            graphics,
            "Predicted impact must enter this radius",
            x,
            layout.y(142),
            0xFF7F969D,
            width
        );

        stationText(graphics, "FIRE RADIUS", x, layout.y(154), 0xFF7F969D, width);
        stationText(
            graphics,
            "Current: " + (int)station.fireRadius() + " blocks",
            x,
            layout.y(198),
            0xFFD7F7FF,
            width
        );
        stationText(
            graphics,
            "Trigger mode turns on inside this radius",
            x,
            layout.y(208),
            0xFF7F969D,
            width
        );

        stationText(graphics, "REDSTONE OUTPUT", x, layout.y(220), 0xFF7F969D, width);
        stationText(
            graphics,
            pendingRedstoneMode == RadarRedstoneMode.ANALOG_DISTANCE
                ? "Block sides: distance strength 1-15"
                : "Block sides: fire trigger 0/15",
            x,
            layout.y(274),
            0xFFC5D5DC,
            width
        );
        stationText(
            graphics,
            "Comparator: distance strength 0-15",
            x,
            layout.y(286),
            0xFF50E7FF,
            width
        );

        int colour = station.redstoneSignal() == 15
            ? 0xFF50E7FF
            : station.redstoneSignal() > 0
                ? 0xFFFFC45A
                : 0xFF8FD7A6;
        stationText(
            graphics,
            "Block signal: " + station.redstoneSignal() + " / 15",
            x,
            layout.y(300),
            colour,
            width
        );
        stationText(
            graphics,
            "Primary: " + (station.primaryThreatId() == null
                ? "CLEAR"
                : station.primaryThreatId().toString().substring(0, 8)),
            x,
            layout.y(320),
            station.primaryThreatId() == null
                ? 0xFF8FD7A6
                : 0xFFFF8B62,
            width
        );
        stationText(
            graphics,
            "Distance: " + (Double.isFinite(station.primaryThreatDistance())
                ? (int)station.primaryThreatDistance() + " blocks"
                : "--"),
            x,
            layout.y(334),
            0xFFC5D5DC,
            width
        );

        graphics.disableScissor();
    }

    private void stationText(
        final GuiGraphicsExtractor graphics,
        final String value,
        final int x,
        final int y,
        final int colour,
        final int maximumWidth
    ) {
        graphics.text(
            font,
            Component.literal(WarModUiText.ellipsize(
                font,
                value,
                maximumWidth
            )),
            x,
            y,
            colour
        );
    }

    @Override
    public boolean mouseClicked(
        final MouseButtonEvent event,
        final boolean doubleClick
    ) {
        int mapWidth = mapWidth();

        if (map.contains(
            event.x(),
            event.y(),
            0,
            HEADER_HEIGHT,
            mapWidth,
            Math.max(1, height - HEADER_HEIGHT - FOOTER_HEIGHT)
        ) && event.button() == 0) {
            if (mode == RadarScreenMode.GLOBAL) {
                ClientRadarTrack hit = nearest(event.x(), event.y(), 12.0);

                if (hit != null) {
                    state.select(hit.id());
                    return true;
                }
            }

            map.beginDrag();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(final MouseButtonEvent event) {
        map.endDrag();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(
        final MouseButtonEvent event,
        final double deltaX,
        final double deltaY
    ) {
        if (map.dragging()) {
            map.transform().panPixels(deltaX, deltaY);
            state.disableFollow();
            return true;
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(
        final double mouseX,
        final double mouseY,
        final double horizontalAmount,
        final double verticalAmount
    ) {
        int mapWidth = mapWidth();
        int mapHeight = Math.max(
            1,
            height - HEADER_HEIGHT - FOOTER_HEIGHT
        );

        if (map.contains(
            mouseX,
            mouseY,
            0,
            HEADER_HEIGHT,
            mapWidth,
            mapHeight
        )) {
            map.transform().zoomAt(
                verticalAmount,
                mouseX,
                mouseY,
                0,
                HEADER_HEIGHT,
                mapWidth,
                mapHeight
            );
            state.disableFollow();
            return true;
        }

        if (mode == RadarScreenMode.STATION) {
            StationSidebarLayout layout = stationLayout();
            stationSidebarScroll = Math.max(
                0,
                Math.min(
                    Math.max(0, STATION_CONTENT_HEIGHT - layout.height()),
                    stationSidebarScroll - (int)(verticalAmount * 18.0)
                )
            );
            positionStationWidgets();
            return true;
        }

        globalSidebarScroll = Math.max(
            0,
            Math.min(
                Math.max(0, RadarSidebar.contentHeight(state) - mapHeight),
                globalSidebarScroll - (int)(verticalAmount * 18.0)
            )
        );
        return true;
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        if (mode == RadarScreenMode.GLOBAL) {
            if (event.key() == 70) {
                state.toggleFollow();
                return true;
            }

            if (event.key() == 268) {
                fitAll();
                return true;
            }

            if (event.key() == 82) {
                centerPlayer();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (!closing) {
            closing = true;

            if (mode == RadarScreenMode.STATION) {
                ClientRadarStationNetworking.close();
            } else {
                ClientRadarNetworking.close();
            }
        }
    }

    private int sidebarWidth() {
        int preferred = (int)Math.round(width * 0.30);
        int maximum = Math.max(180, width - 120);
        return Math.min(
            maximum,
            Math.min(280, Math.max(196, preferred))
        );
    }

    private int mapWidth() {
        return Math.max(1, width - sidebarWidth());
    }

    private void centerPlayer() {
        if (minecraft.player != null) {
            Vec3 position = minecraft.player.position();
            map.transform().center(position.x, position.z);
        }
    }

    private void fitAll() {
        if (mode != RadarScreenMode.GLOBAL) {
            return;
        }

        int mapWidth = mapWidth();
        int mapHeight = Math.max(
            1,
            height - HEADER_HEIGHT - FOOTER_HEIGHT
        );
        List<Vec3> points = new ArrayList<>();

        if (minecraft.player != null) {
            points.add(minecraft.player.position());
        }

        for (ClientRadarTrack track : state.tracks()) {
            points.add(track.launch());
            points.add(track.target());
            points.add(track.position(state.clock().now(0.0F)));
        }

        for (ClientRadarImpact impact : state.impacts()) {
            points.add(impact.snapshot().impactPosition());
        }

        if (points.isEmpty()) {
            return;
        }

        map.transform().fit(
            points.stream().mapToDouble(point -> point.x).min().orElse(0.0),
            points.stream().mapToDouble(point -> point.z).min().orElse(0.0),
            points.stream().mapToDouble(point -> point.x).max().orElse(0.0),
            points.stream().mapToDouble(point -> point.z).max().orElse(0.0),
            mapWidth,
            mapHeight
        );
    }

    private ClientRadarTrack nearest(
        final double mouseX,
        final double mouseY,
        final double radius
    ) {
        if (mode != RadarScreenMode.GLOBAL) {
            return null;
        }

        int mapWidth = mapWidth();
        int mapHeight = Math.max(
            1,
            height - HEADER_HEIGHT - FOOTER_HEIGHT
        );
        ClientRadarTrack best = null;
        double bestDistance = radius * radius;
        double now = state.clock().now(0.0F);

        for (ClientRadarTrack track : state.tracks()) {
            Vec3 position = track.position(now);
            double deltaX = map.transform().screenX(
                position.x,
                0,
                mapWidth
            ) - mouseX;
            double deltaY = map.transform().screenY(
                position.z,
                HEADER_HEIGHT,
                mapHeight
            ) - mouseY;
            double distance = deltaX * deltaX + deltaY * deltaY;

            if (distance < bestDistance) {
                bestDistance = distance;
                best = track;
            }
        }

        return best;
    }
}
