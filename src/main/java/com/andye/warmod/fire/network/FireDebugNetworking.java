package com.andye.warmod.fire.network;

import com.andye.warmod.item.FireDebugStickItem;
import com.andye.warmod.item.component.FireDebugConfig;
import com.andye.warmod.item.component.ModDataComponents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class FireDebugNetworking {
    private static boolean registered;

    private FireDebugNetworking() { }

    public static void register() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(
            ClientboundOpenFireDebugScreenPayload.TYPE,
            ClientboundOpenFireDebugScreenPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
            ServerboundFireDebugConfigPayload.TYPE,
            ServerboundFireDebugConfigPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundFireDebugConfigPayload.TYPE,
            (payload, context) -> apply(context.player(), payload));
        registered = true;
    }

    public static void open(final ServerPlayer player, final InteractionHand hand,
        final ItemStack stack) {
        FireDebugConfig config = FireDebugStickItem.config(stack);
        ServerPlayNetworking.send(player,
            new ClientboundOpenFireDebugScreenPayload(hand, config));
    }

    private static void apply(final ServerPlayer player,
        final ServerboundFireDebugConfigPayload payload) {
        ItemStack stack = player.getItemInHand(payload.hand());
        if (!(stack.getItem() instanceof FireDebugStickItem)) return;
        FireDebugConfig safe = new FireDebugConfig(payload.config().intensity(),
            payload.config().size());
        stack.set(ModDataComponents.FIRE_DEBUG_CONFIG, safe);
        player.sendOverlayMessage(Component.literal("Custom Fire: " + safe.summary()));
    }
}
