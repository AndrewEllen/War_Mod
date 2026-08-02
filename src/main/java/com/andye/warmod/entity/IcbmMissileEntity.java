package com.andye.warmod.entity;

import com.andye.warmod.WarMod;
import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.warhead.WarheadLaunchService;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

public final class IcbmMissileEntity extends Entity {
	private IcbmFlightPlan flightPlan;private boolean separated;
	public IcbmMissileEntity(final EntityType<IcbmMissileEntity> type,final Level level){super(type,level);noPhysics=true;setNoGravity(true);setSilent(true);}
	public IcbmMissileEntity(final EntityType<IcbmMissileEntity> type,final ServerLevel level,final IcbmFlightPlan plan){this(type,level);flightPlan=plan;setPos(plan.launchPosition());}
	@Override protected void defineSynchedData(final SynchedEntityData.Builder builder){}
	@Override public void tick(){super.tick();if(isRemoved()||separated||!(level() instanceof ServerLevel server))return;if(flightPlan==null){cancel(server,"invalid state");return;}
		double elapsed=Math.max(0,server.getGameTime()-flightPlan.launchGameTime());Vec3 previous=IcbmTrajectory.position(flightPlan,Math.max(0,elapsed-1)),next=IcbmTrajectory.position(flightPlan,elapsed);
		if(!next.isFinite()||!loaded(server,next)){cancel(server,"unloaded chunk");return;}RaycastResult ray=raycast(server,previous,next);if(ray.missingChunk()){cancel(server,"unloaded chunk");return;}if(ray.hit().isPresent()&&elapsed<flightPlan.separationTick()){cancel(server,"route collision");return;}
		setPos(next);Vec3 velocity=IcbmTrajectory.velocity(flightPlan,elapsed);setDeltaMovement(velocity);updateRotation(velocity);if(elapsed>=flightPlan.separationTick())separate(server,velocity);
	}
	private void separate(final ServerLevel server,final Vec3 velocity){if(separated)return;separated=true;ServerPlayer owner=null;if(server.getServer()!=null){ServerPlayer p=server.getServer().getPlayerList().getPlayer(flightPlan.ownerPlayerId());if(p!=null&&p.level()==server)owner=p;}
		Optional<WarheadLaunchService.LaunchResult> result=WarheadLaunchService.launchFromCarrier(server,owner,flightPlan.separationPosition(),flightPlan.intendedTarget(),flightPlan.visualSeed(),flightPlan.payloadType());
		if(result.isEmpty()){cancel(server,"terminal launch failure");return;}WarheadLaunchService.LaunchResult terminal=result.get();
		IcbmVisualNetworking.sendSeparation(server,new ClientboundIcbmSeparationPayload(flightPlan.missileId(),terminal.warheadId(),flightPlan.separationPosition(),velocity,server.getGameTime(),flightPlan.visualSeed(),flightPlan.payloadType()),flightPlan.launchPosition(),flightPlan.intendedTarget());
		if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.info("ICBM {} separated {} terminal warhead {} at {}",flightPlan.missileId(),flightPlan.payloadType().serializedName(),terminal.warheadId(),flightPlan.separationPosition());discard();}
	private void cancel(final ServerLevel server,final String reason){if(flightPlan!=null)IcbmVisualNetworking.sendRemove(server,flightPlan.missileId(),flightPlan.launchPosition(),flightPlan.intendedTarget());if(SharedConstants.IS_RUNNING_IN_IDE&&flightPlan!=null)WarMod.LOGGER.info("ICBM {} cancelled: {}",flightPlan.missileId(),reason);discard();}
	@Override public boolean hurtServer(final ServerLevel l,final DamageSource s,final float a){return false;}@Override public boolean isPickable(){return false;}@Override public boolean isPushable(){return false;}@Override public boolean canBeCollidedWith(final Entity e){return false;}@Override public boolean isAttackable(){return false;}@Override public boolean shouldRenderAtSqrDistance(final double d){return false;}
	@Override protected void addAdditionalSaveData(final ValueOutput o){if(flightPlan==null)return;o.store("MissileId",UUIDUtil.STRING_CODEC,flightPlan.missileId());o.store("OwnerId",UUIDUtil.STRING_CODEC,flightPlan.ownerPlayerId());o.store("Launch",Vec3.CODEC,flightPlan.launchPosition());o.store("Burnout",Vec3.CODEC,flightPlan.burnoutPosition());o.store("Separation",Vec3.CODEC,flightPlan.separationPosition());o.store("Target",Vec3.CODEC,flightPlan.intendedTarget());o.putLong("LaunchTime",flightPlan.launchGameTime());o.putInt("Ignition",flightPlan.ignitionTicks());o.putInt("Boost",flightPlan.boostTicks());o.putInt("Coast",flightPlan.coastTicks());o.putLong("Seed",flightPlan.visualSeed());o.putString("Payload",flightPlan.payloadType().serializedName());o.putBoolean("Separated",separated);}
	@Override protected void readAdditionalSaveData(final ValueInput i){try{UUID id=i.read("MissileId",UUIDUtil.STRING_CODEC).orElse(null),owner=i.read("OwnerId",UUIDUtil.STRING_CODEC).orElse(null);Vec3 launch=i.read("Launch",Vec3.CODEC).orElse(null),burnout=i.read("Burnout",Vec3.CODEC).orElse(null),separation=i.read("Separation",Vec3.CODEC).orElse(null),target=i.read("Target",Vec3.CODEC).orElse(null);WarheadPayloadType payload=WarheadPayloadType.fromSerializedName(i.getStringOr("Payload","conventional")).orElse(WarheadPayloadType.CONVENTIONAL);flightPlan=new IcbmFlightPlan(id,owner,launch,burnout,separation,target,i.getLongOr("LaunchTime",Long.MIN_VALUE),i.getIntOr("Ignition",0),i.getIntOr("Boost",0),i.getIntOr("Coast",0),i.getLongOr("Seed",0),payload);separated=i.getBooleanOr("Separated",false);if(separated)discard();else setPos(IcbmTrajectory.position(flightPlan,Math.max(0,level().getGameTime()-flightPlan.launchGameTime())));}catch(RuntimeException ex){flightPlan=null;discard();}}
	private void updateRotation(final Vec3 v){if(v.lengthSqr()<1E-8)return;setYRot((float)(Math.atan2(v.z,v.x)*180/Math.PI)-90);setXRot((float)(-Math.atan2(v.y,v.horizontalDistance())*180/Math.PI));}
	private static boolean loaded(final ServerLevel l,final Vec3 p){return l.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(p.x),SectionPos.blockToSectionCoord(p.z));}
	private RaycastResult raycast(final ServerLevel level,final Vec3 from,final Vec3 to){AtomicBoolean missing=new AtomicBoolean();ClipContext context=new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this);Optional<BlockHitResult> hit=BlockGetter.traverseBlocks(from,to,context,(c,p)->{if(!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(p.getX()),SectionPos.blockToSectionCoord(p.getZ()))){missing.set(true);return Optional.empty();}BlockState state=level.getBlockState(p);VoxelShape shape=c.getBlockShape(state,level,p);BlockHitResult result=level.clipWithInteractionOverride(from,to,p,shape,state);return result==null?null:Optional.of(result);},ignored->Optional.empty());return new RaycastResult(hit==null?Optional.empty():hit,missing.get());}
	private record RaycastResult(Optional<BlockHitResult> hit,boolean missingChunk){}
}
