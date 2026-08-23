package com.andye.warmod.icbm.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Launch geometry plus the exact visual payload identity mounted in the carrier. */
public record ClientboundIcbmLaunchPayload(UUID missileId, Vec3 launchPosition,
    Vec3 burnoutPosition, Vec3 separationPosition, Vec3 intendedTarget,
    long launchGameTime, int ignitionTicks, int boostTicks, int coastTicks,
    long visualSeed, WarheadPayloadType payloadType, WarheadYield yield,
    WarheadDeliveryMode deliveryMode) implements CustomPacketPayload {
    public static final Type<ClientboundIcbmLaunchPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "icbm_launch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundIcbmLaunchPayload>
        STREAM_CODEC = StreamCodec.of(ClientboundIcbmLaunchPayload::write,
            ClientboundIcbmLaunchPayload::read);

    public static ClientboundIcbmLaunchPayload fromPlan(final IcbmFlightPlan plan) {
        return fromPlan(plan, WarheadYield.defaultFor(plan.payloadType()),
            WarheadDeliveryMode.SINGLE);
    }

    public static ClientboundIcbmLaunchPayload fromPlan(final IcbmFlightPlan plan,
        final WarheadYield yield, final WarheadDeliveryMode deliveryMode) {
        WarheadYield resolvedYield = yield == null
            ? WarheadYield.defaultFor(plan.payloadType()) : yield;
        WarheadDeliveryMode resolvedDelivery = deliveryMode == null
            ? WarheadDeliveryMode.SINGLE : deliveryMode;
        return new ClientboundIcbmLaunchPayload(plan.missileId(), plan.launchPosition(),
            plan.burnoutPosition(), plan.separationPosition(), plan.intendedTarget(),
            plan.launchGameTime(), plan.ignitionTicks(), plan.boostTicks(), plan.coastTicks(),
            plan.visualSeed(), plan.payloadType(), resolvedYield, resolvedDelivery);
    }

    public boolean isWellFormed() {
        return missileId != null && payloadType != null && yield != null && deliveryMode != null
            && yield.payloadType() == payloadType && launchPosition != null
            && burnoutPosition != null && separationPosition != null && intendedTarget != null
            && launchPosition.isFinite() && burnoutPosition.isFinite()
            && separationPosition.isFinite() && intendedTarget.isFinite()
            && ignitionTicks > 0 && boostTicks > 0
            && coastTicks >= IcbmConstants.MINIMUM_COAST_TICKS
            && coastTicks <= IcbmConstants.MAXIMUM_COAST_TICKS;
    }

    private static void write(final RegistryFriendlyByteBuf buffer,
        final ClientboundIcbmLaunchPayload payload) {
        buffer.writeUUID(payload.missileId);
        writeVec(buffer, payload.launchPosition); writeVec(buffer, payload.burnoutPosition);
        writeVec(buffer, payload.separationPosition); writeVec(buffer, payload.intendedTarget);
        buffer.writeLong(payload.launchGameTime); buffer.writeVarInt(payload.ignitionTicks);
        buffer.writeVarInt(payload.boostTicks); buffer.writeVarInt(payload.coastTicks);
        buffer.writeLong(payload.visualSeed); buffer.writeVarInt(payload.payloadType.ordinal());
        buffer.writeVarInt(payload.yield.ordinal()); buffer.writeVarInt(payload.deliveryMode.ordinal());
    }

    private static ClientboundIcbmLaunchPayload read(final RegistryFriendlyByteBuf buffer) {
        UUID missileId = buffer.readUUID();
        Vec3 launch = readVec(buffer), burnout = readVec(buffer), separation = readVec(buffer),
            target = readVec(buffer);
        long launchTime = buffer.readLong();
        int ignition = buffer.readVarInt(), boost = buffer.readVarInt(), coast = buffer.readVarInt();
        long seed = buffer.readLong();
        return new ClientboundIcbmLaunchPayload(missileId, launch, burnout, separation, target,
            launchTime, ignition, boost, coast, seed,
            enumAt(WarheadPayloadType.values(), buffer.readVarInt()),
            enumAt(WarheadYield.values(), buffer.readVarInt()),
            enumAt(WarheadDeliveryMode.values(), buffer.readVarInt()));
    }

    private static void writeVec(final RegistryFriendlyByteBuf buffer, final Vec3 value) {
        buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z);
    }
    private static Vec3 readVec(final RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
    private static <T> T enumAt(final T[] values, final int index) {
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("Invalid ICBM enum");
        return values[index];
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
