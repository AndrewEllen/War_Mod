package com.andye.warmod.fire;

/** Immutable lineage deadline policy for one naturally spreading fire. */
final class FireLifecyclePolicy {
    static final long MAX_LIFETIME_TICKS = 12L * 60L * 20L;
    static final long TAPER_TICKS = 90L * 20L;

    private FireLifecyclePolicy() { }

    static long newRootExpiry(final long now) {
        return saturatingAdd(now, MAX_LIFETIME_TICKS);
    }

    static long legacyRootExpiry(final long ignitionGameTime, final long now) {
        long ignition = Math.min(ignitionGameTime, now);
        return saturatingAdd(ignition, MAX_LIFETIME_TICKS);
    }

    static long restoredRootExpiry(final long savedExpiry,
        final long legacyIgnitionGameTime, final long now) {
        if (savedExpiry == Long.MAX_VALUE)
            return legacyRootExpiry(legacyIgnitionGameTime, now);
        return Math.min(savedExpiry, newRootExpiry(now));
    }

    /** Natural descendants and merges retain the earliest contributing root. */
    static long inherit(final long existingExpiry, final long sourceExpiry) {
        return Math.min(existingExpiry, sourceExpiry);
    }

    static boolean expired(final long rootExpiryTick, final long now) {
        return now >= rootExpiryTick;
    }

    /** Full strength until the final 90 seconds, then a smooth fade to zero. */
    static float strength(final long rootExpiryTick, final long now) {
        long remaining = rootExpiryTick - now;
        if (remaining <= 0L) return 0.0F;
        if (remaining >= TAPER_TICKS) return 1.0F;
        float progress = remaining / (float) TAPER_TICKS;
        return progress * progress * (3.0F - 2.0F * progress);
    }

    private static long saturatingAdd(final long value, final long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }
}
