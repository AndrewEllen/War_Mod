package com.andye.warmod.rocket.network;

import com.andye.warmod.WarMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** An attack-key request; the server validates the held launcher and its aim state. */
public record ServerboundRocketLauncherTriggerPayload() implements CustomPacketPayload {
    public static final Type<ServerboundRocketLauncherTriggerPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "rocket_launcher_trigger"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRocketLauncherTriggerPayload>
        STREAM_CODEC = StreamCodec.unit(new ServerboundRocketLauncherTriggerPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
