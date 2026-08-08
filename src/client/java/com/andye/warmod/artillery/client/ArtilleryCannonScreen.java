package com.andye.warmod.artillery.client;

import com.andye.warmod.artillery.ArtilleryConstants;
import com.andye.warmod.artillery.network.ServerboundArtilleryClearTargetPayload;
import com.andye.warmod.artillery.network.ServerboundArtilleryFirePayload;
import com.andye.warmod.artillery.network.ServerboundArtillerySetTargetPayload;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;

public final class ArtilleryCannonScreen extends AbstractContainerScreen<ArtilleryCannonMenu> {
    private static final int WIDTH = 316;
    private static final int HEIGHT = 214;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private Button fireButton;

    public ArtilleryCannonScreen(final ArtilleryCannonMenu menu, final Inventory inventory,
        final Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        titleLabelX = -10_000;
        inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        int targetX = leftPos + 160;
        int fieldY = topPos + 44;
        int fieldWidth = 43;
        xField = field(targetX, fieldY, fieldWidth, "X");
        yField = field(targetX + 47, fieldY, fieldWidth, "Y");
        zField = field(targetX + 94, fieldY, fieldWidth, "Z");
        ArtilleryCannonBlockEntity cannon = menu.cannon();
        if (cannon != null && cannon.storedTarget() != null) {
            Vec3 position = cannon.storedTarget().position();
            xField.setValue(format(position.x));
            yField.setValue(format(position.y));
            zField.setValue(format(position.z));
        }
        addRenderableWidget(Button.builder(Component.literal("Apply target"), button -> apply())
            .bounds(targetX, topPos + 68, 137, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), button -> send(
            new ServerboundArtilleryClearTargetPayload(menu.containerId, menu.cannonPos())))
            .bounds(targetX, topPos + 92, 58, 20).build());
        fireButton = addRenderableWidget(Button.builder(Component.literal("FIRE"), button -> send(
            new ServerboundArtilleryFirePayload(menu.containerId, menu.cannonPos())))
            .bounds(targetX + 62, topPos + 92, 75, 20).build());
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
            send(new ServerboundArtillerySetTargetPayload(menu.containerId, menu.cannonPos(),
                Double.parseDouble(xField.getValue()), Double.parseDouble(yField.getValue()),
                Double.parseDouble(zField.getValue())));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void send(final net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ArtilleryCannonBlockEntity cannon = menu.cannon();
        fireButton.active = cannon != null && cannon.roundsLoaded() > 0
            && cannon.storedTarget() != null;
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX,
        final int mouseY, final float partialTick) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff11181c);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 24, 0xff202a2f);
        graphics.fill(leftPos + 8, topPos + 28, leftPos + 102, topPos + 104, 0xff0c1215);
        graphics.fill(leftPos + 150, topPos + 28, leftPos + 306, topPos + 136, 0xff0c1215);
        graphics.fill(leftPos + 8, topPos + 106, leftPos + 306, topPos + 208, 0xff0c1215);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xff6c777c);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xff0b1114);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, Component.literal("ARTILLERY CANNON"), leftPos + 10, topPos + 8,
            0xffffc45a);
        graphics.text(font, Component.literal("MAGAZINE"), leftPos + 14, topPos + 31, 0xff8299a2);
        graphics.text(font, Component.literal("TARGET XYZ"), leftPos + 160, topPos + 31, 0xff8299a2);
        ArtilleryCannonBlockEntity cannon = menu.cannon();
        if (cannon == null) return;
        graphics.text(font, Component.literal(cannon.roundsLoaded() + " / 16 rounds"),
            leftPos + 14, topPos + 94, 0xffc6d4da);
        graphics.text(font, Component.literal("Max range: " + (int)ArtilleryConstants.MAXIMUM_RANGE_BLOCKS + " blocks"),
            leftPos + 160, topPos + 120, 0xffa9bdc5);
        graphics.text(font, Component.literal("Status: " + cannon.lastStatus()),
            leftPos + 112, topPos + 145, 0xffc6d4da);
        if (cannon.lastFlightTicks() > 0) {
            graphics.text(font, Component.literal(String.format(Locale.ROOT,
                "Last: %.1f°  %.0f blocks  %.1fs", cannon.lastAngleDegrees(),
                cannon.lastRangeBlocks(), cannon.lastFlightTicks() / 20.0)),
                leftPos + 112, topPos + 160, 0xff9ee0ac);
            graphics.text(font, Component.literal(String.format(Locale.ROOT,
                "Apex Y %.1f", cannon.lastApexY())), leftPos + 112, topPos + 174, 0xffa9bdc5);
        }
        graphics.text(font, Component.literal("Inventory"), leftPos + 18, topPos + 108, 0xffc5d5dc);
    }

    private static String format(final double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
