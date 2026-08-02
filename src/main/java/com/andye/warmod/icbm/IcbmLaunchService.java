package com.andye.warmod.icbm;

import com.andye.warmod.WarMod;
import com.andye.warmod.entity.IcbmMissileEntity;
import com.andye.warmod.entity.ModEntityTypes;
import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import com.andye.warmod.icbm.network.IcbmVisualNetworking;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.List;
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
	private IcbmLaunchService() { }
	public static Optional<LaunchResult> launch(final ServerLevel level,final ServerPlayer player,final Vec3 target,final WarheadPayloadType payloadType){
		if(level==null||player==null||target==null||payloadType==null||player.level()!=level||!target.isFinite()
			||player.getEyePosition().distanceTo(target)>1000.001||!loaded(level,target))return Optional.empty();
		Vec3 launch=findLaunchPosition(level,player).orElse(null);if(launch==null)return Optional.empty();
		Vec3 horizontal=new Vec3(target.x-launch.x,0,target.z-launch.z);double horizontalDistance=horizontal.length();
		if(horizontalDistance<1.0)return Optional.empty();horizontal=horizontal.scale(1.0/horizontalDistance);
		double maximumY=level.dimensionType().minY()+level.dimensionType().height()-20.0;
		double cloudHeight=Double.NEGATIVE_INFINITY;try{cloudHeight=level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT,launch).doubleValue();}catch(RuntimeException ignored){}
		double burnoutY=Math.min(maximumY,Math.max(launch.y+IcbmConstants.PREFERRED_BURNOUT_HEIGHT_ABOVE_LAUNCH,Math.max(target.y+150.0,cloudHeight+64.0)));
		double separationY=Math.min(maximumY, target.y+IcbmConstants.PREFERRED_SEPARATION_HEIGHT_ABOVE_TARGET);
		if(burnoutY<launch.y+IcbmConstants.MINIMUM_BURNOUT_HEIGHT_ABOVE_LAUNCH||separationY<target.y+IcbmConstants.MINIMUM_SEPARATION_HEIGHT_ABOVE_TARGET)return Optional.empty();
		double burnoutHorizontal=Mth.clamp(horizontalDistance*.18,55.0,180.0);
		Vec3 burnout=new Vec3(launch.x+horizontal.x*burnoutHorizontal,burnoutY,launch.z+horizontal.z*burnoutHorizontal);
		Vec3 separation=new Vec3(target.x-horizontal.x*IcbmConstants.SEPARATION_HORIZONTAL_OFFSET,separationY,target.z-horizontal.z*IcbmConstants.SEPARATION_HORIZONTAL_OFFSET);
		if(!loaded(level,burnout)||!loaded(level,separation))return Optional.empty();
		double coastHorizontal=new Vec3(separation.x-burnout.x,0,separation.z-burnout.z).length();
		int coast=Mth.clamp((int)Math.ceil(coastHorizontal/5.25),IcbmConstants.MINIMUM_COAST_TICKS,IcbmConstants.MAXIMUM_COAST_TICKS);
		UUID id=UUID.randomUUID();long seed=mix(id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),17)^payloadType.ordinal());
		IcbmFlightPlan plan;
		try{plan=new IcbmFlightPlan(id,player.getUUID(),launch,burnout,separation,target,level.getGameTime(),IcbmConstants.IGNITION_TICKS,IcbmConstants.BOOST_TICKS,coast,seed,payloadType);}catch(IllegalArgumentException ex){return Optional.empty();}
		IcbmMissileEntity entity=new IcbmMissileEntity(ModEntityTypes.ICBM_MISSILE,level,plan);if(!level.addFreshEntity(entity))return Optional.empty();
		IcbmVisualNetworking.sendLaunch(level,ClientboundIcbmLaunchPayload.fromPlan(plan));
		if(SharedConstants.IS_RUNNING_IN_IDE)WarMod.LOGGER.info("ICBM {} launched: payload={}, start={}, burnout={}, separation={}, target={}, boost={}, coast={}",id,payloadType.serializedName(),launch,burnout,separation,target,plan.boostTicks(),plan.coastTicks());
		int terminalTicks=Mth.clamp((int)Math.ceil(separation.distanceTo(target)/3.5),IcbmConstants.MINIMUM_TERMINAL_TICKS,IcbmConstants.MAXIMUM_TERMINAL_TICKS);
		return Optional.of(new LaunchResult(plan,terminalTicks));
	}
	private static Optional<Vec3> findLaunchPosition(final ServerLevel level,final ServerPlayer player){
		Vec3 look=new Vec3(player.getLookAngle().x,0,player.getLookAngle().z);if(look.lengthSqr()<1E-8)look=new Vec3(0,0,1);else look=look.normalize();
		Vec3 back=look.scale(-1),right=new Vec3(-look.z,0,look.x);
		List<Vec3> offsets=List.of(back.scale(12).add(right.scale(3)),back.scale(12).add(right.scale(-3)),back.scale(12),back.scale(16),back.scale(20));
		for(Vec3 offset:offsets){Vec3 xz=player.position().add(offset);if(!loaded(level,xz))continue;int x=Mth.floor(xz.x),z=Mth.floor(xz.z);int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
			Vec3 candidate=new Vec3(x+.5,surface+.25,z+.5);if(clear(level,candidate))return Optional.of(candidate);}
		return Optional.empty();
	}
	private static boolean clear(final ServerLevel level,final Vec3 candidate){BlockPos base=BlockPos.containing(candidate.x,candidate.y,candidate.z);BlockPos ground=base.below();
		if(level.getBlockState(ground).getCollisionShape(level,ground).isEmpty()||!level.getFluidState(ground).isEmpty())return false;
		for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=0;dy<7;dy++){BlockPos p=base.offset(dx,dy,dz);if(!level.getBlockState(p).getCollisionShape(level,p).isEmpty()||!level.getFluidState(p).isEmpty())return false;}return true;}
	private static boolean loaded(final ServerLevel level,final Vec3 p){return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(p.x),SectionPos.blockToSectionCoord(p.z));}
	private static long mix(long v){v^=v>>>30;v*=0xBF58476D1CE4E5B9L;v^=v>>>27;v*=0x94D049BB133111EBL;return v^(v>>>31);}
	public record LaunchResult(IcbmFlightPlan flightPlan,int terminalTicks) { }
}
