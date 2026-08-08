package com.andye.warmod.artillery.network;

import com.andye.warmod.WarMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundArtilleryClearTargetPayload(int menuId, BlockPos cannonPos)
    implements CustomPacketPayload {
    public static final Type<ServerboundArtilleryClearTargetPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "artillery_clear_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundArtilleryClearTargetPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeVarInt(payload.menuId());
            buf.writeBlockPos(payload.cannonPos());
        }, buf -> new ServerboundArtilleryClearTargetPayload(buf.readVarInt(), buf.readBlockPos()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
