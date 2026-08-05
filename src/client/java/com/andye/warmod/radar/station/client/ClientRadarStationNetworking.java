package com.andye.warmod.radar.station.client;

import com.andye.warmod.radar.client.gui.RadarScreen;
import com.andye.warmod.radar.client.gui.RadarScreenMode;
import com.andye.warmod.radar.station.RadarRedstoneMode;
import com.andye.warmod.radar.station.network.ClientboundCloseRadarStationPayload;
import com.andye.warmod.radar.station.network.ClientboundOpenRadarStationPayload;
import com.andye.warmod.radar.station.network.ClientboundRadarStationObservationPayload;
import com.andye.warmod.radar.station.network.ClientboundRadarStationStatePayload;
import com.andye.warmod.radar.station.network.ServerboundCloseRadarStationPayload;
import com.andye.warmod.radar.station.network.ServerboundConfigureRadarStationPayload;
import com.andye.warmod.radar.station.network.ServerboundSetRadarStationChunkLoadingPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class ClientRadarStationNetworking {
    private static boolean registered;

    private ClientRadarStationNetworking() {
    }

    public static void register() {
        if (registered) return;
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundOpenRadarStationPayload.TYPE,
            (payload, context) -> {
                ClientRadarStationState.INSTANCE.open(payload);
                Minecraft.getInstance().gui.setScreen(new RadarScreen(
                    payload.controlsEnabled()
                        ? RadarScreenMode.STATION
                        : RadarScreenMode.STATION_MAP
                ));
            }
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundRadarStationObservationPayload.TYPE,
            (payload, context) -> ClientRadarStationState.INSTANCE.observe(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundRadarStationStatePayload.TYPE,
            (payload, context) -> ClientRadarStationState.INSTANCE.state(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ClientboundCloseRadarStationPayload.TYPE,
            (payload, context) -> {
                if (ClientRadarStationState.INSTANCE.radarId() != null
                    && ClientRadarStationState.INSTANCE.radarId().equals(payload.radarId())) {
                    ClientRadarStationState.INSTANCE.clear();
                    if (Minecraft.getInstance().gui.screen() instanceof RadarScreen screen
                        && screen.stationBacked()) {
                        Minecraft.getInstance().gui.setScreen(null);
                    }
                }
            }
        );
        ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> ClientRadarStationState.INSTANCE.clear()
        );
        registered = true;
    }

    public static void configure(
        final double warningRadius,
        final double fireRadius,
        final RadarRedstoneMode mode
    ) {
        ClientRadarStationState state = ClientRadarStationState.INSTANCE;
        if (state.open()
            && state.controlsEnabled()
            && ClientPlayNetworking.canSend(ServerboundConfigureRadarStationPayload.TYPE)) {
            ClientPlayNetworking.send(new ServerboundConfigureRadarStationPayload(
                state.radarId(),
                state.centre(),
                warningRadius,
                fireRadius,
                mode
            ));
        }
    }

    public static void setDynamicChunkLoading(final boolean enabled) {
        ClientRadarStationState state = ClientRadarStationState.INSTANCE;
        if (state.open()
            && state.controlsEnabled()
            && ClientPlayNetworking.canSend(
                ServerboundSetRadarStationChunkLoadingPayload.TYPE
            )) {
            ClientPlayNetworking.send(new ServerboundSetRadarStationChunkLoadingPayload(
                state.radarId(),
                state.centre(),
                enabled
            ));
        }
    }

    public static void close() {
        ClientRadarStationState state = ClientRadarStationState.INSTANCE;
        if (state.open()
            && ClientPlayNetworking.canSend(ServerboundCloseRadarStationPayload.TYPE)) {
            ClientPlayNetworking.send(new ServerboundCloseRadarStationPayload(
                state.radarId()
            ));
        }
        state.clear();
    }
}
