package com.andye.warmod.radar.station.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.radar.station.RadarRedstoneMode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record ClientboundOpenRadarStationPayload(UUID radarId, BlockPos centre, Identifier dimension,
    long serverGameTime, int sweepPeriodTicks, double detectionRange, double warningRadius, double fireRadius,
    int redstoneSignal, RadarRedstoneMode redstoneMode, @Nullable UUID primaryThreatId, double primaryThreatDistance,
    long phaseOffset) implements CustomPacketPayload {
    public static final Type<ClientboundOpenRadarStationPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "radar_station_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenRadarStationPayload> STREAM_CODEC =
        StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.radarId); buffer.writeBlockPos(payload.centre); buffer.writeIdentifier(payload.dimension);
            buffer.writeLong(payload.serverGameTime); buffer.writeVarInt(payload.sweepPeriodTicks);
            buffer.writeDouble(payload.detectionRange); buffer.writeDouble(payload.warningRadius); buffer.writeDouble(payload.fireRadius);
            buffer.writeVarInt(payload.redstoneSignal); buffer.writeVarInt(payload.redstoneMode.ordinal());
            buffer.writeBoolean(payload.primaryThreatId != null); if (payload.primaryThreatId != null) buffer.writeUUID(payload.primaryThreatId);
            buffer.writeDouble(payload.primaryThreatDistance); buffer.writeLong(payload.phaseOffset);
        }, buffer -> new ClientboundOpenRadarStationPayload(buffer.readUUID(), buffer.readBlockPos(), buffer.readIdentifier(),
            buffer.readLong(), buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
            buffer.readVarInt(), mode(buffer.readVarInt()), buffer.readBoolean() ? buffer.readUUID() : null,
            buffer.readDouble(), buffer.readLong()));
    private static RadarRedstoneMode mode(int id) {
        return RadarRedstoneMode.fromNetworkId(id).orElseThrow(() -> new IllegalArgumentException("Invalid radar mode"));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}