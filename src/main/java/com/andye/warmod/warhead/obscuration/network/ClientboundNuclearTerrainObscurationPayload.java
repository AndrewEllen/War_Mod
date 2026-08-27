package com.andye.warmod.warhead.obscuration.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Authoritative mutation-progress update for persistent nuclear terrain dust. */
public record ClientboundNuclearTerrainObscurationPayload(UUID impactId, long serverGameTime,
    double centerX, double centerY, double centerZ, long visualSeed, float visualScale,
    float destructionRadius, float previousMutationRadius, float currentMutationRadius,
    float completedInteriorRadius, boolean finalBand) implements CustomPacketPayload {
    public static final Type<ClientboundNuclearTerrainObscurationPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "nuclear_terrain_obscuration"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        ClientboundNuclearTerrainObscurationPayload>
        STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeUUID(payload.impactId); buffer.writeLong(payload.serverGameTime);
            buffer.writeDouble(payload.centerX); buffer.writeDouble(payload.centerY);
            buffer.writeDouble(payload.centerZ); buffer.writeLong(payload.visualSeed);
            buffer.writeFloat(payload.visualScale); buffer.writeFloat(payload.destructionRadius);
            buffer.writeFloat(payload.previousMutationRadius);
            buffer.writeFloat(payload.currentMutationRadius);
            buffer.writeFloat(payload.completedInteriorRadius);
            buffer.writeBoolean(payload.finalBand);
        }, buffer -> new ClientboundNuclearTerrainObscurationPayload(buffer.readUUID(),
            buffer.readLong(),
            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readLong(),
            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
            buffer.readFloat(), buffer.readBoolean()));

    public boolean isWellFormed() {
        return impactId != null && Double.isFinite(centerX) && Double.isFinite(centerY)
            && Double.isFinite(centerZ) && Float.isFinite(visualScale)
            && Float.isFinite(destructionRadius) && Float.isFinite(previousMutationRadius)
            && Float.isFinite(currentMutationRadius)
            && Float.isFinite(completedInteriorRadius)
            && visualScale >= 0.05F && visualScale <= 8.0F
            && destructionRadius > 0.0F && destructionRadius <= 2_048.0F
            && previousMutationRadius >= 0.0F
            && currentMutationRadius >= previousMutationRadius
            && currentMutationRadius <= destructionRadius + 0.01F
            && completedInteriorRadius >= 0.0F
            && completedInteriorRadius <= currentMutationRadius + 0.01F;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
