package com.andye.warmod.radar.display.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundRadarDisplayClearPayload(
    Identifier dimension,
    BlockPos controller,
    UUID displayId
) implements CustomPacketPayload {
    public static final Type<ClientboundRadarDisplayClearPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(
            WarMod.MOD_ID,
            "radar_display_clear"
        ));

    public static final StreamCodec<
        RegistryFriendlyByteBuf,
        ClientboundRadarDisplayClearPayload
    > STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeIdentifier(payload.dimension());
            buffer.writeBlockPos(payload.controller());
            buffer.writeUUID(payload.displayId());
        },
        buffer -> new ClientboundRadarDisplayClearPayload(
            buffer.readIdentifier(),
            buffer.readBlockPos(),
            buffer.readUUID()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
