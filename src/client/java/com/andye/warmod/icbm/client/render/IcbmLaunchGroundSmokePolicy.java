package com.andye.warmod.icbm.client.render;

/**
 * Deterministic small-lobe launch smoke volume. It uses the same persistent
 * particle principle as the explosion smoke renderers: each ordinal is a
 * stable particle identity with a birth time, trajectory, buoyancy, and fade.
 * No lobe is recycled or wrapped while the cloud is alive.
 */
public final class IcbmLaunchGroundSmokePolicy {
    public static final int ICBM_LOBES = 960;
    public static final int ANTI_AIR_LOBES = 96;
    /** 21 seconds, intentionally longer than the shortest carrier stage. */
    public static final double LIFETIME_TICKS = 420.0;

    private IcbmLaunchGroundSmokePolicy() { }

    public static Lobe sample(final long launchSeed, final int ordinal,
        final double elapsedTicks, final float scale) {
        long random = mix(launchSeed ^ (long) ordinal * 0x9E3779B97F4A7C15L);
        // Interleave the three shells so every emission cohort contains a
        // dense throat feed, middle body, and expanding outer billow.
        int shell = Math.floorMod(ordinal, 3);
        boolean feed = shell == 0 || ordinal % 11 == 0;
        double age = Math.max(0.0, elapsedTicks);
        int cohort = Math.floorMod(ordinal / 3, 20);
        // Most puffs feed the throat during the slow three-second lift;
        // a smaller trailing cohort keeps supplying the widening cloud.
        double birthDelay = ordinal % 4 == 0
            ? shell * 14.0 + cohort * 3.45 + unit(random, 0) * 1.6
            : shell * 5.0 + cohort * 1.5 + unit(random, 0) * 1.6;
        double lobeAge = Math.max(0.0, age - birthDelay);
        double rollout = smooth(clamp(lobeAge / (shell == 0 ? 62.0 : shell == 1 ? 82.0 : 102.0)));
        double buoyancy = smooth(clamp((lobeAge - 3.0) / (feed ? 94.0 : 118.0)));
        double settling = smooth(clamp((lobeAge - 128.0) / 190.0));
        double fadeIn = smooth(clamp(lobeAge / 8.0));
        double fadeOut = 1.0 - smooth(clamp((age - 330.0) / 90.0));

        double baseAngle = unit(random, 1) * Math.PI * 2.0;
        double angle = baseAngle + Math.sin(lobeAge * (0.014 + unit(random, 2) * 0.012)
            + unit(random, 3) * Math.PI * 2.0) * (0.09 + rollout * 0.14);
        double finalRadius = switch (shell) {
            case 0 -> lerp(0.25, 6.5, unit(random, 4));
            case 1 -> lerp(4.0, 12.5, unit(random, 4));
            default -> lerp(9.0, 15.0, unit(random, 4));
        } * scale;
        double radius = finalRadius * (0.025 + rollout * 0.975)
            * (1.0 + settling * 0.10);
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;

        double height = feed
            ? 0.15 + buoyancy * lerp(9.0, 18.0, unit(random, 5)) * scale
            : 0.10 + buoyancy * lerp(2.0, 11.0, unit(random, 5)) * scale;
        height += settling * (feed ? lerp(0.7, 1.8, unit(random, 6))
            : lerp(0.2, 0.9, unit(random, 6))) * scale;

        double baseSize = lerp(0.72, 1.48, unit(random, 7));
        double size = Math.min(2.4, baseSize * scale
            * (0.82 + rollout * 0.47 + buoyancy * 0.23 + settling * 0.08));
        double alpha = fadeIn * fadeOut * (1.0 - settling * 0.20)
            * (feed ? 0.82 : 0.64) * (0.82 + unit(random, 8) * 0.18);

        double warmth = feed ? 1.0 - smooth(clamp((lobeAge - 18.0) / 72.0)) : 0.0;
        int red = (int) Math.round(210 + warmth * 30 + unit(random, 9) * 12);
        int green = (int) Math.round(214 + warmth * 15 + unit(random, 10) * 10);
        int blue = (int) Math.round(220 - warmth * 12 + unit(random, 11) * 9);
        float rotation = (float) (unit(random, 12) * Math.PI * 2.0
            + lobeAge * (0.006 + unit(random, 13) * 0.010));
        return new Lobe(x, height, z, (float) size, (float) alpha,
            clampColour(red), clampColour(green), clampColour(blue), rotation, feed,
            (float) rollout, (float) birthDelay);
    }

    static double smooth(final double value) {
        double bounded = clamp(value);
        return bounded * bounded * (3.0 - 2.0 * bounded);
    }

    private static double clamp(final double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double lerp(final double from, final double to, final double amount) {
        return from + (to - from) * amount;
    }

    private static int clampColour(final int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double unit(final long value, final int lane) {
        return ((mix(value + lane * 0xD1B54A32D192ED03L) >>> 11) * 0x1.0p-53);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    public record Lobe(double x, double y, double z, float radius, float alpha,
        int red, int green, int blue, float rotation, boolean verticalFeed,
        float terrainRollout, float birthDelay) { }
}
