package com.andye.warmod.radar.client;

import com.andye.warmod.radar.client.gui.RadarScreen;
import com.andye.warmod.radar.network.ClientboundCloseRadarPayload;
import com.andye.warmod.radar.network.ClientboundOpenRadarPayload;
import com.andye.warmod.radar.network.ClientboundRadarImpactPayload;
import com.andye.warmod.radar.network.ClientboundRadarInterceptionPayload;
import com.andye.warmod.radar.network.ClientboundRadarSnapshotPayload;
import com.andye.warmod.radar.network.ClientboundRadarTrackRemovePayload;
import com.andye.warmod.radar.network.ClientboundRadarTrackUpsertPayload;
import com.andye.warmod.radar.network.ServerboundCloseRadarPayload;
import com.andye.warmod.radar.network.ServerboundOpenRadarPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class ClientRadarNetworking {
    private static boolean registered;

    private ClientRadarNetworking() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundOpenRadarPayload.TYPE,
            (payload, context) -> {
                ClientRadarState.INSTANCE.open(payload);
                Minecraft.getInstance().gui.setScreen(new RadarScreen());
            }
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundCloseRadarPayload.TYPE,
            (payload, context) -> {
                ClientRadarState.INSTANCE.clear();

                if (Minecraft.getInstance().gui.screen() instanceof RadarScreen) {
                    Minecraft.getInstance().gui.setScreen(null);
                }
            }
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundRadarSnapshotPayload.TYPE,
            (payload, context) -> ClientRadarState.INSTANCE.snapshot(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundRadarTrackUpsertPayload.TYPE,
            (payload, context) -> ClientRadarState.INSTANCE.upsert(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundRadarTrackRemovePayload.TYPE,
            (payload, context) -> ClientRadarState.INSTANCE.remove(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundRadarImpactPayload.TYPE,
            (payload, context) -> ClientRadarState.INSTANCE.impact(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundRadarInterceptionPayload.TYPE,
            (payload, context) -> ClientRadarState.INSTANCE.interception(payload)
        );

        /*
         * The cache already knew how to expire impact outlines, but nothing
         * invoked it. Run cleanup independently of packet arrival so a quiet
         * radar view cannot retain old explosions forever.
         */
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) {
                ClientRadarState.INSTANCE.pruneImpacts(
                    ClientRadarState.INSTANCE.clock().now(0.0F)
                );
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> ClientRadarState.INSTANCE.clear()
        );
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(
            (client, level) -> ClientRadarState.INSTANCE.clear()
        );

        registered = true;
    }

    public static void requestOpen() {
        if (ClientPlayNetworking.canSend(ServerboundOpenRadarPayload.TYPE)) {
            ClientPlayNetworking.send(new ServerboundOpenRadarPayload());
        }
    }

    public static void close() {
        if (ClientPlayNetworking.canSend(ServerboundCloseRadarPayload.TYPE)) {
            ClientPlayNetworking.send(new ServerboundCloseRadarPayload());
        }

        ClientRadarState.INSTANCE.clear();
    }
}
