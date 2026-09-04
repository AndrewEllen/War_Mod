package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadDeliveryMode;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadYield;
import com.andye.warmod.warhead.WarheadTrajectory;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import com.andye.warmod.warhead.network.ClientboundWarheadTimingCorrectionPayload;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Predicted terminal-warhead visual with critically damped reconciliation. */
public final class WarheadVisualState {
    private static final double SPRING_OMEGA_PER_TICK = 1.20;
    private static final double SNAP_DISTANCE_SQUARED = 64.0 * 64.0;

    private final UUID warheadId;
    private final Vec3 startPosition;
    private final Vec3 intendedTarget;
    private final long launchGameTime;
    private final long visualSeed;
    private final int flightTicks;
    private final WarheadPayloadType payloadType;
    private final WarheadYield yield;
    private final WarheadDeliveryMode deliveryMode;
    private final int clusterIndex;
    private final int clusterCount;
    private int pausedSimulationTicks;
    private boolean waiting;
    private Vec3 safePosition;
    private Vec3 correctionOffset = Vec3.ZERO;
    private long correctionStartGameTime = Long.MIN_VALUE;

    public WarheadVisualState(final UUID id, final Vec3 start, final Vec3 target,
        final long time, final int ticks, final long seed, final WarheadPayloadType payload) {
        this(id, start, target, time, ticks, seed, payload,
            WarheadYield.defaultFor(payload), WarheadDeliveryMode.SINGLE, 0, 1);
    }

    public WarheadVisualState(final UUID id, final Vec3 start, final Vec3 target,
        final long time, final int ticks, final long seed, final WarheadPayloadType payload,
        final WarheadYield exactYield, final WarheadDeliveryMode exactDeliveryMode) {
        this(id, start, target, time, ticks, seed, payload, exactYield,
            exactDeliveryMode, 0, 1);
    }

    public WarheadVisualState(final UUID id, final Vec3 start, final Vec3 target,
        final long time, final int ticks, final long seed, final WarheadPayloadType payload,
        final WarheadYield exactYield, final WarheadDeliveryMode exactDeliveryMode,
        final int exactClusterIndex, final int exactClusterCount) {
        warheadId = id;
        startPosition = start;
        intendedTarget = target;
        launchGameTime = time;
        flightTicks = ticks;
        visualSeed = seed;
        payloadType = payload;
        yield = exactYield;
        deliveryMode = exactDeliveryMode;
        clusterIndex = exactClusterIndex;
        clusterCount = exactClusterCount;
        safePosition = start;
    }

    public static WarheadVisualState fromPayload(final ClientboundWarheadLaunchPayload payload) {
        return new WarheadVisualState(payload.warheadId(),
            new Vec3(payload.startX(), payload.startY(), payload.startZ()),
            new Vec3(payload.targetX(), payload.targetY(), payload.targetZ()),
            payload.launchGameTime(), payload.flightTicks(), payload.visualSeed(),
            payload.payloadType(), payload.yield(), payload.deliveryMode(),
            payload.clusterIndex(), payload.clusterCount());
    }

    public void applyTimingCorrection(final ClientboundWarheadTimingCorrectionPayload payload) {
        long correctionTime = payload.serverGameTime();
        Vec3 before = positionAt(correctionTime, 0.0);
        pausedSimulationTicks = payload.pausedSimulationTicks();
        waiting = payload.waiting();
        safePosition = payload.safePosition();
        Vec3 corrected = basePosition(correctionTime, 0.0);
        Vec3 error = before.subtract(corrected);
        correctionOffset = error.lengthSqr() > SNAP_DISTANCE_SQUARED ? Vec3.ZERO : error;
        correctionStartGameTime = correctionTime;
    }

    public UUID warheadId() { return warheadId; }
    public Vec3 startPosition() { return startPosition; }
    public Vec3 intendedTarget() { return intendedTarget; }
    public long launchGameTime() { return launchGameTime; }
    public int flightTicks() { return flightTicks; }
    public long visualSeed() { return visualSeed; }
    public WarheadPayloadType payloadType() { return payloadType; }
    public WarheadYield yield() { return yield; }
    public WarheadDeliveryMode deliveryMode() { return deliveryMode; }
    public int clusterIndex() { return clusterIndex; }
    public int clusterCount() { return clusterCount; }

    public double elapsedTicks(final long time, final double partial) {
        return Math.max(0.0, time - launchGameTime - pausedSimulationTicks)
            + Math.max(0.0, Math.min(1.0, partial));
    }

    public Vec3 positionAt(final long time, final double partial) {
        Vec3 base = basePosition(time, partial);
        if (correctionOffset.lengthSqr() <= 1.0E-10
            || correctionStartGameTime == Long.MIN_VALUE) return base;
        double elapsed = Math.max(0.0, time + Math.max(0.0, Math.min(1.0, partial))
            - correctionStartGameTime);
        return base.add(correctionOffset.scale(springPositionFactor(elapsed)));
    }

    public Vec3 velocityAt(final long time, final double partial) {
        Vec3 base = waiting ? Vec3.ZERO : WarheadTrajectory.velocity(startPosition,
            intendedTarget, elapsedTicks(time, partial), flightTicks,
            clusterIndex, clusterCount);
        if (correctionOffset.lengthSqr() <= 1.0E-10
            || correctionStartGameTime == Long.MIN_VALUE) return base;
        double elapsed = Math.max(0.0, time + Math.max(0.0, Math.min(1.0, partial))
            - correctionStartGameTime);
        double derivative = -SPRING_OMEGA_PER_TICK * SPRING_OMEGA_PER_TICK * elapsed
            * Math.exp(-SPRING_OMEGA_PER_TICK * elapsed);
        return base.add(correctionOffset.scale(derivative));
    }

    public double progressAt(final long time, final double partial) {
        return WarheadTrajectory.progress(elapsedTicks(time, partial), flightTicks);
    }

    public boolean isExpired(final long time, final double partial) {
        return !waiting && elapsedTicks(time, partial)
            > flightTicks + WarheadConstants.WARHEAD_VISUAL_LIFETIME_GRACE_TICKS;
    }

    private Vec3 basePosition(final long time, final double partial) {
        return waiting ? safePosition : WarheadTrajectory.position(startPosition,
            intendedTarget, elapsedTicks(time, partial), flightTicks,
            clusterIndex, clusterCount);
    }

    private static double springPositionFactor(final double elapsed) {
        double scaled = SPRING_OMEGA_PER_TICK * elapsed;
        return (1.0 + scaled) * Math.exp(-scaled);
    }
}
