package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.function.LongConsumer;

/** Append-only primitive mutation queue consumed without head removal or copying. */
final class LongMutationCursor {
    private final LongArrayList values = new LongArrayList();
    private int nextIndex;

    void add(final long value) {
        values.add(value);
    }

    boolean hasNext() {
        return nextIndex < values.size();
    }

    long nextLong() {
        if (!hasNext()) throw new java.util.NoSuchElementException();
        return values.getLong(nextIndex++);
    }

    int remaining() {
        return values.size() - nextIndex;
    }

    int drain(final int limit, final LongConsumer consumer) {
        if (limit <= 0 || consumer == null) return 0;
        int count = Math.min(limit, remaining());
        int end = nextIndex + count;
        while (nextIndex < end) consumer.accept(values.getLong(nextIndex++));
        return count;
    }
}
