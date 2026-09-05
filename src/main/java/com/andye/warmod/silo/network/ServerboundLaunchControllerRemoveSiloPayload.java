package com.andye.warmod.silo.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundLaunchControllerRemoveSiloPayload(
    int menuId,
    BlockPos centre,
    UUID controllerId,
    UUID siloId
) implements CustomPacketPayload {
    public static final Type<ServerboundLaunchControllerRemoveSiloPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            WarMod.MOD_ID,
            "launch_controller_remove_silo"
        ));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        ServerboundLaunchControllerRemoveSiloPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.menuId());
                buffer.writeBlockPos(payload.centre());
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.controllerId());
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.siloId());
            },
            buffer -> new ServerboundLaunchControllerRemoveSiloPayload(
                buffer.readVarInt(),
                buffer.readBlockPos(),
                UUIDUtil.STREAM_CODEC.decode(buffer),
                UUIDUtil.STREAM_CODEC.decode(buffer)
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
