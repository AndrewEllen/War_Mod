package com.andye.warmod.acoustics.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ExplosionShakeManager {
	public static final ExplosionShakeManager INSTANCE = new ExplosionShakeManager();
	private static final int MAX_IMPULSES = 96;
	private final List<ExplosionShakeImpulse> impulses = new ArrayList<>();
	private long currentTick;

	private ExplosionShakeManager() {
	}

	public synchronized void tick(final long gameTime) {
		this.currentTick = gameTime;
		Iterator<ExplosionShakeImpulse> iterator = this.impulses.iterator();
		while (iterator.hasNext()) {
			ExplosionShakeImpulse impulse = iterator.next();
			if (gameTime >= impulse.startTick() + impulse.durationTicks()) iterator.remove();
		}
	}

	/** Existing acoustic shake entry point. */
	public synchronized void add(final long eventSeed, final double listenerDistance, final double gain) {
		double distanceFactor = 1.0 / (1.0 + Math.pow(listenerDistance / 90.0, 1.55));
		double gainFactor = Math.sqrt(clamp(gain, 0.0, 1.0));
		double closeBoost = 1.0 + 2.2 * (1.0 - clamp(listenerDistance / 160.0, 0.0, 1.0));
		double intensity = clamp(distanceFactor * gainFactor * closeBoost, 0.0, 2.4);
		if (intensity <= 0.001) return;
		addImpulse(eventSeed, listenerDistance, gain, intensity, duration(listenerDistance));
	}

	/**
	 * Supplemental yield-aware impulse delivered when the physical pressure
	 * front reaches the listener. This covers tactical HE and nuclear yields
	 * whose acoustic profile may not use the legacy large-explosion identifier.
	 */
	public synchronized void addVisualImpact(final long visualSeed, final double listenerDistance,
		final double radiusScale, final boolean nuclear) {
		double safeScale = clamp(radiusScale, 0.22, 1.75);
		double reach = (nuclear ? 280.0 : 105.0) * Math.pow(safeScale, 0.82);
		double distanceFactor = 1.0 / (1.0 + Math.pow(listenerDistance / Math.max(24.0, reach), 1.42));
		double base = nuclear ? 2.35 : 0.72;
		double closeBoost = 1.0 + (nuclear ? 1.85 : 0.95)
			* (1.0 - clamp(listenerDistance / Math.max(40.0, reach * 1.35), 0.0, 1.0));
		double intensity = clamp(base * Math.pow(safeScale, 0.72) * distanceFactor * closeBoost,
			0.0, nuclear ? 4.8 : 1.8);
		if (intensity <= 0.004) return;
		int duration = Math.max(6, (int) Math.round(duration(listenerDistance)
			* (nuclear ? 1.28 : 0.82) * Math.pow(safeScale, 0.28)));
		addImpulse(visualSeed ^ 0x56495355414C5348L, listenerDistance, safeScale,
			intensity, duration);
	}

	private void addImpulse(final long eventSeed, final double listenerDistance,
		final double gainMarker, final double intensity, final int duration) {
		long mixed = mix(eventSeed ^ Double.doubleToLongBits(listenerDistance)
			^ Double.doubleToLongBits(gainMarker));
		double pitchPhase = phase(mixed);
		double yawPhase = phase(mix(mixed ^ 0x594157L));
		double rollPhase = phase(mix(mixed ^ 0x524F4C4CL));
		while (this.impulses.size() >= MAX_IMPULSES) this.impulses.removeFirst();
		this.impulses.add(new ExplosionShakeImpulse(
			mixed,
			this.currentTick,
			duration,
			intensity,
			pitchPhase,
			yawPhase,
			rollPhase
		));
	}

	public synchronized ExplosionShakeSample sample(final double partialTick) {
		double tick = this.currentTick + clamp(partialTick, 0.0, 1.0);
		double pitch = 0.0;
		double yaw = 0.0;
		double roll = 0.0;
		double lateral = 0.0;
		double vertical = 0.0;
		double forward = 0.0;
		for (ExplosionShakeImpulse impulse : this.impulses) {
			ExplosionShakeSample sample = impulse.sample(tick);
			pitch += sample.pitch();
			yaw += sample.yaw();
			roll += sample.roll();
			lateral += sample.lateral();
			vertical += sample.vertical();
			forward += sample.forward();
		}
		return new ExplosionShakeSample(
			(float) clamp(pitch, -22.0, 22.0),
			(float) clamp(yaw, -17.0, 17.0),
			(float) clamp(roll, -11.0, 11.0),
			(float) clamp(lateral, -0.34, 0.34),
			(float) clamp(vertical, -0.28, 0.28),
			(float) clamp(forward, -0.22, 0.22)
		);
	}

	public synchronized void clear() {
		this.impulses.clear();
		this.currentTick = 0L;
	}

	private static int duration(final double distance) {
		if (distance < 40.0) return (int) Math.round(44.0 - distance / 40.0 * 8.0);
		if (distance < 100.0) return (int) Math.round(36.0 - (distance - 40.0) / 60.0 * 8.0);
		if (distance < 300.0) return (int) Math.round(24.0 - (distance - 100.0) / 200.0 * 8.0);
		return Math.max(4, (int) Math.round(11.0
			- Math.min(700.0, distance - 300.0) / 700.0 * 7.0));
	}

	private static double phase(final long value) {
		return (value & 0xFFFFL) / 65536.0 * Math.PI * 2.0;
	}

	private static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}
}
