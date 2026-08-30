package com.andye.warmod.warhead;

import net.minecraft.util.Mth;

/** Pure foliage, natural-log, snow and fragile-plant reference policy. */
final class NuclearVegetationPolicy {
    static final int NO_CHANGE = Integer.MIN_VALUE;

    private NuclearVegetationPolicy() { }

    static int leaves(final PreparedImpactSpec impact,
        final WarheadStatePalette palette, final double normalized,
        final long packed) {
        long hash = impact.seed() ^ packed ^ 0x4C45415645535F4EL;
        if (normalized <= 0.70) {
            double retention = impact.customFire() && normalized > 0.30
                ? 0.48 + Mth.clamp((normalized - 0.30) / 0.40, 0.0, 1.0) * 0.24
                : 0.0;
            return NuclearPolicyHash.unit(hash ^ 0x43524F574E5F4649L) < retention
                ? palette.paleLeaves() : palette.air();
        }
        double outer = Mth.clamp((1.0 - normalized) / 0.30, 0.0, 1.0);
        double strip = outer * 0.72;
        double pale = 0.10 + outer * 0.64;
        double selector = NuclearPolicyHash.unit(hash);
        if (selector < strip) return palette.air();
        if (selector < strip + pale) return palette.paleLeaves();
        return NO_CHANGE;
    }

    static int naturalLog(final PreparedImpactSpec impact,
        final WarheadStatePalette palette, final int flags, final int y,
        final int groundY, final double normalized, final long packed) {
        if (normalized <= 0.34) return palette.air();
        if (normalized <= 0.62) return palette.paleLog(flags);
        double upperBias = Mth.clamp((y - groundY - 5.0) / 30.0, 0.0, 1.0);
        double distanceHeat = Mth.clamp((0.94 - normalized) / 0.38,
            0.0, 1.0);
        double chance = distanceHeat * (0.28 + upperBias * 0.72);
        return NuclearPolicyHash.unit(impact.seed() ^ packed
            ^ 0x4C4F47535F415348L) < chance
            ? palette.paleLog(flags) : NO_CHANGE;
    }

    static boolean shouldAffectFragile(final PreparedImpactSpec impact,
        final int flags, final double normalized, final long packed) {
        if ((flags & WarheadSnapshotFlags.SUGAR_CANE) != 0) return true;
        double chance = normalized <= 0.78 ? 1.0
            : Mth.clamp((1.0 - normalized) / 0.22, 0.0, 1.0);
        return NuclearPolicyHash.unit(impact.seed() ^ packed
            ^ 0x46524147494C455FL) < chance;
    }

    static Mutation fragile(final PreparedImpactSpec impact,
        final WarheadStatePalette palette, final int flags,
        final int supportFlags, final double normalized, final long packed) {
        if (!shouldAffectFragile(impact, flags, normalized, packed)) return null;
        if ((flags & (WarheadSnapshotFlags.SNOW
            | WarheadSnapshotFlags.SUGAR_CANE)) != 0) {
            return new Mutation(palette.air(), false);
        }
        long hash = impact.seed() ^ packed ^ 0x46524147494C455FL;
        if ((flags & WarheadSnapshotFlags.AQUATIC_PLANT) != 0) {
            if ((flags & WarheadSnapshotFlags.DOUBLE_UPPER) != 0) {
                return new Mutation(palette.air(), false);
            }
            if ((supportFlags & WarheadSnapshotFlags.SULFUR) != 0
                && NuclearPolicyHash.unit(hash ^ 0x5350494B455F4151L) < 0.38) {
                return new Mutation(palette.decoration().sulfurSpike(
                    (flags & WarheadSnapshotFlags.WATER) != 0), true);
            }
            return new Mutation(palette.air(), false);
        }
        if ((flags & WarheadSnapshotFlags.DOUBLE_UPPER) != 0) {
            return new Mutation(palette.air(), false);
        }
        double selector = NuclearPolicyHash.unit(hash ^ 0x4452595F504C414EL);
        int replacement;
        if ((flags & WarheadSnapshotFlags.BUSH) != 0) {
            replacement = selector < 0.82
                ? palette.tallDryGrass() : palette.deadBush();
        } else if (selector < 0.006) {
            replacement = palette.decoration().witherRose();
        } else if (selector < 0.016) {
            replacement = palette.decoration().closedEyeblossom();
        } else if (selector < 0.18) {
            replacement = palette.tallDryGrass();
        } else if (selector < 0.38) {
            replacement = palette.shortDryGrass();
        } else if (selector < 0.52) {
            replacement = palette.deadBush();
        } else if (selector < 0.58) {
            replacement = palette.decoration().deadCoralFan((int)(hash >>> 20));
        } else {
            replacement = palette.air();
        }
        return new Mutation(replacement, replacement != palette.air());
    }

    record Mutation(int replacementStateId, boolean requiresSurvivalCheck) { }
}
