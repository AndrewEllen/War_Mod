package com.andye.warmod.warhead.client.curtain;

import com.andye.warmod.warhead.curtain.network.ClientboundNuclearCurtainPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Separate receiver/lifecycle path for the destruction curtain. */
public final class ClientNuclearCurtainNetworking {
    private static boolean registered;
    private ClientNuclearCurtainNetworking() { }

    public static void register() {
        if (registered) return;
        ClientPlayNetworking.registerGlobalReceiver(ClientboundNuclearCurtainPayload.TYPE,
            (payload, context) -> ClientNuclearCurtainManager.INSTANCE.accept(payload));
        ClientTickEvents.END_CLIENT_TICK.register(ClientNuclearCurtainManager.INSTANCE::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) ->
            ClientNuclearCurtainManager.INSTANCE.clear());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) ->
            ClientNuclearCurtainManager.INSTANCE.clear());
        registered = true;
    }
}
