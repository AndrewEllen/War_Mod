package com.andye.warmod.artillery.network;

import com.andye.warmod.WarMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundArtillerySetTargetPayload(int menuId, BlockPos cannonPos,
    double x, double y, double z) implements CustomPacketPayload {
    public static final Type<ServerboundArtillerySetTargetPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "artillery_set_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundArtillerySetTargetPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> {
            buf.writeVarInt(payload.menuId());
            buf.writeBlockPos(payload.cannonPos());
            buf.writeDouble(payload.x());
            buf.writeDouble(payload.y());
            buf.writeDouble(payload.z());
        }, buf -> new ServerboundArtillerySetTargetPayload(buf.readVarInt(), buf.readBlockPos(),
            buf.readDouble(), buf.readDouble(), buf.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
