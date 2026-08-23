package com.andye.warmod.firearm.network;

import com.andye.warmod.WarMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One client attack-key request; the server derives and validates the held weapon. */
public record ServerboundFirearmTriggerPayload(boolean pressed) implements CustomPacketPayload {
    public static final Type<ServerboundFirearmTriggerPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "firearm_trigger"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundFirearmTriggerPayload>
        STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL,
            ServerboundFirearmTriggerPayload::pressed,
            ServerboundFirearmTriggerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
