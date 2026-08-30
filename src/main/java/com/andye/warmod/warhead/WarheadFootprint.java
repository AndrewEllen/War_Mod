package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

/**
 * One authoritative description of every horizontal radius a warhead may
 * mutate. Chunk positions are stored with {@code ChunkPos.pack(x, z)}.
 */
public record WarheadFootprint(
    double craterRadius,
    double aftermathRadius,
    double glassRadius,
    double biomeRadius,
    double maximumMutationRadius,
    LongSet requiredChunks
) {
    public WarheadFootprint {
        if (!Double.isFinite(craterRadius) || craterRadius < 0.0
            || !Double.isFinite(aftermathRadius) || aftermathRadius < 0.0
            || !Double.isFinite(glassRadius) || glassRadius < 0.0
            || !Double.isFinite(biomeRadius) || biomeRadius < 0.0
            || !Double.isFinite(maximumMutationRadius) || maximumMutationRadius < 0.0
            || requiredChunks == null || requiredChunks.isEmpty()) {
            throw new IllegalArgumentException("Invalid warhead footprint");
        }
        double calculatedMaximum = Math.max(Math.max(craterRadius, aftermathRadius),
            Math.max(glassRadius, biomeRadius));
        if (maximumMutationRadius + 1.0E-6 < calculatedMaximum) {
            throw new IllegalArgumentException("Maximum radius does not cover every mutation");
        }
        requiredChunks = LongSets.unmodifiable(new LongOpenHashSet(requiredChunks));
    }

    public int requiredChunkCount() {
        return requiredChunks.size();
    }
}
