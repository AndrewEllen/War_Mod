package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadTrajectory;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class WarheadVisualState {
	private final UUID warheadId;private final Vec3 startPosition,intendedTarget;private final long launchGameTime,visualSeed;private final int flightTicks;private final WarheadPayloadType payloadType;
	public WarheadVisualState(final UUID id,final Vec3 start,final Vec3 target,final long time,final int ticks,final long seed,final WarheadPayloadType payload){warheadId=id;startPosition=start;intendedTarget=target;launchGameTime=time;flightTicks=ticks;visualSeed=seed;payloadType=payload;}
	public static WarheadVisualState fromPayload(final ClientboundWarheadLaunchPayload p){return new WarheadVisualState(p.warheadId(),new Vec3(p.startX(),p.startY(),p.startZ()),new Vec3(p.targetX(),p.targetY(),p.targetZ()),p.launchGameTime(),p.flightTicks(),p.visualSeed(),p.payloadType());}
	public UUID warheadId(){return warheadId;}public Vec3 startPosition(){return startPosition;}public Vec3 intendedTarget(){return intendedTarget;}public long launchGameTime(){return launchGameTime;}public int flightTicks(){return flightTicks;}public long visualSeed(){return visualSeed;}public WarheadPayloadType payloadType(){return payloadType;}
	public double elapsedTicks(final long time,final double partial){return Math.max(0,time-launchGameTime)+Math.max(0,Math.min(1,partial));}
	public Vec3 positionAt(final long time,final double partial){return WarheadTrajectory.position(startPosition,intendedTarget,elapsedTicks(time,partial),flightTicks);}public Vec3 velocityAt(final long time,final double partial){return WarheadTrajectory.velocity(startPosition,intendedTarget,elapsedTicks(time,partial),flightTicks);}public double progressAt(final long time,final double partial){return WarheadTrajectory.progress(elapsedTicks(time,partial),flightTicks);}public boolean isExpired(final long time,final double partial){return elapsedTicks(time,partial)>flightTicks+WarheadConstants.WARHEAD_VISUAL_LIFETIME_GRACE_TICKS;}
}