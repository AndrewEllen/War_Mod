package com.andye.warmod.fire;

/**
 * Shared projected-size policy for CPU and GPU fire renderers. Inputs are
 * screen-space pixel diameters, so a spyglass or another FOV zoom naturally
 * requests more detail without a renderer-specific distance exception.
 */
public final class FireVisualLodPolicy {
    public static final double FULL_DETAIL_PIXELS = 32.0;
    public static final double MEDIUM_DETAIL_PIXELS = 14.0;
    public static final double FAR_DETAIL_PIXELS = 4.0;

    private FireVisualLodPolicy() { }

    public static int level(final double projectedHostDiameter) {
        if (!Double.isFinite(projectedHostDiameter) || projectedHostDiameter <= 0.0)
            return 3;
        if (projectedHostDiameter >= FULL_DETAIL_PIXELS) return 0;
        if (projectedHostDiameter >= MEDIUM_DETAIL_PIXELS) return 1;
        if (projectedHostDiameter >= FAR_DETAIL_PIXELS) return 2;
        return 3;
    }

    public static double density(final int level) {
        return switch (clampLevel(level)) {
            case 0 -> 1.0;
            case 1 -> 0.50;
            case 2 -> 0.10;
            default -> 0.035;
        };
    }

    /** Stable source-patch ceiling for a single burning block. */
    public static int representativesPerHost(final int available,
        final double projectedHostDiameter) {
        if (available <= 0) return 0;
        int maximum = switch (level(projectedHostDiameter)) {
            case 0 -> available;
            case 1 -> 6;
            case 2 -> 3;
            default -> 1;
        };
        return Math.min(available, maximum);
    }

    /** Far representatives grow only enough to remain legible, never into blobs. */
    public static float particleScale(final double projectedHostDiameter) {
        return particleScaleForLevel(level(projectedHostDiameter));
    }

    public static float particleScaleForLevel(final int level) {
        return switch (clampLevel(level)) {
            case 0 -> 1.0F;
            case 1 -> 1.05F;
            case 2 -> 1.18F;
            default -> 1.32F;
        };
    }

    /** Probability that a stable, deterministic far ember remains represented. */
    public static double emberRetention(final double projectedEmberDiameter) {
        if (!Double.isFinite(projectedEmberDiameter) || projectedEmberDiameter <= 0.0)
            return 0.06;
        double normalized = smoothStep(0.45, 3.25, projectedEmberDiameter);
        return 0.06 + normalized * 0.94;
    }

    /** Modest size compensation for sub-pixel embers, capped below a flame card. */
    public static float emberScale(final double projectedEmberDiameter) {
        if (!Double.isFinite(projectedEmberDiameter) || projectedEmberDiameter <= 0.0)
            return 1.75F;
        return (float) clamp(1.35 / projectedEmberDiameter, 1.0, 1.75);
    }

    private static int clampLevel(final int level) {
        return Math.max(0, Math.min(3, level));
    }

    private static double smoothStep(final double edge0, final double edge1,
        final double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(final double value, final double minimum,
        final double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
