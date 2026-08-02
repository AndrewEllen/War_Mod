package com.andye.warmod.radar;

import net.minecraft.world.phys.Vec3;

public record RadarCarrierPlanSnapshot(Vec3 launchPosition, Vec3 burnoutPosition, Vec3 separationPosition,
	Vec3 intendedTarget, long launchGameTime, int ignitionTicks, int boostTicks, int coastTicks, long visualSeed) { }
