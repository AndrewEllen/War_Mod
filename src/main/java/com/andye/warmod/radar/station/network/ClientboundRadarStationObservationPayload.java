package com.andye.warmod.radar.station.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.radar.network.RadarNetworkCodecs;
import com.andye.warmod.radar.station.RadarStationConstants;
import com.andye.warmod.radar.station.RadarStationObservation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundRadarStationObservationPayload(
    UUID radarId,
    List<RadarStationObservation> observations
) implements CustomPacketPayload {
    public ClientboundRadarStationObservationPayload {
        observations = List.copyOf(observations.subList(
            0, Math.min(observations.size(), RadarStationConstants.MAX_OBSERVATIONS)));
    }

    public static final Type<ClientboundRadarStationObservationPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "radar_station_observations"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRadarStationObservationPayload>
        STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.radarId);
                buffer.writeVarInt(payload.observations.size());
                for (RadarStationObservation observation : payload.observations) {
                    buffer.writeUUID(observation.trackId());
                    RadarNetworkCodecs.writeTrack(buffer, observation.trackSnapshot());
                    RadarNetworkCodecs.writeVec(buffer, observation.observedPosition());
                    RadarNetworkCodecs.writeVec(buffer, observation.observedVelocity());
                    RadarNetworkCodecs.writeVec(buffer, observation.predictedImpactPosition());
                    buffer.writeLong(observation.observationGameTime());
                    buffer.writeDouble(observation.observedRouteTime());
                    buffer.writeBoolean(observation.threatensWarningZone());
                }
            },
            buffer -> {
                UUID radarId = buffer.readUUID();
                int count = Math.min(RadarStationConstants.MAX_OBSERVATIONS,
                    Math.max(0, buffer.readVarInt()));
                List<RadarStationObservation> observations = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    observations.add(new RadarStationObservation(
                        buffer.readUUID(),
                        RadarNetworkCodecs.readTrack(buffer),
                        RadarNetworkCodecs.readVec(buffer),
                        RadarNetworkCodecs.readVec(buffer),
                        RadarNetworkCodecs.readVec(buffer),
                        buffer.readLong(),
                        buffer.readDouble(),
                        buffer.readBoolean()));
                }
                return new ClientboundRadarStationObservationPayload(radarId, observations);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}