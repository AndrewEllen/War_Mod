package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

final class LongMutationCursorTest {
    @Test
    void drainsWithoutMovingOrReallocatingTheBackingPrefix() {
        LongMutationCursor cursor = new LongMutationCursor();
        for (long value = 0; value < 10_000; value++) cursor.add(value);
        LongArrayList observed = new LongArrayList();
        while (cursor.hasNext()) cursor.drain(32, observed::add);

        assertEquals(10_000, observed.size());
        for (int index = 0; index < observed.size(); index++) {
            assertEquals(index, observed.getLong(index));
        }
        assertEquals(0, cursor.remaining());
        assertFalse(cursor.hasNext());
    }

    @Test
    void supportsDirectPrimitiveIteration() {
        LongMutationCursor cursor = new LongMutationCursor();
        cursor.add(7L);
        cursor.add(11L);
        assertTrue(cursor.hasNext());
        assertEquals(7L, cursor.nextLong());
        assertEquals(11L, cursor.nextLong());
    }
}
