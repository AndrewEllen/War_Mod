package com.andye.warmod.testtool.client;

import com.andye.warmod.item.component.MasterExplosiveConfig;
import com.andye.warmod.item.component.MasterExplosiveDelivery;
import com.andye.warmod.client.gui.WarModUiText;
import com.andye.warmod.testtool.network.ServerboundMasterExplosiveConfigPayload;
import com.andye.warmod.warhead.StrategicExplosionProfile;
import com.andye.warmod.warhead.StrategicExplosionProfiles;
import com.andye.warmod.warhead.WarheadYield;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** Compact in-world configuration menu for the all-yield test launcher. */
public final class MasterExplosiveScreen extends Screen {
	private static final int PANEL_WIDTH = 292;
	private static final int PANEL_HEIGHT = 262;
	private final InteractionHand hand;
	private MasterExplosiveConfig config;
	private Button deliveryButton;
	private Button clusterButton;
	private Button fireButton;
	private Button previousYieldButton;
	private Button nextYieldButton;
	private Button applyButton;
	private int left;
	private int top;

	public MasterExplosiveScreen(final InteractionHand hand, final MasterExplosiveConfig config) {
		super(Component.literal("Master Explosive Test Stick"));
		this.hand = hand;
		this.config = config == null ? MasterExplosiveConfig.DEFAULT : config;
	}

	@Override
	protected void init() {
		left = (width - PANEL_WIDTH) / 2;
		top = (height - PANEL_HEIGHT) / 2;
		int x = left + 20;
		int buttonWidth = PANEL_WIDTH - 40;
		deliveryButton = addRenderableWidget(Button.builder(
			Component.literal(""),
			button -> {
				config = config.withDelivery(config.delivery().toggle());
				refreshLabels();
			}
		).bounds(x, top + 48, buttonWidth, 22).build());
		clusterButton = addRenderableWidget(Button.builder(
			Component.literal(""),
			button -> {
				config = config.withCluster(!config.cluster());
				refreshLabels();
			}
		).bounds(x, top + 76, buttonWidth, 22).build());
		fireButton = addRenderableWidget(Button.builder(
			Component.literal("Aftermath fire: "
				+ (config.customFire() ? "Custom particle fire" : "Vanilla fire blocks")),
			button -> {
				config = config.withCustomFire(!config.customFire());
				button.setMessage(Component.literal("Aftermath fire: "
					+ (config.customFire() ? "Custom particle fire" : "Vanilla fire blocks")));
				refreshLabels();
			}
		).bounds(x, top + 104, buttonWidth, 22).build());
		previousYieldButton = addRenderableWidget(Button.builder(
			Component.literal("<"),
			button -> {
				config = config.withYield(config.yield().previous());
				refreshLabels();
			}
		).bounds(x, top + 146, 32, 22).build());
		nextYieldButton = addRenderableWidget(Button.builder(
			Component.literal(">"),
			button -> {
				config = config.withYield(config.yield().next());
				refreshLabels();
			}
		).bounds(left + PANEL_WIDTH - 52, top + 146, 32, 22).build());
		applyButton = addRenderableWidget(Button.builder(
			Component.literal("APPLY AND CLOSE"),
			button -> applyAndClose()
		).bounds(x, top + 220, buttonWidth, 24).build());
		refreshLabels();
	}

	private void refreshLabels() {
		if (deliveryButton == null) return;
		deliveryButton.setMessage(Component.literal("Delivery: " + config.delivery().displayName()));
		clusterButton.setMessage(Component.literal("Payload: " + (config.cluster() ? "Cluster ×4" : "Single")));
	}

	private void applyAndClose() {
		if (ClientPlayNetworking.canSend(ServerboundMasterExplosiveConfigPayload.TYPE)) {
			ClientPlayNetworking.send(new ServerboundMasterExplosiveConfigPayload(hand, config));
		}
		onClose();
	}

	@Override
	public void extractRenderState(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick
	) {
		WarModUiText.frame(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
		WarModUiText.section(graphics, left + 12, top + 38, PANEL_WIDTH - 24, 140);
		WarModUiText.section(graphics, left + 12, top + 184, PANEL_WIDTH - 24, 28);
		WarModUiText.text(graphics, font, title, left + 14, top + 9, WarModUiText.ACCENT);
		WarModUiText.text(graphics, font, Component.literal("YIELD"), left + 20, top + 136, WarModUiText.TEXT_MUTED);
		String yieldName = config.yield().displayName();
		WarModUiText.text(graphics, font, Component.literal(yieldName),
			left + (PANEL_WIDTH - font.width(yieldName)) / 2, top + 153, WarModUiText.ACCENT);
		StrategicExplosionProfile profile = StrategicExplosionProfiles.get(config.yield());
		String dimensions = String.format(
			java.util.Locale.ROOT,
			"Crater %.0f wide | %.0f deep | Entity range %.0f",
			profile.horizontalRadius() * 2.0,
			profile.downwardRadius(),
			profile.entityBlastRadius() * 2.0F
		);
		WarModUiText.text(graphics, font, Component.literal(dimensions),
			left + (PANEL_WIDTH - font.width(dimensions)) / 2, top + 191, WarModUiText.TEXT);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
