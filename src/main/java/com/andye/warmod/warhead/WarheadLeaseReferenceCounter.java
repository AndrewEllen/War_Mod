package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/** Pure reference accounting shared by preparation leases. */
final class WarheadLeaseReferenceCounter {
    private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();

    int acquire(final long packedChunk) {
        int next = counts.get(packedChunk) + 1;
        counts.put(packedChunk, next);
        return next;
    }

    int release(final long packedChunk) {
        int current = counts.get(packedChunk);
        if (current <= 1) {
            counts.remove(packedChunk);
            return 0;
        }
        counts.put(packedChunk, current - 1);
        return current - 1;
    }

    int count(final long packedChunk) { return counts.get(packedChunk); }
    void clear() { counts.clear(); }
}
