package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import com.andye.warmod.entity.IcbmMissileEntity;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class IcbmLaunchService {
	private static final double[] LAUNCH_DISTANCES={480.0,420.0,360.0,300.0,240.0,180.0,120.0,64.0,20.0,12.0};
	private IcbmLaunchService() { }
	public static Optional<LaunchResult> launch(final ServerLevel level,final ServerPlayer player,final Vec3 target,final WarheadPayloadType payloadType){
		if(level==null||player==null||target==null||payloadType==null||player.level()!=level||!target.isFinite()
			||player.getEyePosition().distanceTo(target)>1000.001||!loaded(level,target))return Optional.empty();
		UUID id=UUID.randomUUID();long seed=mix(id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),17)^payloadType.ordinal());
		Vec3 launch=findLaunchPosition(level,player,seed).orElse(null);if(launch==null)return Optional.empty();
		Vec3 horizontal=new Vec3(target.x-launch.x,0,target.z-launch.z);double horizontalDistance=horizontal.length();
		if(horizontalDistance<1.0)return Optional.empty();horizontal=horizontal.scale(1.0/horizontalDistance);
		double dimensionBuildTop=level.dimensionType().minY()+level.dimensionType().height();
		double preferredFlightCeiling=dimensionBuildTop+192.0,absoluteFlightCeiling=dimensionBuildTop+256.0;
		double cloudHeight=Double.NEGATIVE_INFINITY;try{cloudHeight=level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT,launch).doubleValue();}catch(RuntimeException ignored){}
		double burnoutY=Math.min(preferredFlightCeiling,Math.max(launch.y+IcbmConstants.PREFERRED_BURNOUT_HEIGHT_ABOVE_LAUNCH,Math.max(target.y+210.0,cloudHeight+120.0)));
		double separationY=Math.min(preferredFlightCeiling,target.y+IcbmConstants.PREFERRED_SEPARATION_HEIGHT_ABOVE_TARGET);
		if(!Double.isFinite(burnoutY)||!Double.isFinite(separationY)||burnoutY<launch.y+IcbmConstants.MINIMUM_BURNOUT_HEIGHT_ABOVE_LAUNCH||separationY<target.y+IcbmConstants.MINIMUM_SEPARATION_HEIGHT_ABOVE_TARGET)return Optional.empty();
		double burnoutHorizontal=Mth.clamp(horizontalDistance*.24,120.0,340.0);
		Vec3 burnout=new Vec3(launch.x+horizontal.x*burnoutHorizontal,burnoutY,launch.z+horizontal.z*burnoutHorizontal);
		Vec3 separation=new Vec3(target.x-horizontal.x*IcbmConstants.SEPARATION_HORIZONTAL_OFFSET,separationY,target.z-horizontal.z*IcbmConstants.SEPARATION_HORIZONTAL_OFFSET);
		if(!burnout.isFinite()||!separation.isFinite()||!loaded(level,burnout)||!loaded(level,separation))return Optional.empty();
		double preferredApexY=Math.min(absoluteFlightCeiling,Math.max(Math.max(cloudHeight+220.0,target.y+340.0),launch.y+340.0));
		int coast=chooseCoastTicks(burnout,separation,preferredApexY,absoluteFlightCeiling);if(coast<0)return Optional.empty();
		IcbmFlightPlan plan;
		try{plan=new IcbmFlightPlan(id,player.getUUID(),launch,burnout,separation,target,level.getGameTime(),IcbmConstants.IGNITION_TICKS,IcbmConstants.BOOST_TICKS,coast,seed,payloadType);}catch(IllegalArgumentException ex){return Optional.empty();}
		Vec3 endingVelocity=calculateCoastInitialVelocity(burnout,separation,coast).add(0,-IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED*coast,0);
		if(!endingVelocity.isFinite()||endingVelocity.y>-.35)return Optional.empty();
		IcbmMissileEntity entity=new IcbmMissileEntity(ModEntityTypes.ICBM_MISSILE,level,plan);if(!level.addFreshEntity(entity))return Optional.empty();
		IcbmVisualNetworking.sendLaunch(level,ClientboundIcbmLaunchPayload.fromPlan(plan));
		double selectedDistance=new Vec3(launch.x-player.getX(),0,launch.z-player.getZ()).length();
		if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.info("ICBM {} launched: payload={}, launchDistance={}, start={}, burnout={}, separation={}, target={}, boost={}, coast={}, apex={}",id,payloadType.serializedName(),selectedDistance,launch,burnout,separation,target,plan.boostTicks(),plan.coastTicks(),calculateApexY(burnout,calculateCoastInitialVelocity(burnout,separation,coast),coast));
		int terminalTicks=Mth.clamp((int)Math.ceil(separation.distanceTo(target)/3.5),IcbmConstants.MINIMUM_TERMINAL_TICKS,IcbmConstants.MAXIMUM_TERMINAL_TICKS);
		return Optional.of(new LaunchResult(plan,terminalTicks));
	}
	private static Optional<Vec3> findLaunchPosition(final ServerLevel level,final ServerPlayer player,final long visualSeed){
		Vec3 look=new Vec3(player.getLookAngle().x,0,player.getLookAngle().z);if(look.lengthSqr()<1E-8)look=new Vec3(0,0,1);else look=look.normalize();
		Vec3 back=look.scale(-1),right=new Vec3(-look.z,0,look.x);boolean rightFirst=(visualSeed&1L)==0L;
		for(double distance:LAUNCH_DISTANCES){double side=distance==IcbmConstants.FINAL_FALLBACK_LAUNCH_DISTANCE?3.0:IcbmConstants.PREFERRED_LAUNCH_SIDE_OFFSET;Vec3 first=right.scale(rightFirst?side:-side),second=right.scale(rightFirst?-side:side);Vec3[] offsets={back.scale(distance).add(first),back.scale(distance).add(second),back.scale(distance)};for(Vec3 offset:offsets){Vec3 xz=player.position().add(offset);if(!loaded(level,xz))continue;int x=Mth.floor(xz.x),z=Mth.floor(xz.z);int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);Vec3 candidate=new Vec3(x+.5,surface+.25,z+.5);if(clear(level,candidate))return Optional.of(candidate);}}
		return Optional.empty();
	}
	private static Vec3 calculateCoastInitialVelocity(final Vec3 burnout,final Vec3 separation,final int coastTicks){Vec3 gravity=new Vec3(0,-IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED,0);double duration=coastTicks;return separation.subtract(burnout).subtract(gravity.scale(.5*duration*duration)).scale(1.0/duration);}
	private static double calculateApexAge(final Vec3 initialVelocity,final int coastTicks){return Mth.clamp(-initialVelocity.y/-IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED,0.0,(double)coastTicks);}
	private static double calculateApexY(final Vec3 burnout,final Vec3 initialVelocity,final int coastTicks){double age=calculateApexAge(initialVelocity,coastTicks);return burnout.y+initialVelocity.y*age-.5*IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED*age*age;}
	private static int chooseCoastTicks(final Vec3 burnout,final Vec3 separation,final double preferredApexY,final double absoluteFlightCeiling){int best=-1;double bestScore=Double.POSITIVE_INFINITY;for(int ticks=IcbmConstants.MINIMUM_COAST_TICKS;ticks<=IcbmConstants.MAXIMUM_COAST_TICKS;ticks++){Vec3 initial=calculateCoastInitialVelocity(burnout,separation,ticks),ending=initial.add(0,-IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED*ticks,0);double apex=calculateApexY(burnout,initial,ticks),horizontal=initial.horizontalDistance();if(!initial.isFinite()||!ending.isFinite()||!Double.isFinite(apex)||apex>absoluteFlightCeiling||ending.y>-.35||horizontal<1.0||horizontal>12.0)continue;double speedPenalty=horizontal<3.5?(3.5-horizontal)*45.0:horizontal>6.5?(horizontal-6.5)*45.0:0.0;double score=Math.abs(apex-preferredApexY)+speedPenalty+Math.abs(ticks-210)*.04;if(score<bestScore){bestScore=score;best=ticks;}}return best;}
	private static boolean clear(final ServerLevel level,final Vec3 candidate){BlockPos base=BlockPos.containing(candidate.x,candidate.y,candidate.z);BlockPos ground=base.below();if(level.getBlockState(ground).getCollisionShape(level,ground).isEmpty()||!level.getFluidState(ground).isEmpty())return false;for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=0;dy<7;dy++){BlockPos p=base.offset(dx,dy,dz);if(!level.getBlockState(p).getCollisionShape(level,p).isEmpty()||!level.getFluidState(p).isEmpty())return false;}return true;}
	private static boolean loaded(final ServerLevel level,final Vec3 p){return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(p.x),SectionPos.blockToSectionCoord(p.z));}
	private static long mix(long v){v^=v>>>30;v*=0xBF58476D1CE4E5B9L;v^=v>>>27;v*=0x94D049BB133111EBL;return v^(v>>>31);}
	public record LaunchResult(IcbmFlightPlan flightPlan,int terminalTicks) { }
}