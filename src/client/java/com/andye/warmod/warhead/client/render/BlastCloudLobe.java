package com.andye.warmod.warhead.client.render;

import net.minecraft.world.phys.Vec3;

/** One deterministic lobe in the coherent rising blast cloud. */
public record BlastCloudLobe(
	Vec3 originOffset,
	Vec3 finalOffset,
	double spawnTick,
	double growthTicks,
	double baseRadius,
	double riseFactor,
	double outwardDrift,
	double rotation,
	double phase,
	boolean upperCap,
	int red,
	int green,
	int blue,
	float opacity
) { }