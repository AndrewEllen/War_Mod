package com.andye.warmod.warhead.client.obscuration;

import net.minecraft.util.Mth;

/** Alpha-compositing budget used independently by each protected impact. */
public final class TerrainObscurationOpticalBudget {
    private TerrainObscurationOpticalBudget() { }

    public static float admittedOpacity(final float accumulated,
        final float requested, final float target) {
        float current = Mth.clamp(accumulated, 0.0F, 1.0F);
        float cap = Mth.clamp(target, current, 1.0F);
        if (current >= cap || requested <= 0.0F) return 0.0F;
        float maximum = (cap - current) / Math.max(1.0E-6F, 1.0F - current);
        return Mth.clamp(requested, 0.0F, maximum);
    }

    public static float accumulate(final float accumulated, final float opacity) {
        float current = Mth.clamp(accumulated, 0.0F, 1.0F);
        float layer = Mth.clamp(opacity, 0.0F, 1.0F);
        return 1.0F - (1.0F - current) * (1.0F - layer);
    }
}
