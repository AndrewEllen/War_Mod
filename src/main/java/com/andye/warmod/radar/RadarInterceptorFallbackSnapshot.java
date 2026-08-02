package com.andye.warmod.radar;

import net.minecraft.world.phys.Vec3;

/** Authoritative Mk I fallback transition used by both server and client radar positions. */
public record RadarInterceptorFallbackSnapshot(long transitionGameTime, Vec3 transitionPosition,
    Vec3 transitionVelocity) { }