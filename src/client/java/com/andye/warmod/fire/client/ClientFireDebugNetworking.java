package com.andye.warmod.fire.client;

import com.andye.warmod.fire.network.ClientboundOpenFireDebugScreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class ClientFireDebugNetworking {
    private static boolean registered;
    private ClientFireDebugNetworking() { }

    public static void register() {
        if (registered) return;
        ClientPlayNetworking.registerGlobalReceiver(ClientboundOpenFireDebugScreenPayload.TYPE,
            (payload, context) -> Minecraft.getInstance().gui.setScreen(
                new FireDebugScreen(payload.hand(), payload.config())));
        registered = true;
    }
}
