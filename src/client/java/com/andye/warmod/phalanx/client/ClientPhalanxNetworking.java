package com.andye.warmod.phalanx.client;

import com.andye.warmod.phalanx.network.ClientboundPhalanxImpactPayload;
import com.andye.warmod.phalanx.network.ClientboundPhalanxShotPayload;
import com.andye.warmod.phalanx.network.ClientboundPhalanxStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientPhalanxNetworking {
    private static boolean registered;
    private ClientPhalanxNetworking() { }
    public static void register() {
        if (registered) return;
        ClientPlayNetworking.registerGlobalReceiver(ClientboundPhalanxShotPayload.TYPE, (payload, context) -> {
            double time = context.client().level == null ? 0.0 : context.client().level.getGameTime();
            PhalanxTracerManager.shot(payload, (long)time);
            ClientPhalanxStateManager.INSTANCE.markShot(payload.turretId(), time);
        });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundPhalanxImpactPayload.TYPE, (payload, context) -> PhalanxTracerManager.impact(payload.shotId()));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundPhalanxStatePayload.TYPE, (payload, context) -> {
            double time = context.client().level == null ? 0.0 : context.client().level.getGameTime();
            ClientPhalanxStateManager.INSTANCE.update(payload, time);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { ClientPhalanxStateManager.INSTANCE.clear(); PhalanxTracerManager.clear(); });
        registered = true;
    }
}