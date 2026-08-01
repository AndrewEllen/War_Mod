package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadTrajectory;
import com.andye.warmod.warhead.network.ClientboundWarheadLaunchPayload;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class WarheadVisualState {
	private final UUID warheadId;
	private final Vec3 startPosition;
	private final Vec3 intendedTarget;
	private final long launchGameTime;
	private final int flightTicks;
	private final long visualSeed;

	public WarheadVisualState(
		final UUID warheadId,
		final Vec3 startPosition,
		final Vec3 intendedTarget,
		final long launchGameTime,
		final int flightTicks,
		final long visualSeed
	) {
		this.warheadId = warheadId;
		this.startPosition = startPosition;
		this.intendedTarget = intendedTarget;
		this.launchGameTime = launchGameTime;
		this.flightTicks = flightTicks;
		this.visualSeed = visualSeed;
	}

	public static WarheadVisualState fromPayload(final ClientboundWarheadLaunchPayload payload) {
		return new WarheadVisualState(
			payload.warheadId(),
			new Vec3(payload.startX(), payload.startY(), payload.startZ()),
			new Vec3(payload.targetX(), payload.targetY(), payload.targetZ()),
			payload.launchGameTime(),
			payload.flightTicks(),
			payload.visualSeed()
		);
	}

	public UUID warheadId() {
		return this.warheadId;
	}

	public Vec3 startPosition() {
		return this.startPosition;
	}

	public Vec3 intendedTarget() {
		return this.intendedTarget;
	}

	public long launchGameTime() {
		return this.launchGameTime;
	}

	public int flightTicks() {
		return this.flightTicks;
	}

	public long visualSeed() {
		return this.visualSeed;
	}

	public double elapsedTicks(final long clientGameTime, final double partialTick) {
		long wholeTicks = clientGameTime - this.launchGameTime;
		return Math.max(0.0, wholeTicks) + Math.max(0.0, Math.min(1.0, partialTick));
	}

	public Vec3 positionAt(final long clientGameTime, final double partialTick) {
		return WarheadTrajectory.position(
			this.startPosition,
			this.intendedTarget,
			this.elapsedTicks(clientGameTime, partialTick),
			this.flightTicks
		);
	}

	public Vec3 velocityAt(final long clientGameTime, final double partialTick) {
		return WarheadTrajectory.velocity(
			this.startPosition,
			this.intendedTarget,
			this.elapsedTicks(clientGameTime, partialTick),
			this.flightTicks
		);
	}

	public double progressAt(final long clientGameTime, final double partialTick) {
		return WarheadTrajectory.progress(this.elapsedTicks(clientGameTime, partialTick), this.flightTicks);
	}

	public boolean isExpired(final long clientGameTime, final double partialTick) {
		return this.elapsedTicks(clientGameTime, partialTick)
			> this.flightTicks + WarheadConstants.WARHEAD_VISUAL_LIFETIME_GRACE_TICKS;
	}
}