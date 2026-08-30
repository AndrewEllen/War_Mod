package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class WarheadSectionRevisionCounterTest {
    @Test
    void tracksChunkAndTouchedSectionsIndependently() {
        WarheadSectionRevisionCounter revisions = new WarheadSectionRevisionCounter();
        revisions.markChanged(-2);
        revisions.markChanged(4);
        revisions.markChanged(4);
        assertEquals(3L, revisions.chunkRevision());
        assertEquals(1L, revisions.sectionRevision(-2));
        assertEquals(2L, revisions.sectionRevision(4));
        assertEquals(0L, revisions.sectionRevision(8));
    }
}
