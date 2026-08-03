package com.andye.warmod.antiair.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.antiair.AntiAirLaunchMode;
import com.andye.warmod.antiair.AntiAirMissileVariant;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record ClientboundAntiAirLaunchPayload(UUID interceptorId, @Nullable UUID ownerPlayerId,
    AntiAirMissileVariant variant, @Nullable UUID targetRootTrackId, Vec3 launchPosition, Vec3 burnoutPosition,
    Vec3 noTargetHorizontalOffset, AntiAirLaunchMode launchMode, long launchGameTime, int ignitionTicks, int boostTicks,
    long visualSeed, int guidanceTier, boolean debugNoTargetFlight) implements CustomPacketPayload {
    public static final Type<ClientboundAntiAirLaunchPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "anti_air_launch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAntiAirLaunchPayload> STREAM_CODEC =
        StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.interceptorId); buffer.writeBoolean(payload.ownerPlayerId != null);
            if (payload.ownerPlayerId != null) buffer.writeUUID(payload.ownerPlayerId);
            buffer.writeVarInt(payload.variant.ordinal()); buffer.writeBoolean(payload.targetRootTrackId != null);
            if (payload.targetRootTrackId != null) buffer.writeUUID(payload.targetRootTrackId);
            vec(buffer, payload.launchPosition); vec(buffer, payload.burnoutPosition); vec(buffer, payload.noTargetHorizontalOffset);
            buffer.writeVarInt(payload.launchMode.ordinal()); buffer.writeLong(payload.launchGameTime);
            buffer.writeVarInt(payload.ignitionTicks); buffer.writeVarInt(payload.boostTicks); buffer.writeLong(payload.visualSeed);
            buffer.writeVarInt(payload.guidanceTier); buffer.writeBoolean(payload.debugNoTargetFlight);
        }, buffer -> new ClientboundAntiAirLaunchPayload(buffer.readUUID(), buffer.readBoolean() ? buffer.readUUID() : null,
            enumAt(AntiAirMissileVariant.values(), buffer.readVarInt()), buffer.readBoolean() ? buffer.readUUID() : null,
            vec(buffer), vec(buffer), vec(buffer), enumAt(AntiAirLaunchMode.values(), buffer.readVarInt()), buffer.readLong(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(), buffer.readVarInt(), buffer.readBoolean()));
    private static void vec(RegistryFriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z);
    }
    private static Vec3 vec(RegistryFriendlyByteBuf buffer) {
        Vec3 value = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        if (!value.isFinite()) throw new IllegalArgumentException("Invalid anti-air coordinate");
        return value;
    }
    private static <T> T enumAt(T[] values, int index) {
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("Invalid anti-air enum");
        return values[index];
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}