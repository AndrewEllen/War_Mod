package com.andye.warmod.artillery.client.gui;

import com.andye.warmod.artillery.network.ServerboundArtilleryFirePayload;
import com.andye.warmod.artillery.network.ServerboundArtilleryTargetPayload;
import com.andye.warmod.block.entity.ArtilleryCannonBlockEntity;
import com.andye.warmod.menu.ArtilleryCannonMenu;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;

public final class ArtilleryCannonScreen extends AbstractContainerScreen<ArtilleryCannonMenu> {
    private EditBox x, y, z; private Button fire;
    public ArtilleryCannonScreen(final ArtilleryCannonMenu menu, final Inventory inventory, final Component title) { super(menu, inventory, title, 194, 194); titleLabelX = -10_000; inventoryLabelX = -10_000; }
    @Override protected void init() { super.init(); x = field(leftPos + 86, topPos + 38, "X"); y = field(leftPos + 120, topPos + 38, "Y"); z = field(leftPos + 154, topPos + 38, "Z"); ArtilleryCannonBlockEntity cannon = menu.cannon(); if (cannon != null && cannon.target() != null) { Vec3 target = cannon.target().position(); x.setValue(format(target.x)); y.setValue(format(target.y)); z.setValue(format(target.z)); } addRenderableWidget(Button.builder(Component.literal("Set target"), ignored -> setTarget()).bounds(leftPos + 86, topPos + 62, 100, 20).build()); fire = addRenderableWidget(Button.builder(Component.literal("FIRE"), ignored -> send(new ServerboundArtilleryFirePayload(menu.containerId, menu.position()))).bounds(leftPos + 86, topPos + 88, 100, 20).build()); }
    private EditBox field(final int px, final int py, final String hint) { EditBox field = new EditBox(font, px, py, 30, 18, Component.literal(hint)); field.setHint(Component.literal(hint)); field.setMaxLength(10); addRenderableWidget(field); return field; }
    private void setTarget() { try { send(new ServerboundArtilleryTargetPayload(menu.containerId, menu.position(), Double.parseDouble(x.getValue()), Double.parseDouble(y.getValue()), Double.parseDouble(z.getValue()))); } catch (NumberFormatException ignored) { } }
    private static void send(final net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) { if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload); }
    @Override protected void containerTick() { super.containerTick(); fire.active = menu.cannon() != null && menu.cannon().cooldown() == 0; }
    @Override public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partial) { graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff10171b); graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 24, 0xff26343b); super.extractRenderState(graphics, mouseX, mouseY, partial); ArtilleryCannonBlockEntity cannon = menu.cannon(); graphics.text(font, Component.literal("ARTILLERY CANNON"), leftPos + 8, topPos + 8, 0xffffc45a); graphics.text(font, Component.literal("AMMUNITION"), leftPos + 20, topPos + 32, 0xff9db4bd); graphics.text(font, Component.literal("TARGET XYZ"), leftPos + 88, topPos + 26, 0xff9db4bd); graphics.text(font, Component.literal("Range 1,000 | Apex 384"), leftPos + 88, topPos + 116, 0xffc7d8dd); if (cannon != null) { graphics.text(font, Component.literal("Count " + cannon.ammunition().getCount() + " / 16"), leftPos + 16, topPos + 78, 0xffe6edf0); if (!cannon.lastError().isBlank()) graphics.text(font, Component.literal(cannon.lastError()), leftPos + 8, topPos + 136, 0xffff7568); } }
    private static String format(final double value) { return String.format(Locale.ROOT, "%.1f", value); }
}
