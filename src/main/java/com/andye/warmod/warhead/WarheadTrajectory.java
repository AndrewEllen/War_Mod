package com.andye.warmod.warhead;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public final class WarheadTrajectory {
	private static final double EASING_POWER = 1.6;
	private static final double VELOCITY_STEP_TICKS = 0.25;

	private WarheadTrajectory() {
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