package com.andye.warmod.icbm.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record ClientboundIcbmLaunchPayload(UUID missileId, Vec3 launchPosition, Vec3 burnoutPosition, Vec3 separationPosition,
	Vec3 intendedTarget, long launchGameTime, int ignitionTicks, int boostTicks, int coastTicks, long visualSeed,
	WarheadPayloadType payloadType) implements CustomPacketPayload {
	public static final Type<ClientboundIcbmLaunchPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(WarMod.MOD_ID,"icbm_launch"));
	public static final StreamCodec<RegistryFriendlyByteBuf,ClientboundIcbmLaunchPayload> STREAM_CODEC=StreamCodec.composite(
		UUIDUtil.STREAM_CODEC,ClientboundIcbmLaunchPayload::missileId,Vec3.STREAM_CODEC,ClientboundIcbmLaunchPayload::launchPosition,
		Vec3.STREAM_CODEC,ClientboundIcbmLaunchPayload::burnoutPosition,Vec3.STREAM_CODEC,ClientboundIcbmLaunchPayload::separationPosition,
		Vec3.STREAM_CODEC,ClientboundIcbmLaunchPayload::intendedTarget,ByteBufCodecs.LONG,ClientboundIcbmLaunchPayload::launchGameTime,
		ByteBufCodecs.VAR_INT,ClientboundIcbmLaunchPayload::ignitionTicks,ByteBufCodecs.VAR_INT,ClientboundIcbmLaunchPayload::boostTicks,
		ByteBufCodecs.VAR_INT,ClientboundIcbmLaunchPayload::coastTicks,ByteBufCodecs.LONG,ClientboundIcbmLaunchPayload::visualSeed,
		WarheadPayloadType.STREAM_CODEC,ClientboundIcbmLaunchPayload::payloadType,ClientboundIcbmLaunchPayload::new);
	public static ClientboundIcbmLaunchPayload fromPlan(final IcbmFlightPlan p){return new ClientboundIcbmLaunchPayload(p.missileId(),p.launchPosition(),p.burnoutPosition(),p.separationPosition(),p.intendedTarget(),p.launchGameTime(),p.ignitionTicks(),p.boostTicks(),p.coastTicks(),p.visualSeed(),p.payloadType());}
	public boolean isWellFormed(){return missileId!=null&&payloadType!=null&&launchPosition!=null&&burnoutPosition!=null&&separationPosition!=null&&intendedTarget!=null&&launchPosition.isFinite()&&burnoutPosition.isFinite()&&separationPosition.isFinite()&&intendedTarget.isFinite()&&ignitionTicks>0&&boostTicks>0&&coastTicks>=IcbmConstants.MINIMUM_COAST_TICKS&&coastTicks<=IcbmConstants.MAXIMUM_COAST_TICKS;}
	@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
