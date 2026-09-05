package com.andye.warmod.fire;

import net.minecraft.world.phys.Vec3;

/** Authoritative windborne firebrand whose visible path matches its collision path. */
public record FireEmberSnapshot(long id, Vec3 position, Vec3 velocity, Vec3 wind,
    float intensity, long seed, long startGameTime, int lifetime) { }
