package com.andye.warmod.radar.display.client;

import com.andye.warmod.radar.display.network.ClientboundRadarDisplayClearPayload;
import com.andye.warmod.radar.display.network.ClientboundRadarDisplayStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientRadarDisplayNetworking {
    private static boolean registered;

    private ClientRadarDisplayNetworking() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundRadarDisplayStatePayload.TYPE,
            (payload, context) ->
                ClientRadarDisplayState.INSTANCE.update(payload)
        );

        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundRadarDisplayClearPayload.TYPE,
            (payload, context) ->
                ClientRadarDisplayState.INSTANCE.clear(payload)
        );

        ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) ->
                ClientRadarDisplayState.INSTANCE.clearAll()
        );

        registered = true;
    }
}
