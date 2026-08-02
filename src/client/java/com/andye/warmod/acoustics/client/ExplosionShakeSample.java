package com.andye.warmod.acoustics.client;

public record ExplosionShakeSample(float pitch, float yaw, float roll, float lateral, float vertical, float forward) {
	public static final ExplosionShakeSample ZERO = new ExplosionShakeSample(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
}