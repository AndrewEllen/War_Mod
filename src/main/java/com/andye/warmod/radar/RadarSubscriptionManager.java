package com.andye.warmod.radar;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.IcbmFlightControllerManager;
import com.andye.warmod.radar.network.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class RadarSubscriptionManager {
	private static final Map<UUID,Subscription> SUBSCRIPTIONS=new HashMap<>();
	private RadarSubscriptionManager() { }
	public static synchronized void open(final ServerPlayer player){if(!RadarAccess.hasRadarAccess(player)){ServerPlayNetworking.send(player,new ClientboundCloseRadarPayload());return;}ServerLevel level=player.level();RadarTrackingService.reconcileIcbmFlights(level);SUBSCRIPTIONS.put(player.getUUID(),new Subscription(level,level.getGameTime()));ServerPlayNetworking.send(player,new ClientboundOpenRadarPayload(level.dimension().identifier(),level.getGameTime()));sendSnapshot(player);if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.info("Radar subscription opened {}",player.getUUID());}
	public static synchronized void close(final ServerPlayer player,final boolean notify){SUBSCRIPTIONS.remove(player.getUUID());if(notify)ServerPlayNetworking.send(player,new ClientboundCloseRadarPayload());}
	public static synchronized void resync(final ServerPlayer player){if(!RadarAccess.hasRadarAccess(player)){close(player,true);return;}if(!SUBSCRIPTIONS.containsKey(player.getUUID())){open(player);return;}sendSnapshot(player);}
	public static synchronized void broadcast(final ServerLevel level,final CustomPacketPayload payload){for(ServerPlayer player:level.players()){Subscription sub=SUBSCRIPTIONS.get(player.getUUID());if(sub!=null&&sub.level==level)ServerPlayNetworking.send(player,payload);}}
	public static synchronized void tick(final ServerLevel level){long now=level.getGameTime();for(ServerPlayer player:new ArrayList<>(level.players())){Subscription sub=SUBSCRIPTIONS.get(player.getUUID());if(sub==null)continue;if(sub.level!=level||!RadarAccess.hasRadarAccess(player)){close(player,true);continue;}if(now-sub.lastSnapshot>=200){sendSnapshot(player);SUBSCRIPTIONS.put(player.getUUID(),new Subscription(level,now));}}}
	public static synchronized void disconnect(final ServerPlayer player){close(player,false);}
	public static synchronized void stop(final MinecraftServer server){for(ServerPlayer player:server.getPlayerList().getPlayers())if(SUBSCRIPTIONS.containsKey(player.getUUID()))close(player,true);SUBSCRIPTIONS.clear();}
	private static void sendSnapshot(final ServerPlayer player){ServerLevel level=player.level();ServerPlayNetworking.send(player,new ClientboundRadarSnapshotPayload(level.dimension().identifier(),level.getGameTime(),RadarTrackingService.snapshotTracks(level),RadarTrackingService.snapshotImpacts(level)));}
	private record Subscription(ServerLevel level,long lastSnapshot) { }
}
