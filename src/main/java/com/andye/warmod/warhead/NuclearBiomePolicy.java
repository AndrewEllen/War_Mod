package com.andye.warmod.warhead;

/** Pure façade that gives the biome dome an explicit policy owner. */
final class NuclearBiomePolicy {
    private NuclearBiomePolicy() { }

    static boolean survivesFeather(final double distance, final double radius,
        final double randomUnit) {
        return NuclearBiomeDome.survivesFeather(distance, radius, randomUnit);
    }

    static int verticalHeight(final double distance, final double radius,
        final float visualScale) {
        return NuclearBiomeDome.verticalHeight(distance, radius, visualScale);
    }
}
