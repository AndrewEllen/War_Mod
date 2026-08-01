package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundWarheadLaunchPayload(
	UUID warheadId,
	double startX,
	double startY,
	double startZ,
	double targetX,
	double targetY,
	double targetZ,
	long launchGameTime,
	int flightTicks,
	long visualSeed
) implements CustomPacketPayload {
	public static final Type<ClientboundWarheadLaunchPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_launch")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadLaunchPayload> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC,
		ClientboundWarheadLaunchPayload::warheadId,
		ByteBufCodecs.DOUBLE,
		ClientboundWarheadLaunchPayload::startX,
		ByteBufCodecs.DOUBLE,
		ClientboundWarheadLaunchPayload::startY,
		ByteBufCodecs.DOUBLE,
		ClientboundWarheadLaunchPayload::startZ,
		ByteBufCodecs.DOUBLE,
		ClientboundWarheadLaunchPayload::targetX,
		ByteBufCodecs.DOUBLE,
		ClientboundWarheadLaunchPayload::targetY,
		ByteBufCodecs.DOUBLE,
		ClientboundWarheadLaunchPayload::targetZ,
		ByteBufCodecs.LONG,
		ClientboundWarheadLaunchPayload::launchGameTime,
		ByteBufCodecs.VAR_INT,
		ClientboundWarheadLaunchPayload::flightTicks,
		ByteBufCodecs.LONG,
		ClientboundWarheadLaunchPayload::visualSeed,
		ClientboundWarheadLaunchPayload::new
	);

	public boolean isWellFormed() {
		return this.warheadId != null
			&& finiteCoordinate(this.startX)
			&& finiteCoordinate(this.startY)
			&& finiteCoordinate(this.startZ)
			&& finiteCoordinate(this.targetX)
			&& finiteCoordinate(this.targetY)
			&& finiteCoordinate(this.targetZ)
			&& this.flightTicks >= 1
			&& this.flightTicks <= 200
			&& distanceSquared(this.startX, this.startY, this.startZ, this.targetX, this.targetY, this.targetZ) <= 8192.0 * 8192.0;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static boolean finiteCoordinate(final double coordinate) {
		return Double.isFinite(coordinate) && Math.abs(coordinate) <= 30_000_000.0;
	}

	private static double distanceSquared(
		final double startX,
		final double startY,
		final double startZ,
		final double targetX,
		final double targetY,
		final double targetZ
	) {
		double x = startX - targetX;
		double y = startY - targetY;
		double z = startZ - targetZ;
		return x * x + y * y + z * z;
	}
}