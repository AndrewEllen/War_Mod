package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundWarheadImpactPayload(UUID warheadId, double impactX, double impactY, double impactZ,
	long impactGameTime, long visualSeed, WarheadPayloadType payloadType, float impactVisualScale) implements CustomPacketPayload {
	public static final Type<ClientboundWarheadImpactPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_impact"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadImpactPayload> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, ClientboundWarheadImpactPayload::warheadId,
		ByteBufCodecs.DOUBLE, ClientboundWarheadImpactPayload::impactX, ByteBufCodecs.DOUBLE, ClientboundWarheadImpactPayload::impactY,
		ByteBufCodecs.DOUBLE, ClientboundWarheadImpactPayload::impactZ, ByteBufCodecs.LONG, ClientboundWarheadImpactPayload::impactGameTime,
		ByteBufCodecs.LONG, ClientboundWarheadImpactPayload::visualSeed, WarheadPayloadType.STREAM_CODEC, ClientboundWarheadImpactPayload::payloadType,
		ByteBufCodecs.FLOAT, ClientboundWarheadImpactPayload::impactVisualScale, ClientboundWarheadImpactPayload::new);
	public boolean isWellFormed() { return warheadId != null && payloadType != null && finite(impactX) && finite(impactY) && finite(impactZ)
		&& Float.isFinite(impactVisualScale) && impactVisualScale >= 0.05F && impactVisualScale <= 8.0F; }
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	private static boolean finite(final double value) { return Double.isFinite(value) && Math.abs(value) <= 30_000_000.0; }
}