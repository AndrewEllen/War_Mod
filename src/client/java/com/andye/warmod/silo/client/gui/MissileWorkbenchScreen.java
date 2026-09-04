package com.andye.warmod.silo.client.gui;

import com.andye.warmod.client.gui.WarModUiText;
import com.andye.warmod.menu.MissileWorkbenchMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class MissileWorkbenchScreen extends AbstractContainerScreen<MissileWorkbenchMenu> {
    public MissileWorkbenchScreen(MissileWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 196, 191);
        titleLabelX = inventoryLabelX = -10000;
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        WarModUiText.frame(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots)
            WarModUiText.slot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, false, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        text(graphics, "Missile Workbench", 10, 8);
        text(graphics, "Body", 23, 28);
        text(graphics, "Chip", 60, 28);
        text(graphics, "Head", 96, 28);
        text(graphics, "Missile", 141, 28);
        text(graphics, ">", 130, 45);
        text(graphics, "Top: body | N/S: chip", 12, 70);
        text(graphics, "E/W: head | Bottom: output", 12, 82);
        text(graphics, "Inventory", 17, 97);
    }

    private void text(GuiGraphicsExtractor graphics, String text, int x, int y) {
        WarModUiText.text(
                graphics,
                font,
                Component.literal(text),
                leftPos + x,
                topPos + y,
                WarModUiText.TEXT);
    }
}
