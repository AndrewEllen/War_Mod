package com.andye.warmod.fire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record FireCellSnapshot(BlockPos position, float intensity, float heat,
    FirePhase phase, long seed, Vec3 wind) { }
