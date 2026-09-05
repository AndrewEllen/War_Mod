package com.andye.warmod.warhead;

import net.minecraft.util.Mth;

/** Deterministic, allocation-free radial crack field shared by crater writers. */
final class NuclearCrackField {
    private static final long FIELD_SALT = 0x4E55434C45415243L;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private NuclearCrackField() { }

    static boolean contains(final long visualSeed, final double centerX,
        final double centerZ, final double sampleX, final double sampleZ,
        final double maximumRadius) {
        if (!(maximumRadius > 0.0) || !Double.isFinite(maximumRadius)) return false;
        double dx = sampleX - centerX;
        double dz = sampleZ - centerZ;
        double radial = Math.hypot(dx, dz);
        if (radial > maximumRadius) return false;
        /* Every arm shares this small core, so rasterisation cannot leave the
           visually conspicuous empty ring that the old polar predicate made. */
        if (radial <= 1.75) return true;

        long fieldSeed = mix(visualSeed ^ FIELD_SALT);
        int branches = 5 + (int) Math.floor(unit(fieldSeed) * 4.0);
        double rotation = unit(fieldSeed ^ 0x524F544154494F4EL) * Mth.TWO_PI;
        double spacing = Mth.TWO_PI / branches;
        double nodeStep = Mth.clamp(maximumRadius / 11.0, 4.0, 9.0);
        for (int branch = 0; branch < branches; branch++) {
            long branchSeed = mix(fieldSeed + branch * GOLDEN_GAMMA);
            double branchAngle = rotation + branch * spacing
                + signed(branchSeed ^ 0x414E474C455F4A47L) * spacing * 0.19;
            double cos = Math.cos(branchAngle);
            double sin = Math.sin(branchAngle);
            double forward = dx * cos + dz * sin;
            if (forward < -0.75 || forward > maximumRadius + 0.75) continue;
            double sideways = -dx * sin + dz * cos;
            double expected = jaggedOffset(branchSeed, forward, maximumRadius, nodeStep);
            double halfWidth = 0.62 + unit(branchSeed ^ ((long) Math.floor(
                Math.max(0.0, forward) / nodeStep) * GOLDEN_GAMMA)) * 0.32;
            if (Math.abs(sideways - expected) <= halfWidth) return true;

            /* Sparse secondary splits remain attached to their parent, then
               peel away in a separately jagged direction. */
            if ((branch & 1) == 0) {
                double forkStart = maximumRadius * (0.38
                    + unit(branchSeed ^ 0x464F524B5F41544CL) * 0.14);
                if (forward >= forkStart) {
                    double parentAtFork = jaggedOffset(branchSeed, forkStart,
                        maximumRadius, nodeStep);
                    double forkDistance = forward - forkStart;
                    double forkSign = unit(branchSeed ^ 0x464F524B5F534947L) < 0.5
                        ? -1.0 : 1.0;
                    double forkJitter = jaggedOffset(branchSeed ^ 0x464F524B5F4A4147L,
                        forkDistance, Math.max(nodeStep, maximumRadius - forkStart), nodeStep)
                        - jaggedOffset(branchSeed ^ 0x464F524B5F4A4147L,
                            0.0, Math.max(nodeStep, maximumRadius - forkStart), nodeStep);
                    double forkExpected = parentAtFork + forkSign * forkDistance * 0.20
                        + forkJitter * 0.62;
                    if (Math.abs(sideways - forkExpected) <= halfWidth * 0.82) return true;
                }
            }
        }
        return false;
    }

    private static double jaggedOffset(final long seed, final double forward,
        final double maximumRadius, final double nodeStep) {
        double clamped = Mth.clamp(forward, 0.0, maximumRadius);
        double node = clamped / nodeStep;
        int lower = (int) Math.floor(node);
        double blend = node - lower;
        double lowerOffset = controlOffset(seed, lower, nodeStep, maximumRadius);
        double upperOffset = controlOffset(seed, lower + 1, nodeStep, maximumRadius);
        return Mth.lerp(blend, lowerOffset, upperOffset);
    }

    private static double controlOffset(final long seed, final int node,
        final double nodeStep, final double maximumRadius) {
        if (node <= 0) return 0.0;
        double progress = Mth.clamp(node * nodeStep / maximumRadius, 0.0, 1.0);
        double amplitude = 0.85 + progress * Math.min(4.8, maximumRadius * 0.075);
        return signed(seed + node * GOLDEN_GAMMA) * amplitude;
    }

    private static double signed(final long value) { return unit(value) * 2.0 - 1.0; }
    private static double unit(final long value) { return (mix(value) >>> 11) * 0x1.0p-53; }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
