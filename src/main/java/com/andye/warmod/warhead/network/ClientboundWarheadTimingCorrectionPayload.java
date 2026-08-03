package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record ClientboundWarheadTimingCorrectionPayload(UUID warheadId, long serverGameTime, int pausedSimulationTicks, boolean waiting, Vec3 safePosition) implements CustomPacketPayload {
    public static final Type<ClientboundWarheadTimingCorrectionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_timing_correction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadTimingCorrectionPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
        buffer.writeUUID(payload.warheadId); buffer.writeLong(payload.serverGameTime); buffer.writeVarInt(payload.pausedSimulationTicks); buffer.writeBoolean(payload.waiting); buffer.writeDouble(payload.safePosition.x); buffer.writeDouble(payload.safePosition.y); buffer.writeDouble(payload.safePosition.z);
    }, buffer -> new ClientboundWarheadTimingCorrectionPayload(buffer.readUUID(), buffer.readLong(), buffer.readVarInt(), buffer.readBoolean(), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())));
    public boolean isWellFormed() { return warheadId != null && pausedSimulationTicks >= 0 && safePosition != null && safePosition.isFinite(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}