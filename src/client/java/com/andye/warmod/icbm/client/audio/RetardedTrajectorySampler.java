package com.andye.warmod.icbm.client.audio;

import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RetardedTrajectorySampler {
	private static final double PROPAGATION_SPEED_BLOCKS_PER_SECOND = 343.0;
	private RetardedTrajectorySampler() { }

	public static Sample sample(final IcbmFlightPlan plan, final double currentClientGameTime, final Vec3 listenerPosition) {
		double minimumTick = plan.launchGameTime() - 240.0;
		double maximumTick = plan.launchGameTime() + plan.separationTick() + 240.0;
		double sourceTick = Mth.clamp(currentClientGameTime, minimumTick, maximumTick);
		for (int iteration = 0; iteration < 3; iteration++) {
			double elapsed = sourceTick - plan.launchGameTime();
			Vec3 position = IcbmTrajectory.position(plan, elapsed);
			double delayTicks = listenerPosition.distanceTo(position) / PROPAGATION_SPEED_BLOCKS_PER_SECOND * 20.0;
			sourceTick = Mth.clamp(currentClientGameTime - delayTicks, minimumTick, maximumTick);
		}
		double elapsed = sourceTick - plan.launchGameTime();
		Vec3 position = IcbmTrajectory.position(plan, elapsed);
		return new Sample(sourceTick, elapsed, position, listenerPosition.distanceTo(position),
			elapsed >= 0.0 && elapsed < plan.ignitionTicks() + plan.boostTicks());
	}

	public record Sample(double sourceTick, double elapsedTicks, Vec3 position, double apparentDistance,
		boolean delayedThrustActive) { }
}
