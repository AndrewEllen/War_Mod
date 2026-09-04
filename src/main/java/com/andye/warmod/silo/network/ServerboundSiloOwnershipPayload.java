package com.andye.warmod.silo.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.defence.DefenceOwnershipAction;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundSiloOwnershipPayload(
    int menuId,
    BlockPos centre,
    UUID siloId,
    DefenceOwnershipAction action,
    String playerName
) implements CustomPacketPayload {
    public static final Type<ServerboundSiloOwnershipPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "silo_ownership")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSiloOwnershipPayload>
        STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.menuId);
                buffer.writeBlockPos(payload.centre);
                buffer.writeUUID(payload.siloId);
                buffer.writeVarInt(payload.action.ordinal());
                buffer.writeUtf(payload.playerName, 16);
            },
            buffer -> new ServerboundSiloOwnershipPayload(
                buffer.readVarInt(),
                buffer.readBlockPos(),
                buffer.readUUID(),
                DefenceOwnershipAction.byNetworkId(buffer.readVarInt()),
                buffer.readUtf(16)
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
