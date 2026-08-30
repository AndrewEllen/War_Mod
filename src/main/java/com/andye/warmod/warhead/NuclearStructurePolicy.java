package com.andye.warmod.warhead;

import net.minecraft.util.Mth;

/** Pure glass and structure transformations from the reference aftermath. */
final class NuclearStructurePolicy {
    static final int NO_CHANGE = Integer.MIN_VALUE;

    private NuclearStructurePolicy() { }

    static int glass(final PreparedImpactSpec impact,
        final NuclearTerrainProfile profile, final WarheadStatePalette palette,
        final double radial, final long packed) {
        if (radial > profile.glassRadius()) return NO_CHANGE;
        double normalized = radial / Math.max(1.0, profile.maximumMutationRadius());
        double chance = normalized <= 0.72 ? 1.0
            : Mth.clamp((1.0 - normalized) / 0.28, 0.0, 1.0);
        return NuclearPolicyHash.unit(impact.seed() ^ packed
            ^ 0x474C4153535F4E55L) < chance ? palette.air() : NO_CHANGE;
    }

    static int structuralLog(final PreparedImpactSpec impact,
        final WarheadStatePalette palette, final int flags,
        final double normalized, final long packed) {
        double chance = normalized <= 0.64 ? 1.0
            : Mth.clamp((0.90 - normalized) / 0.26, 0.0, 1.0);
        return NuclearPolicyHash.unit(impact.seed() ^ packed
            ^ 0x5354525543544C47L) < chance
            ? palette.paleLog(flags) : NO_CHANGE;
    }

    static int plank(final PreparedImpactSpec impact,
        final WarheadStatePalette palette, final double normalized,
        final long packed) {
        double chance = normalized <= 0.58 ? 1.0
            : Mth.clamp((0.84 - normalized) / 0.26, 0.0, 1.0);
        return NuclearPolicyHash.unit(impact.seed() ^ packed
            ^ 0x504C414E4B5F4153L) < chance
            ? palette.paleWood() : NO_CHANGE;
    }

    static int cobble(final PreparedImpactSpec impact,
        final WarheadStatePalette palette, final double normalized,
        final long packed) {
        double chance = normalized <= 0.50 ? 1.0
            : Mth.clamp((0.76 - normalized) / 0.26, 0.0, 1.0);
        return NuclearPolicyHash.unit(impact.seed() ^ packed
            ^ 0x434F42424C455F44L) < chance
            ? palette.cobbledDeepslate() : NO_CHANGE;
    }
}
