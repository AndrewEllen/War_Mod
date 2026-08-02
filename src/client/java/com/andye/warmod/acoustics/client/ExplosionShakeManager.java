package com.andye.warmod.acoustics.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ExplosionShakeManager {
	public static final ExplosionShakeManager INSTANCE = new ExplosionShakeManager();
	private static final int MAX_IMPULSES = 64;
	private final List<ExplosionShakeImpulse> impulses = new ArrayList<>();
	private long currentTick;

	private ExplosionShakeManager() { }

	public synchronized void tick(final long gameTime) {
		this.currentTick = gameTime;
		Iterator<ExplosionShakeImpulse> iterator = this.impulses.iterator();
		while (iterator.hasNext()) {
			ExplosionShakeImpulse impulse = iterator.next();
			if (gameTime >= impulse.startTick() + impulse.durationTicks()) iterator.remove();
		}
	}

	public synchronized void add(final long eventSeed, final double listenerDistance, final double gain) {
		double distanceFactor = 1.0 / (1.0 + Math.pow(listenerDistance / 90.0, 1.55));
		double gainFactor = Math.sqrt(clamp(gain, 0.0, 1.0));
		double intensity = clamp(distanceFactor * gainFactor, 0.0, 1.0);
		if (intensity <= 0.001) return;
		int duration = duration(listenerDistance);
		long mixed = mix(eventSeed ^ Double.doubleToLongBits(listenerDistance) ^ Double.doubleToLongBits(gain));
		double pitchPhase = phase(mixed);
		double yawPhase = phase(mix(mixed ^ 0x594157L));
		double rollPhase = phase(mix(mixed ^ 0x524F4C4CL));
		while (this.impulses.size() >= MAX_IMPULSES) this.impulses.removeFirst();
		this.impulses.add(new ExplosionShakeImpulse(mixed, this.currentTick, duration, intensity, pitchPhase, yawPhase, rollPhase));
	}

	public synchronized ExplosionShakeSample sample(final double partialTick) {
		double tick = this.currentTick + clamp(partialTick, 0.0, 1.0);
		double pitch = 0.0, yaw = 0.0, roll = 0.0, lateral = 0.0, vertical = 0.0, forward = 0.0;
		for (ExplosionShakeImpulse impulse : this.impulses) {
			ExplosionShakeSample sample = impulse.sample(tick);
			pitch += sample.pitch(); yaw += sample.yaw(); roll += sample.roll();
			lateral += sample.lateral(); vertical += sample.vertical(); forward += sample.forward();
		}
		return new ExplosionShakeSample((float) clamp(pitch, -5.0, 5.0), (float) clamp(yaw, -4.0, 4.0),
			(float) clamp(roll, -2.5, 2.5), (float) clamp(lateral, -0.08, 0.08),
			(float) clamp(vertical, -0.065, 0.065), (float) clamp(forward, -0.05, 0.05));
	}

	public synchronized void clear() { this.impulses.clear(); this.currentTick = 0L; }

	private static int duration(final double distance) {
		if (distance < 40.0) return 26;
		if (distance < 100.0) return (int) Math.round(22.0 - (distance - 40.0) / 60.0 * 4.0);
		if (distance < 300.0) return (int) Math.round(18.0 - (distance - 100.0) / 200.0 * 7.0);
		return Math.max(4, (int) Math.round(11.0 - Math.min(700.0, distance - 300.0) / 700.0 * 7.0));
	}
	private static double phase(final long value) { return (value & 0xFFFFL) / 65536.0 * Math.PI * 2.0; }
	private static double clamp(final double value, final double minimum, final double maximum) { return Math.max(minimum, Math.min(maximum, value)); }
	private static long mix(long value) { value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27; value *= 0x94D049BB133111EBL; return value ^ (value >>> 31); }
}