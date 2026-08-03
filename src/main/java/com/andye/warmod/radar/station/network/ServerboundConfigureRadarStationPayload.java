package com.andye.warmod.radar.station.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.radar.station.RadarRedstoneMode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundConfigureRadarStationPayload(UUID radarId, BlockPos centre, double warningRadius,
    double fireRadius, RadarRedstoneMode redstoneMode) implements CustomPacketPayload {
    public static final Type<ServerboundConfigureRadarStationPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "radar_station_configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundConfigureRadarStationPayload> STREAM_CODEC =
        StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.radarId); buffer.writeBlockPos(payload.centre); buffer.writeDouble(payload.warningRadius);
            buffer.writeDouble(payload.fireRadius); buffer.writeVarInt(payload.redstoneMode.ordinal());
        }, buffer -> new ServerboundConfigureRadarStationPayload(buffer.readUUID(), buffer.readBlockPos(), buffer.readDouble(),
            buffer.readDouble(), mode(buffer.readVarInt())));
    private static RadarRedstoneMode mode(int id) {
        return RadarRedstoneMode.fromNetworkId(id).orElseThrow(() -> new IllegalArgumentException("Invalid radar mode"));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}