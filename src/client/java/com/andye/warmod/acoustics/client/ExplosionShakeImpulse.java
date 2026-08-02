package com.andye.warmod.acoustics.client;

/** One deterministic damped camera impulse sampled from its acoustic event seed. */
public record ExplosionShakeImpulse(long seed, long startTick, int durationTicks, double intensity,
	double pitchPhase, double yawPhase, double rollPhase) {
	public ExplosionShakeSample sample(final double tick) {
		double age = tick - this.startTick;
		if (age < 0.0 || age >= this.durationTicks || this.intensity <= 0.0) return ExplosionShakeSample.ZERO;
		double progress = age / this.durationTicks;
		double damping = Math.exp(-3.4 * progress) * (1.0 - progress);
		double amplitude = this.intensity * damping;
		double pitch = Math.sin(age * 1.48 + this.pitchPhase) * amplitude * 4.0;
		double yaw = Math.sin(age * 1.17 + this.yawPhase) * amplitude * 3.2;
		double roll = Math.sin(age * 1.73 + this.rollPhase) * amplitude * 2.0;
		double lateral = Math.sin(age * 1.31 + this.yawPhase) * amplitude * 0.055;
		double vertical = Math.sin(age * 1.64 + this.pitchPhase) * amplitude * 0.045;
		double forward = Math.sin(age * 1.09 + this.rollPhase) * amplitude * 0.035;
		return new ExplosionShakeSample((float) pitch, (float) yaw, (float) roll,
			(float) lateral, (float) vertical, (float) forward);
	}
}