package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

final class ImpactStreamPolicyTest {
    @Test
    void completionRequiresEveryExactFootprintChunkAndNoWorkInFlight() {
        LongOpenHashSet required = set(11L, 12L, 13L);
        LongOpenHashSet wrongSameCount = set(11L, 12L, 99L);
        LongOpenHashSet compiled = set(11L, 12L, 13L, 77L);

        assertFalse(ImpactStreamPolicy.containsAllRequiredChunks(required, wrongSameCount));
        assertTrue(ImpactStreamPolicy.containsAllRequiredChunks(required, compiled));
        assertEquals(ImpactStreamState.OPEN, ImpactStreamPolicy.state(true, false,
            true, false, required, compiled, set(13L)));
        assertEquals(ImpactStreamState.COMPLETE, ImpactStreamPolicy.state(true, false,
            true, false, required, compiled, set()));
    }

    @Test
    void missingCancelledAndTerminalCompileStatesAreFailuresNotFalseCompletion() {
        LongOpenHashSet required = set(1L);
        LongOpenHashSet compiled = set(1L);
        LongOpenHashSet empty = set();

        assertEquals(ImpactStreamState.FAILED, ImpactStreamPolicy.state(false, false,
            false, false, null, null, null));
        assertEquals(ImpactStreamState.FAILED, ImpactStreamPolicy.state(true, true,
            true, false, required, compiled, empty));
        assertEquals(ImpactStreamState.FAILED, ImpactStreamPolicy.state(true, false,
            true, true, required, compiled, empty));
    }

    @Test
    void allTacticalChunksCanReachPreparedOrFallbackOwnership() {
        LongOpenHashSet required = new LongOpenHashSet();
        LongOpenHashSet prepared = new LongOpenHashSet();
        LongOpenHashSet fallback = new LongOpenHashSet();
        for (long packed = 0; packed < 1_041; packed++) {
            required.add(packed);
            if (packed % 17 == 0) fallback.add(packed);
            else prepared.add(packed);
        }

        assertTrue(ImpactStreamPolicy.containsAllOwnedChunks(required, prepared, fallback));
        fallback.remove(17L);
        assertFalse(ImpactStreamPolicy.containsAllOwnedChunks(required, prepared, fallback));
        prepared.add(17L);
        assertTrue(ImpactStreamPolicy.containsAllOwnedChunks(required, prepared, fallback));
        fallback.add(17L);
        assertFalse(ImpactStreamPolicy.containsAllOwnedChunks(required, prepared, fallback),
            "a required chunk cannot be owned by both compiler paths");
    }

    private static LongOpenHashSet set(final long... values) {
        LongOpenHashSet result = new LongOpenHashSet();
        for (long value : values) result.add(value);
        return result;
    }
}
