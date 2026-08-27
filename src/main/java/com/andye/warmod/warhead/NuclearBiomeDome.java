package com.andye.warmod.warhead;

import net.minecraft.util.Mth;

/** Pure radial/vertical policy for the nuclear wasteland biome volume. */
final class NuclearBiomeDome {
    static final double RADIUS_SCALE = 1.10;
    static final double FEATHER_START = 0.88;
    static final int EDGE_HEIGHT = 32;
    static final int BOTTOM_OFFSET = -12;

    private NuclearBiomeDome() { }

    static double radius(final double physicalAftermathRadius) {
        return Math.max(0.0, physicalAftermathRadius) * RADIUS_SCALE;
    }

    static int verticalHeight(final double horizontalDistance,
        final double biomeRadius, final float visualScale) {
        if (!Double.isFinite(horizontalDistance) || !Double.isFinite(biomeRadius)
            || biomeRadius <= 0.0 || horizontalDistance > biomeRadius) return -1;
        double normalizedRadius = Mth.clamp(horizontalDistance / biomeRadius, 0.0, 1.0);
        double dome = Math.sqrt(Math.max(0.0,
            1.0 - normalizedRadius * normalizedRadius));
        int centralHeight = Mth.clamp(Mth.floor(72.0 + visualScale * 22.0), 80, 176);
        return Mth.floor(EDGE_HEIGHT + dome * (centralHeight - EDGE_HEIGHT));
    }

    static boolean survivesFeather(final double horizontalDistance,
        final double biomeRadius, final double deterministicUnit) {
        if (!Double.isFinite(horizontalDistance) || !Double.isFinite(biomeRadius)
            || biomeRadius <= 0.0 || horizontalDistance > biomeRadius) return false;
        double normalized = horizontalDistance / biomeRadius;
        if (normalized <= FEATHER_START) return true;
        double chance = (1.0 - normalized) / (1.0 - FEATHER_START);
        return deterministicUnit <= Mth.clamp(chance, 0.0, 1.0);
    }
}
