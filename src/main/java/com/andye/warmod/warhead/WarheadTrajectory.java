package com.andye.warmod.warhead;

import com.andye.warmod.icbm.IcbmConstants;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public final class WarheadTrajectory {
	private static final double EASING_POWER = 1.6;
	private static final double VELOCITY_STEP_TICKS = 0.25;
	private static final double CLUSTER_GRAVITY_PER_TICK_SQUARED = 0.05;

	private WarheadTrajectory() {
	}

	/** Duration used by both carrier ETA preparation and the spawned terminal. */
	public static int terminalFlightTicks(final Vec3 start, final Vec3 intendedTarget) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(intendedTarget, "intendedTarget");
		if (!start.isFinite() || !intendedTarget.isFinite()) {
			throw new IllegalArgumentException("trajectory positions must be finite");
		}
		int estimated = (int)Math.ceil(start.distanceTo(intendedTarget)
			/ WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK);
		return Math.max(IcbmConstants.MINIMUM_TERMINAL_TICKS,
			Math.min(IcbmConstants.MAXIMUM_TERMINAL_TICKS, estimated));
	}

	public static double progress(final double elapsedTicks, final int flightTicks) {
		validateElapsed(elapsedTicks);
		validateFlightTicks(flightTicks);
		return clamp(elapsedTicks / flightTicks, 0.0, 1.0);
	}

	public static double easedProgress(final double elapsedTicks, final int flightTicks) {
		return Math.pow(progress(elapsedTicks, flightTicks), EASING_POWER);
	}

	public static Vec3 position(
		final Vec3 start,
		final Vec3 intendedTarget,
		final double elapsedTicks,
		final int flightTicks
	) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(intendedTarget, "intendedTarget");
		if (!start.isFinite() || !intendedTarget.isFinite()) {
			throw new IllegalArgumentException("trajectory positions must be finite");
		}

		return start.lerp(intendedTarget, easedProgress(elapsedTicks, flightTicks));
	}

	public static Vec3 position(
		final Vec3 start,
		final Vec3 intendedTarget,
		final double elapsedTicks,
		final int flightTicks,
		final int clusterIndex,
		final int clusterCount
	) {
		if (clusterCount != 4) return position(start, intendedTarget, elapsedTicks, flightTicks);
		return clusterBallisticPosition(start, intendedTarget, elapsedTicks, flightTicks);
	}

	public static Vec3 velocity(
		final Vec3 start,
		final Vec3 intendedTarget,
		final double elapsedTicks,
		final int flightTicks
	) {
		Vec3 current = position(start, intendedTarget, elapsedTicks, flightTicks);
		Vec3 next = position(start, intendedTarget, elapsedTicks + VELOCITY_STEP_TICKS, flightTicks);
		return next.subtract(current).scale(1.0 / VELOCITY_STEP_TICKS);
	}

	public static Vec3 velocity(
		final Vec3 start,
		final Vec3 intendedTarget,
		final double elapsedTicks,
		final int flightTicks,
		final int clusterIndex,
		final int clusterCount
	) {
		if (clusterCount != 4) return velocity(start, intendedTarget, elapsedTicks, flightTicks);
		validateTrajectory(start, intendedTarget, elapsedTicks, flightTicks);
		double ticks = clamp(elapsedTicks, 0.0, flightTicks);
		return clusterInitialVelocity(start, intendedTarget, flightTicks)
			.add(0.0, -CLUSTER_GRAVITY_PER_TICK_SQUARED * ticks, 0.0);
	}

	private static Vec3 clusterBallisticPosition(final Vec3 start, final Vec3 target,
		final double elapsedTicks, final int flightTicks) {
		validateTrajectory(start, target, elapsedTicks, flightTicks);
		double ticks = clamp(elapsedTicks, 0.0, flightTicks);
		return start.add(clusterInitialVelocity(start, target, flightTicks).scale(ticks))
			.add(0.0, -0.5 * CLUSTER_GRAVITY_PER_TICK_SQUARED * ticks * ticks, 0.0);
	}

	private static Vec3 clusterInitialVelocity(final Vec3 start, final Vec3 target,
		final int flightTicks) {
		return target.subtract(start)
			.add(0.0, 0.5 * CLUSTER_GRAVITY_PER_TICK_SQUARED
				* flightTicks * flightTicks, 0.0)
			.scale(1.0 / flightTicks);
	}

	private static void validateTrajectory(final Vec3 start, final Vec3 target,
		final double elapsedTicks, final int flightTicks) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(target, "intendedTarget");
		if (!start.isFinite() || !target.isFinite())
			throw new IllegalArgumentException("trajectory positions must be finite");
		validateElapsed(elapsedTicks);
		validateFlightTicks(flightTicks);
	}

	private static void validateElapsed(final double elapsedTicks) {
		if (!Double.isFinite(elapsedTicks)) {
			throw new IllegalArgumentException("elapsedTicks must be finite");
		}
	}

	private static void validateFlightTicks(final int flightTicks) {
		if (flightTicks <= 0) {
			throw new IllegalArgumentException("flightTicks must be greater than zero");
		}
	}

	private static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
