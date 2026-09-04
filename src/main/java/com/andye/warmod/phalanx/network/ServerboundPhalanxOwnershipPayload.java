package com.andye.warmod.phalanx.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.defence.DefenceOwnershipAction;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundPhalanxOwnershipPayload(
    int menuId,
    BlockPos centre,
    UUID turretId,
    DefenceOwnershipAction action,
    String playerName
) implements CustomPacketPayload {
    public static final Type<ServerboundPhalanxOwnershipPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "phalanx_ownership")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPhalanxOwnershipPayload>
        STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.menuId);
                buffer.writeBlockPos(payload.centre);
                buffer.writeUUID(payload.turretId);
                buffer.writeVarInt(payload.action.ordinal());
                buffer.writeUtf(payload.playerName, 16);
            },
            buffer -> new ServerboundPhalanxOwnershipPayload(
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
