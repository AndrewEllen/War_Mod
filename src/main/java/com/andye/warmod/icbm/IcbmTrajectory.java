package com.andye.warmod.icbm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class IcbmTrajectory {
	private IcbmTrajectory() { }
	public static IcbmFlightPhase phase(final IcbmFlightPlan p,final double t){if(t<p.ignitionTicks())return IcbmFlightPhase.IGNITION;if(t<p.ignitionTicks()+p.boostTicks())return IcbmFlightPhase.POWERED_ASCENT;if(t<p.separationTick())return IcbmFlightPhase.BALLISTIC_COAST;return IcbmFlightPhase.SEPARATED;}
	public static boolean thrustActive(final IcbmFlightPlan p,final double t){return t>=0&&t<p.ignitionTicks()+p.boostTicks();}
	public static Vec3 position(final IcbmFlightPlan p,final double elapsed){
		double t=Math.max(0,Math.min(elapsed,p.separationTick()));
		if(t<p.ignitionTicks()){double u=t/p.ignitionTicks();return p.launchPosition().add(0,0.5*u*u,0);}
		if(t<p.ignitionTicks()+p.boostTicks())return powered(p,(t-p.ignitionTicks())/p.boostTicks());
		double age=Math.min(p.coastTicks(),t-p.ignitionTicks()-p.boostTicks());
		Vec3 gravity=new Vec3(0,-IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED,0);
		return p.burnoutPosition().add(coastInitialVelocity(p).scale(age)).add(gravity.scale(0.5*age*age));
	}
	public static Vec3 coastInitialVelocity(final IcbmFlightPlan p){Vec3 gravity=new Vec3(0,-IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED,0);double duration=p.coastTicks();return p.separationPosition().subtract(p.burnoutPosition()).subtract(gravity.scale(.5*duration*duration)).scale(1.0/duration);}
	public static Vec3 velocity(final IcbmFlightPlan p,final double raw){double t=Math.max(0,Math.min(raw,p.separationTick()));if(t<p.ignitionTicks()){double u=t/p.ignitionTicks();return new Vec3(0,u/p.ignitionTicks(),0);}if(t<p.ignitionTicks()+p.boostTicks())return poweredVelocity(p,(t-p.ignitionTicks())/p.boostTicks());double age=Math.min(p.coastTicks(),t-p.ignitionTicks()-p.boostTicks());return coastInitialVelocity(p).add(0,-IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED*age,0);}
	private static Vec3 powered(final IcbmFlightPlan p,final double raw){double u=Mth.clamp(raw,0,1);Vec3 a=p.launchPosition().add(0,.5,0),b=p.burnoutPosition();Vec3 horizontal=horizontalDirection(a,b);Vec3 m0=horizontal.scale(.12).add(0,.993,0).normalize().scale(.55*p.boostTicks()),m1=coastInitialVelocity(p).scale(p.boostTicks());double u2=u*u,u3=u2*u;return a.scale(2*u3-3*u2+1).add(m0.scale(u3-2*u2+u)).add(b.scale(-2*u3+3*u2)).add(m1.scale(u3-u2));}
	private static Vec3 poweredVelocity(final IcbmFlightPlan p,final double raw){double u=Mth.clamp(raw,0,1);Vec3 a=p.launchPosition().add(0,.5,0),b=p.burnoutPosition();Vec3 m0=horizontalDirection(a,b).scale(.12).add(0,.993,0).normalize().scale(.55*p.boostTicks()),m1=coastInitialVelocity(p).scale(p.boostTicks());double u2=u*u;return a.scale(6*u2-6*u).add(m0.scale(3*u2-4*u+1)).add(b.scale(-6*u2+6*u)).add(m1.scale(3*u2-2*u)).scale(1.0/p.boostTicks());}
	private static Vec3 horizontalDirection(final Vec3 a,final Vec3 b){Vec3 horizontal=new Vec3(b.x-a.x,0,b.z-a.z);return horizontal.lengthSqr()<1E-8?new Vec3(0,0,1):horizontal.normalize();}
}