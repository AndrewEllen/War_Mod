package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundWarheadRemovePayload(UUID warheadId) implements CustomPacketPayload {
	public static final Type<ClientboundWarheadRemovePayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_remove")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadRemovePayload> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC,
		ClientboundWarheadRemovePayload::warheadId,
		ClientboundWarheadRemovePayload::new
	);

	public boolean isWellFormed() {
		return this.warheadId != null;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}