package com.andye.warmod.warhead.client.obscuration;

import com.andye.warmod.warhead.obscuration.network.ClientboundNuclearTerrainObscurationPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Separate receiver/lifecycle path for the destruction curtain. */
public final class ClientNuclearTerrainObscurationNetworking {
    private static boolean registered;
    private ClientNuclearTerrainObscurationNetworking() { }

    public static void register() {
        if (registered) return;
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundNuclearTerrainObscurationPayload.TYPE,
            (payload, context) ->
                ClientNuclearTerrainObscurationManager.INSTANCE.accept(payload));
        ClientTickEvents.END_CLIENT_TICK.register(
            ClientNuclearTerrainObscurationManager.INSTANCE::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) ->
            ClientNuclearTerrainObscurationManager.INSTANCE.clear());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) ->
            ClientNuclearTerrainObscurationManager.INSTANCE.clear());
        registered = true;
    }
}
