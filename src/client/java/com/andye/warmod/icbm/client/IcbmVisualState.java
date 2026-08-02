package com.andye.warmod.icbm.client;

import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import com.andye.warmod.icbm.network.ClientboundIcbmLaunchPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.world.phys.Vec3;

public record IcbmVisualState(IcbmFlightPlan flightPlan) {
	public static IcbmVisualState fromPayload(final ClientboundIcbmLaunchPayload p){return new IcbmVisualState(new IcbmFlightPlan(p.missileId(),new java.util.UUID(0,0),p.launchPosition(),p.burnoutPosition(),p.separationPosition(),p.intendedTarget(),p.launchGameTime(),p.ignitionTicks(),p.boostTicks(),p.coastTicks(),p.visualSeed(),p.payloadType()));}
	public double elapsed(final long time,final double partial){return Math.max(0,time-flightPlan.launchGameTime())+Math.max(0,Math.min(1,partial));}
	public Vec3 position(final long time,final double partial){return IcbmTrajectory.position(flightPlan,elapsed(time,partial));}public Vec3 velocity(final long time,final double partial){return IcbmTrajectory.velocity(flightPlan,elapsed(time,partial));}public boolean expired(final long time){return elapsed(time,0)>flightPlan.separationTick()+40;}
	public List<IcbmTrailSample> trail(final long time,final double partial){double now=elapsed(time,partial),end=Math.min(now,flightPlan.ignitionTicks()+flightPlan.boostTicks());List<IcbmTrailSample> samples=new ArrayList<>();for(double t=Math.max(0,end-140);t<=end;t+=1.5){double age=now-t;if(age>140)continue;SplittableRandom r=new SplittableRandom(flightPlan.visualSeed()^(long)(t*31));samples.add(new IcbmTrailSample(IcbmTrajectory.position(flightPlan,t),age,(float)r.nextDouble(.45,1.25),new Vec3(r.nextDouble(-.012,.012),r.nextDouble(.006,.02),r.nextDouble(-.012,.012)),(float)r.nextDouble(0,Math.PI*2)));}return List.copyOf(samples);}
}
