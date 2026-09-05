package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/** Monotonic mod-owned source revisions used to validate prepared plans. */
public final class WarheadSectionRevisionCounter {
    private final Int2LongOpenHashMap sections = new Int2LongOpenHashMap();
    private long chunkRevision;

    public long chunkRevision() {
        return chunkRevision;
    }

    public long sectionRevision(final int sectionY) {
        return sections.get(sectionY);
    }

    public void markChanged(final int sectionY) {
        chunkRevision++;
        sections.addTo(sectionY, 1L);
    }
}
