package com.andye.warmod.warhead.client.render;

import net.minecraft.util.Mth;

/** Shared timing contract for CPU and provisional GPU conventional shrouds. */
public final class ConventionalSmokeShroudPolicy {
    public static final double BEGIN_TICK = 4.0;
    public static final double USEFUL_COVERAGE_TICK = 60.0;
    public static final double MINIMUM_AIRBORNE_HOLD_TICK = 160.0;
    public static final double SETTLING_START_MIN_TICK = 180.0;
    public static final double SETTLING_START_MAX_TICK = 240.0;
    public static final double MAIN_FADE_START_TICK = 300.0;
    public static final double MAIN_FADE_END_TICK = 700.0;
    public static final float MINIMUM_INDIVIDUAL_LIFETIME = 240.0F;
    public static final float MAXIMUM_INDIVIDUAL_LIFETIME = 520.0F;

    private ConventionalSmokeShroudPolicy() { }

    public static boolean active(final double ageTicks) {
        return Double.isFinite(ageTicks) && ageTicks >= BEGIN_TICK
            && ageTicks < MAIN_FADE_END_TICK;
    }

    public static float coverage(final double ageTicks) {
        float value = Mth.clamp((float) ((ageTicks - BEGIN_TICK)
            / (USEFUL_COVERAGE_TICK - BEGIN_TICK)), 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    public static float systemFade(final double ageTicks) {
        if (ageTicks < MAIN_FADE_START_TICK) return 1.0F;
        return Mth.clamp((float) ((MAIN_FADE_END_TICK - ageTicks)
            / (MAIN_FADE_END_TICK - MAIN_FADE_START_TICK)), 0.0F, 1.0F);
    }

    public static float individualLifetime(final float unit) {
        return Mth.lerp(Mth.clamp(unit, 0.0F, 1.0F),
            MINIMUM_INDIVIDUAL_LIFETIME, MAXIMUM_INDIVIDUAL_LIFETIME);
    }

    public static float settlingStart(final float unit) {
        return Mth.lerp(Mth.clamp(unit, 0.0F, 1.0F),
            (float) SETTLING_START_MIN_TICK, (float) SETTLING_START_MAX_TICK);
    }
}
