package com.andye.warmod.warhead;

/** Shared deterministic hashing used by the extracted reference policies. */
final class NuclearPolicyHash {
    private NuclearPolicyHash() { }

    static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    static double unit(final long value) {
        return (mix(value) >>> 11) * 0x1.0p-53;
    }

    static boolean clusteredPatch(final long seed, final int x, final int z,
        final int cellSize, final double selectionChance,
        final double minimumRadius, final double maximumRadius, final long salt) {
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        long hash = seed ^ ((long)cellX << 32) ^ (cellZ & 0xFFFF_FFFFL) ^ salt;
        if (unit(hash) >= selectionChance) return false;
        double centerX = cellX * cellSize + 1.0
            + unit(hash ^ 0x58434C5553544552L) * (cellSize - 2.0);
        double centerZ = cellZ * cellSize + 1.0
            + unit(hash ^ 0x5A434C5553544552L) * (cellSize - 2.0);
        double dx = x + 0.5 - centerX;
        double dz = z + 0.5 - centerZ;
        double radius = minimumRadius + unit(hash ^ 0x52434C5553544552L)
            * (maximumRadius - minimumRadius);
        return dx * dx + dz * dz <= radius * radius;
    }
}
