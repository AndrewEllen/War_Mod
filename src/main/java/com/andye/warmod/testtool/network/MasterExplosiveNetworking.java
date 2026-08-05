package com.andye.warmod.testtool.network;

import com.andye.warmod.item.MasterExplosiveStickItem;
import com.andye.warmod.item.component.MasterExplosiveConfig;
import com.andye.warmod.item.component.ModDataComponents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class MasterExplosiveNetworking {
	private static boolean registered;

	private MasterExplosiveNetworking() {
	}

	public static void register() {
		if (registered) return;
		PayloadTypeRegistry.clientboundPlay().register(
			ClientboundOpenMasterExplosiveScreenPayload.TYPE,
			ClientboundOpenMasterExplosiveScreenPayload.STREAM_CODEC
		);
		PayloadTypeRegistry.serverboundPlay().register(
			ServerboundMasterExplosiveConfigPayload.TYPE,
			ServerboundMasterExplosiveConfigPayload.STREAM_CODEC
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ServerboundMasterExplosiveConfigPayload.TYPE,
			(payload, context) -> apply(context.player(), payload)
		);
		registered = true;
	}

	public static void open(final ServerPlayer player, final InteractionHand hand, final ItemStack stack) {
		MasterExplosiveConfig config = stack.getOrDefault(
			ModDataComponents.MASTER_EXPLOSIVE_CONFIG,
			MasterExplosiveConfig.DEFAULT
		);
		ServerPlayNetworking.send(player, new ClientboundOpenMasterExplosiveScreenPayload(hand, config));
	}

	private static void apply(
		final ServerPlayer player,
		final ServerboundMasterExplosiveConfigPayload payload
	) {
		ItemStack stack = player.getItemInHand(payload.hand());
		if (!(stack.getItem() instanceof MasterExplosiveStickItem)) return;
		stack.set(ModDataComponents.MASTER_EXPLOSIVE_CONFIG, payload.config());
		player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
			"Master Explosive: " + payload.config().summary()
		));
	}
}
