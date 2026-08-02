package com.andye.warmod.warhead;

import net.minecraft.world.phys.Vec3;

/** Pure timing and shaping helpers shared by the client visual systems. */
public final class WarheadVisualMath {
	private static final double CONE_ACTIVATION_START=.32,CONE_ACTIVATION_FULL=.55,CONE_ATTACK_TICKS=4.0;
	public static final double AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK=343.0/20.0,AIR_SHOCKWAVE_DURATION_TICKS=72.0;
	private WarheadVisualMath(){}
	public static double normalizedSpeed(final Vec3 velocity,final double expectedMaximumSpeed){if(velocity==null||!velocity.isFinite()||!Double.isFinite(expectedMaximumSpeed)||expectedMaximumSpeed<=0)return 0;return clamp(velocity.length()/expectedMaximumSpeed,0,1);}
	public static double coneActivation(final double normalizedSpeed){if(!Double.isFinite(normalizedSpeed))return 0;return smoothstep((normalizedSpeed-CONE_ACTIVATION_START)/(CONE_ACTIVATION_FULL-CONE_ACTIVATION_START));}
	public static double coneAttack(final double elapsedTicksSinceThreshold){if(!Double.isFinite(elapsedTicksSinceThreshold))return 0;return smoothstep(elapsedTicksSinceThreshold/CONE_ATTACK_TICKS);}
	public static double conePulse(final double elapsedTicks,final long visualSeed){if(!Double.isFinite(elapsedTicks))return 1;double seedPhase=(visualSeed&0xFFFFL)/65536.0*Math.PI*2;return .93+.07*(.5+.5*Math.sin(elapsedTicks*.58+seedPhase));}
	public static double coneFade(final double remainingTicks){return smoothstep(remainingTicks/4.0);}
	public static double reentryHeat(final double progress,final Vec3 velocity,final double remainingTicks){double p=Double.isFinite(progress)?clamp(progress,0,1):0;double speed=normalizedSpeed(velocity,WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK*1.65);double progressHeat=smoothstep(.45,.62,p)*.16+smoothstep(.62,.82,p)*.34+smoothstep(.82,.96,p)*.50;double speedGate=smoothstep(.28,.62,speed);double finalTaper=remainingTicks<1.0?.82+.18*smoothstep(remainingTicks):1.0;return clamp(progressHeat*speedGate*finalTaper,0,1);}
	public static double reentryShimmer(final double elapsedTicks,final long visualSeed){if(!Double.isFinite(elapsedTicks))return 1;double phase=(visualSeed&0xffffL)/65536.0*Math.PI*2;return clamp(1+.075*Math.sin(elapsedTicks*.43+phase)+.045*Math.sin(elapsedTicks*.79-phase*1.7)+.025*Math.sin(elapsedTicks*1.31+phase*.4),.82,1.18);}
	public static double terminalConeCompression(final double progress){return smoothstep(.72,.98,progress);}
	public static double vaporBandPhase(final double elapsedTicks,final int bandIndex,final int bandCount,final long visualSeed){if(!Double.isFinite(elapsedTicks)||bandCount<=0)return 0;double seedOffset=((visualSeed>>>16)&0xFFFFL)/65536.0;return fractionalPart(elapsedTicks*.10+bandIndex/(double)bandCount+seedOffset);}
	public static double airShockwaveRadius(final double ageTicks){double safeAge=Double.isFinite(ageTicks)?ageTicks:0;return clamp(safeAge,0,AIR_SHOCKWAVE_DURATION_TICKS)*AIR_SHOCKWAVE_SPEED_BLOCKS_PER_TICK;}
	/** Compatibility overload: appearance scale must never alter propagation. */
	public static double airShockwaveRadius(final double ageTicks,final double ignoredAppearanceScale){return airShockwaveRadius(ageTicks);}
	public static double airShockwaveAlpha(final double ageTicks){if(!Double.isFinite(ageTicks)||ageTicks<0||ageTicks>=AIR_SHOCKWAVE_DURATION_TICKS)return 0;double fade=ageTicks<=30?1:1-smoothstep(30,AIR_SHOCKWAVE_DURATION_TICKS,ageTicks);return .38*Math.pow(fade,.70);}
	public static double airShockwaveThickness(final double ageTicks,final double thicknessScale){double progress=clamp(ageTicks/AIR_SHOCKWAVE_DURATION_TICKS,0,1);double safe=Double.isFinite(thicknessScale)?Math.max(.05,thicknessScale):1;return safe*(.8+2.7*smoothstep(progress));}
	public static double groundShockwaveDistance(final double ageTicks){return airShockwaveRadius(Math.max(0,ageTicks-.75))*.92;}
	/** Compatibility overload: appearance scale must never alter propagation. */
	public static double groundShockwaveDistance(final double ageTicks,final double ignoredAppearanceScale){return groundShockwaveDistance(ageTicks);}
	public static double groundShockwaveRadius(final double ageTicks){return groundShockwaveDistance(ageTicks);}
	public static double groundShockwaveAlpha(final double ageTicks){if(!Double.isFinite(ageTicks)||ageTicks<0||ageTicks>=AIR_SHOCKWAVE_DURATION_TICKS)return 0;return Math.min(.78,airShockwaveAlpha(ageTicks)*1.65);}
	public static double fireballRise(final double ageTicks){if(!Double.isFinite(ageTicks)||ageTicks<=10)return 0;return 18*smoothstep(clamp((ageTicks-10)/65,0,1));}
	public static double fireballAlpha(final double ageTicks){if(!Double.isFinite(ageTicks)||ageTicks<0||ageTicks>=80)return 0;if(ageTicks<=3)return 1-.12*smoothstep(ageTicks/3);return .88*Math.pow(1-clamp((ageTicks-3)/77,0,1),.64);}
	public static double clamp(final double value,final double minimum,final double maximum){return Math.max(minimum,Math.min(maximum,value));}
	private static double smoothstep(final double value){double t=clamp(value,0,1);return t*t*(3-2*t);}
	private static double smoothstep(final double edge0,final double edge1,final double value){if(edge1<=edge0)return value<edge0?0:1;return smoothstep((value-edge0)/(edge1-edge0));}
	private static double fractionalPart(final double value){return value-Math.floor(value);}
}