package com.andye.warmod.firearm.client;

import com.andye.warmod.firearm.network.ClientboundFirearmImpactPayload;
import com.andye.warmod.firearm.network.ClientboundFirearmShotPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientFirearmNetworking {
    private static boolean registered;
    private ClientFirearmNetworking() { }
    public static void register() {
        if (registered) return;
        ClientPlayNetworking.registerGlobalReceiver(ClientboundFirearmShotPayload.TYPE,
            (payload, context) -> FirearmTracerManager.shot(payload,
                context.client().level == null ? 0.0 : context.client().level.getGameTime()));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundFirearmImpactPayload.TYPE,
            (payload, context) -> FirearmTracerManager.impact(payload.shotId(),
                payload.position(), context.client().level == null ? 0.0
                    : context.client().level.getGameTime()));
        ClientTickEvents.END_CLIENT_TICK.register(ClientFirearmInput::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            FirearmTracerManager.clear());
        registered = true;
    }
}
