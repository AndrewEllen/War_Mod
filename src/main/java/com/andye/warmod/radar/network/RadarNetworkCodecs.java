package com.andye.warmod.radar.network;

import com.andye.warmod.radar.*;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public final class RadarNetworkCodecs {
	static final int MAX_TRACKS=128,MAX_IMPACTS=128,MAX_TILES=128;
	private RadarNetworkCodecs() { }
	public static void writeVec(final RegistryFriendlyByteBuf b,final Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
	public static Vec3 readVec(final RegistryFriendlyByteBuf b){Vec3 v=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());if(!v.isFinite()||Math.abs(v.x)>30_000_000||Math.abs(v.z)>30_000_000||Math.abs(v.y)>32768)throw new IllegalArgumentException("Invalid radar coordinate");return v;}
	public static void writeTrack(final RegistryFriendlyByteBuf b,final RadarTrackSnapshot s){b.writeUUID(s.trackId());b.writeVarInt(s.kind().ordinal());b.writeUUID(s.ownerPlayerId());b.writeUtf(s.ownerDisplayName(),64);b.writeVarInt(s.payloadType().ordinal());b.writeVarInt(s.phase().ordinal());b.writeBoolean(s.carrierPlan().isPresent());s.carrierPlan().ifPresent(p->writeCarrier(b,p));b.writeBoolean(s.terminalPlan().isPresent());s.terminalPlan().ifPresent(p->writeTerminal(b,p));}
	public static RadarTrackSnapshot readTrack(final RegistryFriendlyByteBuf b){UUID id=b.readUUID();RadarTrackKind kind=enumAt(RadarTrackKind.values(),b.readVarInt());UUID owner=b.readUUID();String name=b.readUtf(64);WarheadPayloadType payload=enumAt(WarheadPayloadType.values(),b.readVarInt());RadarTrackPhase phase=enumAt(RadarTrackPhase.values(),b.readVarInt());Optional<RadarCarrierPlanSnapshot> carrier=b.readBoolean()?Optional.of(readCarrier(b)):Optional.empty();Optional<RadarTerminalPlanSnapshot> terminal=b.readBoolean()?Optional.of(readTerminal(b)):Optional.empty();return new RadarTrackSnapshot(id,kind,owner,name,payload,phase,carrier,terminal);}
	private static void writeCarrier(final RegistryFriendlyByteBuf b,final RadarCarrierPlanSnapshot p){writeVec(b,p.launchPosition());writeVec(b,p.burnoutPosition());writeVec(b,p.separationPosition());writeVec(b,p.intendedTarget());b.writeLong(p.launchGameTime());b.writeVarInt(p.ignitionTicks());b.writeVarInt(p.boostTicks());b.writeVarInt(p.coastTicks());b.writeLong(p.visualSeed());}
	private static RadarCarrierPlanSnapshot readCarrier(final RegistryFriendlyByteBuf b){Vec3 a=readVec(b),c=readVec(b),d=readVec(b),e=readVec(b);long time=b.readLong();int ignition=duration(b,20),boost=duration(b,200),coast=duration(b,1000);long seed=b.readLong();if(a.distanceTo(e)>32768||a.distanceTo(c)>32768||c.distanceTo(d)>32768)throw new IllegalArgumentException("Radar carrier route too long");return new RadarCarrierPlanSnapshot(a,c,d,e,time,ignition,boost,coast,seed);}
	private static void writeTerminal(final RegistryFriendlyByteBuf b,final RadarTerminalPlanSnapshot p){b.writeUUID(p.warheadId());writeVec(b,p.startPosition());writeVec(b,p.targetPosition());b.writeLong(p.launchGameTime());b.writeVarInt(p.flightTicks());b.writeLong(p.visualSeed());}
	private static RadarTerminalPlanSnapshot readTerminal(final RegistryFriendlyByteBuf b){UUID id=b.readUUID();Vec3 start=readVec(b),target=readVec(b);long time=b.readLong();int ticks=duration(b,1000);long seed=b.readLong();if(start.distanceTo(target)>8192)throw new IllegalArgumentException("Radar terminal route too long");return new RadarTerminalPlanSnapshot(id,start,target,time,ticks,seed);}
	static void writeImpact(final RegistryFriendlyByteBuf b,final RadarImpactSnapshot s){b.writeUUID(s.rootTrackId());b.writeUUID(s.terminalWarheadId());writeVec(b,s.impactPosition());b.writeLong(s.impactGameTime());b.writeVarInt(s.payloadType().ordinal());b.writeFloat(s.impactVisualScale());}
	static RadarImpactSnapshot readImpact(final RegistryFriendlyByteBuf b){UUID root=b.readUUID(),terminal=b.readUUID();Vec3 p=readVec(b);long time=b.readLong();WarheadPayloadType type=enumAt(WarheadPayloadType.values(),b.readVarInt());float scale=b.readFloat();if(!Float.isFinite(scale)||scale<=0||scale>32)throw new IllegalArgumentException("Invalid radar impact scale");return new RadarImpactSnapshot(root,terminal,p,time,type,scale);}
	public static void writeTracks(final RegistryFriendlyByteBuf b,final List<RadarTrackSnapshot> tracks){b.writeVarInt(Math.min(MAX_TRACKS,tracks.size()));for(int i=0;i<Math.min(MAX_TRACKS,tracks.size());i++)writeTrack(b,tracks.get(i));}
	static List<RadarTrackSnapshot> readTracks(final RegistryFriendlyByteBuf b){int n=count(b,MAX_TRACKS);List<RadarTrackSnapshot> r=new ArrayList<>(n);for(int i=0;i<n;i++)r.add(readTrack(b));return List.copyOf(r);}
	static void writeImpacts(final RegistryFriendlyByteBuf b,final List<RadarImpactSnapshot> impacts){b.writeVarInt(Math.min(MAX_IMPACTS,impacts.size()));for(int i=0;i<Math.min(MAX_IMPACTS,impacts.size());i++)writeImpact(b,impacts.get(i));}
	static List<RadarImpactSnapshot> readImpacts(final RegistryFriendlyByteBuf b){int n=count(b,MAX_IMPACTS);List<RadarImpactSnapshot> r=new ArrayList<>(n);for(int i=0;i<n;i++)r.add(readImpact(b));return List.copyOf(r);}
	static int count(final RegistryFriendlyByteBuf b,final int max){int n=b.readVarInt();if(n<0||n>max)throw new IllegalArgumentException("Radar list bound");return n;}
	private static int duration(final RegistryFriendlyByteBuf b,final int max){int n=b.readVarInt();if(n<=0||n>max)throw new IllegalArgumentException("Invalid radar duration");return n;}
	private static <T>T enumAt(final T[] values,final int ordinal){if(ordinal<0||ordinal>=values.length)throw new IllegalArgumentException("Invalid radar enum");return values[ordinal];}
}
