package com.andye.warmod.warhead.client.render;

import net.minecraft.world.phys.Vec3;

/** Immutable deterministic parameters for one fireball or cooling-cloud lobe. */
public record FireballLobe(Vec3 baseOffset,double spawnDelayTicks,double baseRadius,double expansionMultiplier,
	double riseSpeed,double horizontalDrift,double rotation,double frameOffset,double frameRateScale,
	double pulsePhase,double pulseRate,double angularDrift,double verticalDrift,float opacityMultiplier) { }