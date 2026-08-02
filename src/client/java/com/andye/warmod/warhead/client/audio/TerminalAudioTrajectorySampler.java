package com.andye.warmod.warhead.client.audio;

import com.andye.warmod.warhead.WarheadConstants;
import com.andye.warmod.warhead.WarheadTrajectory;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.WarheadVisualState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class TerminalAudioTrajectorySampler {
	private static final double PROPAGATION_SPEED_BLOCKS_PER_SECOND = 343.0;
	private TerminalAudioTrajectorySampler() { }
	public static Sample sample(final WarheadVisualState state, final double currentClientGameTime, final Vec3 listener) {
		double minimum = state.launchGameTime() - 200.0;
		double maximum = state.launchGameTime() + state.flightTicks() + 200.0;
		double sourceTick = Mth.clamp(currentClientGameTime, minimum, maximum);
		for (int iteration = 0; iteration < 3; iteration++) {
			double elapsed = sourceTick - state.launchGameTime();
			Vec3 position = WarheadTrajectory.position(state.startPosition(), state.intendedTarget(), elapsed, state.flightTicks());
			double delay = listener.distanceTo(position) / PROPAGATION_SPEED_BLOCKS_PER_SECOND * 20.0;
			sourceTick = Mth.clamp(currentClientGameTime - delay, minimum, maximum);
		}
		double elapsed = sourceTick - state.launchGameTime();
		Vec3 position = WarheadTrajectory.position(state.startPosition(), state.intendedTarget(), elapsed, state.flightTicks());
		Vec3 velocity = WarheadTrajectory.velocity(state.startPosition(), state.intendedTarget(), elapsed, state.flightTicks());
		double normalizedSpeed = WarheadVisualMath.normalizedSpeed(velocity,
			WarheadConstants.TRAJECTORY_SPEED_BLOCKS_PER_TICK * 1.65);
		return new Sample(sourceTick, elapsed, position, listener.distanceTo(position), normalizedSpeed,
			elapsed >= 0.0 && elapsed < state.flightTicks() && normalizedSpeed > 0.32);
	}
	public record Sample(double sourceTick,double elapsedTicks,Vec3 position,double apparentDistance,
		double normalizedSpeed,boolean rushActive) { }
}
