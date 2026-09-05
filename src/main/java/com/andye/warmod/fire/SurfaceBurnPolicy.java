package com.andye.warmod.fire;

import net.minecraft.util.Mth;

/** Deterministic lifetime rules for non-consumable surface fire. */
final class SurfaceBurnPolicy {
    static final float MAX_EXTERNAL_EXTENSION_FRACTION = 0.25F;
    static final long EXTERNAL_HEAT_INTERVAL_TICKS = 8L;
    static final long SMOULDER_TAIL_TICKS = 80L;
    static final long REIGNITION_COOLDOWN_TICKS = 240L;

    private SurfaceBurnPolicy() { }

    static int initialLifetimeTicks(final int burnTicks) {
        return Mth.clamp(burnTicks, 120, 900);
    }

    static float initialExternalHeatBudget(final int lifetimeTicks) {
        return Math.max(0.0F, lifetimeTicks * MAX_EXTERNAL_EXTENSION_FRACTION);
    }

    static Extension extend(final long hardBurnEndTick, final float remainingBudgetTicks,
        final long lastExternalHeatTick, final float heatAmount, final long now) {
        if (!Float.isFinite(heatAmount) || heatAmount <= 0.0F
            || !Float.isFinite(remainingBudgetTicks) || remainingBudgetTicks <= 0.0F
            || now >= hardBurnEndTick
            || (lastExternalHeatTick != Long.MIN_VALUE
                && now - lastExternalHeatTick < EXTERNAL_HEAT_INTERVAL_TICKS)) {
            return new Extension(hardBurnEndTick, Math.max(0.0F, remainingBudgetTicks),
                lastExternalHeatTick);
        }
        float requested = Mth.clamp(heatAmount * 18.0F, 1.0F, 22.0F);
        float granted = Math.min(remainingBudgetTicks, requested);
        long extensionTicks = (long) Math.floor(granted);
        if (extensionTicks <= 0L) return new Extension(hardBurnEndTick,
            Math.max(0.0F, remainingBudgetTicks), lastExternalHeatTick);
        return new Extension(hardBurnEndTick + extensionTicks,
            Math.max(0.0F, remainingBudgetTicks - extensionTicks), now);
    }

    static boolean tailExpired(final long hardBurnEndTick, final long now) {
        return now >= hardBurnEndTick + SMOULDER_TAIL_TICKS;
    }

    static long cooldownExpiry(final long lockTick) {
        return lockTick + SMOULDER_TAIL_TICKS + REIGNITION_COOLDOWN_TICKS;
    }

    record Extension(long hardBurnEndTick, float remainingBudgetTicks,
        long lastExternalHeatTick) { }
}
