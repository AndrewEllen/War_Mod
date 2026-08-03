package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadTrajectory;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadTimingCorrectionPayload;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class WarheadVisualState {
    private final UUID warheadId; private final Vec3 startPosition, intendedTarget; private final long launchGameTime, visualSeed; private final int flightTicks; private final WarheadPayloadType payloadType;
    private int pausedSimulationTicks; private boolean waiting; private Vec3 safePosition;
    public WarheadVisualState(UUID id, Vec3 start, Vec3 target, long time, int ticks, long seed, WarheadPayloadType payload) { warheadId=id; startPosition=start; intendedTarget=target; launchGameTime=time; flightTicks=ticks; visualSeed=seed; payloadType=payload; safePosition=start; }
    public static WarheadVisualState fromPayload(ClientboundWarheadLaunchPayload payload) { return new WarheadVisualState(payload.warheadId(),new Vec3(payload.startX(),payload.startY(),payload.startZ()),new Vec3(payload.targetX(),payload.targetY(),payload.targetZ()),payload.launchGameTime(),payload.flightTicks(),payload.visualSeed(),payload.payloadType()); }
    public void applyTimingCorrection(ClientboundWarheadTimingCorrectionPayload payload) { pausedSimulationTicks=payload.pausedSimulationTicks(); waiting=payload.waiting(); safePosition=payload.safePosition(); }
    public UUID warheadId(){return warheadId;} public Vec3 startPosition(){return startPosition;} public Vec3 intendedTarget(){return intendedTarget;} public long launchGameTime(){return launchGameTime;} public int flightTicks(){return flightTicks;} public long visualSeed(){return visualSeed;} public WarheadPayloadType payloadType(){return payloadType;}
    public double elapsedTicks(long time,double partial){return Math.max(0,time-launchGameTime-pausedSimulationTicks)+Math.max(0,Math.min(1,partial));}
    public Vec3 positionAt(long time,double partial){return waiting?safePosition:WarheadTrajectory.position(startPosition,intendedTarget,elapsedTicks(time,partial),flightTicks);} public Vec3 velocityAt(long time,double partial){return waiting?Vec3.ZERO:WarheadTrajectory.velocity(startPosition,intendedTarget,elapsedTicks(time,partial),flightTicks);} public double progressAt(long time,double partial){return WarheadTrajectory.progress(elapsedTicks(time,partial),flightTicks);} public boolean isExpired(long time,double partial){return !waiting&&elapsedTicks(time,partial)>flightTicks+WarheadConstants.WARHEAD_VISUAL_LIFETIME_GRACE_TICKS;}
}