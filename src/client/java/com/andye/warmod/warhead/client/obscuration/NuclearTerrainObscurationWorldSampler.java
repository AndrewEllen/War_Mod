package com.andye.warmod.warhead.client.obscuration;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Bounded height/normal sampler used only when a stable dust cell is created. */
final class NuclearTerrainObscurationWorldSampler {
    private NuclearTerrainObscurationWorldSampler() { }

    static GroundSample sample(final ClientLevel level, final Vec3 impactCenter,
        final double worldX, final double worldZ) {
        int x = (int) Math.floor(worldX);
        int z = (int) Math.floor(worldZ);
        if (level == null || impactCenter == null || !impactCenter.isFinite()
            || !level.hasChunkAt(x, z)) {
            return new GroundSample(new Vec3(worldX, impactCenter == null
                ? 0.0 : impactCenter.y - 0.45, worldZ), new Vec3(0.0, 1.0, 0.0));
        }
        int y = height(level, x, z);
        if (y <= level.getMinY() || y >= level.getMaxY()) y = (int) impactCenter.y;
        int west = height(level, x - 1, z);
        int east = height(level, x + 1, z);
        int north = height(level, x, z - 1);
        int south = height(level, x, z + 1);
        Vec3 normal = new Vec3(west - east, 2.0, north - south).normalize();
        if (!normal.isFinite() || normal.lengthSqr() < 0.25)
            normal = new Vec3(0.0, 1.0, 0.0);
        return new GroundSample(new Vec3(worldX, y + 0.06, worldZ), normal);
    }

    private static int height(final ClientLevel level, final int x, final int z) {
        if (!level.hasChunkAt(x, z)) return (level.getMinY() + level.getMaxY()) / 2;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    record GroundSample(Vec3 position, Vec3 normal) { }
}
