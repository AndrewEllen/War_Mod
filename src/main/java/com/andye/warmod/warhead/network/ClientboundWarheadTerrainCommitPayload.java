package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Ordered after vanilla chunk packets so the client can acknowledge their processing. */
public record ClientboundWarheadTerrainCommitPayload(UUID impactId, long sequence,
    int chunkPackets, int changedChunks, int changedSections, int changedCells,
    int changedBiomeQuarts, long serverGameTime) implements CustomPacketPayload {
    public static final Type<ClientboundWarheadTerrainCommitPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_terrain_commit"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        ClientboundWarheadTerrainCommitPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.impactId);
                buffer.writeLong(payload.sequence);
                buffer.writeVarInt(payload.chunkPackets);
                buffer.writeVarInt(payload.changedChunks);
                buffer.writeVarInt(payload.changedSections);
                buffer.writeVarInt(payload.changedCells);
                buffer.writeVarInt(payload.changedBiomeQuarts);
                buffer.writeLong(payload.serverGameTime);
            }, buffer -> new ClientboundWarheadTerrainCommitPayload(buffer.readUUID(),
                buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readLong()));
    public boolean isWellFormed() {
        return impactId != null && sequence > 0L && chunkPackets >= 0
            && changedChunks >= 0 && changedSections >= 0 && changedCells >= 0
            && changedBiomeQuarts >= 0;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
