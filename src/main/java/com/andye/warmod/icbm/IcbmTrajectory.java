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
		double age=Math.min(p.coastTicks(),t-p.ignitionTicks()-p.boostTicks());Vec3 g=new Vec3(0,-IcbmConstants.COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED,0);
		Vec3 displacement=p.separationPosition().subtract(p.burnoutPosition());double duration=p.coastTicks();
		Vec3 v0=displacement.subtract(g.scale(0.5*duration*duration)).scale(1.0/duration);
		return p.burnoutPosition().add(v0.scale(age)).add(g.scale(0.5*age*age));
	}
	public static Vec3 velocity(final IcbmFlightPlan p,final double t){double e=Math.max(0,Math.min(t,p.separationTick()));double epsilon=.05;return position(p,Math.min(p.separationTick(),e+epsilon)).subtract(position(p,Math.max(0,e-epsilon))).scale(1.0/(2*epsilon));}
	private static Vec3 powered(final IcbmFlightPlan p,final double raw){double u=Mth.clamp(raw,0,1);Vec3 a=p.launchPosition().add(0,.5,0),b=p.burnoutPosition();
		Vec3 horizontal=new Vec3(b.x-a.x,0,b.z-a.z);if(horizontal.lengthSqr()<1E-8)horizontal=new Vec3(0,0,1);else horizontal=horizontal.normalize();
		double scale=a.distanceTo(b)*.72;Vec3 m0=horizontal.scale(.10).add(0,.995,0).normalize().scale(scale);Vec3 m1=horizontal.scale(.82).add(0,.57,0).normalize().scale(scale);
		double u2=u*u,u3=u2*u;return a.scale(2*u3-3*u2+1).add(m0.scale(u3-2*u2+u)).add(b.scale(-2*u3+3*u2)).add(m1.scale(u3-u2));}
}
