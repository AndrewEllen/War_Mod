package com.andye.warmod.silo.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerboundLaunchControllerLaunchPayload(
    int menuId,
    BlockPos centre,
    UUID controllerId
) implements CustomPacketPayload {
    public static final Type<ServerboundLaunchControllerLaunchPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            WarMod.MOD_ID,
            "launch_controller_launch"
        ));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        ServerboundLaunchControllerLaunchPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.menuId());
                buffer.writeBlockPos(payload.centre());
                UUIDUtil.STREAM_CODEC.encode(buffer, payload.controllerId());
            },
            buffer -> new ServerboundLaunchControllerLaunchPayload(
                buffer.readVarInt(),
                buffer.readBlockPos(),
                UUIDUtil.STREAM_CODEC.decode(buffer)
            )
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
