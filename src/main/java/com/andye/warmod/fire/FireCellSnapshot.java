package com.andye.warmod.fire;

import net.minecraft.world.phys.Vec3;

public record FireCellSnapshot(long id, FireSurfaceAnchor anchor, float intensity,
    float heat, float coverage, float smoke, FirePhase phase, long seed,
    long ignitionGameTime, Vec3 wind) { }
