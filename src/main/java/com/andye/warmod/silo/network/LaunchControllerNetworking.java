package com.andye.warmod.silo.network;

import com.andye.warmod.menu.LaunchControllerMenu;
import com.andye.warmod.silo.LaunchControllerLaunchService;
import com.andye.warmod.silo.MissileSiloLaunchTrigger;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LaunchControllerNetworking {
    private static boolean registered;

    private LaunchControllerNetworking() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        PayloadTypeRegistry.serverboundPlay().register(
            ServerboundLaunchControllerLaunchPayload.TYPE,
            ServerboundLaunchControllerLaunchPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            ServerboundLaunchControllerRemoveSiloPayload.TYPE,
            ServerboundLaunchControllerRemoveSiloPayload.STREAM_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
            ServerboundLaunchControllerLaunchPayload.TYPE,
            (payload, context) -> launch(context.player(), payload)
        );
        ServerPlayNetworking.registerGlobalReceiver(
            ServerboundLaunchControllerRemoveSiloPayload.TYPE,
            (payload, context) -> remove(context.player(), payload)
        );
        registered = true;
    }

    private static void launch(
        final ServerPlayer player,
        final ServerboundLaunchControllerLaunchPayload payload
    ) {
        LaunchControllerMenu menu = menu(
            player,
            payload.menuId(),
            payload.centre(),
            payload.controllerId()
        );
        if (menu == null) {
            return;
        }
        var result = LaunchControllerLaunchService.requestLaunches(
            player.level(),
            menu.controller(),
            MissileSiloLaunchTrigger.CONTROLLER_UI,
            player.getUUID(),
            player.getGameProfile().name(),
            null
        );
        player.sendSystemMessage(Component.literal(result.summary()));
    }

    private static void remove(
        final ServerPlayer player,
        final ServerboundLaunchControllerRemoveSiloPayload payload
    ) {
        LaunchControllerMenu menu = menu(
            player,
            payload.menuId(),
            payload.centre(),
            payload.controllerId()
        );
        if (menu == null) {
            return;
        }
        if (!menu.controller().removeLink(payload.siloId())) {
            player.sendSystemMessage(Component.literal("Silo link was not found"));
        }
    }

    private static LaunchControllerMenu menu(
        final ServerPlayer player,
        final int menuId,
        final BlockPos centre,
        final UUID controllerId
    ) {
        if (!(player.containerMenu instanceof LaunchControllerMenu menu)
            || menu.containerId != menuId
            || !menu.centre().equals(centre)
            || !menu.controllerId().equals(controllerId)
            || menu.controller() == null
            || !menu.stillValid(player)) {
            return null;
        }
        return menu;
    }
}
