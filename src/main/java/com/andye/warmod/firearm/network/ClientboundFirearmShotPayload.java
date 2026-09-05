package com.andye.warmod.firearm.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record ClientboundFirearmShotPayload(UUID shotId, byte firearmType,
    Vec3 origin, Vec3 velocity, Vec3 acceleration, long visualSeed,
    int maximumAge) implements CustomPacketPayload {
    public static final Type<ClientboundFirearmShotPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "firearm_shot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFirearmShotPayload>
        STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.shotId); buffer.writeByte(payload.firearmType);
            writeVec(buffer, payload.origin); writeVec(buffer, payload.velocity);
            writeVec(buffer, payload.acceleration); buffer.writeLong(payload.visualSeed);
            buffer.writeVarInt(payload.maximumAge);
        }, buffer -> new ClientboundFirearmShotPayload(buffer.readUUID(), buffer.readByte(),
            readVec(buffer), readVec(buffer), readVec(buffer), buffer.readLong(),
            buffer.readVarInt()));

    private static void writeVec(final RegistryFriendlyByteBuf buffer, final Vec3 value) {
        buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z);
    }
    private static Vec3 readVec(final RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
