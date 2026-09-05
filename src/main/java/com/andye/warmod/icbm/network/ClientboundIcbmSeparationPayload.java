package com.andye.warmod.icbm.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record ClientboundIcbmSeparationPayload(UUID missileId, UUID terminalWarheadId,
    Vec3 separationPosition, Vec3 carrierVelocity, long separationGameTime,
    long visualSeed, WarheadPayloadType payloadType, WarheadYield yield,
    WarheadDeliveryMode deliveryMode) implements CustomPacketPayload {
    public static final Type<ClientboundIcbmSeparationPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "icbm_separation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundIcbmSeparationPayload>
        STREAM_CODEC = StreamCodec.of(ClientboundIcbmSeparationPayload::write,
            ClientboundIcbmSeparationPayload::read);

    public ClientboundIcbmSeparationPayload(final UUID missileId,
        final UUID terminalWarheadId, final Vec3 separationPosition,
        final Vec3 carrierVelocity, final long separationGameTime, final long visualSeed,
        final WarheadPayloadType payloadType) {
        this(missileId, terminalWarheadId, separationPosition, carrierVelocity,
            separationGameTime, visualSeed, payloadType,
            WarheadYield.defaultFor(payloadType), WarheadDeliveryMode.SINGLE);
    }

    public boolean isWellFormed() {
        return missileId != null && terminalWarheadId != null
            && separationPosition != null && carrierVelocity != null
            && payloadType != null && yield != null && deliveryMode != null
            && yield.payloadType() == payloadType
            && separationPosition.isFinite() && carrierVelocity.isFinite();
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(final RegistryFriendlyByteBuf buffer,
        final ClientboundIcbmSeparationPayload payload) {
        buffer.writeUUID(payload.missileId);
        buffer.writeUUID(payload.terminalWarheadId);
        Vec3.STREAM_CODEC.encode(buffer, payload.separationPosition);
        Vec3.STREAM_CODEC.encode(buffer, payload.carrierVelocity);
        buffer.writeLong(payload.separationGameTime);
        buffer.writeLong(payload.visualSeed);
        WarheadPayloadType.STREAM_CODEC.encode(buffer, payload.payloadType);
        buffer.writeVarInt(payload.yield.ordinal());
        buffer.writeVarInt(payload.deliveryMode.ordinal());
    }

    private static ClientboundIcbmSeparationPayload read(final RegistryFriendlyByteBuf buffer) {
        UUID missile = buffer.readUUID();
        UUID terminal = buffer.readUUID();
        Vec3 position = Vec3.STREAM_CODEC.decode(buffer);
        Vec3 velocity = Vec3.STREAM_CODEC.decode(buffer);
        long gameTime = buffer.readLong();
        long seed = buffer.readLong();
        WarheadPayloadType payload = WarheadPayloadType.STREAM_CODEC.decode(buffer);
        WarheadYield yield = enumValue(WarheadYield.values(), buffer.readVarInt(), "yield");
        WarheadDeliveryMode delivery = enumValue(WarheadDeliveryMode.values(),
            buffer.readVarInt(), "delivery mode");
        return new ClientboundIcbmSeparationPayload(missile, terminal, position, velocity,
            gameTime, seed, payload, yield, delivery);
    }

    private static <T> T enumValue(final T[] values, final int ordinal,
        final String label) {
        if (ordinal < 0 || ordinal >= values.length)
            throw new IllegalArgumentException("Invalid ICBM " + label);
        return values[ordinal];
    }
}
