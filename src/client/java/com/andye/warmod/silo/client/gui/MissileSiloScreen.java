package com.andye.warmod.silo.client.gui;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.MissileSiloGuidanceFrameStructure;
import com.andye.warmod.block.MissileSiloState;
import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.menu.MissileSiloMenu;
import com.andye.warmod.silo.MissilePayloadItems;
import com.andye.warmod.silo.network.ServerboundSiloClearTargetPayload;
import com.andye.warmod.silo.network.ServerboundSiloLaunchPayload;
import com.andye.warmod.silo.network.ServerboundSiloSetTargetPayload;
import com.andye.warmod.silo.network.ServerboundSiloUseHeldDesignatorPayload;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class MissileSiloScreen extends AbstractContainerScreen<MissileSiloMenu> {
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/gui/missile_silo.png");
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private Button launchButton, applyButton, clearButton, heldButton;

    public MissileSiloScreen(final MissileSiloMenu menu, final Inventory inventory,
        final Component title) {
        super(menu, inventory, title, 376, 236);
        titleLabelX = 12;
        titleLabelY = 8;
        inventoryLabelX = 107;
        inventoryLabelY = 138;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 246;
        int y = topPos + 44;
        xField = field(x, y, "X");
        yField = field(x, y + 23, "Y");
        zField = field(x, y + 46, "Z");
        MissileSiloBlockEntity silo = menu.silo();
        if (silo != null && silo.storedTarget() != null) {
            var position = silo.storedTarget().position();
            xField.setValue(format(position.x));
            yField.setValue(format(position.y));
            zField.setValue(format(position.z));
        }
        applyButton = addRenderableWidget(Button.builder(Component.literal("Apply Target"), button -> apply())
            .bounds(x, y + 69, 112, 20).build());
        clearButton = addRenderableWidget(Button.builder(Component.literal("Clear"),
            button -> send(new ServerboundSiloClearTargetPayload(menu.containerId,
                menu.centre(), menu.siloId()))).bounds(x, y + 92, 53, 20).build());
        heldButton = addRenderableWidget(Button.builder(Component.literal("From Held"),
            button -> send(new ServerboundSiloUseHeldDesignatorPayload(menu.containerId,
                menu.centre(), menu.siloId()))).bounds(x + 59, y + 92, 53, 20).build());
        launchButton = addRenderableWidget(Button.builder(Component.literal("LAUNCH"),
            button -> send(new ServerboundSiloLaunchPayload(menu.containerId,
                menu.centre(), menu.siloId()))).bounds(x, y + 115, 112, 20).build());
    }

    private EditBox field(final int x, final int y, final String hint) {
        EditBox box = new EditBox(font, x, y, 112, 18, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(20);
        addRenderableWidget(box);
        return box;
    }

    private void apply() {
        try {
            double x = Double.parseDouble(xField.getValue());
            double y = Double.parseDouble(yField.getValue());
            double z = Double.parseDouble(zField.getValue());
            send(new ServerboundSiloSetTargetPayload(menu.containerId,
                menu.centre(), menu.siloId(), x, y, z));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void send(final net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        MissileSiloBlockEntity silo = menu.silo();
        launchButton.active = silo != null && silo.siloState() == MissileSiloState.READY;
        boolean interceptor = silo != null && MissilePayloadItems.isInterceptor(silo.missileStack());
        xField.active = yField.active = zField.active = !interceptor;
        xField.visible = yField.visible = zField.visible = true;
        xField.setHint(Component.literal(interceptor ? "Not used" : "X"));
        yField.setHint(Component.literal(interceptor ? "Not used" : "Y"));
        zField.setHint(Component.literal(interceptor ? "Not used" : "Z"));
        applyButton.visible = clearButton.visible = heldButton.visible = launchButton.visible = true;
        applyButton.active = clearButton.active = heldButton.active = !interceptor;
        launchButton.setMessage(Component.literal(interceptor ? "INTERCEPT" : "LAUNCH"));
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics,
        final int mouseX, final int mouseY, final float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos,
            0, 0, imageWidth, imageHeight, 512, 256);
        drawSlotBackgrounds(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        MissileSiloBlockEntity silo = menu.silo();
        if (silo == null) return;

        String shortId = menu.siloId().toString().substring(0, 8);
        graphics.text(font, Component.literal("MISSILE SILO"), leftPos + 12, topPos + 8, 0xffffc45a);
        graphics.text(font, Component.literal(shortId), leftPos + 112, topPos + 8, 0xff9db0b8);
        graphics.text(font, Component.literal(silo.siloState().name()), leftPos + 174, topPos + 8,
            silo.siloState() == MissileSiloState.ERROR ? 0xffff5a4c : 0xff9ee0ac);

        String payload = MissilePayloadItems.payloadType(silo.missileStack())
            .map(type -> type.serializedName()).orElse("empty");
        graphics.text(font, Component.literal("PAYLOAD"), leftPos + 14, topPos + 30, 0xff8299a2);
        graphics.text(font, Component.literal(payload.toUpperCase(Locale.ROOT)),
            leftPos + 14, topPos + 80, 0xffe5edf0);
        graphics.text(font, Component.literal("Count  " + silo.missileStack().getCount() + " / 16"),
            leftPos + 14, topPos + 94, 0xffc6d4da);

        int statusX = leftPos + 110;
        graphics.text(font, Component.literal("SYSTEM STATUS"), statusX, topPos + 30, 0xff8299a2);
        status(graphics, statusX, topPos + 46, "State", silo.siloState().name());
        status(graphics, statusX, topPos + 60, "Left support", "Tier " + silo.leftGuidanceTier());
        status(graphics, statusX, topPos + 74, "Right support", "Tier " + silo.rightGuidanceTier());
        status(graphics, statusX, topPos + 88, "Effective", "Tier " + silo.installedGuidanceTier());
        boolean interceptor = MissilePayloadItems.isInterceptor(silo.missileStack());
        int error = (int)(interceptor
            ? com.andye.warmod.antiair.AntiAirGuidanceResolver.maximumMiss(silo.installedGuidanceTier())
            : MissileSiloGuidanceFrameStructure.maximumGuidanceError(silo.installedGuidanceTier()));
        status(graphics, statusX, topPos + 102, interceptor ? "Maximum miss" : "Max X/Z error", error == 0 ? "0" : "Â±" + error);
        status(graphics, statusX, topPos + 116, interceptor ? "Target mode" : "Readiness",
            interceptor ? "Automatic" : silo.siloState() == MissileSiloState.READY ? "READY" : "HOLD");

        double reload = silo.reloadTicksTotal() == 0 ? 0.0
            : 1.0 - (double)silo.reloadTicksRemaining() / silo.reloadTicksTotal();
        graphics.fill(leftPos + 14, topPos + 111, leftPos + 94, topPos + 119, 0xff26383f);
        graphics.fill(leftPos + 14, topPos + 111, leftPos + 14 + (int)(80 * reload),
            topPos + 119, 0xffffb43b);
        graphics.text(font, Component.literal("Reload " + (int)Math.round(reload * 100) + "%"),
            leftPos + 14, topPos + 122, 0xffa9bdc5);

        graphics.text(font, Component.literal(interceptor ? "AUTOMATIC INTERCEPTION" : "TARGET"), leftPos + 246, topPos + 30, 0xff8299a2);
        if (interceptor) {
            graphics.text(font, Component.literal("Defended radius  500"), leftPos + 246, topPos + 145, 0xffc5d5dc);
            graphics.text(font, Component.literal("Acquisition  500"), leftPos + 246, topPos + 158, 0xffc5d5dc);
            boolean mkOne = MissilePayloadItems.antiAirVariant(silo.missileStack()).orElseThrow().ballisticFallback();
            graphics.text(font, Component.literal("FAILURE SYSTEM"), leftPos + 246, topPos + 171, 0xff8299a2);
            graphics.text(font, Component.literal(mkOne ? "None" : "Self-destruct fallback"), leftPos + 246, topPos + 184, 0xffffc45a);
            graphics.text(font, Component.literal("MISS BEHAVIOUR"), leftPos + 246, topPos + 197, 0xff8299a2);
            graphics.text(font, Component.literal(mkOne ? "Uncontrolled return to ground" : "Safe aerial self-destruction"), leftPos + 246, topPos + 210, 0xffc5d5dc);
            graphics.text(font, Component.literal("Stored XYZ retained; not used"), leftPos + 246, topPos + 223, 0xff8299a2);
        }
        if (!silo.lastError().isBlank()) {
            graphics.textWithWordWrap(font, Component.literal(silo.lastError()),
                leftPos + 110, topPos + 132, 124, 0xffff7568);
        }
    }

    private void status(final GuiGraphicsExtractor graphics, final int x, final int y,
        final String label, final String value) {
        graphics.text(font, Component.literal(label), x, y, 0xff8ba0a8);
        graphics.text(font, Component.literal(value), x + 72, y, 0xffe2eaed);
    }

    private void drawSlotBackgrounds(final GuiGraphicsExtractor graphics,
        final int mouseX, final int mouseY) {
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xff6c777c);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xff0b1114);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x55ffffff);
            }
            if (slot.index == 0 && menu.silo() != null && !menu.silo().extractionAllowed()) {
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x884d2727);
            }
        }
    }

    private static String format(final double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}