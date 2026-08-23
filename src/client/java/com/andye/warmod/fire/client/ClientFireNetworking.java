package com.andye.warmod.fire.client;

import com.andye.warmod.fire.network.ClientboundFireStatePayload;
import com.andye.warmod.fire.network.ClientboundFireWindImpulsePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientFireNetworking {
    private static boolean registered;

    private ClientFireNetworking() { }

    public static void register() {
        if (registered) return;
        ClientPlayNetworking.registerGlobalReceiver(ClientboundFireStatePayload.TYPE,
            (payload, context) -> ClientFireVisualManager.INSTANCE.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundFireWindImpulsePayload.TYPE,
            (payload, context) -> ClientFireVisualManager.INSTANCE.acceptImpulse(payload));
        ClientTickEvents.END_CLIENT_TICK.register(ClientFireVisualManager.INSTANCE::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) ->
            ClientFireVisualManager.INSTANCE.clear());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) ->
            ClientFireVisualManager.INSTANCE.clear());
        registered = true;
    }
}
