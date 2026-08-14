package com.andye.warmod.testtool.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.item.component.MasterExplosiveConfig;
import com.andye.warmod.item.component.MasterExplosiveDelivery;
import com.andye.warmod.warhead.WarheadYield;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

public record ServerboundMasterExplosiveConfigPayload(
	InteractionHand hand,
	MasterExplosiveConfig config
) implements CustomPacketPayload {
	public static final Type<ServerboundMasterExplosiveConfigPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "master_explosive_config")
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundMasterExplosiveConfigPayload> STREAM_CODEC =
		StreamCodec.of(ServerboundMasterExplosiveConfigPayload::write, ServerboundMasterExplosiveConfigPayload::read);

	private static void write(
		final RegistryFriendlyByteBuf buffer,
		final ServerboundMasterExplosiveConfigPayload payload
	) {
		buffer.writeByte(payload.hand == InteractionHand.OFF_HAND ? 1 : 0);
		buffer.writeByte(payload.config.delivery().ordinal());
		buffer.writeBoolean(payload.config.cluster());
		buffer.writeByte(payload.config.yield().ordinal());
		buffer.writeBoolean(payload.config.customFire());
	}

	private static ServerboundMasterExplosiveConfigPayload read(final RegistryFriendlyByteBuf buffer) {
		InteractionHand hand = buffer.readUnsignedByte() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		MasterExplosiveDelivery[] deliveries = MasterExplosiveDelivery.values();
		WarheadYield[] yields = WarheadYield.values();
		MasterExplosiveDelivery delivery = deliveries[Math.min(buffer.readUnsignedByte(), deliveries.length - 1)];
		boolean cluster = buffer.readBoolean();
		WarheadYield yield = yields[Math.min(buffer.readUnsignedByte(), yields.length - 1)];
		boolean customFire = buffer.readBoolean();
		return new ServerboundMasterExplosiveConfigPayload(
			hand,
			new MasterExplosiveConfig(delivery, cluster, yield, customFire)
		);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
