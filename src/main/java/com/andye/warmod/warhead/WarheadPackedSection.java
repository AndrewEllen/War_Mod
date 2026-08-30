package com.andye.warmod.warhead;

/** Compact detached copy of a block-state palette and its packed indices. */
record WarheadPackedSection(int sectionY, int[] paletteStateIds,
    int bitsPerEntry, long[] storage) {
    WarheadPackedSection {
        if (paletteStateIds == null || paletteStateIds.length == 0
            || bitsPerEntry < 0 || bitsPerEntry > 32 || storage == null) {
            throw new IllegalArgumentException("Malformed packed warhead section");
        }
        paletteStateIds = paletteStateIds.clone();
        storage = storage.clone();
    }

    int stateId(final int localX, final int localY, final int localZ) {
        if (bitsPerEntry == 0 || storage.length == 0) return paletteStateIds[0];
        int index = (localY << 4 | localZ) << 4 | localX;
        int valuesPerLong = 64 / bitsPerEntry;
        int cell = index / valuesPerLong;
        int bit = (index - cell * valuesPerLong) * bitsPerEntry;
        int paletteIndex = (int)(storage[cell] >>> bit
            & (1L << bitsPerEntry) - 1L);
        return paletteIndex >= 0 && paletteIndex < paletteStateIds.length
            ? paletteStateIds[paletteIndex] : paletteStateIds[0];
    }

    long estimatedBytes() {
        return 48L + (long)paletteStateIds.length * Integer.BYTES
            + (long)storage.length * Long.BYTES;
    }

    @Override public int[] paletteStateIds() { return paletteStateIds.clone(); }
    @Override public long[] storage() { return storage.clone(); }
}
