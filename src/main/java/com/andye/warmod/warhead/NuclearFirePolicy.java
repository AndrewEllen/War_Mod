package com.andye.warmod.warhead;

import net.minecraft.util.Mth;

/** Deterministic ground, tree and crater fire decisions from the reference path. */
final class NuclearFirePolicy {
    private NuclearFirePolicy() { }

    static boolean craterCrack(final PreparedImpactSpec impact,
        final NuclearTerrainProfile profile, final int x, final int z,
        final double craterNormalized) {
        return craterNormalized <= 1.02 && NuclearCrackField.contains(impact.seed(),
            impact.target().x, impact.target().z, x + 0.5, z + 0.5,
            profile.horizontalRadius() * 1.02);
    }

    static float craterIntensity(final double normalized) {
        return (float)Mth.clamp(0.62
            + (1.0 - Math.min(1.0, normalized)) * 0.36, 0.10, 1.0);
    }

    static boolean firePocket(final long seed, final int x, final int z,
        final double normalized) {
        if (normalized >= 0.92) return false;
        int cellSize = 12;
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        long hash = seed ^ ((long)cellX << 32) ^ (cellZ & 0xFFFF_FFFFL)
            ^ 0x46495245504F434BL;
        double heat = Mth.clamp((0.96 - normalized) / 0.76, 0.0, 1.0);
        if (NuclearPolicyHash.unit(hash) >= 0.045 + 0.145 * heat) return false;
        double centerX = cellX * cellSize + 1.5
            + NuclearPolicyHash.unit(hash ^ 0x5843454E544552L) * 9.0;
        double centerZ = cellZ * cellSize + 1.5
            + NuclearPolicyHash.unit(hash ^ 0x5A43454E544552L) * 9.0;
        double dx = x + 0.5 - centerX;
        double dz = z + 0.5 - centerZ;
        double radius = 2.0
            + NuclearPolicyHash.unit(hash ^ 0x5241444955535F50L) * 1.8;
        return dx * dx + dz * dz <= radius * radius;
    }

    static boolean legacyFirePocket(final long seed, final int x, final int z,
        final double normalized) {
        if (normalized >= 0.86) return false;
        int cellSize = 14;
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        long hash = seed ^ ((long)cellX << 32) ^ (cellZ & 0xFFFF_FFFFL)
            ^ 0x46495245504F434BL;
        double chance = 0.11 * Mth.clamp(1.05 - normalized, 0.22, 1.0);
        if (NuclearPolicyHash.unit(hash) >= chance) return false;
        double centerX = cellX * cellSize + 2.0
            + NuclearPolicyHash.unit(hash ^ 0x5843454E544552L) * 10.0;
        double centerZ = cellZ * cellSize + 2.0
            + NuclearPolicyHash.unit(hash ^ 0x5A43454E544552L) * 10.0;
        double dx = x + 0.5 - centerX;
        double dz = z + 0.5 - centerZ;
        double radius = 1.8
            + NuclearPolicyHash.unit(hash ^ 0x5241444955535F50L) * 1.5;
        return dx * dx + dz * dz <= radius * radius;
    }

    static float pocketIntensity(final PreparedImpactSpec impact,
        final double normalized) {
        double heat = Mth.clamp((0.96 - normalized) / 0.76, 0.0, 1.0);
        return impact.customFire()
            ? (float)Mth.clamp(0.30 + heat * 0.58
                + impact.yield().visualScale() * 0.025, 0.10, 1.0)
            : Mth.clamp(0.72F + impact.yield().visualScale() * 0.07F,
                0.10F, 1.0F);
    }

    static TreeFire treeFire(final PreparedImpactSpec impact,
        final double normalized, final long packed, final double chanceScale) {
        long seed = impact.seed() ^ packed ^ 0x545245455F464952L;
        if (!impact.customFire()) {
            double chance = 0.22 * Mth.clamp((0.82 - normalized) / 0.48,
                0.0, 1.0);
            if (NuclearPolicyHash.unit(seed) >= chance) return null;
            float intensity = (float)Mth.clamp(0.62
                + (1.0 - normalized) * 0.38, 0.10, 1.0);
            return new TreeFire(intensity, seed);
        }
        double heat = Mth.clamp((0.94 - normalized) / 0.60, 0.0, 1.0);
        double chance = (0.08 + 0.58 * heat * heat) * chanceScale;
        if (NuclearPolicyHash.unit(seed) >= chance) return null;
        return new TreeFire((float)Mth.clamp(0.35 + heat * 0.65,
            0.10, 1.0), seed);
    }

    record TreeFire(float intensity, long seed) { }
}
