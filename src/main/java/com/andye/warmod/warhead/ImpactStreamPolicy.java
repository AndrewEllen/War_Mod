package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.longs.LongSet;

/** Pure delivery-state policy for an impact's prepared chunk stream. */
final class ImpactStreamPolicy {
    private ImpactStreamPolicy() { }

    static ImpactStreamState state(final boolean preparationPresent,
        final boolean cancelled, final boolean compilePresent, final boolean failed,
        final LongSet requiredChunks, final LongSet compiledChunks,
        final LongSet inFlightChunks) {
        if (!preparationPresent || cancelled || !compilePresent || failed
            || requiredChunks == null || compiledChunks == null || inFlightChunks == null) {
            return ImpactStreamState.FAILED;
        }
        return inFlightChunks.isEmpty()
            && containsAllRequiredChunks(requiredChunks, compiledChunks)
                ? ImpactStreamState.COMPLETE : ImpactStreamState.OPEN;
    }

    static boolean containsAllRequiredChunks(final LongSet requiredChunks,
        final LongSet compiledChunks) {
        if (requiredChunks == null || compiledChunks == null
            || compiledChunks.size() < requiredChunks.size()) return false;
        for (long packed : requiredChunks) {
            if (!compiledChunks.contains(packed)) return false;
        }
        return true;
    }
}
