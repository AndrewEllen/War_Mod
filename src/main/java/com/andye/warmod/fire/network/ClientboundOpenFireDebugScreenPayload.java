package com.andye.warmod.fire.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.item.component.FireDebugConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

public record ClientboundOpenFireDebugScreenPayload(InteractionHand hand,
    FireDebugConfig config) implements CustomPacketPayload {
    public static final Type<ClientboundOpenFireDebugScreenPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "open_fire_debug_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenFireDebugScreenPayload>
        STREAM_CODEC = StreamCodec.of(ClientboundOpenFireDebugScreenPayload::write,
            ClientboundOpenFireDebugScreenPayload::read);

    private static void write(final RegistryFriendlyByteBuf buffer,
        final ClientboundOpenFireDebugScreenPayload payload) {
        buffer.writeByte(payload.hand == InteractionHand.OFF_HAND ? 1 : 0);
        buffer.writeFloat(payload.config.intensity());
        buffer.writeByte(payload.config.size());
    }

    private static ClientboundOpenFireDebugScreenPayload read(final RegistryFriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readUnsignedByte() == 1
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return new ClientboundOpenFireDebugScreenPayload(hand,
            new FireDebugConfig(buffer.readFloat(), buffer.readUnsignedByte()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
