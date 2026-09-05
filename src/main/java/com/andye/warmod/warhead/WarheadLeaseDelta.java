package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

/** Deterministic retarget transition; shared chunks retain their reference. */
record WarheadLeaseDelta(LongSet acquired, LongSet retained, LongSet released) {
    WarheadLeaseDelta {
        acquired = immutable(acquired);
        retained = immutable(retained);
        released = immutable(released);
    }

    static WarheadLeaseDelta between(final LongSet previous, final LongSet revised) {
        LongOpenHashSet acquired = new LongOpenHashSet(revised);
        acquired.removeAll(previous);
        LongOpenHashSet retained = new LongOpenHashSet(revised);
        retained.retainAll(previous);
        LongOpenHashSet released = new LongOpenHashSet(previous);
        released.removeAll(revised);
        return new WarheadLeaseDelta(acquired, retained, released);
    }

    private static LongSet immutable(final LongSet values) {
        return LongSets.unmodifiable(new LongOpenHashSet(values));
    }
}
