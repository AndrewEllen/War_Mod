package com.andye.warmod.rocket.network;

import com.andye.warmod.item.RocketLauncherItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Networking boundary for the shoulder launcher's held-aim trigger. */
public final class RocketLauncherNetworking {
    private static boolean registered;

    private RocketLauncherNetworking() { }

    public static void register() {
        if (registered) return;
        PayloadTypeRegistry.serverboundPlay().register(
            ServerboundRocketLauncherTriggerPayload.TYPE,
            ServerboundRocketLauncherTriggerPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundRocketLauncherTriggerPayload.TYPE,
            (payload, context) -> RocketLauncherItem.fireHeld(context.player()));
        registered = true;
    }
}
