package com.andye.warmod.phalanx.client.gui;

import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.client.gui.WarModUiText;
import com.andye.warmod.defence.DefenceOwnershipAction;
import com.andye.warmod.defence.DefenceOwnershipSnapshot;
import com.andye.warmod.menu.PhalanxMenu;
import com.andye.warmod.phalanx.PhalanxConstants;
import com.andye.warmod.phalanx.PhalanxGunStatus;
import com.andye.warmod.phalanx.client.ClientPhalanxStateManager;
import com.andye.warmod.phalanx.network.ServerboundPhalanxOwnershipPayload;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class PhalanxScreen
    extends AbstractContainerScreen<PhalanxMenu> {

    private static final int SCREEN_WIDTH = 230;
    private static final int SCREEN_HEIGHT = 252;
    private static final int MAIN_WIDTH = 204;
    private static final int OWNERSHIP_WIDTH = 156;

    private EditBox allyField;
    private Button ownershipButton;
    private Button addAllyButton;
    private Button removeAllyButton;
    private Button ownershipToggle;
    private Button previousAlliesButton;
    private Button nextAlliesButton;
    private boolean ownershipExpanded;
    private int allyPage;
    private String preservedAlly;
    private boolean allyWasFocused;

    public PhalanxScreen(
        final PhalanxMenu menu,
        final Inventory inventory,
        final Component title
    ) {
        super(menu, inventory, title, SCREEN_WIDTH, SCREEN_HEIGHT);
        titleLabelX = -10_000;
        inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = preferredLeftPos();
        int x = ownershipX();
        ownershipButton = addRenderableWidget(Button.builder(Component.literal("Claim"), button ->
                sendOwnership(currentOwnershipAction()))
                .bounds(x, topPos + 62, OWNERSHIP_WIDTH, 20).build());
        allyField = new EditBox(font, x, topPos + 87, OWNERSHIP_WIDTH,
                18, Component.literal("Player name"));
        allyField.setHint(Component.literal("Player name"));
        allyField.setMaxLength(16);
        addRenderableWidget(allyField);
        addAllyButton = addRenderableWidget(Button.builder(Component.literal("Add"), button ->
                sendOwnership(DefenceOwnershipAction.ADD_ALLY))
                .bounds(x, topPos + 109, 74, 20).build());
        removeAllyButton = addRenderableWidget(Button.builder(Component.literal("Remove"), button ->
                sendOwnership(DefenceOwnershipAction.REMOVE_ALLY))
                .bounds(x + 78, topPos + 109, 78, 20).build());
        previousAlliesButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            allyPage = Math.max(0, allyPage - 1);
            refreshOwnershipControls(menu.turret());
        }).bounds(x, topPos + 221, 74, 20).build());
        nextAlliesButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            allyPage++;
            refreshOwnershipControls(menu.turret());
        }).bounds(x + 82, topPos + 221, 74, 20).build());
        ownershipToggle = addRenderableWidget(Button.builder(Component.literal(ownershipExpanded ? "<" : ">"),
                button -> toggleOwnership()).bounds(leftPos + SCREEN_WIDTH - 22, topPos + 7, 16, 18).build());
        if (preservedAlly != null) allyField.setValue(preservedAlly);
        if (allyWasFocused && ownershipExpanded) allyField.setFocused(true);
        setOwnershipVisible(ownershipExpanded);
    }

    private int ownershipX() {
        int right = leftPos + SCREEN_WIDTH + 4;
        if (right + OWNERSHIP_WIDTH <= width - 4) return right;
        int left = leftPos - OWNERSHIP_WIDTH - 4;
        if (left >= 4) return left;
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
        if (allyField != null) {
            preservedAlly = allyField.getValue();
            allyWasFocused = allyField.isFocused();
        }
        super.resize(width, height);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshOwnershipControls(menu.turret());
    }

    private DefenceOwnershipAction currentOwnershipAction() {
        PhalanxBlockEntity turret = menu.turret();
        return turret == null || turret.ownership().ownerPlayerId() == null
                ? DefenceOwnershipAction.CLAIM : DefenceOwnershipAction.UNCLAIM;
    }

    private void sendOwnership(final DefenceOwnershipAction action) {
        PhalanxBlockEntity turret = menu.turret();
        if (turret == null) return;
        String playerName = switch (action) {
            case ADD_ALLY, REMOVE_ALLY -> allyField.getValue().trim();
            default -> "";
        };
        if ((action == DefenceOwnershipAction.ADD_ALLY
                || action == DefenceOwnershipAction.REMOVE_ALLY) && playerName.isEmpty()) return;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new ServerboundPhalanxOwnershipPayload(menu.containerId, menu.centre(),
                        turret.turretId(), action, playerName));
    }

    private void refreshOwnershipControls(final PhalanxBlockEntity turret) {
        DefenceOwnershipSnapshot ownership = turret == null
                ? DefenceOwnershipSnapshot.unclaimed() : turret.ownership();
        boolean unclaimed = ownership.ownerPlayerId() == null;
        boolean owner = !unclaimed && minecraft.player != null
                && ownership.isOwner(minecraft.player.getUUID());
        ownershipButton.setMessage(Component.literal(unclaimed ? "Claim" : "Unclaim"));
        ownershipButton.active = ownershipExpanded && (unclaimed || owner);
        allyField.active = ownershipExpanded && owner;
        addAllyButton.active = ownershipExpanded && owner;
        removeAllyButton.active = ownershipExpanded && owner;
        int pageCount = Math.max(1, (ownership.allies().size() + 4) / 5);
        allyPage = Math.min(allyPage, pageCount - 1);
        previousAlliesButton.active = ownershipExpanded && allyPage > 0;
        nextAlliesButton.active = ownershipExpanded && allyPage + 1 < pageCount;
    }

    @Override
    public void extractRenderState(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partial
    ) {
        WarModUiText.frame(graphics, leftPos, topPos, SCREEN_WIDTH, imageHeight);
        WarModUiText.section(graphics, leftPos + 8, topPos + 28,
            MAIN_WIDTH, 130);
        WarModUiText.section(graphics, leftPos + 4, topPos + 162,
            MAIN_WIDTH + 8, imageHeight - 166);
        if (ownershipExpanded)
            WarModUiText.section(graphics, ownershipX() - 4, topPos + 28,
                OWNERSHIP_WIDTH + 8, imageHeight - 32);

        drawSlotBackgrounds(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partial);

        PhalanxBlockEntity turret = menu.turret();

        if (turret == null) {
            return;
        }
        if (ownershipModal()) {
            drawOwnership(graphics, turret.ownership());
            return;
        }

        double clientTime = turret.getLevel() == null
            ? 0.0
            : turret.getLevel().getGameTime() + partial;

        ClientPhalanxStateManager.View network =
            ClientPhalanxStateManager.INSTANCE.view(
                turret.turretId(),
                clientTime
            );

        int rounds = network != null
            ? network.rounds()
            : turret.rounds();
        PhalanxGunStatus status = network != null
            ? network.status()
            : turret.status();
        boolean enabled = network != null
            ? network.enabled()
            : turret.enabled();
        float bloom = network != null
            ? network.bloom()
            : turret.bloom();

        WarModUiText.text(graphics,
            font,
            Component.literal("ANTI-AIR TURRET"),
            leftPos + 8,
            topPos + 8,
            WarModUiText.ACCENT
        );
        WarModUiText.text(graphics,
            font,
            WarModUiText.ellipsize(font, Component.literal(
                "Status: " + status.name().replace('_', ' ')
            ), MAIN_WIDTH - 20),
            leftPos + 14,
            topPos + 31,
            status == PhalanxGunStatus.OUT_OF_AMMO
                ? WarModUiText.ERROR
                : WarModUiText.SUCCESS
        );

        String enabledText = enabled ? "ENABLED" : "DISABLED";
        WarModUiText.text(graphics,
            font,
            Component.literal(enabledText),
            leftPos + MAIN_WIDTH - font.width(enabledText) - 6,
            topPos + 19,
            enabled ? WarModUiText.SUCCESS : WarModUiText.ERROR
        );

        WarModUiText.text(graphics,
            font,
            Component.literal(
                "Ammunition: " + rounds + " / "
                    + PhalanxConstants.ROUNDS_PER_TURRET
            ),
            leftPos + 14,
            topPos + 90,
            WarModUiText.TEXT
        );
        WarModUiText.text(graphics,
            font,
            Component.literal(
                "Tracking: "
                    + (int)PhalanxConstants.HORIZONTAL_TRACKING_RADIUS_BLOCKS
                    + " horizontal"
            ),
            leftPos + 14,
            topPos + 104,
            WarModUiText.TEXT_MUTED
        );
        WarModUiText.text(graphics,
            font,
            Component.literal(
                "Firing: "
                    + (int)PhalanxConstants.HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS
                    + " horizontal"
            ),
            leftPos + 14,
            topPos + 118,
            WarModUiText.TEXT_MUTED
        );
        WarModUiText.text(graphics,
            font,
            Component.literal("Azimuth: 360 degrees"),
            leftPos + 14,
            topPos + 132,
            WarModUiText.TEXT_MUTED
        );
        WarModUiText.text(graphics,
            font,
            Component.literal(
                String.format(
                    Locale.ROOT,
                    "Full elevation | Spread %.2f",
                    PhalanxConstants.BASE_SPREAD_DEGREES + bloom
                )
            ),
            leftPos + 14,
            topPos + 146,
            WarModUiText.TEXT_MUTED
        );
        WarModUiText.text(graphics,
            font,
            Component.literal("Inventory"),
            leftPos + 29,
            topPos + 155,
            WarModUiText.TEXT_MUTED
        );
        if (ownershipExpanded) drawOwnership(graphics, turret.ownership());
    }

    private void drawOwnership(
        final GuiGraphicsExtractor graphics,
        final DefenceOwnershipSnapshot ownership
    ) {
        int x = ownershipX();
        WarModUiText.text(graphics, font, Component.literal("OWNERSHIP"), x,
            topPos + 33, WarModUiText.TEXT);
        String owner = ownership.ownerPlayerId() == null
            ? "Unclaimed" : "Owner: " + ownership.ownerDisplayName();
        WarModUiText.text(graphics, font, Component.literal(owner), x, topPos + 48,
            ownership.ownerPlayerId() == null
            ? WarModUiText.TEXT_MUTED : WarModUiText.SUCCESS);
        WarModUiText.text(graphics, font, Component.literal("Allies"), x,
            topPos + 136, WarModUiText.TEXT);
        int start = allyPage * 5;
        int shown = Math.min(5, Math.max(0, ownership.allies().size() - start));
        for (int index = 0; index < shown; index++) {
            WarModUiText.text(graphics, font,
                Component.literal(ownership.allies().get(start + index).playerName()),
                x + 4, topPos + 149 + index * 12, WarModUiText.TEXT_MUTED);
        }
        int pageCount = Math.max(1, (ownership.allies().size() + 4) / 5);
        WarModUiText.text(graphics, font,
            Component.literal("Page " + (allyPage + 1) + "/" + pageCount),
            x + 58, topPos + 210, WarModUiText.TEXT_MUTED);
    }

    private void drawSlotBackgrounds(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY
    ) {
        if (ownershipModal()) return;
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;

            WarModUiText.slot(graphics, x, y,
                mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18,
                false);
        }
    }

    @Override
    public void extractContents(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partial
    ) {
        super.extractContents(graphics, ownershipModal() ? -10_000 : mouseX,
            ownershipModal() ? -10_000 : mouseY, partial);
    }

    @Override
    protected void extractSlots(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY
    ) {
        if (!ownershipModal()) super.extractSlots(graphics, mouseX, mouseY);
    }

    @Override
    protected boolean hasClickedOutside(
        final double mouseX,
        final double mouseY,
        final int x,
        final int y
    ) {
        if (ownershipExpanded && inside(mouseX, mouseY, ownershipX() - 4,
            topPos, OWNERSHIP_WIDTH + 8, imageHeight)) return false;
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
        return inside(mouseX, mouseY, x, topPos + 62, OWNERSHIP_WIDTH, 20)
                || inside(mouseX, mouseY, x, topPos + 87, OWNERSHIP_WIDTH, 18)
                || inside(mouseX, mouseY, x, topPos + 109, 74, 20)
                || inside(mouseX, mouseY, x + 78, topPos + 109, 78, 20)
                || inside(mouseX, mouseY, x, topPos + 221, 74, 20)
                || inside(mouseX, mouseY, x + 82, topPos + 221, 74, 20);
    }

    private static boolean inside(final double mouseX, final double mouseY,
        final int x, final int y, final int width, final int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
