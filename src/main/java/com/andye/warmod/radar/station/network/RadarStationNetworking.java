package com.andye.warmod.radar.station.network;

import com.andye.warmod.block.RadarStationStructure;
import com.andye.warmod.block.entity.RadarStationBlockEntity;
import com.andye.warmod.radar.station.RadarRedstoneMode;
import com.andye.warmod.radar.station.RadarStationConstants;
import com.andye.warmod.radar.station.RadarStationObservation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class RadarStationNetworking {
    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();
    private static boolean registered;
    private RadarStationNetworking() { }

    public static void register() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(ClientboundOpenRadarStationPayload.TYPE, ClientboundOpenRadarStationPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundRadarStationObservationPayload.TYPE, ClientboundRadarStationObservationPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundRadarStationStatePayload.TYPE, ClientboundRadarStationStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundCloseRadarStationPayload.TYPE, ClientboundCloseRadarStationPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundCloseRadarStationPayload.TYPE, ServerboundCloseRadarStationPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundConfigureRadarStationPayload.TYPE, ServerboundConfigureRadarStationPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ServerboundCloseRadarStationPayload.TYPE,
            (payload, context) -> VIEWERS.remove(context.player().getUUID()));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundConfigureRadarStationPayload.TYPE,
            (payload, context) -> configure(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VIEWERS.remove(handler.getPlayer().getUUID()));
        registered = true;
    }

    public static void open(ServerPlayer player, RadarStationBlockEntity station) {
        if (player.level() != station.getLevel() || player.distanceToSqr(Vec3.atCenterOf(station.getBlockPos())) > 64.0) return;
        VIEWERS.put(player.getUUID(), new Viewer(player, station.radarId(), station.getBlockPos()));
        ServerPlayNetworking.send(player, new ClientboundOpenRadarStationPayload(station.radarId(), station.getBlockPos(),
            player.level().dimension().identifier(), player.level().getGameTime(), RadarStationConstants.SWEEP_PERIOD_TICKS,
            RadarStationConstants.DETECTION_RANGE_BLOCKS, station.warningRadius(), station.fireRadius(),
            station.redstoneSignal(), station.redstoneMode(), station.primaryThreatId(), station.primaryThreatDistance(),
            station.phaseOffset()));
        sendState(station);
        if (!station.observations().isEmpty()) sendObservations(station, List.copyOf(station.observations()));
    }

    public static void sendObservations(RadarStationBlockEntity station, List<RadarStationObservation> observations) {
        send(station, new ClientboundRadarStationObservationPayload(station.radarId(), observations));
    }

    public static void sendState(RadarStationBlockEntity station) {
        if (station.getLevel() instanceof ServerLevel level) send(station, new ClientboundRadarStationStatePayload(station.radarId(),
            station.warningRadius(), station.fireRadius(), station.redstoneSignal(), station.redstoneMode(),
            station.primaryThreatId(), station.primaryThreatDistance(), station.warningActive(), station.observations().size(),
            station.threatCount(), level.getGameTime()));
    }

    private static void send(RadarStationBlockEntity station,
        net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        VIEWERS.values().removeIf(viewer -> {
            if (!viewer.radarId.equals(station.radarId())) return false;
            if (viewer.player.level() != station.getLevel() || !station.getBlockPos().equals(viewer.centre)) {
                ServerPlayNetworking.send(viewer.player, new ClientboundCloseRadarStationPayload(station.radarId()));
                return true;
            }
            ServerPlayNetworking.send(viewer.player, payload);
            return false;
        });
    }

    public static void closeStation(UUID id) {
        VIEWERS.values().removeIf(viewer -> {
            if (!viewer.radarId.equals(id)) return false;
            ServerPlayNetworking.send(viewer.player, new ClientboundCloseRadarStationPayload(id));
            return true;
        });
    }

    private static void configure(ServerPlayer player, ServerboundConfigureRadarStationPayload payload) {
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (viewer == null || !viewer.radarId.equals(payload.radarId()) || !viewer.centre.equals(payload.centre())
            || !Double.isFinite(payload.warningRadius()) || !Double.isFinite(payload.fireRadius())
            || !RadarRedstoneMode.isRegistered(payload.redstoneMode())
            || player.distanceToSqr(Vec3.atCenterOf(payload.centre())) > 64.0) return;
        if (!(player.level().getBlockEntity(payload.centre()) instanceof RadarStationBlockEntity station)
            || !station.radarId().equals(payload.radarId())
            || !RadarStationStructure.complete(player.level(), payload.centre(), station.facing())) return;
        station.configure(payload.warningRadius(), payload.fireRadius(), payload.redstoneMode());
        sendState(station);
    }

    private record Viewer(ServerPlayer player, UUID radarId, net.minecraft.core.BlockPos centre) { }
}