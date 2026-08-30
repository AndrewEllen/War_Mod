package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class WarheadLeaseBookkeepingTest {
    @Test
    void overlappingLeasesReleaseThePhysicalReferenceOnlyAfterTheLastOwner() {
        long shared = ChunkPos.pack(2, -4);
        WarheadLeaseReferenceCounter references = new WarheadLeaseReferenceCounter();
        assertEquals(1, references.acquire(shared));
        assertEquals(2, references.acquire(shared));
        assertEquals(1, references.release(shared));
        assertEquals(1, references.count(shared));
        assertEquals(0, references.release(shared));
        assertEquals(0, references.count(shared));
    }

    @Test
    void retargetRetainsIntersectionAndDiffsOnlyChangedChunks() {
        long a = ChunkPos.pack(0, 0);
        long b = ChunkPos.pack(1, 0);
        long c = ChunkPos.pack(2, 0);
        long d = ChunkPos.pack(3, 0);
        WarheadLeaseDelta delta = WarheadLeaseDelta.between(
            new LongOpenHashSet(new long[] {a, b, c}),
            new LongOpenHashSet(new long[] {b, c, d}));
        assertEquals(new LongOpenHashSet(new long[] {d}), delta.acquired());
        assertEquals(new LongOpenHashSet(new long[] {b, c}), delta.retained());
        assertEquals(new LongOpenHashSet(new long[] {a}), delta.released());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> delta.acquired().add(ChunkPos.pack(99, 99)));
        assertTrue(delta.retained().contains(b));
    }
}
