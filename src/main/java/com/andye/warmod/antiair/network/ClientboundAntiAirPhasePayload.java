package com.andye.warmod.antiair.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.antiair.AntiAirFlightPhase;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public record ClientboundAntiAirPhasePayload(UUID interceptorId, AntiAirFlightPhase phase, long gameTime,
    @Nullable Vec3 transitionPosition, @Nullable Vec3 transitionVelocity) implements CustomPacketPayload {
    public static final Type<ClientboundAntiAirPhasePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "anti_air_phase"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAntiAirPhasePayload> STREAM_CODEC =
        StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.interceptorId); buffer.writeVarInt(payload.phase.ordinal()); buffer.writeLong(payload.gameTime);
            boolean hasTransition = payload.transitionPosition != null && payload.transitionVelocity != null;
            buffer.writeBoolean(hasTransition);
            if (hasTransition) { vec(buffer, payload.transitionPosition); vec(buffer, payload.transitionVelocity); }
        }, buffer -> {
            UUID id = buffer.readUUID(); AntiAirFlightPhase phase = AntiAirFlightPhase.values()[buffer.readVarInt()];
            long time = buffer.readLong();
            return buffer.readBoolean() ? new ClientboundAntiAirPhasePayload(id, phase, time, vec(buffer), vec(buffer))
                : new ClientboundAntiAirPhasePayload(id, phase, time, null, null);
        });
    public static ClientboundAntiAirPhasePayload normal(UUID id, AntiAirFlightPhase phase, long gameTime) {
        return new ClientboundAntiAirPhasePayload(id, phase, gameTime, null, null);
    }
    private static void vec(RegistryFriendlyByteBuf buffer, Vec3 value) { buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf buffer) { return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}