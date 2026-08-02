package com.andye.warmod.silo;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record MissileSiloCollisionContext(UUID siloId, BlockPos siloCentre, Set<BlockPos> ignoredStructureBlocks,
    double missileWidth, double missileHeight) {
    public MissileSiloCollisionContext {
        ignoredStructureBlocks = Set.copyOf(ignoredStructureBlocks);
        if (missileWidth <= 0 || missileHeight <= 0) throw new IllegalArgumentException("Invalid missile dimensions");
    }
}
