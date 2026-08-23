package com.andye.warmod.warhead.client.curtain;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Independent, packet-time terrain sampler for destruction-curtain anchors. */
final class NuclearCurtainWorldSampler {
    private static final double[] FALLBACK_SCALES = {0.0, 0.25, 0.50, 0.75};

    private NuclearCurtainWorldSampler() { }

    static Vec3 sample(final ClientLevel level, final Vec3 center, final double radius,
        final double angle) {
        if (level == null || center == null || !center.isFinite()
            || !Double.isFinite(radius) || !Double.isFinite(angle)) return null;
        int x = (int) Math.floor(center.x + Math.cos(angle) * radius);
        int z = (int) Math.floor(center.z + Math.sin(angle) * radius);
        if (!level.hasChunkAt(x, z)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinY() || y >= level.getMaxY()) return null;
        return new Vec3(x + 0.5, y + 0.04, z + 0.5);
    }

    /** Geometry-only fallback used when the rapidly advancing shell is beyond
        the client's currently sampled heightmap. It keeps the shroud alive and
        lets normal terrain anchors take over as soon as chunks are available. */
    static Vec3 fallback(final ClientLevel level, final Vec3 center, final double radius,
        final double angle) {
        if (level == null || center == null || !center.isFinite() || !Double.isFinite(radius)
            || !Double.isFinite(angle)) return null;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double y = center.y - 0.45;
        /* Reuse the nearest loaded height along the same radial when the actual
           front is outside the client heightmap. The impact-center chunk is the
           usual fallback, keeping the curtain on ground rather than in mid-air. */
        for (double scale : FALLBACK_SCALES) {
            int sampleX = (int) Math.floor(center.x + cos * radius * scale);
            int sampleZ = (int) Math.floor(center.z + sin * radius * scale);
            if (!level.hasChunkAt(sampleX, sampleZ)) continue;
            int sampleY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                sampleX, sampleZ);
            if (sampleY > level.getMinY() && sampleY < level.getMaxY()) {
                y = sampleY + 0.04;
                break;
            }
        }
        return new Vec3(center.x + cos * radius, y, center.z + sin * radius);
    }
}
