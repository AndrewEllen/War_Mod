package com.andye.warmod.radar;

import com.andye.warmod.WarMod;
import com.andye.warmod.entity.IncomingWarheadEntity;
import com.andye.warmod.icbm.*;
import com.andye.warmod.radar.network.*;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class RadarTrackingService {
	public static final int MAX_ACTIVE_TRACKS_PER_LEVEL=128,MAX_RECENT_IMPACTS_PER_LEVEL=128;
	public static final long RECENT_IMPACT_RETENTION_TICKS=1200,IMPACT_TRACK_RETENTION_TICKS=200;
	private static final UUID SERVER_OWNER=new UUID(0L,0L);
	private static final Map<ServerLevel,State> STATES=new WeakHashMap<>();
	private RadarTrackingService() { }

	public static synchronized void registerIcbm(final ServerLevel level,final IcbmFlightPlan plan){
		State s=state(level);if(s.tracksByRootId.containsKey(plan.missileId()))return;makeRoom(level,s);
		ServerPlayer owner=level.getServer().getPlayerList().getPlayer(plan.ownerPlayerId());String name=owner==null?"SERVER":owner.getGameProfile().name();
		RadarTrack track=new RadarTrack(plan.missileId(),RadarTrackKind.ICBM,plan.ownerPlayerId(),boundedName(name),plan.payloadType(),plan.launchGameTime(),plan,RadarTrackPhase.IGNITION);
		s.tracksByRootId.put(track.trackId,track);upsert(level,track);log("Radar track registered {}",track.trackId);
	}

	public static synchronized void registerDirectWarhead(final ServerLevel level,final ServerPlayer owner,final WarheadLaunchService.LaunchResult launch){
		State s=state(level);if(s.tracksByRootId.containsKey(launch.radarRootTrackId()))return;makeRoom(level,s);
		UUID ownerId=owner==null?SERVER_OWNER:owner.getUUID();String name=owner==null?"SERVER":owner.getGameProfile().name();
		RadarTrack track=new RadarTrack(launch.radarRootTrackId(),RadarTrackKind.DIRECT_WARHEAD,ownerId,boundedName(name),launch.payloadType(),launch.launchGameTime(),null,RadarTrackPhase.PAYLOAD_DELIVERY);
		attachTerminal(s,track,launch);s.tracksByRootId.put(track.trackId,track);upsert(level,track);log("Radar track registered {}",track.trackId);
	}

	public static synchronized void registerTerminalSeparation(final ServerLevel level,final UUID rootId,final WarheadLaunchService.LaunchResult launch){
		State s=state(level);RadarTrack track=s.tracksByRootId.get(rootId);if(track==null)return;attachTerminal(s,track,launch);track.phase=RadarTrackPhase.PAYLOAD_DELIVERY;track.lastStateChangeGameTime=level.getGameTime();upsert(level,track);log("Radar separation registered {}",rootId);
	}

	public static synchronized void reconcileWarhead(final ServerLevel level,final IncomingWarheadEntity entity){
		if(entity.warheadId()==null)return;State s=state(level);UUID root=entity.radarRootTrackId();if(s.tracksByRootId.containsKey(root))return;
		WarheadLaunchService.LaunchResult launch=new WarheadLaunchService.LaunchResult(entity.warheadId(),entity.startPosition(),entity.intendedTarget(),entity.launchGameTime(),entity.flightTicks(),entity.visualSeed(),entity.payloadType(),root);
		ServerPlayer owner=entity.ownerPlayerId()==null?null:level.getServer().getPlayerList().getPlayer(entity.ownerPlayerId());registerDirectWarhead(level,owner,launch);
	}

	public static synchronized void registerImpact(final ServerLevel level,final UUID warheadId,final UUID rootId,final Vec3 position,final WarheadPayloadType payload,final float scale){
		State s=state(level);RadarTrack track=s.tracksByRootId.get(rootId);if(track!=null&&track.phase==RadarTrackPhase.IMPACT)return;
		long now=level.getGameTime();RadarImpactSnapshot impact=new RadarImpactSnapshot(rootId,warheadId,position,now,payload,scale);
		if(track!=null){track.phase=RadarTrackPhase.IMPACT;track.impactPosition=position;track.impactGameTime=now;track.impactVisualScale=scale;track.lastStateChangeGameTime=now;upsert(level,track);}
		s.recentImpacts.removeIf(i->i.rootTrackId().equals(rootId));s.recentImpacts.addLast(impact);while(s.recentImpacts.size()>MAX_RECENT_IMPACTS_PER_LEVEL)s.recentImpacts.removeFirst();
		RadarSubscriptionManager.broadcast(level,new ClientboundRadarImpactPayload(impact));log("Radar impact registered {}",rootId);
	}

	public static synchronized void removeTrack(final ServerLevel level,final UUID rootId,final RadarRemovalReason reason){State s=STATES.get(level);if(s==null)return;RadarTrack removed=s.tracksByRootId.remove(rootId);if(removed==null)return;if(removed.terminalWarheadId!=null)s.terminalWarheadToRootTrack.remove(removed.terminalWarheadId);RadarSubscriptionManager.broadcast(level,new ClientboundRadarTrackRemovePayload(rootId,reason));log("Radar track removed {} ({})",rootId,reason);}
	public static synchronized List<RadarTrackSnapshot> snapshotTracks(final ServerLevel level){State s=STATES.get(level);if(s==null)return List.of();return s.tracksByRootId.values().stream().map(RadarTrack::snapshot).toList();}
	public static synchronized List<RadarImpactSnapshot> snapshotImpacts(final ServerLevel level){State s=STATES.get(level);return s==null?List.of():List.copyOf(s.recentImpacts);}
	public static synchronized void reconcileIcbmFlights(final ServerLevel level){for(IcbmFlightPlan plan:IcbmFlightControllerManager.snapshot(level))registerIcbm(level,plan);}

	public static synchronized void tick(final ServerLevel level){State s=STATES.get(level);if(s==null)return;long now=level.getGameTime();
		for(RadarTrack track:new ArrayList<>(s.tracksByRootId.values())){if(track.phase==RadarTrackPhase.IMPACT){if(now-track.impactGameTime>IMPACT_TRACK_RETENTION_TICKS)removeTrack(level,track.trackId,RadarRemovalReason.EXPIRED);continue;}if(track.carrierFlightPlan!=null&&track.terminalWarheadId==null){long elapsed=Math.max(0,now-track.carrierFlightPlan.launchGameTime());RadarTrackPhase phase=elapsed<track.carrierFlightPlan.ignitionTicks()?RadarTrackPhase.IGNITION:elapsed<track.carrierFlightPlan.ignitionTicks()+track.carrierFlightPlan.boostTicks()?RadarTrackPhase.BOOST:RadarTrackPhase.MIDCOURSE;if(phase!=track.phase){track.phase=phase;track.lastStateChangeGameTime=now;upsert(level,track);}}}
		s.recentImpacts.removeIf(i->now-i.impactGameTime()>RECENT_IMPACT_RETENTION_TICKS);
	}
	public static synchronized void clear(final ServerLevel level){STATES.remove(level);}
	public static synchronized void clearAll(){STATES.clear();}
	private static void attachTerminal(final State s,final RadarTrack t,final WarheadLaunchService.LaunchResult l){t.terminalWarheadId=l.warheadId();t.terminalStartPosition=l.startPosition();t.terminalTargetPosition=l.intendedTarget();t.terminalLaunchGameTime=l.launchGameTime();t.terminalFlightTicks=l.flightTicks();t.terminalVisualSeed=l.visualSeed();s.terminalWarheadToRootTrack.put(l.warheadId(),t.trackId);}
	private static State state(final ServerLevel l){return STATES.computeIfAbsent(l,k->new State());}
	private static void makeRoom(final ServerLevel l,final State s){while(s.tracksByRootId.size()>=MAX_ACTIVE_TRACKS_PER_LEVEL){UUID id=s.tracksByRootId.keySet().iterator().next();removeTrack(l,id,RadarRemovalReason.EVICTED);}}
	private static void upsert(final ServerLevel l,final RadarTrack t){RadarSubscriptionManager.broadcast(l,new ClientboundRadarTrackUpsertPayload(l.getGameTime(),t.snapshot()));}
	private static String boundedName(final String s){return s==null||s.isBlank()?"SERVER":s.substring(0,Math.min(64,s.length()));}
	private static void log(final String format,final Object...args){if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.info(format,args);}
	private static final class State{final LinkedHashMap<UUID,RadarTrack> tracksByRootId=new LinkedHashMap<>();final Map<UUID,UUID> terminalWarheadToRootTrack=new HashMap<>();final ArrayDeque<RadarImpactSnapshot> recentImpacts=new ArrayDeque<>();}
}
