package com.andye.warmod.fire.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.fire.wind.FireWindImpulse;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Immediate pressure-wave advection for client-only fire, ember, and smoke geometry. */
public record ClientboundFireWindImpulsePayload(double x, double y, double z,
    double radius, double strength, long startGameTime, int durationTicks, boolean nuclear)
    implements CustomPacketPayload {
    public static final Type<ClientboundFireWindImpulsePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "fire_wind_impulse"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFireWindImpulsePayload>
        STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeDouble(payload.x); buffer.writeDouble(payload.y);
            buffer.writeDouble(payload.z); buffer.writeDouble(payload.radius);
            buffer.writeDouble(payload.strength); buffer.writeLong(payload.startGameTime);
            buffer.writeVarInt(payload.durationTicks);
            buffer.writeBoolean(payload.nuclear);
        }, buffer -> new ClientboundFireWindImpulsePayload(buffer.readDouble(),
            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
            buffer.readDouble(), buffer.readLong(), buffer.readVarInt(), buffer.readBoolean()));

    public static ClientboundFireWindImpulsePayload from(final FireWindImpulse impulse) {
        return new ClientboundFireWindImpulsePayload(impulse.center().x, impulse.center().y,
            impulse.center().z, impulse.radius(), impulse.strength(), impulse.startTick(),
            impulse.durationTicks(), impulse.nuclear());
    }

    public boolean isWellFormed() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Double.isFinite(radius) && Double.isFinite(strength)
            && radius > 0.0 && radius <= 2_048.0 && strength > 0.0 && strength <= 16.0
            && durationTicks > 0 && durationTicks <= 1_200;
    }

    public FireWindImpulse impulse() {
        return new FireWindImpulse(new Vec3(x, y, z), radius, strength,
            startGameTime, durationTicks, nuclear);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
