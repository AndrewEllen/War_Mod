package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadEffectProfile;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundWarheadImpactPayload(UUID warheadId, long stateSequence,
    WarheadNetworkState state, long serverGameTime, double impactX, double impactY,
    double impactZ, long impactGameTime, long visualSeed, WarheadPayloadType payloadType,
    float impactVisualScale, float windX, float windZ,
    WarheadEffectProfile effectProfile)
    implements CustomPacketPayload {
    public static final Type<ClientboundWarheadImpactPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadImpactPayload>
        STREAM_CODEC = StreamCodec.of(ClientboundWarheadImpactPayload::write,
            ClientboundWarheadImpactPayload::read);

    public ClientboundWarheadImpactPayload(final UUID warheadId, final double impactX,
        final double impactY, final double impactZ, final long impactGameTime,
        final long visualSeed, final WarheadPayloadType payloadType,
        final float impactVisualScale, final WarheadEffectProfile effectProfile) {
        this(warheadId, 1L, WarheadNetworkState.IMPACTED, impactGameTime, impactX,
            impactY, impactZ, impactGameTime, visualSeed, payloadType, impactVisualScale,
            0.0F, 0.0F, effectProfile);
    }

    public ClientboundWarheadImpactPayload(final UUID warheadId, final double impactX,
        final double impactY, final double impactZ, final long impactGameTime,
        final long visualSeed, final WarheadPayloadType payloadType,
        final float impactVisualScale, final float windX, final float windZ,
        final WarheadEffectProfile effectProfile) {
        this(warheadId, 1L, WarheadNetworkState.IMPACTED, impactGameTime, impactX,
            impactY, impactZ, impactGameTime, visualSeed, payloadType, impactVisualScale,
            windX, windZ, effectProfile);
    }

    public ClientboundWarheadImpactPayload withAuthoritativeState(final long sequence,
        final long currentServerGameTime) {
        return new ClientboundWarheadImpactPayload(warheadId, sequence,
            WarheadNetworkState.IMPACTED, currentServerGameTime, impactX, impactY, impactZ,
            impactGameTime, visualSeed, payloadType, impactVisualScale, windX, windZ,
            effectProfile);
    }

    public boolean isWellFormed() {
        return warheadId != null && stateSequence > 0L && state == WarheadNetworkState.IMPACTED
            && payloadType != null && effectProfile != null && Double.isFinite(impactX)
            && Double.isFinite(impactY) && Double.isFinite(impactZ)
            && Float.isFinite(impactVisualScale) && impactVisualScale >= 0.05F
            && impactVisualScale <= 8.0F && Float.isFinite(windX)
            && Float.isFinite(windZ) && Math.abs(windX) <= 2.5F
            && Math.abs(windZ) <= 2.5F;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void write(final RegistryFriendlyByteBuf buffer,
        final ClientboundWarheadImpactPayload payload) {
        buffer.writeUUID(payload.warheadId); buffer.writeLong(payload.stateSequence);
        buffer.writeVarInt(payload.state.ordinal()); buffer.writeLong(payload.serverGameTime);
        buffer.writeDouble(payload.impactX); buffer.writeDouble(payload.impactY);
        buffer.writeDouble(payload.impactZ); buffer.writeLong(payload.impactGameTime);
        buffer.writeLong(payload.visualSeed);
        WarheadPayloadType.STREAM_CODEC.encode(buffer, payload.payloadType);
        buffer.writeFloat(payload.impactVisualScale);
        buffer.writeFloat(payload.windX); buffer.writeFloat(payload.windZ);
        buffer.writeVarInt(payload.effectProfile.ordinal());
    }

    private static ClientboundWarheadImpactPayload read(final RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID(); long sequence = buffer.readLong();
        int stateOrdinal = buffer.readVarInt();
        if (stateOrdinal != WarheadNetworkState.IMPACTED.ordinal())
            throw new IllegalArgumentException("Invalid impact state");
        long serverTime = buffer.readLong();
        double x = buffer.readDouble(), y = buffer.readDouble(), z = buffer.readDouble();
        long impactTime = buffer.readLong(), seed = buffer.readLong();
        WarheadPayloadType payload = WarheadPayloadType.STREAM_CODEC.decode(buffer);
        float scale = buffer.readFloat(); float windX = buffer.readFloat();
        float windZ = buffer.readFloat(); int effectOrdinal = buffer.readVarInt();
        if (effectOrdinal < 0 || effectOrdinal >= WarheadEffectProfile.values().length)
            throw new IllegalArgumentException("Invalid warhead effect profile");
        return new ClientboundWarheadImpactPayload(id, sequence,
            WarheadNetworkState.IMPACTED, serverTime, x, y, z, impactTime, seed,
            payload, scale, windX, windZ, WarheadEffectProfile.values()[effectOrdinal]);
    }
}
