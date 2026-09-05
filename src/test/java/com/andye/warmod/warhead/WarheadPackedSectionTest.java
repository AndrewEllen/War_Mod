package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.junit.jupiter.api.Test;

final class WarheadPackedSectionTest {
    static { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

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

    @Test
    void roundTripsRealPalettedContainersAcrossPaletteWidths() {
        for (int paletteSize : new int[] {1, 2, 3, 16, 17, 33, 257}) {
            roundTrip(paletteSize, 0x5EED_0000L + paletteSize);
        }
    }

    @Test
    void rejectsMalformedStorageAndPaletteIndicesInsteadOfReturningPaletteZero() {
        assertThrows(IllegalArgumentException.class, () -> new WarheadPackedSection(
            0, new int[] {0, 1}, 1, new long[1]));

        long[] storage = new long[128];
        storage[0] = 3L;
        WarheadPackedSection section = new WarheadPackedSection(0,
            new int[] {11, 22, 33}, 2, storage);
        assertThrows(IllegalStateException.class, () -> section.stateId(0, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> section.stateId(16, 0, 0));
    }

    private static void roundTrip(final int paletteSize, final long seed) {
        Strategy<BlockState> strategy = Strategy.createForBlockStates(
            Block.BLOCK_STATE_REGISTRY);
        PalettedContainer<BlockState> source = new PalettedContainer<>(
            Blocks.AIR.defaultBlockState(), strategy);
        ArrayList<BlockState> palette = new ArrayList<>(paletteSize);
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            palette.add(state);
            if (palette.size() == paletteSize) break;
        }
        assertEquals(paletteSize, palette.size());
        SplittableRandom random = new SplittableRandom(seed);
        int[] expected = new int[4_096];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = (y << 4 | z) << 4 | x;
                    BlockState state = palette.get(paletteSize == 1 ? 0
                        : random.nextInt(paletteSize));
                    source.set(x, y, z, state);
                    expected[index] = Block.getId(state);
                }
            }
        }
        PalettedContainerRO.PackedData<BlockState> packed = source.pack(strategy);
        int[] stateIds = packed.paletteEntries().stream().mapToInt(Block::getId).toArray();
        long[] storage = packed.storage().map(stream -> stream.toArray())
            .orElseGet(() -> new long[0]);
        WarheadPackedSection detached = new WarheadPackedSection(-3, stateIds,
            packed.bitsPerEntry(), storage);
        int registrySize = Block.BLOCK_STATE_REGISTRY.size();
        int[] flags = new int[registrySize];
        flags[Block.getId(Blocks.AIR.defaultBlockState())] = WarheadSnapshotFlags.AIR;
        int[] tops = new int[256];
        Arrays.fill(tops, -33);
        WarheadChunkSnapshot snapshot = new WarheadChunkSnapshot(new ChunkPos(0, 0),
            1L, -3, new long[] {1L}, -48, -33, -48, -33,
            WarheadSnapshotFeatures.CRATER_VOLUME, tops, tops,
            new WarheadStateMetadata(flags, new float[registrySize],
                Block.getId(Blocks.AIR.defaultBlockState())), List.of(detached),
            new byte[] {WarheadSectionCoverage.CAPTURED_PACKED.wireId()},
            new int[] {WarheadSnapshotFeatures.CRATER_VOLUME},
            new long[0], new int[0]);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = (y << 4 | z) << 4 | x;
                    assertEquals(expected[index], detached.stateId(x, y, z),
                        "palette=" + paletteSize + " cell=" + index);
                    assertEquals(expected[index], snapshot.requireStateIdAt(x,
                        y - 48, z, WarheadSnapshotFeatures.CRATER_VOLUME),
                        "snapshot palette=" + paletteSize + " cell=" + index);
                }
            }
        }
    }
}
