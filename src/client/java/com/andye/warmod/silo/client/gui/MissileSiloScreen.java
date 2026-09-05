package com.andye.warmod.silo.client.gui;

import com.andye.warmod.antiair.AntiAirConstants;
import com.andye.warmod.antiair.AntiAirGuidanceResolver;
import com.andye.warmod.block.MissileSiloGuidanceFrameStructure;
import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.client.gui.WarModUiText;
import com.andye.warmod.defence.DefenceOwnershipAction;
import com.andye.warmod.defence.DefenceOwnershipSnapshot;
import com.andye.warmod.menu.MissileSiloMenu;
import com.andye.warmod.silo.MissilePayloadItems;
import com.andye.warmod.silo.network.ServerboundSiloClearTargetPayload;
import com.andye.warmod.silo.network.ServerboundSiloLaunchPayload;
import com.andye.warmod.silo.network.ServerboundSiloSetTargetPayload;
import com.andye.warmod.silo.network.ServerboundSiloUseHeldDesignatorPayload;
import com.andye.warmod.silo.network.ServerboundSiloOwnershipPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class MissileSiloScreen extends AbstractContainerScreen<MissileSiloMenu> {
    private static final int SCREEN_WIDTH = 420,
            SCREEN_HEIGHT = 236,
            PAYLOAD_X = 8,
            PAYLOAD_WIDTH = 132,
            GUIDANCE_X = 144,
            GUIDANCE_WIDTH = 126,
            TARGET_X = 274,
            TARGET_WIDTH = 138,
            OWNERSHIP_WIDTH = 152,
            TARGET_FIELD_Y = 44,
            APPLY_Y = 68,
            SECONDARY_BUTTON_Y = 92,
            LAUNCH_Y = 116;
    private EditBox xField, yField, zField;
    private Button launchButton, applyButton, clearButton, heldButton;
    private EditBox allyField;
    private Button ownershipButton, addAllyButton, removeAllyButton, ownershipToggle,
            previousAlliesButton, nextAlliesButton;
    private boolean ownershipExpanded;
    private int allyPage;
    private String preservedX, preservedY, preservedZ, preservedAlly, focusedField;

    public MissileSiloScreen(
            final MissileSiloMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        titleLabelX = -10_000;
        inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = preferredLeftPos();
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
        restoreTargetFields();
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
        addOwnershipControls();
        ownershipToggle = addRenderableWidget(
                Button.builder(Component.literal(ownershipExpanded ? "<" : ">"),
                                button -> toggleOwnership())
                        .bounds(leftPos + SCREEN_WIDTH - 22, topPos + 7, 16, 18)
                        .build());
        restoreFocusedField();
    }

    private EditBox field(final int x, final int y, final int width, final String hint) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(20);
        addRenderableWidget(box);
        return box;
    }

    private void addOwnershipControls() {
        int x = ownershipX();
        ownershipButton = addRenderableWidget(
                Button.builder(Component.literal("Claim"), button ->
                        sendOwnership(currentOwnershipAction()))
                        .bounds(x, topPos + 65, OWNERSHIP_WIDTH, 20)
                        .build());
        allyField = field(x, topPos + 91, OWNERSHIP_WIDTH, "Player name");
        allyField.setMaxLength(16);
        addAllyButton = addRenderableWidget(
                Button.builder(Component.literal("Add"), button ->
                        sendOwnership(DefenceOwnershipAction.ADD_ALLY))
                        .bounds(x, topPos + 113, 72, 20)
                        .build());
        removeAllyButton = addRenderableWidget(
                Button.builder(Component.literal("Remove"), button ->
                        sendOwnership(DefenceOwnershipAction.REMOVE_ALLY))
                        .bounds(x + 76, topPos + 113, 76, 20)
                        .build());
        previousAlliesButton = addRenderableWidget(
                Button.builder(Component.literal("<"), button -> {
                    allyPage = Math.max(0, allyPage - 1);
                    refreshOwnershipControls(menu.silo());
                }).bounds(x, topPos + 211, 72, 20).build());
        nextAlliesButton = addRenderableWidget(
                Button.builder(Component.literal(">"), button -> {
                    allyPage++;
                    refreshOwnershipControls(menu.silo());
                }).bounds(x + 80, topPos + 211, 72, 20).build());
        setOwnershipVisible(ownershipExpanded);
    }

    private int ownershipX() {
        int right = leftPos + SCREEN_WIDTH + 4;
        if (right + OWNERSHIP_WIDTH <= width - 4) return right;
        int left = leftPos - OWNERSHIP_WIDTH - 4;
        if (left >= 4) return left;
        // On a high GUI scale, keep the panel inside the compact screen rather
        // than letting it extend beyond either edge.
        return leftPos + SCREEN_WIDTH - OWNERSHIP_WIDTH - 8;
    }

    private boolean combinedLayoutFits() {
        return SCREEN_WIDTH + 4 + OWNERSHIP_WIDTH <= width - 8;
    }

    private int preferredLeftPos() {
        int layoutWidth = ownershipExpanded && combinedLayoutFits()
                ? SCREEN_WIDTH + 4 + OWNERSHIP_WIDTH : SCREEN_WIDTH;
        return (width - layoutWidth) / 2;
    }

    private boolean ownershipModal() {
        return ownershipExpanded && !combinedLayoutFits();
    }

    private void toggleOwnership() {
        ownershipExpanded = !ownershipExpanded;
        // Rebuild around the combined width when there is room; resize already
        // preserves the typed fields and their focus for this screen.
        resize(width, height);
    }

    private void setOwnershipVisible(final boolean visible) {
        ownershipButton.visible = visible;
        allyField.visible = visible;
        addAllyButton.visible = visible;
        removeAllyButton.visible = visible;
        previousAlliesButton.visible = visible;
        nextAlliesButton.visible = visible;
    }

    @Override
    public void resize(final int width, final int height) {
        preserveFields();
        super.resize(width, height);
    }

    private void preserveFields() {
        if (xField == null) return;
        preservedX = xField.getValue();
        preservedY = yField.getValue();
        preservedZ = zField.getValue();
        preservedAlly = allyField.getValue();
        focusedField = xField.isFocused() ? "x" : yField.isFocused() ? "y"
                : zField.isFocused() ? "z" : allyField.isFocused() ? "ally" : null;
    }

    private void restoreTargetFields() {
        if (preservedX == null) return;
        xField.setValue(preservedX);
        yField.setValue(preservedY);
        zField.setValue(preservedZ);
    }

    private void restoreFocusedField() {
        if (preservedAlly != null) allyField.setValue(preservedAlly);
        if (focusedField == null) return;
        EditBox focus = switch (focusedField) {
            case "x" -> xField;
            case "y" -> yField;
            case "z" -> zField;
            default -> allyField;
        };
        focus.setFocused(true);
    }

    private void sendOwnership(final DefenceOwnershipAction action) {
        String playerName = switch (action) {
            case ADD_ALLY, REMOVE_ALLY -> allyField.getValue().trim();
            default -> "";
        };
        if ((action == DefenceOwnershipAction.ADD_ALLY
                || action == DefenceOwnershipAction.REMOVE_ALLY) && playerName.isEmpty()) return;
        send(new ServerboundSiloOwnershipPayload(
                menu.containerId, menu.centre(), menu.siloId(), action, playerName));
    }

    private DefenceOwnershipAction currentOwnershipAction() {
        MissileSiloBlockEntity silo = menu.silo();
        return silo == null || silo.ownership().ownerPlayerId() == null
                ? DefenceOwnershipAction.CLAIM : DefenceOwnershipAction.UNCLAIM;
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
        launchButton.visible = !ownershipModal();
        launchButton.active = silo != null && silo.siloState() == MissileSiloState.READY;
        launchButton.setMessage(Component.literal(interceptor ? "INTERCEPT" : "LAUNCH"));
        if (ownershipModal()) {
            xField.visible = yField.visible = zField.visible = false;
            applyButton.visible = clearButton.visible = heldButton.visible = false;
        }
        refreshOwnershipControls(silo);
    }

    private void refreshOwnershipControls(final MissileSiloBlockEntity silo) {
        DefenceOwnershipSnapshot ownership = silo == null
                ? DefenceOwnershipSnapshot.unclaimed() : silo.ownership();
        boolean unclaimed = ownership.ownerPlayerId() == null;
        boolean owner = !unclaimed && minecraft.player != null
                && ownership.isOwner(minecraft.player.getUUID());
        ownershipButton.setMessage(Component.literal(unclaimed ? "Claim" : "Unclaim"));
        ownershipButton.active = ownershipExpanded && (unclaimed || owner);
        allyField.active = ownershipExpanded && owner;
        addAllyButton.active = ownershipExpanded && owner;
        removeAllyButton.active = ownershipExpanded && owner;
        int pageCount = Math.max(1, (ownership.allies().size() + 3) / 4);
        allyPage = Math.min(allyPage, pageCount - 1);
        previousAlliesButton.active = ownershipExpanded && allyPage > 0;
        nextAlliesButton.active = ownershipExpanded && allyPage + 1 < pageCount;
    }

    private void drawBackground(final GuiGraphicsExtractor graphics) {
        WarModUiText.frame(graphics, leftPos, topPos, SCREEN_WIDTH, imageHeight);
        if (ownershipModal()) {
            WarModUiText.section(graphics, ownershipX() - 4, topPos + 26,
                    OWNERSHIP_WIDTH + 8, 206);
            return;
        }
        WarModUiText.section(graphics, leftPos + PAYLOAD_X, topPos + 26, PAYLOAD_WIDTH, 98);
        WarModUiText.section(graphics, leftPos + GUIDANCE_X, topPos + 26, GUIDANCE_WIDTH, 98);
        WarModUiText.section(graphics, leftPos + TARGET_X - 4, topPos + 26, TARGET_WIDTH + 8, 114);
        WarModUiText.section(graphics, leftPos + 123, topPos + 140, 174, 92);
        if (ownershipExpanded)
            WarModUiText.section(graphics, ownershipX() - 4, topPos + 26,
                    OWNERSHIP_WIDTH + 8, 206);
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
        if (ownershipModal()) {
            drawOwnership(graphics, silo.ownership());
            return;
        }
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
                leftPos + SCREEN_WIDTH - font.width(stateText) - 30,
                topPos + 8,
                stateColour);
        WarModUiText.text(
                graphics,
                font,
                Component.literal("PAYLOAD"),
                leftPos + PAYLOAD_X + 6,
                topPos + 31,
                WarModUiText.TEXT);
        String payloadName = silo.missileStack().isEmpty()
                ? "EMPTY"
                : silo.missileStack().getHoverName().getString().toUpperCase(Locale.ROOT);
        String[] payloadLines = splitPayloadName(payloadName, PAYLOAD_WIDTH - 12);
        WarModUiText.text(
                graphics,
                font,
                Component.literal(payloadLines[0]),
                leftPos + PAYLOAD_X + 4,
                topPos + 76,
                WarModUiText.TEXT);
        if (!payloadLines[1].isBlank())
            WarModUiText.text(
                    graphics,
                    font,
                    Component.literal(payloadLines[1]),
                    leftPos + PAYLOAD_X + 4,
                    topPos + 88,
                    WarModUiText.TEXT);
        WarModUiText.text(
                graphics,
                font,
                Component.literal("Count " + silo.missileStack().getCount() + " / 16"),
                leftPos + PAYLOAD_X + 4,
                topPos + 106,
                WarModUiText.TEXT);
        var guidanceMissile =
                silo.reservedMissile().isEmpty() ? silo.missileStack() : silo.reservedMissile();
        boolean hasGuidanceMissile = MissilePayloadItems.isMissile(guidanceMissile);
        int guidanceTier = MissilePayloadItems.guidanceTier(guidanceMissile);
        int statusX = leftPos + GUIDANCE_X + 6;
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
                GUIDANCE_WIDTH - 12);
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
                interceptor ? "Max miss" : "Inaccuracy",
                hasGuidanceMissile ? error + " blocks" : "--",
                GUIDANCE_WIDTH - 12);
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
        graphics.fill(statusX, topPos + 101, statusX + GUIDANCE_WIDTH - 12, topPos + 109, WarModUiText.SURFACE);
        graphics.fill(
                statusX,
                topPos + 101,
                statusX + (int) Math.round((GUIDANCE_WIDTH - 12) * reload),
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
                                    font, "ERROR: " + silo.lastError(), SCREEN_WIDTH - 20)),
                    leftPos + 10,
                    topPos + 127,
                    WarModUiText.ERROR);
        if (ownershipExpanded) drawOwnership(graphics, silo.ownership());
        WarModUiText.text(
                graphics,
                font,
                Component.literal("Inventory"),
                leftPos + 129,
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
        if (ownershipModal()) return;
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

    @Override
    public void extractContents(
            final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY,
            final float partialTick) {
        super.extractContents(graphics, ownershipModal() ? -10_000 : mouseX,
                ownershipModal() ? -10_000 : mouseY, partialTick);
    }

    @Override
    protected void extractSlots(
            final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        if (!ownershipModal()) super.extractSlots(graphics, mouseX, mouseY);
    }

    @Override
    protected boolean hasClickedOutside(
            final double mouseX, final double mouseY, final int x, final int y) {
        if (ownershipExpanded
                && inside(mouseX, mouseY, ownershipX() - 4, topPos,
                        OWNERSHIP_WIDTH + 8, imageHeight)) return false;
        return super.hasClickedOutside(mouseX, mouseY, x, y);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        if (ownershipModal() && !ownershipControlAt(event.x(), event.y())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(final MouseButtonEvent event) {
        if (ownershipModal() && !ownershipControlAt(event.x(), event.y())) return true;
        return super.mouseReleased(event);
    }

    private boolean ownershipControlAt(final double mouseX, final double mouseY) {
        if (inside(mouseX, mouseY, leftPos + SCREEN_WIDTH - 22, topPos + 7, 16, 18)) return true;
        int x = ownershipX();
        return inside(mouseX, mouseY, x, topPos + 65, OWNERSHIP_WIDTH, 20)
                || inside(mouseX, mouseY, x, topPos + 91, OWNERSHIP_WIDTH, 18)
                || inside(mouseX, mouseY, x, topPos + 113, 72, 20)
                || inside(mouseX, mouseY, x + 76, topPos + 113, 76, 20)
                || inside(mouseX, mouseY, x, topPos + 211, 72, 20)
                || inside(mouseX, mouseY, x + 80, topPos + 211, 72, 20);
    }

    private static boolean inside(final double mouseX, final double mouseY,
            final int x, final int y, final int width, final int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawOwnership(
            final GuiGraphicsExtractor graphics, final DefenceOwnershipSnapshot ownership) {
        int x = ownershipX();
        WarModUiText.text(graphics, font, Component.literal("OWNERSHIP"), x, topPos + 31,
                WarModUiText.TEXT);
        String owner = ownership.ownerPlayerId() == null
                ? "Unclaimed" : "Owner: " + ownership.ownerDisplayName();
        WarModUiText.text(graphics, font, Component.literal(owner),
                x, topPos + 48, ownership.ownerPlayerId() == null
                        ? WarModUiText.TEXT_MUTED : WarModUiText.SUCCESS);
        WarModUiText.text(graphics, font, Component.literal("Allies"), x, topPos + 140,
                WarModUiText.TEXT);
        int start = allyPage * 4;
        int shown = Math.min(4, Math.max(0, ownership.allies().size() - start));
        for (int index = 0; index < shown; index++) {
            WarModUiText.text(graphics, font,
                    Component.literal(ownership.allies().get(start + index).playerName()),
                    x + 4, topPos + 153 + index * 12, WarModUiText.TEXT_MUTED);
        }
        int pageCount = Math.max(1, (ownership.allies().size() + 3) / 4);
        WarModUiText.text(graphics, font,
                Component.literal("Page " + (allyPage + 1) + "/" + pageCount),
                x + 56, topPos + 198, WarModUiText.TEXT_MUTED);
    }

    private String[] splitPayloadName(final String value, final int width) {
        if (font.width(value) <= width) return new String[] {value, ""};
        int split = value.lastIndexOf(' ');
        while (split > 0 && font.width(value.substring(0, split)) > width)
            split = value.lastIndexOf(' ', split - 1);
        if (split <= 0)
            return new String[] {WarModUiText.ellipsize(font, value, width), ""};
        return new String[] {
            value.substring(0, split),
            WarModUiText.ellipsize(font, value.substring(split + 1), width)
        };
    }
}
