package com.andye.warmod.warhead;

/** Compact immutable counters used by preparation and commit diagnostics. */
public record MutationCategoryCounts(long craterExcavation, long craterShell,
    long craterCleanup, long surface, long vegetation, long structure,
    long decoration, long fire, long biomeQuarts, long other) {

    public MutationCategoryCounts {
        if (craterExcavation < 0L || craterShell < 0L || craterCleanup < 0L
            || surface < 0L || vegetation < 0L || structure < 0L
            || decoration < 0L || fire < 0L || biomeQuarts < 0L || other < 0L) {
            throw new IllegalArgumentException("Negative mutation category count");
        }
    }

    public long blockMutations() {
        return craterExcavation + craterShell + craterCleanup + surface
            + vegetation + structure + decoration + other;
    }

    public long count(final WarheadMutationCategory category) {
        return switch (category) {
            case CRATER_EXCAVATION -> craterExcavation;
            case CRATER_SHELL -> craterShell;
            case CRATER_CLEANUP -> craterCleanup;
            case SURFACE -> surface;
            case VEGETATION -> vegetation;
            case STRUCTURE -> structure;
            case DECORATION -> decoration;
            case OTHER -> other;
        };
    }

    public MutationCategoryCounts add(final MutationCategoryCounts otherCounts) {
        return new MutationCategoryCounts(
            craterExcavation + otherCounts.craterExcavation,
            craterShell + otherCounts.craterShell,
            craterCleanup + otherCounts.craterCleanup,
            surface + otherCounts.surface,
            vegetation + otherCounts.vegetation,
            structure + otherCounts.structure,
            decoration + otherCounts.decoration,
            fire + otherCounts.fire,
            biomeQuarts + otherCounts.biomeQuarts,
            other + otherCounts.other);
    }

    public static MutationCategoryCounts empty() {
        return new MutationCategoryCounts(0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L);
    }
}
