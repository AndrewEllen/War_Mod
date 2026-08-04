package com.andye.warmod.phalanx.client.gui;

import com.andye.warmod.block.entity.PhalanxBlockEntity;
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

    private static final int SCREEN_WIDTH = 196;
    private static final int SCREEN_HEIGHT = 220;

    public PhalanxScreen(
        final PhalanxMenu menu,
        final Inventory inventory,
        final Component title
    ) {
        super(
            menu,
            inventory,
            title,
            SCREEN_WIDTH,
            SCREEN_HEIGHT
        );

        /*
         * This screen draws its own title and inventory label.
         */
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
        graphics.fill(
            leftPos,
            topPos,
            leftPos + imageWidth,
            topPos + imageHeight,
            0xFF161E22
        );

        graphics.fill(
            leftPos,
            topPos,
            leftPos + imageWidth,
            topPos + 24,
            0xFF202A2F
        );

        graphics.fill(
            leftPos + 8,
            topPos + 28,
            leftPos + imageWidth - 8,
            topPos + 122,
            0xFF0C1215
        );

        graphics.fill(
            leftPos + 4,
            topPos + 130,
            leftPos + imageWidth - 4,
            topPos + imageHeight - 4,
            0xFF0C1215
        );

        drawSlotBackgrounds(
            graphics,
            mouseX,
            mouseY
        );

        super.extractRenderState(
            graphics,
            mouseX,
            mouseY,
            partial
        );

        PhalanxBlockEntity turret =
            menu.turret();

        if (turret == null) {
            return;
        }

        double clientTime =
            turret.getLevel() == null
                ? 0.0
                : turret.getLevel()
                    .getGameTime()
                    + partial;

        ClientPhalanxStateManager.View network =
            ClientPhalanxStateManager.INSTANCE.view(
                turret.turretId(),
                clientTime
            );

        int rounds =
            network != null
                ? network.rounds()
                : turret.rounds();

        PhalanxGunStatus status =
            network != null
                ? network.status()
                : turret.status();

        boolean enabled =
            network != null
                ? network.enabled()
                : turret.enabled();

        float bloom =
            network != null
                ? network.bloom()
                : turret.bloom();

        graphics.text(
            font,
            Component.literal(
                "PHALANX POINT DEFENCE"
            ),
            leftPos + 8,
            topPos + 8,
            0xFFFFC45A
        );

        graphics.text(
            font,
            Component.literal(
                "Status: "
                    + status.name()
                        .replace('_', ' ')
            ),
            leftPos + 14,
            topPos + 31,
            status == PhalanxGunStatus.OUT_OF_AMMO
                ? 0xFFFF7568
                : 0xFF8FD5B5
        );

        String enabledText =
            enabled
                ? "ENABLED"
                : "DISABLED";

        graphics.text(
            font,
            Component.literal(enabledText),
            leftPos + imageWidth
                - font.width(enabledText)
                - 14,
            topPos + 31,
            enabled
                ? 0xFF8FD5B5
                : 0xFFFF7568
        );

        graphics.text(
            font,
            Component.literal(
                "Ammunition: "
                    + rounds
                    + " / "
                    + PhalanxConstants
                        .ROUNDS_PER_TURRET
            ),
            leftPos + 14,
            topPos + 70,
            0xFFE2EAED
        );

        graphics.text(
            font,
            Component.literal(
                "Horizontal radius: "
                    + (int)PhalanxConstants
                        .HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS
                    + " blocks"
            ),
            leftPos + 14,
            topPos + 84,
            0xFFC5D5DC
        );

        graphics.text(
            font,
            Component.literal(
                "Azimuth: 360 degrees"
            ),
            leftPos + 14,
            topPos + 98,
            0xFFC5D5DC
        );

        graphics.text(
            font,
            Component.literal(
                "Elevation: "
                    + (int)PhalanxConstants
                        .MIN_ELEVATION_DEGREES
                    + " to +"
                    + (int)PhalanxConstants
                        .MAX_ELEVATION_DEGREES
                    + " degrees"
            ),
            leftPos + 14,
            topPos + 106,
            0xFFC5D5DC
        );

        graphics.text(
            font,
            Component.literal(
                String.format(
                    Locale.ROOT,
                    "Aim spread: %.2f degrees",
                    PhalanxConstants
                        .BASE_SPREAD_DEGREES
                        + bloom
                )
            ),
            leftPos + 14,
            topPos + 118,
            0xFFC5D5DC
        );
    }

    private void drawSlotBackgrounds(
        final GuiGraphicsExtractor graphics,
        final int mouseX,
        final int mouseY
    ) {
        for (Slot slot : menu.slots) {
            int x =
                leftPos + slot.x - 1;

            int y =
                topPos + slot.y - 1;

            graphics.fill(
                x,
                y,
                x + 18,
                y + 18,
                0xFF68757A
            );

            graphics.fill(
                x + 1,
                y + 1,
                x + 17,
                y + 17,
                0xFF0B1114
            );

            if (
                mouseX >= x
                && mouseX < x + 18
                && mouseY >= y
                && mouseY < y + 18
            ) {
                graphics.fill(
                    x + 1,
                    y + 1,
                    x + 17,
                    y + 17,
                    0x55FFFFFF
                );
            }
        }
    }
}
