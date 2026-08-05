package com.andye.warmod.radar.station.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.radar.station.RadarRedstoneMode;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record ClientboundRadarStationStatePayload(
    UUID radarId,
    double warningRadius,
    double fireRadius,
    int redstoneSignal,
    RadarRedstoneMode redstoneMode,
    @Nullable UUID primaryThreatId,
    double primaryThreatDistance,
    boolean warningActive,
    int contacts,
    int threats,
    long serverGameTime,
    boolean dynamicChunkLoading
) implements CustomPacketPayload {
    public static final Type<ClientboundRadarStationStatePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "radar_station_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRadarStationStatePayload>
        STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.radarId);
            buffer.writeDouble(payload.warningRadius);
            buffer.writeDouble(payload.fireRadius);
            buffer.writeVarInt(payload.redstoneSignal);
            buffer.writeVarInt(payload.redstoneMode.ordinal());
            buffer.writeBoolean(payload.primaryThreatId != null);
            if (payload.primaryThreatId != null) buffer.writeUUID(payload.primaryThreatId);
            buffer.writeDouble(payload.primaryThreatDistance);
            buffer.writeBoolean(payload.warningActive);
            buffer.writeVarInt(payload.contacts);
            buffer.writeVarInt(payload.threats);
            buffer.writeLong(payload.serverGameTime);
            buffer.writeBoolean(payload.dynamicChunkLoading);
        }, buffer -> new ClientboundRadarStationStatePayload(
            buffer.readUUID(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readVarInt(),
            mode(buffer.readVarInt()),
            buffer.readBoolean() ? buffer.readUUID() : null,
            buffer.readDouble(),
            buffer.readBoolean(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readLong(),
            buffer.readBoolean()
        ));

    private static RadarRedstoneMode mode(final int id) {
        return RadarRedstoneMode.fromNetworkId(id).orElseThrow(
            () -> new IllegalArgumentException("Invalid radar mode")
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
