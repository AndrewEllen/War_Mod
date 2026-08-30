package com.andye.warmod.warhead;

import net.minecraft.util.Mth;

/** Pure extraction of the 62a89 scorched/fused nuclear surface gradient. */
final class NuclearSurfacePolicy {
    static final int NO_CHANGE = Integer.MIN_VALUE;

    private NuclearSurfacePolicy() { }

    static boolean mudPatch(final long seed, final int x, final int z,
        final double aftermathNormalized) {
        return aftermathNormalized > 0.22 && aftermathNormalized < 0.92
            && NuclearPolicyHash.clusteredPatch(seed, x, z, 11, 0.105,
                1.7, 3.2, 0x4D55445F50415443L);
    }

    static boolean sulfurPatch(final long seed, final int x, final int z,
        final double aftermathNormalized, final boolean touchesWater) {
        return aftermathNormalized < 0.88 && touchesWater
            && NuclearPolicyHash.clusteredPatch(seed, x, z, 9, 0.36,
                2.5, 5.2, 0x53554C4655525041L);
    }

    static int replacementDepth(final double aftermathNormalized) {
        return aftermathNormalized < 0.45 ? 3
            : aftermathNormalized < 0.78 ? 2 : 1;
    }

    static int replacement(final WarheadStatePalette palette, final int flags,
        final long hash, final double craterNormalized,
        final double aftermathNormalized, final int depth,
        final boolean mudPatch, final boolean sulfurPatch) {
        if (depth == 0 && sulfurPatch
            && (flags & WarheadSnapshotFlags.NATURAL_SURFACE) != 0) {
            return NuclearPolicyHash.unit(hash ^ 0x504F54454E545F53L) < 0.085
                ? palette.potentSulfur() : palette.sulfur();
        }
        if (depth == 0 && mudPatch && (flags & WarheadSnapshotFlags.SOIL) != 0) {
            return palette.mud();
        }
        if ((flags & WarheadSnapshotFlags.SNOW) != 0) return palette.air();
        if ((flags & WarheadSnapshotFlags.SAND) != 0) {
            return craterNormalized <= 1.65
                ? fusedSand(palette, hash, false, craterNormalized)
                : outerFusedSand(palette, hash, false);
        }
        if ((flags & WarheadSnapshotFlags.RED_SAND) != 0) {
            return craterNormalized <= 1.65
                ? fusedSand(palette, hash, true, craterNormalized)
                : outerFusedSand(palette, hash, true);
        }
        if ((flags & WarheadSnapshotFlags.SOIL) != 0 && craterNormalized <= 1.70) {
            return scorchedSoil(palette, hash, craterNormalized);
        }
        double edgeFalloff = Math.pow(Math.max(0.0, 1.0 - aftermathNormalized), 0.65);
        double outerChance = aftermathNormalized <= 0.78
            ? 1.0 : 0.18 + edgeFalloff * 0.82;
        if ((flags & WarheadSnapshotFlags.SOIL) != 0
            && NuclearPolicyHash.unit(hash ^ 0x4F555445525F4153L) < outerChance) {
            return outerScorchedSoil(palette, hash);
        }
        if ((flags & WarheadSnapshotFlags.COMMON_ROCK) != 0
            && (depth == 0 || (flags & WarheadSnapshotFlags.EXPOSED) != 0)) {
            double chance = craterNormalized <= 1.38 ? 1.0
                : Mth.clamp((0.58 - aftermathNormalized) / 0.34, 0.0, 0.82);
            if (NuclearPolicyHash.unit(hash ^ 0x524F434B5F534341L) < chance) {
                return darkCraterRock(palette, hash, craterNormalized);
            }
        }
        return NO_CHANGE;
    }

    private static int fusedSand(final WarheadStatePalette p, final long hash,
        final boolean red, final double normalized) {
        double selector = NuclearPolicyHash.unit(hash ^ 0x46555345445F534EL);
        double heat = Mth.clamp(1.25 - normalized, 0.0, 1.0);
        if (!red) {
            if (selector < 0.10 + heat * 0.10) return p.tintedGlass();
            if (selector < 0.22 + heat * 0.14) return p.blackGlass();
            if (selector < 0.34 + heat * 0.12) return p.grayGlass();
            if (selector < 0.46 + heat * 0.10) return p.lightGrayGlass();
            if (selector < 0.68) return p.whiteTerracotta();
            if (selector < 0.84) return p.calcite();
            return p.sandstone();
        }
        if (selector < 0.20 + heat * 0.16) return p.blackGlass();
        if (selector < 0.34 + heat * 0.12) return p.grayGlass();
        if (selector < 0.72) return p.terracotta();
        if (selector < 0.88) return p.redSandstone();
        return p.gravel();
    }

    private static int outerFusedSand(final WarheadStatePalette p,
        final long hash, final boolean red) {
        double selector = NuclearPolicyHash.unit(hash ^ 0x4F5554455253414EL);
        if (red) {
            if (selector < 0.62) return p.terracotta();
            if (selector < 0.84) return p.redSandstone();
            return p.gravel();
        }
        if (selector < 0.48) return p.whiteTerracotta();
        if (selector < 0.72) return p.sandstone();
        if (selector < 0.90) return p.gravel();
        return p.lightGrayGlass();
    }

    private static int scorchedSoil(final WarheadStatePalette p, final long hash,
        final double normalized) {
        double selector = NuclearPolicyHash.unit(hash ^ 0x53434F524348534FL);
        if (normalized < 0.82) {
            if (selector < 0.06) {
                return p.decoration().deadCoralBlock((int)(hash >>> 18));
            }
            if (selector < 0.28) return p.tuff();
            if (selector < 0.50) return p.coarseDirt();
            if (selector < 0.68) return p.paleMoss();
            if (selector < 0.84) return p.rootedDirt();
            return p.podzol();
        }
        if (selector < 0.055) {
            return p.decoration().deadCoralBlock((int)(hash >>> 18));
        }
        if (selector < 0.30) return p.podzol();
        if (selector < 0.56) return p.coarseDirt();
        if (selector < 0.72) return p.paleMoss();
        if (selector < 0.88) return p.tuff();
        return p.rootedDirt();
    }

    private static int outerScorchedSoil(final WarheadStatePalette p,
        final long hash) {
        double selector = NuclearPolicyHash.unit(hash ^ 0x4F55544552534F49L);
        if (selector < 0.035) {
            return p.decoration().deadCoralBlock((int)(hash >>> 18));
        }
        if (selector < 0.28) return p.coarseDirt();
        if (selector < 0.47) return p.podzol();
        if (selector < 0.61) return p.mycelium();
        if (selector < 0.75) return p.paleMoss();
        if (selector < 0.88) return p.tuff();
        return p.rootedDirt();
    }

    private static int darkCraterRock(final WarheadStatePalette p,
        final long hash, final double normalized) {
        double selector = NuclearPolicyHash.unit(hash ^ 0x4441524B5F524F43L);
        if (normalized < 0.58) {
            if (selector < 0.28) return p.basalt();
            if (selector < 0.48) return p.blackstone();
            if (selector < 0.72) return p.deepslate();
            return p.cobbledDeepslate();
        }
        if (selector < 0.26) return p.cobbledDeepslate();
        if (selector < 0.50) return p.deepslate();
        if (selector < 0.72) return p.tuff();
        if (selector < 0.88) return p.basalt();
        return p.blackstone();
    }
}
