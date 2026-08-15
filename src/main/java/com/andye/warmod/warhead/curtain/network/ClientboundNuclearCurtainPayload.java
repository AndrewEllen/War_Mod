package com.andye.warmod.warhead.curtain.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Self-contained shell-crossing update for the independent destruction curtain. */
public record ClientboundNuclearCurtainPayload(UUID impactId, long serverGameTime,
    double centerX, double centerY, double centerZ, long visualSeed, float visualScale,
    float previousRadius, float currentRadius, boolean finalBand) implements CustomPacketPayload {
    public static final Type<ClientboundNuclearCurtainPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "nuclear_destruction_curtain"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundNuclearCurtainPayload>
        STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.impactId); buffer.writeLong(payload.serverGameTime);
            buffer.writeDouble(payload.centerX); buffer.writeDouble(payload.centerY);
            buffer.writeDouble(payload.centerZ); buffer.writeLong(payload.visualSeed);
            buffer.writeFloat(payload.visualScale); buffer.writeFloat(payload.previousRadius);
            buffer.writeFloat(payload.currentRadius); buffer.writeBoolean(payload.finalBand);
        }, buffer -> new ClientboundNuclearCurtainPayload(buffer.readUUID(), buffer.readLong(),
            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readLong(),
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean()));

    public boolean isWellFormed() {
        return impactId != null && Double.isFinite(centerX) && Double.isFinite(centerY)
            && Double.isFinite(centerZ) && Float.isFinite(visualScale)
            && Float.isFinite(previousRadius) && Float.isFinite(currentRadius)
            && visualScale >= 0.05F && visualScale <= 8.0F && previousRadius >= 0.0F
            && currentRadius >= previousRadius && currentRadius <= 2_048.0F;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
