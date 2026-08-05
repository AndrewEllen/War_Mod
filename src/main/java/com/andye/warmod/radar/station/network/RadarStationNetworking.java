package com.andye.warmod.radar.station.network;

import com.andye.warmod.block.RadarStationStructure;
import com.andye.warmod.block.entity.RadarStationBlockEntity;
import com.andye.warmod.item.component.LinkedRadarStation;
import com.andye.warmod.radar.station.RadarRedstoneMode;
import com.andye.warmod.radar.station.RadarStationChunkTicketManager;
import com.andye.warmod.radar.station.RadarStationConstants;
import com.andye.warmod.radar.station.RadarStationObservation;
import com.andye.warmod.radar.station.RadarStationSavedData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class RadarStationNetworking {
    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();
    private static boolean registered;

    private RadarStationNetworking() {
    }

    public static void register() {
        if (registered) return;
        PayloadTypeRegistry.clientboundPlay().register(
            ClientboundOpenRadarStationPayload.TYPE,
            ClientboundOpenRadarStationPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            ClientboundRadarStationObservationPayload.TYPE,
            ClientboundRadarStationObservationPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            ClientboundRadarStationStatePayload.TYPE,
            ClientboundRadarStationStatePayload.STREAM_CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
            ClientboundCloseRadarStationPayload.TYPE,
            ClientboundCloseRadarStationPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            ServerboundCloseRadarStationPayload.TYPE,
            ServerboundCloseRadarStationPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            ServerboundConfigureRadarStationPayload.TYPE,
            ServerboundConfigureRadarStationPayload.STREAM_CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
            ServerboundSetRadarStationChunkLoadingPayload.TYPE,
            ServerboundSetRadarStationChunkLoadingPayload.STREAM_CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
            ServerboundCloseRadarStationPayload.TYPE,
            (payload, context) -> removeViewer(context.player().getUUID())
        );
        ServerPlayNetworking.registerGlobalReceiver(
            ServerboundConfigureRadarStationPayload.TYPE,
            (payload, context) -> configure(context.player(), payload)
        );
        ServerPlayNetworking.registerGlobalReceiver(
            ServerboundSetRadarStationChunkLoadingPayload.TYPE,
            (payload, context) -> setChunkLoading(context.player(), payload)
        );
        ServerPlayConnectionEvents.DISCONNECT.register(
            (handler, server) -> removeViewer(handler.getPlayer().getUUID())
        );
        registered = true;
    }

    /** Opens the nearby configuration screen. */
    public static void open(
        final ServerPlayer player,
        final RadarStationBlockEntity station
    ) {
        if (player.level() != station.getLevel()
            || player.distanceToSqr(Vec3.atCenterOf(station.getBlockPos())) > 64.0) {
            return;
        }
        openInternal(player, station, true, false);
    }

    /** Opens a station-scoped full-screen map from anywhere in the same dimension. */
    public static boolean openMap(
        final ServerPlayer player,
        final LinkedRadarStation link
    ) {
        if (link == null || !player.level().dimension().equals(link.dimension())) {
            player.sendSystemMessage(Component.literal(
                "Linked Radar Station is in another dimension"
            ));
            return false;
        }

        ServerLevel level = player.level();
        var record = RadarStationSavedData.get(level).find(link.radarId()).orElse(null);
        if (record == null || !record.centre().equals(link.centre())) {
            player.sendSystemMessage(Component.literal("Linked Radar Station no longer exists"));
            return false;
        }

        RadarStationChunkTicketManager.acquireViewer(
            level,
            link.radarId(),
            link.centre()
        );

        /* The ticket keeps the full 5x5 area simulation-ticking. Load the
         * station's immediate 3x3 chunk neighbourhood synchronously so a
         * structure crossing a chunk boundary can be validated on this click;
         * the outer ticketed chunks may finish loading normally. */
        int centreChunkX = link.centre().getX() >> 4;
        int centreChunkZ = link.centre().getZ() >> 4;
        for (int chunkX = centreChunkX - 1; chunkX <= centreChunkX + 1; chunkX++) {
            for (int chunkZ = centreChunkZ - 1; chunkZ <= centreChunkZ + 1; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }

        if (!(level.getBlockEntity(link.centre()) instanceof RadarStationBlockEntity station)
            || !station.radarId().equals(link.radarId())
            || !RadarStationStructure.complete(
                level,
                station.getBlockPos(),
                station.facing()
            )) {
            RadarStationChunkTicketManager.releaseViewer(level, link.radarId());
            player.sendSystemMessage(Component.literal("Linked Radar Station is unavailable"));
            return false;
        }

        openInternal(player, station, false, true);
        return true;
    }

    private static void openInternal(
        final ServerPlayer player,
        final RadarStationBlockEntity station,
        final boolean controlsEnabled,
        final boolean ticketHeld
    ) {
        removeViewer(player.getUUID());
        ServerLevel level = (ServerLevel)station.getLevel();
        VIEWERS.put(player.getUUID(), new Viewer(
            player,
            level,
            station.radarId(),
            station.getBlockPos(),
            controlsEnabled,
            ticketHeld
        ));
        ServerPlayNetworking.send(player, new ClientboundOpenRadarStationPayload(
            station.radarId(),
            station.getBlockPos(),
            level.dimension().identifier(),
            level.getGameTime(),
            RadarStationConstants.SWEEP_PERIOD_TICKS,
            RadarStationConstants.DETECTION_RANGE_BLOCKS,
            station.warningRadius(),
            station.fireRadius(),
            station.redstoneSignal(),
            station.redstoneMode(),
            station.primaryThreatId(),
            station.primaryThreatDistance(),
            station.phaseOffset(),
            controlsEnabled,
            station.dynamicChunkLoading()
        ));
        sendState(station);
        if (!station.observations().isEmpty()) {
            sendObservations(station, List.copyOf(station.observations()));
        }
    }

    public static void sendObservations(
        final RadarStationBlockEntity station,
        final List<RadarStationObservation> observations
    ) {
        send(station, new ClientboundRadarStationObservationPayload(
            station.radarId(),
            observations
        ));
    }

    public static void sendState(final RadarStationBlockEntity station) {
        if (!(station.getLevel() instanceof ServerLevel level)) return;
        send(station, new ClientboundRadarStationStatePayload(
            station.radarId(),
            station.warningRadius(),
            station.fireRadius(),
            station.redstoneSignal(),
            station.redstoneMode(),
            station.primaryThreatId(),
            station.primaryThreatDistance(),
            station.warningActive(),
            station.observations().size(),
            station.threatCount(),
            level.getGameTime(),
            station.dynamicChunkLoading()
        ));
    }

    private static void send(
        final RadarStationBlockEntity station,
        final net.minecraft.network.protocol.common.custom.CustomPacketPayload payload
    ) {
        VIEWERS.values().removeIf(viewer -> {
            if (!viewer.radarId.equals(station.radarId())) return false;
            if (viewer.player.level() != station.getLevel()
                || !station.getBlockPos().equals(viewer.centre)) {
                ServerPlayNetworking.send(
                    viewer.player,
                    new ClientboundCloseRadarStationPayload(station.radarId())
                );
                releaseViewerTicket(viewer);
                return true;
            }
            ServerPlayNetworking.send(viewer.player, payload);
            return false;
        });
    }

    public static void closeStation(final UUID id) {
        VIEWERS.values().removeIf(viewer -> {
            if (!viewer.radarId.equals(id)) return false;
            ServerPlayNetworking.send(
                viewer.player,
                new ClientboundCloseRadarStationPayload(id)
            );
            releaseViewerTicket(viewer);
            return true;
        });
    }

    private static void configure(
        final ServerPlayer player,
        final ServerboundConfigureRadarStationPayload payload
    ) {
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (!validControlViewer(player, viewer, payload.radarId(), payload.centre())
            || !Double.isFinite(payload.warningRadius())
            || !Double.isFinite(payload.fireRadius())
            || !RadarRedstoneMode.isRegistered(payload.redstoneMode())) {
            return;
        }
        if (!(player.level().getBlockEntity(payload.centre())
                instanceof RadarStationBlockEntity station)
            || !station.radarId().equals(payload.radarId())
            || !RadarStationStructure.complete(
                player.level(),
                payload.centre(),
                station.facing()
            )) {
            return;
        }
        station.configure(
            payload.warningRadius(),
            payload.fireRadius(),
            payload.redstoneMode()
        );
        sendState(station);
    }

    private static void setChunkLoading(
        final ServerPlayer player,
        final ServerboundSetRadarStationChunkLoadingPayload payload
    ) {
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (!validControlViewer(player, viewer, payload.radarId(), payload.centre())) return;
        if (!(player.level().getBlockEntity(payload.centre())
                instanceof RadarStationBlockEntity station)
            || !station.radarId().equals(payload.radarId())) {
            return;
        }
        station.setDynamicChunkLoading(payload.enabled());
        sendState(station);
    }

    private static boolean validControlViewer(
        final ServerPlayer player,
        final Viewer viewer,
        final UUID radarId,
        final net.minecraft.core.BlockPos centre
    ) {
        return viewer != null
            && viewer.controlsEnabled
            && viewer.radarId.equals(radarId)
            && viewer.centre.equals(centre)
            && player.distanceToSqr(Vec3.atCenterOf(centre)) <= 64.0;
    }

    private static void removeViewer(final UUID playerId) {
        Viewer viewer = VIEWERS.remove(playerId);
        if (viewer != null) releaseViewerTicket(viewer);
    }

    private static void releaseViewerTicket(final Viewer viewer) {
        if (viewer.ticketHeld) {
            RadarStationChunkTicketManager.releaseViewer(
                viewer.level,
                viewer.radarId
            );
        }
    }

    private record Viewer(
        ServerPlayer player,
        ServerLevel level,
        UUID radarId,
        net.minecraft.core.BlockPos centre,
        boolean controlsEnabled,
        boolean ticketHeld
    ) {
    }
}
