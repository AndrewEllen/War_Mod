package com.andye.warmod.icbm.network;

import com.andye.warmod.icbm.IcbmConstants;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class IcbmVisualNetworking {
	private static boolean registered; private IcbmVisualNetworking() { }
	public static void registerPayloadTypes(){if(registered)return;PayloadTypeRegistry.clientboundPlay().register(ClientboundIcbmLaunchPayload.TYPE,ClientboundIcbmLaunchPayload.STREAM_CODEC);PayloadTypeRegistry.clientboundPlay().register(ClientboundIcbmSeparationPayload.TYPE,ClientboundIcbmSeparationPayload.STREAM_CODEC);PayloadTypeRegistry.clientboundPlay().register(ClientboundIcbmRemovePayload.TYPE,ClientboundIcbmRemovePayload.STREAM_CODEC);registered=true;}
	public static void sendLaunch(final ServerLevel level,final ClientboundIcbmLaunchPayload p){if(p.isWellFormed())send(level,p,p.launchPosition(),routeCenter(p.launchPosition(),p.separationPosition()),p.intendedTarget());}
	public static void sendSeparation(final ServerLevel level,final ClientboundIcbmSeparationPayload p,final Vec3 launch,final Vec3 target){if(p.isWellFormed())send(level,p,launch,p.separationPosition(),target);}
	public static void sendRemove(final ServerLevel level,final UUID id,final Vec3 launch,final Vec3 target){send(level,new ClientboundIcbmRemovePayload(id),launch,routeCenter(launch,target),target);}
	private static void send(final ServerLevel level,final CustomPacketPayload payload,final Vec3...centers){Set<ServerPlayer> recipients=new LinkedHashSet<>();for(ServerPlayer player:PlayerLookup.level(level))for(Vec3 center:centers)if(player.position().distanceToSqr(center)<=IcbmConstants.VISUAL_RANGE_BLOCKS*IcbmConstants.VISUAL_RANGE_BLOCKS){recipients.add(player);break;}for(ServerPlayer player:recipients)ServerPlayNetworking.send(player,payload);}
	private static Vec3 routeCenter(final Vec3 a,final Vec3 b){return a.add(b).scale(.5);}
}
