package com.andye.warmod.icbm.client.audio;

import com.andye.warmod.icbm.IcbmFlightPlan;
import com.andye.warmod.icbm.IcbmTrajectory;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RetardedTrajectorySampler {
	private RetardedTrajectorySampler() { }

	public static Sample sample(final IcbmFlightPlan plan, final double currentClientGameTime, final Vec3 listenerPosition) {
		double sourceTick = Mth.clamp(currentClientGameTime, plan.launchGameTime(),
			plan.launchGameTime() + plan.separationTick());
		double elapsed = sourceTick - plan.launchGameTime();
		Vec3 position = IcbmTrajectory.position(plan, elapsed);
		return new Sample(sourceTick, elapsed, position, listenerPosition.distanceTo(position),
			elapsed >= 0.0 && elapsed < plan.ignitionTicks() + plan.boostTicks());
	}

	public record Sample(double sourceTick, double elapsedTicks, Vec3 position, double apparentDistance,
		boolean delayedThrustActive) { }
}
