package com.andye.warmod.entity;

import com.andye.warmod.WarMod;
import com.andye.warmod.acoustics.AcousticEngine;
import com.andye.warmod.acoustics.AcousticSounds;
import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.radar.RadarTrackingService;
import com.andye.warmod.warhead.WarheadImpactService;
import com.andye.warmod.warhead.IncomingWarheadRegistry;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadTrajectory;
import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.network.WarheadVisualNetworking;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class IncomingWarheadEntity extends Entity {
	private UUID warheadId, ownerPlayerId, radarRootTrackId; private Vec3 startPosition=Vec3.ZERO, intendedTarget=Vec3.ZERO;
	private long launchGameTime=Long.MIN_VALUE,visualSeed;private int flightTicks,clusterIndex,clusterCount=1;private boolean impacted,cancelled,sonicBoomEmitted;
	private WarheadPayloadType payloadType=WarheadPayloadType.CONVENTIONAL;
	public IncomingWarheadEntity(final EntityType<IncomingWarheadEntity> type, final Level level) {
		super(type,level); this.noPhysics=true; setNoGravity(true); setSilent(true);
	}
	public IncomingWarheadEntity(final EntityType<IncomingWarheadEntity> type, final ServerLevel level, final UUID warheadId,
		final UUID ownerPlayerId, final Vec3 startPosition, final Vec3 intendedTarget, final long launchGameTime,
		final int flightTicks, final long visualSeed, final WarheadPayloadType payloadType, final UUID radarRootTrackId, final int clusterIndex, final int clusterCount) {
		this(type,level); this.warheadId=Objects.requireNonNull(warheadId); this.ownerPlayerId=ownerPlayerId;
		this.startPosition=Objects.requireNonNull(startPosition); this.intendedTarget=Objects.requireNonNull(intendedTarget);
		this.launchGameTime=launchGameTime; this.flightTicks=flightTicks; this.visualSeed=visualSeed; this.payloadType=Objects.requireNonNull(payloadType); this.radarRootTrackId=Objects.requireNonNull(radarRootTrackId); this.clusterIndex=clusterIndex; this.clusterCount=Math.max(1,clusterCount); setPos(startPosition);
	}
	@Override protected void defineSynchedData(final SynchedEntityData.Builder builder) { }
	@Override public void tick() {
		super.tick(); if(isRemoved()||impacted||!(level() instanceof ServerLevel server)) return;
		IncomingWarheadRegistry.register(server,this);RadarTrackingService.reconcileWarhead(server,this);
		if(!valid()){cancel(server);discard();return;}
		long elapsedGame=server.getGameTime()-launchGameTime; double elapsed=Math.max(0.0,Math.min(Integer.MAX_VALUE,elapsedGame));
		Vec3 previous=WarheadTrajectory.position(startPosition,intendedTarget,Math.max(0,elapsed-1),flightTicks);
		Vec3 next=WarheadTrajectory.position(startPosition,intendedTarget,elapsed,flightTicks);
		if(!next.isFinite()||!previous.isFinite()||!loaded(server,next)){cancel(server);discard();return;}
		RaycastResult raycast=raycastLoaded(server,previous,next); if(raycast.missingChunk()){cancel(server);discard();return;}
		if(raycast.hit().isPresent()) impact(server,raycast.hit().get().getLocation()); else if(elapsedGame>=flightTicks) impact(server,intendedTarget);
		else {Vec3 velocity=WarheadTrajectory.velocity(startPosition,intendedTarget,elapsed,flightTicks);setPos(next);setDeltaMovement(velocity);updateRotation(velocity);emitSonicBoom(server,next,velocity);}
	}
	@Override public boolean hurtServer(final ServerLevel level,final DamageSource source,final float amount){return false;}
	@Override public boolean isPickable(){return false;} @Override public boolean isPushable(){return false;}
	@Override public boolean canBeCollidedWith(final Entity entity){return false;} @Override public boolean isAttackable(){return false;}
	@Override public boolean shouldRenderAtSqrDistance(final double distance){return false;}
	@Override protected void readAdditionalSaveData(final ValueInput input){
		warheadId=input.read("WarheadId",UUIDUtil.STRING_CODEC).orElse(null); ownerPlayerId=input.read("OwnerPlayerId",UUIDUtil.STRING_CODEC).orElse(null); radarRootTrackId=input.read("RadarRootTrackId",UUIDUtil.STRING_CODEC).orElse(warheadId);
		startPosition=input.read("StartPosition",Vec3.CODEC).orElse(Vec3.ZERO); intendedTarget=input.read("IntendedTarget",Vec3.CODEC).orElse(Vec3.ZERO);
		launchGameTime=input.getLongOr("LaunchGameTime",Long.MIN_VALUE); flightTicks=input.getIntOr("FlightTicks",0); visualSeed=input.getLongOr("VisualSeed",0);
		impacted=input.getBooleanOr("Impacted",false);sonicBoomEmitted=input.getBooleanOr("SonicBoomEmitted",false); payloadType=WarheadPayloadType.fromSerializedName(input.getStringOr("PayloadType","conventional")).orElse(WarheadPayloadType.CONVENTIONAL); clusterIndex=input.getIntOr("ClusterIndex",0);clusterCount=Math.max(1,input.getIntOr("ClusterCount",1));
		if(!valid()||impacted){discard();return;} setPos(startPosition);setNoGravity(true);setSilent(true);noPhysics=true;
	}
	@Override protected void addAdditionalSaveData(final ValueOutput output){
		output.storeNullable("WarheadId",UUIDUtil.STRING_CODEC,warheadId); output.storeNullable("OwnerPlayerId",UUIDUtil.STRING_CODEC,ownerPlayerId); output.storeNullable("RadarRootTrackId",UUIDUtil.STRING_CODEC,radarRootTrackId);
		if(startPosition!=null&&startPosition.isFinite())output.store("StartPosition",Vec3.CODEC,startPosition);
		if(intendedTarget!=null&&intendedTarget.isFinite())output.store("IntendedTarget",Vec3.CODEC,intendedTarget);
		output.putLong("LaunchGameTime",launchGameTime);output.putInt("FlightTicks",flightTicks);output.putLong("VisualSeed",visualSeed);
		output.putBoolean("Impacted",impacted);output.putBoolean("SonicBoomEmitted",sonicBoomEmitted);output.putString("PayloadType",payloadType.serializedName());output.putInt("ClusterIndex",clusterIndex);output.putInt("ClusterCount",clusterCount);
	}
	public UUID warheadId(){return warheadId;} public UUID ownerPlayerId(){return ownerPlayerId;} public UUID radarRootTrackId(){return radarRootTrackId==null?warheadId:radarRootTrackId;}
	public Vec3 startPosition(){return startPosition;} public Vec3 intendedTarget(){return intendedTarget;} public long launchGameTime(){return launchGameTime;}
	public int flightTicks(){return flightTicks;} public long visualSeed(){return visualSeed;} public WarheadPayloadType payloadType(){return payloadType;} public int clusterIndex(){return clusterIndex;} public int clusterCount(){return clusterCount;}
 public boolean cancelForInterception(ServerLevel server,UUID interceptorId,Vec3 interceptPosition){if(impacted||cancelled||isRemoved())return false;cancelled=true;impacted=true;WarheadVisualNetworking.sendRemove(server,warheadId,intendedTarget);IncomingWarheadRegistry.unregister(server,radarRootTrackId(),warheadId);discard();return true;} public boolean cancelForPointDefence(ServerLevel server,UUID bulletId,Vec3 hitPosition){return cancelForInterception(server,bulletId,hitPosition);}
	private boolean valid(){return warheadId!=null&&radarRootTrackId()!=null&&startPosition!=null&&intendedTarget!=null&&payloadType!=null&&startPosition.isFinite()&&intendedTarget.isFinite()
		&&startPosition.distanceTo(intendedTarget)<=8192&&launchGameTime!=Long.MIN_VALUE&&flightTicks>=1&&flightTicks<=IcbmConstants.MAXIMUM_TERMINAL_TICKS;}
	private void emitSonicBoom(final ServerLevel server,final Vec3 position,final Vec3 velocity){
		if(impacted||sonicBoomEmitted||!position.isFinite()||!velocity.isFinite())return;
		double normalized=WarheadVisualMath.normalizedSpeed(velocity,WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK*1.65);
		if(normalized>.55&&velocity.y<0){
			AcousticEngine.playSound(server,position,AcousticSounds.TERMINAL_SONIC_BOOM_ID,SoundSource.BLOCKS,1.35F,1.0F);
			sonicBoomEmitted=true;
			if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.info("Warhead {} emitted sonic boom at {}",warheadId,position);
		}
	}
	private void impact(final ServerLevel server,final Vec3 hit){
		if(impacted||!hit.isFinite())return; impacted=true; ServerPlayer owner=null;
		if(ownerPlayerId!=null&&server.getServer()!=null){ServerPlayer p=server.getServer().getPlayerList().getPlayer(ownerPlayerId);if(p!=null&&p.level()==server)owner=p;}
		if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.info("Warhead {} impacted: payload={}, position={}",warheadId,payloadType.serializedName(),hit);
		WarheadImpactService.impact(server,owner,warheadId,radarRootTrackId(),hit,visualSeed,payloadType);IncomingWarheadRegistry.unregister(server,radarRootTrackId(),warheadId);discard();
	}
	private void cancel(final ServerLevel server){if(warheadId!=null&&intendedTarget!=null&&intendedTarget.isFinite())WarheadVisualNetworking.sendRemove(server,warheadId,intendedTarget);}
	private void updateRotation(final Vec3 v){if(v.lengthSqr()<1E-8)return;setYRot((float)(Math.atan2(v.z,v.x)*180/Math.PI)-90);setXRot((float)(-Math.atan2(v.y,v.horizontalDistance())*180/Math.PI));}
	private static boolean loaded(final ServerLevel level,final Vec3 p){return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(p.x),SectionPos.blockToSectionCoord(p.z));}
	private RaycastResult raycastLoaded(final ServerLevel level,final Vec3 from,final Vec3 to){
		AtomicBoolean missing=new AtomicBoolean();ClipContext context=new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this);
		Optional<BlockHitResult> hit=BlockGetter.traverseBlocks(from,to,context,(c,p)->{if(!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(p.getX()),SectionPos.blockToSectionCoord(p.getZ()))){missing.set(true);return Optional.empty();}
			BlockState state=level.getBlockState(p);VoxelShape shape=c.getBlockShape(state,level,p);BlockHitResult result=level.clipWithInteractionOverride(from,to,p,shape,state);return result==null?null:Optional.of(result);},ignored->Optional.empty());
		return new RaycastResult(hit==null?Optional.empty():hit,missing.get());
	}
	private record RaycastResult(Optional<BlockHitResult> hit,boolean missingChunk){}
}
