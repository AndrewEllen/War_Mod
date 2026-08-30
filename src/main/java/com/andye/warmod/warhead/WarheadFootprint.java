package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

/**
 * One authoritative description of every horizontal radius a warhead may
 * mutate. Chunk positions are stored with {@code ChunkPos.pack(x, z)}.
 */
public record WarheadFootprint(
    NuclearTerrainProfile terrainProfile,
    LongSet requiredChunks
) {
    public WarheadFootprint {
        if (terrainProfile == null || requiredChunks == null || requiredChunks.isEmpty()) {
            throw new IllegalArgumentException("Invalid warhead footprint");
        }
        requiredChunks = LongSets.unmodifiable(new LongOpenHashSet(requiredChunks));
    }

    public double craterRadius() { return terrainProfile.horizontalRadius(); }
    public double aftermathRadius() { return terrainProfile.aftermathRadius(); }
    public double glassRadius() { return terrainProfile.glassRadius(); }
    public double biomeRadius() { return terrainProfile.biomeRadius(); }
    public double maximumMutationRadius() { return terrainProfile.maximumMutationRadius(); }

    public int requiredChunkCount() {
        return requiredChunks.size();
    }
}
