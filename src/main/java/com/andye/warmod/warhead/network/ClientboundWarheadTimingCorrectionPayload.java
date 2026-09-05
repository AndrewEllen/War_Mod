package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record ClientboundWarheadTimingCorrectionPayload(UUID warheadId, long stateSequence,
    WarheadNetworkState state, long serverGameTime, int pausedSimulationTicks,
    Vec3 safePosition) implements CustomPacketPayload {
    public static final Type<ClientboundWarheadTimingCorrectionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_timing_correction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadTimingCorrectionPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
        buffer.writeUUID(payload.warheadId); buffer.writeLong(payload.stateSequence);
        buffer.writeVarInt(payload.state.ordinal()); buffer.writeLong(payload.serverGameTime);
        buffer.writeVarInt(payload.pausedSimulationTicks); buffer.writeDouble(payload.safePosition.x);
        buffer.writeDouble(payload.safePosition.y); buffer.writeDouble(payload.safePosition.z);
    }, buffer -> new ClientboundWarheadTimingCorrectionPayload(buffer.readUUID(),
        buffer.readLong(), readState(buffer.readVarInt()), buffer.readLong(), buffer.readVarInt(),
        new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())));
    public boolean isWellFormed() { return warheadId != null && stateSequence > 0L
        && (state == WarheadNetworkState.FLIGHT || state == WarheadNetworkState.WAITING_FOR_WORLD)
        && pausedSimulationTicks >= 0 && safePosition != null && safePosition.isFinite(); }
    public boolean waiting() { return state == WarheadNetworkState.WAITING_FOR_WORLD; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static WarheadNetworkState readState(final int ordinal) {
        if (ordinal < 0 || ordinal >= WarheadNetworkState.values().length)
            throw new IllegalArgumentException("Invalid warhead state");
        return WarheadNetworkState.values()[ordinal];
    }
}
