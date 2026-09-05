package com.andye.warmod.radar.client.gui;

import com.andye.warmod.client.gui.WarModUiText;
import com.andye.warmod.radar.client.ClientRadarImpact;
import com.andye.warmod.radar.client.ClientRadarNetworking;
import com.andye.warmod.radar.client.ClientRadarState;
import com.andye.warmod.radar.client.ClientRadarTrack;
import com.andye.warmod.radar.station.RadarRedstoneMode;
import com.andye.warmod.radar.station.client.ClientRadarBlip;
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
    private static final double GLOBAL_MAXIMUM_VIEW_RADIUS = 25_000.0;

    private final RadarMapWidget map = new RadarMapWidget();
    private final ClientRadarState state = ClientRadarState.INSTANCE;
    private final ClientRadarStationState station = ClientRadarStationState.INSTANCE;
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
    private Button chunkLoadingButton;

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
        int y(final int contentY) { return top + contentY - scroll; }
        int bottom() { return top + height; }
        boolean fullyContains(final int widgetY, final int widgetHeight) {
            return widgetY >= top && widgetY + widgetHeight <= bottom();
        }
    }

    public RadarScreen() {
        this(RadarScreenMode.GLOBAL);
    }

    public RadarScreen(final RadarScreenMode mode) {
        super(Component.literal(switch (mode) {
            case STATION -> "Radar Station";
            case STATION_MAP -> "Remote Display - Linked Station";
            case GLOBAL -> "Remote Display";
        }));
        this.mode = mode;
    }

    public boolean stationBacked() {
        return mode == RadarScreenMode.STATION || mode == RadarScreenMode.STATION_MAP;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean isInGameUi() { return true; }

    @Override
    public void tick() {
        if (stationBacked()) {
            double now = minecraft.level == null
                ? 0.0
                : minecraft.level.getGameTime();
            station.prune(now);

            if (mode == RadarScreenMode.STATION) {
                if (!redstoneModeDirty
                    && redstoneModeButton != null
                    && pendingRedstoneMode != station.redstoneMode()) {
                    pendingRedstoneMode = station.redstoneMode();
                    redstoneModeButton.setMessage(redstoneModeButtonText());
                }
                if (chunkLoadingButton != null) {
                    chunkLoadingButton.setMessage(chunkLoadingButtonText());
                }
            } else if (station.followSelected() && station.selected() != null) {
                Vec3 position = station.selected().observation().observedPosition();
                map.transform().center(position.x, position.z);
                map.transform().constrain(mapWidth(), mapHeight());
            }
            return;
        }

        double now = state.clock().now(0.0F);
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
            if (stationBacked() && station.centre() != null) {
                double centreX = station.centre().getX() + 0.5;
                double centreZ = station.centre().getZ() + 0.5;
                map.transform().setBounds(
                    centreX,
                    centreZ,
                    station.detectionRange()
                );
                map.transform().fitBoundedArea(
                    centreX,
                    centreZ,
                    station.detectionRange(),
                    mapWidth(),
                    mapHeight()
                );
            } else {
                map.transform().clearBounds();
                map.transform().setMaximumVisibleRadius(GLOBAL_MAXIMUM_VIEW_RADIUS);
                centerPlayer();
                fitAll();
            }
            initializedMap = true;
        }

        if (mode != RadarScreenMode.STATION) return;

        StationSidebarLayout layout = stationLayout();
        int controlWidth = layout.contentWidth();
        int halfWidth = (controlWidth - 6) / 2;

        chunkLoadingButton = addRenderableWidget(Button.builder(
            chunkLoadingButtonText(),
            button -> toggleChunkLoading()
        ).bounds(
            layout.contentX() + Math.max(0, controlWidth - 94),
            layout.y(4),
            Math.min(94, controlWidth),
            18
        ).build());

        radiusField = addRenderableWidget(new EditBox(
            font, layout.contentX(), layout.y(88), controlWidth, 18,
            Component.literal("Warning radius")
        ));
        radiusField.setValue(Integer.toString((int)station.warningRadius()));
        radiusField.setMaxLength(4);

        warningMinusButton = addRenderableWidget(Button.builder(
            Component.literal("-16"),
            button -> adjust(radiusField, -16)
        ).bounds(layout.contentX(), layout.y(110), halfWidth, 20).build());
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
            font, layout.contentX(), layout.y(154), controlWidth, 18,
            Component.literal("Fire radius")
        ));
        fireRadiusField.setValue(Integer.toString((int)station.fireRadius()));
        fireRadiusField.setMaxLength(4);

        fireMinusButton = addRenderableWidget(Button.builder(
            Component.literal("-16"),
            button -> adjust(fireRadiusField, -16)
        ).bounds(layout.contentX(), layout.y(176), halfWidth, 20).build());
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
        ).bounds(layout.contentX(), layout.y(220), controlWidth, 20).build());
        applySettingsButton = addRenderableWidget(Button.builder(
            Component.literal("Apply settings"),
            button -> applySettings()
        ).bounds(layout.contentX(), layout.y(246), controlWidth, 20).build());

        positionStationWidgets();
    }

    private int mapHeight() {
        return Math.max(1, height - HEADER_HEIGHT - FOOTER_HEIGHT);
    }

    private StationSidebarLayout stationLayout() {
        int mapWidth = mapWidth();
        int panelHeight = mapHeight();
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
        if (mode != RadarScreenMode.STATION || radiusField == null) return;
        StationSidebarLayout layout = stationLayout();
        positionStationWidget(chunkLoadingButton, layout.y(4), 18, layout);
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
        pendingRedstoneMode = pendingRedstoneMode == RadarRedstoneMode.ANALOG_DISTANCE
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

    private Component chunkLoadingButtonText() {
        return Component.literal(
            station.dynamicChunkLoading() ? "Route load: ON" : "Route load: OFF"
        );
    }

    private void toggleChunkLoading() {
        ClientRadarStationNetworking.setDynamicChunkLoading(
            !station.dynamicChunkLoading()
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
        int mapHeight = mapHeight();
        graphics.fill(0, 0, width, height, WarModUiText.BACKGROUND);

        if (stationBacked()) {
            renderStationMap(graphics, mapWidth, mapHeight, partial);
            if (mode == RadarScreenMode.STATION) {
                renderStationSidebar(graphics);
            }
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
        renderChrome(graphics, mouseX, mouseY);
    }

    private void renderStationMap(
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
    }

    private void renderStationSidebar(final GuiGraphicsExtractor graphics) {
        StationSidebarLayout layout = stationLayout();
        graphics.fill(
            layout.left(),
            layout.top(),
            layout.left() + layout.width(),
            layout.bottom(),
            WarModUiText.BACKGROUND
        );
        graphics.enableScissor(
            layout.left(),
            layout.top(),
            layout.left() + layout.width(),
            layout.bottom()
        );
        int x = layout.contentX();
        int textWidth = layout.contentWidth();
        stationText(
            graphics,
            "RADAR STATION",
            x,
            layout.y(8),
            WarModUiText.WARNING,
            Math.max(20, textWidth - 100)
        );
        stationText(
            graphics,
            station.radarId() == null
                ? "Offline"
                : station.radarId().toString().substring(0, 8),
            x,
            layout.y(28),
            WarModUiText.TEXT,
            textWidth
        );
        stationText(
            graphics,
            "Range " + (int)station.detectionRange()
                + " | Sweep "
                + String.format(Locale.ROOT, "%.1fs", station.sweepPeriod() / 20.0),
            x,
            layout.y(42),
            WarModUiText.TEXT,
            textWidth
        );
        stationText(
            graphics,
            "Contacts " + station.contacts() + " | Threats " + station.threats(),
            x,
            layout.y(56),
            station.threats() > 0 ? WarModUiText.ERROR : WarModUiText.SUCCESS,
            textWidth
        );
        stationText(graphics, "WARNING RADIUS", x, layout.y(76), WarModUiText.TEXT_MUTED, textWidth);
        stationText(
            graphics,
            "Current: " + (int)station.warningRadius() + " blocks",
            x,
            layout.y(132),
            WarModUiText.TEXT,
            textWidth
        );
        stationText(
            graphics,
            "Predicted impact must enter this radius",
            x,
            layout.y(142),
            WarModUiText.TEXT_MUTED,
            textWidth
        );
        stationText(graphics, "FIRE RADIUS", x, layout.y(154), WarModUiText.TEXT_MUTED, textWidth);
        stationText(
            graphics,
            "Current: " + (int)station.fireRadius() + " blocks",
            x,
            layout.y(198),
            WarModUiText.TEXT,
            textWidth
        );
        stationText(
            graphics,
            "Trigger mode turns on inside this radius",
            x,
            layout.y(208),
            WarModUiText.TEXT_MUTED,
            textWidth
        );
        stationText(graphics, "REDSTONE OUTPUT", x, layout.y(220), WarModUiText.TEXT_MUTED, textWidth);
        stationText(
            graphics,
            pendingRedstoneMode == RadarRedstoneMode.ANALOG_DISTANCE
                ? "Block sides: distance strength 1-15"
                : "Block sides: fire trigger 0/15",
            x,
            layout.y(274),
            WarModUiText.TEXT,
            textWidth
        );
        stationText(
            graphics,
            "Comparator: distance strength 0-15",
            x,
            layout.y(286),
            WarModUiText.SUCCESS,
            textWidth
        );
        int colour = station.redstoneSignal() == 15
            ? WarModUiText.SUCCESS
            : station.redstoneSignal() > 0 ? WarModUiText.WARNING : WarModUiText.SUCCESS;
        stationText(
            graphics,
            "Block signal: " + station.redstoneSignal() + " / 15",
            x,
            layout.y(300),
            colour,
            textWidth
        );
        stationText(
            graphics,
            "Primary: " + (station.primaryThreatId() == null
                ? "CLEAR"
                : station.primaryThreatId().toString().substring(0, 8)),
            x,
            layout.y(320),
            station.primaryThreatId() == null ? WarModUiText.SUCCESS : WarModUiText.ERROR,
            textWidth
        );
        stationText(
            graphics,
            "Distance: " + (Double.isFinite(station.primaryThreatDistance())
                ? (int)station.primaryThreatDistance() + " blocks"
                : "--"),
            x,
            layout.y(334),
            WarModUiText.TEXT,
            textWidth
        );
        stationText(
            graphics,
            "Route chunk loading: "
                + (station.dynamicChunkLoading() ? "enabled" : "disabled"),
            x,
            layout.y(350),
            station.dynamicChunkLoading() ? WarModUiText.SUCCESS : WarModUiText.WARNING,
            textWidth
        );
        graphics.disableScissor();
    }

    private void renderChrome(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY
    ) {
        graphics.fill(0, 0, width, HEADER_HEIGHT, WarModUiText.BACKGROUND);
        String heading = switch (mode) {
            case STATION -> "RADAR STATION SWEEP";
            case STATION_MAP -> "LINKED RADAR STATION";
            case GLOBAL -> "REMOTE DISPLAY";
        };
        WarModUiText.text(graphics, font, Component.literal(heading), 8, 8, WarModUiText.WARNING);
        String dimension = stationBacked()
            ? station.dimension() == null ? "unknown" : station.dimension().toString()
            : state.dimensionId() == null ? "unknown" : state.dimensionId().toString();
        String modeName = switch (mode) {
            case STATION -> "Station Controls";
            case STATION_MAP -> "Station Sweep";
            case GLOBAL -> "Strategic Grid";
        };
        String status = dimension
            + "   Scale: "
            + String.format(Locale.ROOT, "%.2f blocks/px", map.transform().blocksPerPixel())
            + "   Mode: " + modeName;
        int statusX = 8 + font.width(heading) + 18;
        WarModUiText.text(graphics,
            font,
            Component.literal(WarModUiText.ellipsize(
                font,
                status,
                Math.max(0, width - statusX - 8)
            )),
            statusX,
            8,
            WarModUiText.TEXT
        );

        graphics.fill(0, height - FOOTER_HEIGHT, width, height, WarModUiText.BACKGROUND);
        String footer = switch (mode) {
            case GLOBAL -> "Drag: Pan  Wheel: Zoom  Click: Select  F: Follow  Home: Fit  R: Centre  Esc: Close";
            case STATION_MAP -> "Drag: Pan  Wheel: Zoom  Click: Select  F: Follow  Home: Full range  R: Station  Esc: Close";
            case STATION -> "Drag: Pan  Wheel: Zoom  Sidebar wheel: Scroll  Esc: Close";
        };
        WarModUiText.text(graphics,
            font,
            Component.literal(WarModUiText.ellipsize(font, footer, Math.max(0, width - 16))),
            8,
            height - 15,
            WarModUiText.TEXT_MUTED
        );

        if (mode == RadarScreenMode.GLOBAL) {
            ClientRadarTrack hovered = nearestGlobal(mouseX, mouseY, 12.0);
            if (hovered != null) RadarTooltip.render(graphics, font, hovered, mouseX, mouseY);
        } else if (mode == RadarScreenMode.STATION_MAP) {
            ClientRadarBlip hovered = nearestStation(mouseX, mouseY, 12.0);
            if (hovered != null) {
                RadarTooltip.render(graphics, font, hovered.track(), mouseX, mouseY);
            }
        }
    }

    private void stationText(
        final GuiGraphicsExtractor graphics,
        final String value,
        final int x,
        final int y,
        final int colour,
        final int maximumWidth
    ) {
        WarModUiText.text(graphics,
            font,
            Component.literal(WarModUiText.ellipsize(font, value, maximumWidth)),
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
            mapHeight()
        ) && event.button() == 0) {
            if (mode == RadarScreenMode.GLOBAL) {
                ClientRadarTrack hit = nearestGlobal(event.x(), event.y(), 12.0);
                if (hit != null) {
                    state.select(hit.id());
                    return true;
                }
            } else if (mode == RadarScreenMode.STATION_MAP) {
                ClientRadarBlip hit = nearestStation(event.x(), event.y(), 12.0);
                if (hit != null) {
                    station.select(hit.observation().trackId());
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
            map.transform().constrain(mapWidth(), mapHeight());
            if (mode == RadarScreenMode.GLOBAL) state.disableFollow();
            if (mode == RadarScreenMode.STATION_MAP) station.disableFollow();
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
        int mapHeight = mapHeight();
        if (map.contains(mouseX, mouseY, 0, HEADER_HEIGHT, mapWidth, mapHeight)) {
            map.transform().zoomAt(
                verticalAmount,
                mouseX,
                mouseY,
                0,
                HEADER_HEIGHT,
                mapWidth,
                mapHeight
            );
            if (mode == RadarScreenMode.GLOBAL) state.disableFollow();
            if (mode == RadarScreenMode.STATION_MAP) station.disableFollow();
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

        if (mode == RadarScreenMode.GLOBAL) {
            globalSidebarScroll = Math.max(
                0,
                Math.min(
                    Math.max(0, RadarSidebar.contentHeight(state) - mapHeight),
                    globalSidebarScroll - (int)(verticalAmount * 18.0)
                )
            );
            return true;
        }
        return super.mouseScrolled(
            mouseX,
            mouseY,
            horizontalAmount,
            verticalAmount
        );
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
        } else if (mode == RadarScreenMode.STATION_MAP) {
            if (event.key() == 70) {
                station.toggleFollow();
                return true;
            }
            if (event.key() == 268) {
                fitStationRange();
                return true;
            }
            if (event.key() == 82) {
                centerStation();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (!closing) {
            closing = true;
            if (stationBacked()) {
                ClientRadarStationNetworking.close();
            } else {
                ClientRadarNetworking.close();
            }
        }
    }

    private int sidebarWidth() {
        int preferred = (int)Math.round(width * 0.30);
        int maximum = Math.max(180, width - 120);
        return Math.min(maximum, Math.min(280, Math.max(196, preferred)));
    }

    private int mapWidth() {
        return mode == RadarScreenMode.STATION_MAP
            ? Math.max(1, width)
            : Math.max(1, width - sidebarWidth());
    }

    private void centerPlayer() {
        if (minecraft.player != null) {
            Vec3 position = minecraft.player.position();
            map.transform().center(position.x, position.z);
        }
    }

    private void centerStation() {
        if (station.centre() == null) return;
        map.transform().center(
            station.centre().getX() + 0.5,
            station.centre().getZ() + 0.5
        );
        map.transform().constrain(mapWidth(), mapHeight());
    }

    private void fitStationRange() {
        if (station.centre() == null) return;
        map.transform().fitBoundedArea(
            station.centre().getX() + 0.5,
            station.centre().getZ() + 0.5,
            station.detectionRange(),
            mapWidth(),
            mapHeight()
        );
    }

    private void fitAll() {
        if (mode != RadarScreenMode.GLOBAL) return;
        int mapWidth = mapWidth();
        int mapHeight = mapHeight();
        List<Vec3> points = new ArrayList<>();
        if (minecraft.player != null) points.add(minecraft.player.position());
        double now = state.clock().now(0.0F);
        for (ClientRadarTrack track : state.tracks()) {
            points.add(track.launch());
            points.add(track.target());
            points.add(track.position(now));
        }
        for (ClientRadarImpact impact : state.impacts()) {
            points.add(impact.snapshot().impactPosition());
        }
        if (points.isEmpty()) return;
        map.transform().fit(
            points.stream().mapToDouble(point -> point.x).min().orElse(0.0),
            points.stream().mapToDouble(point -> point.z).min().orElse(0.0),
            points.stream().mapToDouble(point -> point.x).max().orElse(0.0),
            points.stream().mapToDouble(point -> point.z).max().orElse(0.0),
            mapWidth,
            mapHeight
        );
    }

    private ClientRadarTrack nearestGlobal(
        final double mouseX,
        final double mouseY,
        final double radius
    ) {
        if (mode != RadarScreenMode.GLOBAL) return null;
        ClientRadarTrack best = null;
        double bestDistance = radius * radius;
        double now = state.clock().now(0.0F);
        for (ClientRadarTrack track : state.tracks()) {
            Vec3 position = track.position(now);
            double dx = map.transform().screenX(position.x, 0, mapWidth()) - mouseX;
            double dy = map.transform().screenY(
                position.z,
                HEADER_HEIGHT,
                mapHeight()
            ) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = track;
            }
        }
        return best;
    }

    private ClientRadarBlip nearestStation(
        final double mouseX,
        final double mouseY,
        final double radius
    ) {
        if (mode != RadarScreenMode.STATION_MAP) return null;
        ClientRadarBlip best = null;
        double bestDistance = radius * radius;
        for (ClientRadarBlip blip : station.blips()) {
            Vec3 position = blip.observation().observedPosition();
            double dx = map.transform().screenX(position.x, 0, mapWidth()) - mouseX;
            double dy = map.transform().screenY(
                position.z,
                HEADER_HEIGHT,
                mapHeight()
            ) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = blip;
            }
        }
        return best;
    }
}
