package com.andye.warmod.icbm.client;

import com.andye.warmod.icbm.IcbmConstants;
import com.andye.warmod.icbm.network.ClientboundIcbmSeparationPayload;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record SpentIcbmStageState(UUID missileId,Vec3 startPosition,Vec3 startVelocity,long separationGameTime,long visualSeed,int lifetimeTicks,float tumbleX,float tumbleY,float tumbleZ) {
	public static SpentIcbmStageState from(final ClientboundIcbmSeparationPayload p){SplittableRandom r=new SplittableRandom(p.visualSeed());return new SpentIcbmStageState(p.missileId(),p.separationPosition(),p.carrierVelocity(),p.separationGameTime(),p.visualSeed(),r.nextInt(IcbmConstants.SPENT_STAGE_MINIMUM_LIFETIME_TICKS,IcbmConstants.SPENT_STAGE_MAXIMUM_LIFETIME_TICKS+1),(float)r.nextDouble(-.12,.12),(float)r.nextDouble(-.12,.12),(float)r.nextDouble(-.12,.12));}
	public double age(final long time,final double partial){return Math.max(0,time-separationGameTime)+Math.max(0,Math.min(1,partial));}public Vec3 position(final long time,final double partial){double t=age(time,partial);return startPosition.add(startVelocity.scale(t)).add(0,-.5*.04*t*t,0);}public boolean expired(final long time){return age(time,0)>=lifetimeTicks;}public float alpha(final long time,final double partial){double remaining=lifetimeTicks-age(time,partial);return (float)Math.max(0,Math.min(1,remaining/15.0));}
}
