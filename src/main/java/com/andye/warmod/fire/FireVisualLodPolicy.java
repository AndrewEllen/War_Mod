package com.andye.warmod.fire;

import com.andye.warmod.fire.network.FireVisualBand;

/**
 * Shared projected-size policy for CPU and GPU fire renderers. Inputs are
 * screen-space pixel diameters, so a spyglass or another FOV zoom naturally
 * requests more detail without a renderer-specific distance exception.
 */
public final class FireVisualLodPolicy {
    public static final double FULL_DETAIL_PIXELS = 32.0;
    public static final double MEDIUM_DETAIL_PIXELS = 14.0;
    public static final double FAR_DETAIL_PIXELS = 4.0;
    public static final double HORIZON_DETAIL_PIXELS = 1.2;
    public static final double FULL_ENTER_PIXELS = 36.0;
    public static final double FULL_EXIT_PIXELS = 28.0;
    public static final double MEDIUM_ENTER_PIXELS = 16.0;
    public static final double MEDIUM_EXIT_PIXELS = 11.0;
    public static final double FAR_ENTER_PIXELS = 5.0;
    public static final double FAR_EXIT_PIXELS = 3.0;
    public static final double HORIZON_ENTER_PIXELS = 1.5;
    public static final double HORIZON_EXIT_PIXELS = 0.9;

    private FireVisualLodPolicy() { }

    public static int level(final double projectedHostDiameter) {
        if (!Double.isFinite(projectedHostDiameter) || projectedHostDiameter <= 0.0)
            return 4;
        if (projectedHostDiameter >= FULL_DETAIL_PIXELS) return 0;
        if (projectedHostDiameter >= MEDIUM_DETAIL_PIXELS) return 1;
        if (projectedHostDiameter >= FAR_DETAIL_PIXELS) return 2;
        if (projectedHostDiameter >= HORIZON_DETAIL_PIXELS) return 3;
        return 4;
    }

    /** Stateful enter/exit thresholds prevent camera and spyglass boundary chatter. */
    public static int level(final double projectedHostDiameter,
        final int previousLevel) {
        if (!Double.isFinite(projectedHostDiameter) || projectedHostDiameter <= 0.0)
            return 4;
        int previous = clampLevel(previousLevel);
        if (previous == 0 && projectedHostDiameter >= FULL_EXIT_PIXELS) return 0;
        if (projectedHostDiameter >= FULL_ENTER_PIXELS) return 0;
        if (previous <= 1 && projectedHostDiameter >= MEDIUM_EXIT_PIXELS) return 1;
        if (projectedHostDiameter >= MEDIUM_ENTER_PIXELS) return 1;
        if (previous <= 2 && projectedHostDiameter >= FAR_EXIT_PIXELS) return 2;
        if (projectedHostDiameter >= FAR_ENTER_PIXELS) return 2;
        if (previous <= 3 && projectedHostDiameter >= HORIZON_EXIT_PIXELS) return 3;
        if (projectedHostDiameter >= HORIZON_ENTER_PIXELS) return 3;
        return 4;
    }

    public static double density(final int level) {
        return switch (clampLevel(level)) {
            case 0 -> 1.0;
            case 1 -> 0.50;
            case 2 -> 0.10;
            case 3 -> 0.035;
            default -> 0.018;
        };
    }

    /**
     * Normalized screen-space representation weight. Distance bands define
     * which hierarchy levels are available; projected host size chooses among
     * those levels, so FOV zoom can promote detail without a distance special
     * case and overlapping parent/child cells crossfade without an energy step.
     */
    public static float representationWeight(final FireVisualBand band,
        final double distance, final int selectedLevel) {
        if (band == null || !Double.isFinite(distance)) return 0.0F;
        int selected = clampLevel(selectedLevel);
        double numerator = representationScore(band, distance, selected);
        if (numerator <= 0.0) return 0.0F;
        double denominator = 0.0;
        for (FireVisualBand candidate : FireVisualBand.values()) {
            denominator += representationScore(candidate, distance, selected);
        }
        if (denominator <= 1.0E-8) return 0.0F;
        double outerFade = 1.0 - smoothStep(1_472.0, 1_536.0, distance);
        return (float)clamp(numerator / denominator * outerFade, 0.0, 1.0);
    }

    private static double representationScore(final FireVisualBand band,
        final double distance, final int selectedLevel) {
        double availability = band.weight(distance);
        if (availability <= 0.0) return 0.0;
        int levelDistance = Math.abs(band.wireId() - selectedLevel);
        return availability / (1.0 + levelDistance * 0.78);
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
            case 3 -> 1.32F;
            default -> 1.38F;
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
        return Math.max(0, Math.min(4, level));
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
