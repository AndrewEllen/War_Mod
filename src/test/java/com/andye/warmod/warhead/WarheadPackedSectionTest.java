package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class WarheadPackedSectionTest {
    @Test
    void decodesTheDiscPaletteLayoutWithoutWorldAccess() {
        int bits = 2;
        int valuesPerLong = 64 / bits;
        long[] storage = new long[4096 / valuesPerLong];
        int localX = 7, localY = 11, localZ = 3;
        int index = (localY << 4 | localZ) << 4 | localX;
        int cell = index / valuesPerLong;
        int bit = (index - cell * valuesPerLong) * bits;
        storage[cell] = 2L << bit;
        WarheadPackedSection section = new WarheadPackedSection(4,
            new int[] {0, 17, 29}, bits, storage);
        assertEquals(29, section.stateId(localX, localY, localZ));
        assertEquals(0, section.stateId(0, 0, 0));
    }
}
