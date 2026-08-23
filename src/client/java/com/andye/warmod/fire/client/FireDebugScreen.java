package com.andye.warmod.fire.client;

import com.andye.warmod.fire.network.ServerboundFireDebugConfigPayload;
import com.andye.warmod.item.component.FireDebugConfig;
import com.andye.warmod.client.gui.WarModUiText;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

/** In-world two-slider configuration panel for the custom fire tool. */
public final class FireDebugScreen extends Screen {
    private static final int PANEL_WIDTH = 316;
    private static final int PANEL_HEIGHT = 226;
    private final InteractionHand hand;
    private FireDebugConfig draft;
    private int left;
    private int top;

    public FireDebugScreen(final InteractionHand hand, final FireDebugConfig config) {
        super(Component.literal("Custom Fire Debug Stick"));
        this.hand = hand;
        this.draft = config == null ? FireDebugConfig.DEFAULT : config;
    }

    @Override protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        int x = left + 22;
        int controlWidth = PANEL_WIDTH - 44;
        AbstractSliderButton intensitySlider = new AbstractSliderButton(x, top + 58, controlWidth, 22,
            Component.empty(), intensitySliderValue()) {
            @Override protected void updateMessage() {
                setMessage(Component.literal("Intensity: "
                    + Math.round(draft.intensity() * 100.0F) + "%"));
            }
            @Override protected void applyValue() {
                float intensity = FireDebugConfig.MIN_INTENSITY
                    + (float) value * (FireDebugConfig.MAX_INTENSITY
                        - FireDebugConfig.MIN_INTENSITY);
                draft = draft.withIntensity(Math.round(intensity * 100.0F) / 100.0F);
            }
        };
        intensitySlider.setMessage(Component.literal("Intensity: "
            + Math.round(draft.intensity() * 100.0F) + "%"));
        addRenderableWidget(intensitySlider);
        AbstractSliderButton sizeSlider = new AbstractSliderButton(x, top + 112, controlWidth, 22,
            Component.empty(), sizeSliderValue()) {
            @Override protected void updateMessage() {
                setMessage(Component.literal("Placement size: " + draft.size()));
            }
            @Override protected void applyValue() {
                int size = FireDebugConfig.MIN_SIZE + (int) Math.round(value
                    * (FireDebugConfig.MAX_SIZE - FireDebugConfig.MIN_SIZE));
                draft = draft.withSize(size);
            }
        };
        sizeSlider.setMessage(Component.literal("Placement size: " + draft.size()));
        addRenderableWidget(sizeSlider);
        addRenderableWidget(Button.builder(Component.literal("APPLY"), button -> apply())
            .bounds(x, top + 178, 174, 26).build());
        addRenderableWidget(Button.builder(Component.literal("CANCEL"), button -> onClose())
            .bounds(x + 180, top + 178, controlWidth - 180, 26).build());
    }

    private double intensitySliderValue() {
        return Mth.clamp((draft.intensity() - FireDebugConfig.MIN_INTENSITY)
            / (FireDebugConfig.MAX_INTENSITY - FireDebugConfig.MIN_INTENSITY), 0.0, 1.0);
    }

    private double sizeSliderValue() {
        return Mth.clamp((draft.size() - FireDebugConfig.MIN_SIZE)
            / (double) (FireDebugConfig.MAX_SIZE - FireDebugConfig.MIN_SIZE), 0.0, 1.0);
    }

    private void apply() {
        if (ClientPlayNetworking.canSend(ServerboundFireDebugConfigPayload.TYPE))
            ClientPlayNetworking.send(new ServerboundFireDebugConfigPayload(hand, draft));
        onClose();
    }

    @Override public void extractRenderState(final GuiGraphicsExtractor graphics,
        final int mouseX, final int mouseY, final float partialTick) {
        WarModUiText.frame(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        WarModUiText.section(graphics, left + 12, top + 40, PANEL_WIDTH - 24, 110);
        WarModUiText.section(graphics, left + 12, top + 154, PANEL_WIDTH - 24, 16);
        graphics.text(font, title, left + 14, top + 9, WarModUiText.ACCENT);
        graphics.text(font, Component.literal("HEAT RELEASE / GROWTH CEILING"),
            left + 22, top + 47, WarModUiText.TEXT_MUTED);
        graphics.text(font, Component.literal("INITIAL SURFACE RADIUS"),
            left + 22, top + 101, WarModUiText.TEXT_MUTED);
        String help = draft.size() == 1
            ? "Size 1 attaches one fire patch at the exact hit point"
            : "Larger sizes seed exposed fuel surfaces around the hit point";
        graphics.text(font, Component.literal(help),
            left + (PANEL_WIDTH - font.width(help)) / 2, top + 157, WarModUiText.TEXT);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
}
