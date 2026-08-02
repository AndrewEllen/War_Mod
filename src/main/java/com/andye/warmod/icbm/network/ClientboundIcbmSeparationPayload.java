package com.andye.warmod.icbm.network;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record ClientboundIcbmSeparationPayload(UUID missileId,UUID terminalWarheadId,Vec3 separationPosition,
	Vec3 carrierVelocity,long separationGameTime,long visualSeed,WarheadPayloadType payloadType) implements CustomPacketPayload {
	public static final Type<ClientboundIcbmSeparationPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(WarMod.MOD_ID,"icbm_separation"));
	public static final StreamCodec<RegistryFriendlyByteBuf,ClientboundIcbmSeparationPayload> STREAM_CODEC=StreamCodec.composite(
		UUIDUtil.STREAM_CODEC,ClientboundIcbmSeparationPayload::missileId,UUIDUtil.STREAM_CODEC,ClientboundIcbmSeparationPayload::terminalWarheadId,
		Vec3.STREAM_CODEC,ClientboundIcbmSeparationPayload::separationPosition,Vec3.STREAM_CODEC,ClientboundIcbmSeparationPayload::carrierVelocity,
		ByteBufCodecs.LONG,ClientboundIcbmSeparationPayload::separationGameTime,ByteBufCodecs.LONG,ClientboundIcbmSeparationPayload::visualSeed,
		WarheadPayloadType.STREAM_CODEC,ClientboundIcbmSeparationPayload::payloadType,ClientboundIcbmSeparationPayload::new);
	public boolean isWellFormed(){return missileId!=null&&terminalWarheadId!=null&&separationPosition!=null&&carrierVelocity!=null&&payloadType!=null&&separationPosition.isFinite()&&carrierVelocity.isFinite();}
	@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
