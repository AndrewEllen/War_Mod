package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundWarheadTerrainCommitAckPayload(UUID impactId, long sequence)
    implements CustomPacketPayload {
    public static final Type<ServerboundWarheadTerrainCommitAckPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_terrain_commit_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        ServerboundWarheadTerrainCommitAckPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.impactId);
                buffer.writeLong(payload.sequence);
            }, buffer -> new ServerboundWarheadTerrainCommitAckPayload(
                buffer.readUUID(), buffer.readLong()));
    public boolean isWellFormed() { return impactId != null && sequence > 0L; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
