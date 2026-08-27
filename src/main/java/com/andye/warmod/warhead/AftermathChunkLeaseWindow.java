package com.andye.warmod.warhead;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Pure geometry for the bounded radial aftermath ticket window. */
final class AftermathChunkLeaseWindow {
    private AftermathChunkLeaseWindow() { }

    static Set<ChunkPos> chunks(final Vec3 center, final double innerRadius,
        final double outerRadius) {
        if (center == null || !center.isFinite() || !Double.isFinite(innerRadius)
            || !Double.isFinite(outerRadius) || outerRadius < 0.0) return Set.of();
        double inner = Math.max(0.0, Math.min(innerRadius, outerRadius));
        double outer = Math.max(inner, outerRadius);
        int minimumChunkX = Mth.floor((center.x - outer) / 16.0);
        int maximumChunkX = Mth.floor((center.x + outer) / 16.0);
        int minimumChunkZ = Mth.floor((center.z - outer) / 16.0);
        int maximumChunkZ = Mth.floor((center.z + outer) / 16.0);
        LinkedHashSet<ChunkPos> result = new LinkedHashSet<>();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (chunkIntersectsCircle(chunkX, chunkZ, center.x, center.z, outer)
                    && !chunkFullyInsideCircle(chunkX, chunkZ, center.x, center.z, inner)) {
                    result.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }
        return Set.copyOf(result);
    }

    static boolean chunkIntersectsCircle(final int chunkX, final int chunkZ,
        final double centerX, final double centerZ, final double radius) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
            || !Double.isFinite(radius) || radius < 0.0) return false;
        double minimumX = chunkX * 16.0;
        double minimumZ = chunkZ * 16.0;
        double maximumX = minimumX + 16.0;
        double maximumZ = minimumZ + 16.0;
        double closestX = Mth.clamp(centerX, minimumX, maximumX);
        double closestZ = Mth.clamp(centerZ, minimumZ, maximumZ);
        double dx = closestX - centerX;
        double dz = closestZ - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    private static boolean chunkFullyInsideCircle(final int chunkX, final int chunkZ,
        final double centerX, final double centerZ, final double radius) {
        if (radius <= 0.0) return false;
        double minimumX = chunkX * 16.0;
        double minimumZ = chunkZ * 16.0;
        double maximumX = minimumX + 16.0;
        double maximumZ = minimumZ + 16.0;
        double farthestX = Math.max(Math.abs(minimumX - centerX),
            Math.abs(maximumX - centerX));
        double farthestZ = Math.max(Math.abs(minimumZ - centerZ),
            Math.abs(maximumZ - centerZ));
        return farthestX * farthestX + farthestZ * farthestZ < radius * radius;
    }
}
