package com.andye.warmod.rocket.client;

import com.andye.warmod.item.RocketLauncherItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.andye.warmod.rocket.network.ServerboundRocketLauncherTriggerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Stops attack from mining while a shouldered launcher is being aimed. */
public final class ClientRocketLauncherInput {
    private static boolean attackDown;
    private static boolean registered;

    private ClientRocketLauncherInput() { }

    public static void register() {
        if (registered) return;
        ClientTickEvents.END_CLIENT_TICK.register(ClientRocketLauncherInput::tick);
        registered = true;
    }

    public static boolean trigger() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!aimingLauncher(minecraft)) {
            attackDown = false;
            return false;
        }
        if (!attackDown) {
            ServerboundRocketLauncherTriggerPayload payload =
                new ServerboundRocketLauncherTriggerPayload();
            if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
            attackDown = true;
        }
        if (minecraft.gameMode != null) minecraft.gameMode.stopDestroyBlock();
        return true;
    }

    private static void tick(final Minecraft minecraft) {
        if (!aimingLauncher(minecraft) || !minecraft.options.keyAttack.isDown()) {
            attackDown = false;
            return;
        }
        // Vanilla can consume attack while RMB use is active, so polling closes the
        // gap where Minecraft.startAttack never reaches the mixin.
        trigger();
    }

    private static boolean aimingLauncher(final Minecraft minecraft) {
        return minecraft.player != null
            && minecraft.player.getMainHandItem().getItem() instanceof RocketLauncherItem
            && minecraft.player.isUsingItem()
            && minecraft.player.getUseItem() == minecraft.player.getMainHandItem();
    }
}
