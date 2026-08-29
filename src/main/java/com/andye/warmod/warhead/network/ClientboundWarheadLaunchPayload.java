package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundWarheadLaunchPayload(UUID warheadId, long stateSequence,
    WarheadNetworkState state, long serverGameTime, double startX, double startY, double startZ,
    double targetX, double targetY, double targetZ, long launchGameTime, int flightTicks,
    long visualSeed, WarheadPayloadType payloadType, WarheadYield yield,
    WarheadDeliveryMode deliveryMode) implements CustomPacketPayload {
    public static final Type<ClientboundWarheadLaunchPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_launch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadLaunchPayload>
        STREAM_CODEC = StreamCodec.of(ClientboundWarheadLaunchPayload::write,
            ClientboundWarheadLaunchPayload::read);

    public ClientboundWarheadLaunchPayload(final UUID warheadId,
        final double startX, final double startY, final double startZ,
        final double targetX, final double targetY, final double targetZ,
        final long launchGameTime, final int flightTicks, final long visualSeed,
        final WarheadPayloadType payloadType) {
        this(warheadId, startX, startY, startZ, targetX, targetY, targetZ,
            launchGameTime, flightTicks, visualSeed, payloadType,
            WarheadYield.defaultFor(payloadType), WarheadDeliveryMode.SINGLE);
    }

    public ClientboundWarheadLaunchPayload(final UUID warheadId,
        final double startX, final double startY, final double startZ,
        final double targetX, final double targetY, final double targetZ,
        final long launchGameTime, final int flightTicks, final long visualSeed,
        final WarheadPayloadType payloadType, final WarheadYield yield,
        final WarheadDeliveryMode deliveryMode) {
        this(warheadId, 1L, WarheadNetworkState.FLIGHT, launchGameTime,
            startX, startY, startZ, targetX, targetY, targetZ, launchGameTime,
            flightTicks, visualSeed, payloadType, yield, deliveryMode);
    }

    public ClientboundWarheadLaunchPayload withAuthoritativeState(final long sequence,
        final long currentServerGameTime) {
        return new ClientboundWarheadLaunchPayload(warheadId, sequence,
            WarheadNetworkState.FLIGHT, currentServerGameTime, startX, startY, startZ,
            targetX, targetY, targetZ, launchGameTime, flightTicks, visualSeed,
            payloadType, yield, deliveryMode);
    }

    public boolean isWellFormed() {
        return warheadId != null && stateSequence > 0L
            && state == WarheadNetworkState.FLIGHT && payloadType != null
            && yield != null && yield.payloadType() == payloadType && deliveryMode != null
            && finite(startX) && finite(startY) && finite(startZ)
            && finite(targetX) && finite(targetY) && finite(targetZ) && flightTicks >= 1;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static boolean finite(final double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30_000_000.0;
    }

    private static void write(final RegistryFriendlyByteBuf buffer,
        final ClientboundWarheadLaunchPayload payload) {
        buffer.writeUUID(payload.warheadId);
        buffer.writeLong(payload.stateSequence);
        buffer.writeVarInt(payload.state.ordinal());
        buffer.writeLong(payload.serverGameTime);
        buffer.writeDouble(payload.startX); buffer.writeDouble(payload.startY);
        buffer.writeDouble(payload.startZ); buffer.writeDouble(payload.targetX);
        buffer.writeDouble(payload.targetY); buffer.writeDouble(payload.targetZ);
        buffer.writeLong(payload.launchGameTime); buffer.writeVarInt(payload.flightTicks);
        buffer.writeLong(payload.visualSeed);
        WarheadPayloadType.STREAM_CODEC.encode(buffer, payload.payloadType);
        buffer.writeVarInt(payload.yield.ordinal());
        buffer.writeVarInt(payload.deliveryMode.ordinal());
    }

    private static ClientboundWarheadLaunchPayload read(final RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID(); long sequence = buffer.readLong();
        int stateOrdinal = buffer.readVarInt();
        if (stateOrdinal != WarheadNetworkState.FLIGHT.ordinal())
            throw new IllegalArgumentException("Invalid launch state");
        long serverTime = buffer.readLong();
        double startX = buffer.readDouble(), startY = buffer.readDouble(), startZ = buffer.readDouble();
        double targetX = buffer.readDouble(), targetY = buffer.readDouble(), targetZ = buffer.readDouble();
        long launchTime = buffer.readLong(); int ticks = buffer.readVarInt();
        long seed = buffer.readLong();
        WarheadPayloadType payload = WarheadPayloadType.STREAM_CODEC.decode(buffer);
        WarheadYield yield = enumValue(WarheadYield.values(), buffer.readVarInt(), "yield");
        WarheadDeliveryMode delivery = enumValue(WarheadDeliveryMode.values(),
            buffer.readVarInt(), "delivery mode");
        return new ClientboundWarheadLaunchPayload(id, sequence, WarheadNetworkState.FLIGHT,
            serverTime, startX, startY, startZ, targetX, targetY, targetZ, launchTime,
            ticks, seed, payload, yield, delivery);
    }

    private static <T> T enumValue(final T[] values, final int ordinal,
        final String label) {
        if (ordinal < 0 || ordinal >= values.length)
            throw new IllegalArgumentException("Invalid warhead " + label);
        return values[ordinal];
    }
}
