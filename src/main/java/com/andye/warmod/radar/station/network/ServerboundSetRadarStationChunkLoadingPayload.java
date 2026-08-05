package com.andye.warmod.radar.station.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundSetRadarStationChunkLoadingPayload(
    UUID radarId,
    BlockPos centre,
    boolean enabled
) implements CustomPacketPayload {
    public static final Type<ServerboundSetRadarStationChunkLoadingPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            WarMod.MOD_ID,
            "radar_station_chunk_loading"
        ));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        ServerboundSetRadarStationChunkLoadingPayload> STREAM_CODEC =
        StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.radarId);
            buffer.writeBlockPos(payload.centre);
            buffer.writeBoolean(payload.enabled);
        }, buffer -> new ServerboundSetRadarStationChunkLoadingPayload(
            buffer.readUUID(),
            buffer.readBlockPos(),
            buffer.readBoolean()
        ));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
