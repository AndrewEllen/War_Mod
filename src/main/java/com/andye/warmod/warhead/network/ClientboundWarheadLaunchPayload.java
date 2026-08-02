package com.andye.warmod.warhead.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.icbm.IcbmConstants;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientboundWarheadLaunchPayload(UUID warheadId, double startX, double startY, double startZ,
	double targetX, double targetY, double targetZ, long launchGameTime, int flightTicks, long visualSeed,
	WarheadPayloadType payloadType) implements CustomPacketPayload {
	public static final Type<ClientboundWarheadLaunchPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "warhead_launch"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarheadLaunchPayload> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, ClientboundWarheadLaunchPayload::warheadId,
		ByteBufCodecs.DOUBLE, ClientboundWarheadLaunchPayload::startX, ByteBufCodecs.DOUBLE, ClientboundWarheadLaunchPayload::startY,
		ByteBufCodecs.DOUBLE, ClientboundWarheadLaunchPayload::startZ, ByteBufCodecs.DOUBLE, ClientboundWarheadLaunchPayload::targetX,
		ByteBufCodecs.DOUBLE, ClientboundWarheadLaunchPayload::targetY, ByteBufCodecs.DOUBLE, ClientboundWarheadLaunchPayload::targetZ,
		ByteBufCodecs.LONG, ClientboundWarheadLaunchPayload::launchGameTime, ByteBufCodecs.VAR_INT, ClientboundWarheadLaunchPayload::flightTicks,
		ByteBufCodecs.LONG, ClientboundWarheadLaunchPayload::visualSeed, WarheadPayloadType.STREAM_CODEC, ClientboundWarheadLaunchPayload::payloadType,
		ClientboundWarheadLaunchPayload::new);
	public boolean isWellFormed() {
		return warheadId != null && payloadType != null && finite(startX) && finite(startY) && finite(startZ) && finite(targetX)
			&& finite(targetY) && finite(targetZ) && flightTicks >= 1 && flightTicks <= IcbmConstants.MAXIMUM_TERMINAL_TICKS
			&& new net.minecraft.world.phys.Vec3(startX,startY,startZ).distanceToSqr(new net.minecraft.world.phys.Vec3(targetX,targetY,targetZ)) <= 8192.0*8192.0;
	}
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	private static boolean finite(final double value) { return Double.isFinite(value) && Math.abs(value) <= 30_000_000.0; }
}