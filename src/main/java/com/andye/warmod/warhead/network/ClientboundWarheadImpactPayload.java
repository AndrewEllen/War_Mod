package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundWarheadImpactPayload(
	UUID warheadId,
	double impactX,
	double impactY,
	double impactZ,
	long impactGameTime,
	long visualSeed,
	float visualScale
) implements CustomPacketPayload {
	public static final Type<ClientboundWarheadImpactPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_impact")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadImpactPayload> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC,
		ClientboundWarheadImpactPayload::warheadId,
		ByteBufCodecs.DOUBLE,
		ClientboundWarheadImpactPayload::impactX,
		ByteBufCodecs.DOUBLE,
		ClientboundWarheadImpactPayload::impactY,
		ByteBufCodecs.DOUBLE,
		ClientboundWarheadImpactPayload::impactZ,
		ByteBufCodecs.LONG,
		ClientboundWarheadImpactPayload::impactGameTime,
		ByteBufCodecs.LONG,
		ClientboundWarheadImpactPayload::visualSeed,
		ByteBufCodecs.FLOAT,
		ClientboundWarheadImpactPayload::visualScale,
		ClientboundWarheadImpactPayload::new
	);

	public boolean isWellFormed() {
		return this.warheadId != null
			&& finiteCoordinate(this.impactX)
			&& finiteCoordinate(this.impactY)
			&& finiteCoordinate(this.impactZ)
			&& Float.isFinite(this.visualScale)
			&& this.visualScale >= 0.05F
			&& this.visualScale <= 8.0F;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static boolean finiteCoordinate(final double coordinate) {
		return Double.isFinite(coordinate) && Math.abs(coordinate) <= 30_000_000.0;
	}
}