package com.andye.warmod.phalanx.client.gui;

import com.andye.warmod.block.entity.PhalanxBlockEntity;
import com.andye.warmod.client.gui.WarModUiText;
import com.andye.warmod.menu.PhalanxMenu;
import com.andye.warmod.phalanx.PhalanxConstants;
import com.andye.warmod.phalanx.PhalanxGunStatus;
import com.andye.warmod.phalanx.client.ClientPhalanxStateManager;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class PhalanxScreen
    extends AbstractContainerScreen<PhalanxMenu> {

    private static final int SCREEN_WIDTH = 220;
    private static final int SCREEN_HEIGHT = 252;

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
    public void extractRenderState(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY,
        final float partial
    ) {
        WarModUiText.frame(graphics, leftPos, topPos, imageWidth, imageHeight);
        WarModUiText.section(graphics, leftPos + 8, topPos + 28,
            imageWidth - 16, 130);
        WarModUiText.section(graphics, leftPos + 4, topPos + 162,
            imageWidth - 8, imageHeight - 166);

        drawSlotBackgrounds(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partial);

        PhalanxBlockEntity turret = menu.turret();

        if (turret == null) {
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
            Component.literal("PHALANX POINT DEFENCE"),
            leftPos + 8,
            topPos + 8,
            WarModUiText.ACCENT
        );
        WarModUiText.text(graphics,
            font,
            WarModUiText.ellipsize(font, Component.literal(
                "Status: " + status.name().replace('_', ' ')
            ), imageWidth - 28),
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
            leftPos + imageWidth - font.width(enabledText) - 14,
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
    }

    private void drawSlotBackgrounds(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY
    ) {
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;

            WarModUiText.slot(graphics, x, y,
                mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18,
                false);
        }
    }
}
