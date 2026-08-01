package com.andye.warmod.acoustics.physics;

public final class AcousticPropagation {
	private AcousticPropagation() {
	}

	public static long delayTicks(final double distanceBlocks, final double propagationSpeedBlocksPerSecond) {
		if (!Double.isFinite(distanceBlocks) || distanceBlocks < 0.0) {
			throw new IllegalArgumentException("distanceBlocks must be finite and non-negative");
		}
		validateSpeed(propagationSpeedBlocksPerSecond);
		double ticks = Math.ceil(distanceBlocks / propagationSpeedBlocksPerSecond * 20.0);
		if (ticks > Long.MAX_VALUE) {
			throw new IllegalArgumentException("Acoustic delay is too large");
		}
		return (long)ticks;
	}

	public static double waveRadiusBlocks(final long elapsedTicks, final double propagationSpeedBlocksPerSecond) {
		validateSpeed(propagationSpeedBlocksPerSecond);
		return Math.max(0L, elapsedTicks) * propagationSpeedBlocksPerSecond / 20.0;
	}

	private static void validateSpeed(final double speed) {
		if (!Double.isFinite(speed) || speed <= 0.0) {
			throw new IllegalArgumentException("propagationSpeedBlocksPerSecond must be finite and greater than zero");
		}
	}
}
