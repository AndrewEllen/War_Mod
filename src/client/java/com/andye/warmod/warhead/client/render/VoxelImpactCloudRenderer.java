package com.andye.warmod.warhead.client.render;

import net.minecraft.world.phys.Vec3;

/**
 * Compatibility-only source value retained while older render-state code is
 * migrated. The voxel renderer and every voxel mesh/cache path have been
 * removed; nuclear visuals are rendered exclusively by the packed particle
 * field in {@link NuclearParticleCloudRenderer}.
 */
@Deprecated(forRemoval = true)
public final class VoxelImpactCloudRenderer {
    private VoxelImpactCloudRenderer() { }

    public record CloudSource(Vec3 offset, double ageTicks, float visualScale, long seed)
        implements NuclearCloudSource {
        public CloudSource {
            if (offset == null || !offset.isFinite()) offset = Vec3.ZERO;
        }
    }
}
