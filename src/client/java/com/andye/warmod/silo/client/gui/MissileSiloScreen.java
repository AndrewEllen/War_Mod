package com.andye.warmod.silo.client.gui;

import com.andye.warmod.antiair.AntiAirConstants;
import com.andye.warmod.antiair.AntiAirGuidanceResolver;
import com.andye.warmod.block.MissileSiloGuidanceFrameStructure;
import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.client.gui.WarModUiText;
import com.andye.warmod.menu.MissileSiloMenu;
import com.andye.warmod.silo.MissilePayloadItems;
import com.andye.warmod.silo.network.ServerboundSiloClearTargetPayload;
import com.andye.warmod.silo.network.ServerboundSiloLaunchPayload;
import com.andye.warmod.silo.network.ServerboundSiloSetTargetPayload;
import com.andye.warmod.silo.network.ServerboundSiloUseHeldDesignatorPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class MissileSiloScreen extends AbstractContainerScreen<MissileSiloMenu> {
    private static final int SCREEN_WIDTH = 316,
            SCREEN_HEIGHT = 236,
            TARGET_X = 202,
            TARGET_WIDTH = 102,
            TARGET_FIELD_Y = 44,
            APPLY_Y = 68,
            SECONDARY_BUTTON_Y = 92,
            LAUNCH_Y = 116;
    private EditBox xField, yField, zField;
    private Button launchButton, applyButton, clearButton, heldButton;

    public MissileSiloScreen(
            final MissileSiloMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        titleLabelX = -10_000;
        inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + TARGET_X,
                fieldY = topPos + TARGET_FIELD_Y,
                fieldGap = 4,
                fieldWidth = (TARGET_WIDTH - fieldGap * 2) / 3;
        xField = field(x, fieldY, fieldWidth, "X");
        yField = field(x + fieldWidth + fieldGap, fieldY, fieldWidth, "Y");
        zField = field(x + (fieldWidth + fieldGap) * 2, fieldY, fieldWidth, "Z");
        MissileSiloBlockEntity silo = menu.silo();
        if (silo != null && silo.storedTarget() != null) {
            Vec3 position = silo.storedTarget().position();
            xField.setValue(format(position.x));
            yField.setValue(format(position.y));
            zField.setValue(format(position.z));
        }
        applyButton =
                addRenderableWidget(
                        Button.builder(Component.literal("Apply target"), button -> apply())
                                .bounds(x, topPos + APPLY_Y, TARGET_WIDTH, 20)
                                .build());
        clearButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("Clear"),
                                        button ->
                                                send(
                                                        new ServerboundSiloClearTargetPayload(
                                                                menu.containerId,
                                                                menu.centre(),
                                                                menu.siloId())))
                                .bounds(x, topPos + SECONDARY_BUTTON_Y, 38, 20)
                                .build());
        heldButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("Held target"),
                                        button ->
                                                send(
                                                        new ServerboundSiloUseHeldDesignatorPayload(
                                                                menu.containerId,
                                                                menu.centre(),
                                                                menu.siloId())))
                                .bounds(x + 42, topPos + SECONDARY_BUTTON_Y, 60, 20)
                                .build());
        launchButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("LAUNCH"),
                                        button ->
                                                send(
                                                        new ServerboundSiloLaunchPayload(
                                                                menu.containerId,
                                                                menu.centre(),
                                                                menu.siloId())))
                                .bounds(x, topPos + LAUNCH_Y, TARGET_WIDTH, 20)
                                .build());
    }

    private EditBox field(final int x, final int y, final int width, final String hint) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(20);
        addRenderableWidget(box);
        return box;
    }

    private void apply() {
        try {
            send(
                    new ServerboundSiloSetTargetPayload(
                            menu.containerId,
                            menu.centre(),
                            menu.siloId(),
                            Double.parseDouble(xField.getValue()),
                            Double.parseDouble(yField.getValue()),
                            Double.parseDouble(zField.getValue())));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void send(
            final net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        MissileSiloBlockEntity silo = menu.silo();
        boolean
                interceptor =
                        silo != null
                                && MissilePayloadItems.isInterceptor(
                                        silo.reservedMissile().isEmpty()
                                                ? silo.missileStack()
                                                : silo.reservedMissile()),
                strategic = !interceptor;
        xField.visible = yField.visible = zField.visible = strategic;
        applyButton.visible = clearButton.visible = heldButton.visible = strategic;
        xField.active = yField.active = zField.active = strategic;
        applyButton.active = clearButton.active = heldButton.active = strategic;
        launchButton.visible = true;
        launchButton.active = silo != null && silo.siloState() == MissileSiloState.READY;
        launchButton.setMessage(Component.literal(interceptor ? "INTERCEPT" : "LAUNCH"));
    }

    private void drawBackground(final GuiGraphicsExtractor graphics) {
        WarModUiText.frame(graphics, leftPos, topPos, imageWidth, imageHeight);
        WarModUiText.section(graphics, leftPos + 8, topPos + 26, 76, 98);
        WarModUiText.section(graphics, leftPos + 88, topPos + 26, 108, 98);
        WarModUiText.section(graphics, leftPos + 198, topPos + 26, 110, 114);
        WarModUiText.section(graphics, leftPos + 64, topPos + 140, 188, 92);
    }

    @Override
    public void extractRenderState(
            final GuiGraphicsExtractor graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        drawBackground(graphics);
        drawSlotBackgrounds(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        MissileSiloBlockEntity silo = menu.silo();
        if (silo == null) return;
        WarModUiText.text(
                graphics,
                font,
                Component.literal("MISSILE SILO"),
                leftPos + 10,
                topPos + 8,
                WarModUiText.TEXT);
        String stateText = silo.siloState().name();
        int stateColour =
                silo.siloState() == MissileSiloState.ERROR
                        ? WarModUiText.ERROR
                        : silo.siloState() == MissileSiloState.READY
                                ? WarModUiText.SUCCESS
                                : WarModUiText.TEXT;
        WarModUiText.text(
                graphics,
                font,
                Component.literal(stateText),
                leftPos + imageWidth - font.width(stateText) - 10,
                topPos + 8,
                stateColour);
        WarModUiText.text(
                graphics,
                font,
                Component.literal("PAYLOAD"),
                leftPos + 14,
                topPos + 31,
                WarModUiText.TEXT);
        String payloadName =
                silo.missileStack().isEmpty()
                        ? "EMPTY"
                        : MissilePayloadItems.payloadType(silo.missileStack())
                                .map(
                                        type ->
                                                type.serializedName()
                                                        .replace('_', ' ')
                                                        .toUpperCase(Locale.ROOT))
                                .orElseGet(
                                        () ->
                                                silo.missileStack()
                                                        .getHoverName()
                                                        .getString()
                                                        .toUpperCase(Locale.ROOT));
        WarModUiText.text(
                graphics,
                font,
                Component.literal(WarModUiText.ellipsize(font, payloadName, 68)),
                leftPos + 12,
                topPos + 82,
                WarModUiText.TEXT);
        WarModUiText.text(
                graphics,
                font,
                Component.literal("Count " + silo.missileStack().getCount() + " / 16"),
                leftPos + 12,
                topPos + 98,
                WarModUiText.TEXT);
        var guidanceMissile =
                silo.reservedMissile().isEmpty() ? silo.missileStack() : silo.reservedMissile();
        boolean hasGuidanceMissile = MissilePayloadItems.isMissile(guidanceMissile);
        int guidanceTier = MissilePayloadItems.guidanceTier(guidanceMissile);
        int statusX = leftPos + 94;
        WarModUiText.text(
                graphics,
                font,
                Component.literal("MISSILE GUIDANCE"),
                statusX,
                topPos + 31,
                WarModUiText.TEXT);
        compactLine(
                graphics,
                statusX,
                topPos + 48,
                "Chip tier",
                hasGuidanceMissile ? Integer.toString(guidanceTier) : "--",
                96);
        boolean interceptor = MissilePayloadItems.isInterceptor(guidanceMissile);
        int error =
                (int)
                        (interceptor
                                ? AntiAirGuidanceResolver.maximumMiss(guidanceTier)
                                : MissileSiloGuidanceFrameStructure.maximumGuidanceError(
                                        guidanceTier));
        compactLine(
                graphics,
                statusX,
                topPos + 66,
                interceptor ? "Max miss" : "Error",
                hasGuidanceMissile ? error + " blocks" : "--",
                96);
        double reload =
                silo.reloadTicksTotal() == 0
                        ? 1.0
                        : 1.0 - (double) silo.reloadTicksRemaining() / silo.reloadTicksTotal();
        reload = Math.max(0, Math.min(1, reload));
        WarModUiText.text(
                graphics,
                font,
                Component.literal("Reload " + (int) Math.round(reload * 100) + "%"),
                statusX,
                topPos + 86,
                WarModUiText.TEXT);
        graphics.fill(statusX, topPos + 101, statusX + 96, topPos + 109, WarModUiText.SURFACE);
        graphics.fill(
                statusX,
                topPos + 101,
                statusX + (int) Math.round(96 * reload),
                topPos + 109,
                WarModUiText.SUCCESS);
        if (!interceptor)
            WarModUiText.text(
                    graphics,
                    font,
                    Component.literal("TARGET XYZ"),
                    leftPos + TARGET_X,
                    topPos + 31,
                    WarModUiText.TEXT);
        else {
            int panelX = leftPos + TARGET_X;
            WarModUiText.text(
                    graphics,
                    font,
                    Component.literal("AUTO DEFENCE"),
                    panelX,
                    topPos + 31,
                    WarModUiText.TEXT);
            WarModUiText.text(
                    graphics,
                    font,
                    Component.literal(
                            "Range: "
                                    + (int) AntiAirConstants.DEFENDED_TRAJECTORY_RADIUS_BLOCKS
                                    + " blocks"),
                    panelX,
                    topPos + 48,
                    WarModUiText.TEXT);
            boolean ballistic =
                    MissilePayloadItems.antiAirVariant(guidanceMissile)
                            .map(variant -> variant.ballisticFallback())
                            .orElse(false);
            String fallback = ballistic ? "Fallback: Ballistic" : "Fallback: Self-destruct";
            WarModUiText.text(
                    graphics,
                    font,
                    Component.literal(WarModUiText.ellipsize(font, fallback, TARGET_WIDTH)),
                    panelX,
                    topPos + 64,
                    ballistic ? WarModUiText.TEXT : WarModUiText.SUCCESS);
            WarModUiText.text(
                    graphics,
                    font,
                    Component.literal("Targeting: Automatic"),
                    panelX,
                    topPos + 80,
                    WarModUiText.TEXT);
        }
        if (!silo.lastError().isBlank())
            WarModUiText.text(
                    graphics,
                    font,
                    Component.literal(
                            WarModUiText.ellipsize(
                                    font, "ERROR: " + silo.lastError(), imageWidth - 20)),
                    leftPos + 10,
                    topPos + 127,
                    WarModUiText.ERROR);
        WarModUiText.text(
                graphics,
                font,
                Component.literal("Inventory"),
                leftPos + 70,
                topPos + 139,
                WarModUiText.TEXT);
    }

    private void compactLine(
            final GuiGraphicsExtractor graphics,
            final int x,
            final int y,
            final String label,
            final String value,
            final int width) {
        WarModUiText.text(
                graphics,
                font,
                Component.literal(WarModUiText.ellipsize(font, label + ": " + value, width)),
                x,
                y,
                WarModUiText.TEXT);
    }

    private void drawSlotBackgrounds(
            final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1, y = topPos + slot.y - 1;
            boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
            boolean locked =
                    slot.index == 0 && menu.silo() != null && !menu.silo().extractionAllowed();
            WarModUiText.slot(graphics, x, y, hovered, locked);
        }
    }

    private static String format(final double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
