package com.andye.warmod.firearm.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record ClientboundFirearmImpactPayload(UUID shotId, Vec3 position)
    implements CustomPacketPayload {
    public static final Type<ClientboundFirearmImpactPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "firearm_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFirearmImpactPayload>
        STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.shotId); buffer.writeDouble(payload.position.x);
            buffer.writeDouble(payload.position.y); buffer.writeDouble(payload.position.z);
        }, buffer -> new ClientboundFirearmImpactPayload(buffer.readUUID(),
            new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
