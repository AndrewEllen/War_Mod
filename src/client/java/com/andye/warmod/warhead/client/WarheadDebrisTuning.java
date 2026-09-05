package com.andye.warmod.warhead.client;

import net.minecraft.util.Mth;

/** Client-side launch-velocity tuning for newly received terrain debris batches. */
public final class WarheadDebrisTuning {
    public static final float DEFAULT_HORIZONTAL_VELOCITY_MULTIPLIER = 1.0F;
    public static final float DEFAULT_VERTICAL_VELOCITY_MULTIPLIER = 1.0F;
    public static final float DEFAULT_NUCLEAR_HORIZONTAL_VELOCITY_MULTIPLIER = 1.25F;
    public static final float DEFAULT_NUCLEAR_VERTICAL_VELOCITY_MULTIPLIER = 1.10F;
    private static final float MINIMUM_MULTIPLIER = 0.0F;
    private static final float MAXIMUM_MULTIPLIER = 4.0F;

    private static volatile float horizontalVelocityMultiplier =
        DEFAULT_HORIZONTAL_VELOCITY_MULTIPLIER;
    private static volatile float verticalVelocityMultiplier =
        DEFAULT_VERTICAL_VELOCITY_MULTIPLIER;
    private static volatile float nuclearHorizontalVelocityMultiplier =
        DEFAULT_NUCLEAR_HORIZONTAL_VELOCITY_MULTIPLIER;
    private static volatile float nuclearVerticalVelocityMultiplier =
        DEFAULT_NUCLEAR_VERTICAL_VELOCITY_MULTIPLIER;

    private WarheadDebrisTuning() { }

    public static float horizontalVelocityMultiplier() {
        return horizontalVelocityMultiplier;
    }

    public static float verticalVelocityMultiplier() {
        return verticalVelocityMultiplier;
    }

    public static float nuclearHorizontalVelocityMultiplier() {
        return nuclearHorizontalVelocityMultiplier;
    }

    public static float nuclearVerticalVelocityMultiplier() {
        return nuclearVerticalVelocityMultiplier;
    }

    public static void setHorizontalVelocityMultiplier(final float multiplier) {
        float value = clamp(multiplier);
        horizontalVelocityMultiplier = value;
        nuclearHorizontalVelocityMultiplier = value;
    }

    public static void setVerticalVelocityMultiplier(final float multiplier) {
        float value = clamp(multiplier);
        verticalVelocityMultiplier = value;
        nuclearVerticalVelocityMultiplier = value;
    }

    public static void reset() {
        horizontalVelocityMultiplier = DEFAULT_HORIZONTAL_VELOCITY_MULTIPLIER;
        verticalVelocityMultiplier = DEFAULT_VERTICAL_VELOCITY_MULTIPLIER;
        nuclearHorizontalVelocityMultiplier =
            DEFAULT_NUCLEAR_HORIZONTAL_VELOCITY_MULTIPLIER;
        nuclearVerticalVelocityMultiplier =
            DEFAULT_NUCLEAR_VERTICAL_VELOCITY_MULTIPLIER;
    }

    private static float clamp(final float multiplier) {
        if (!Float.isFinite(multiplier)) return 1.0F;
        return Mth.clamp(multiplier, MINIMUM_MULTIPLIER, MAXIMUM_MULTIPLIER);
    }
}
