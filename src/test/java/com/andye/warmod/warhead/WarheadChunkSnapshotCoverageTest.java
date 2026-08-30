package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

final class WarheadChunkSnapshotCoverageTest {
    static { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void missingRequiredSectionFailsInsteadOfReadingAsAir() {
        WarheadChunkSnapshot snapshot = snapshot(WarheadSectionCoverage.NOT_CAPTURED,
            List.of());

        assertFalse(snapshot.covers(WarheadSnapshotFeatures.CRATER_VOLUME, 0, 15));
        assertThrows(WarheadSnapshotIncompleteException.class,
            () -> snapshot.requireCoverage(WarheadSnapshotFeatures.CRATER_VOLUME));
        WarheadSnapshotIncompleteException failure = assertThrows(
            WarheadSnapshotIncompleteException.class,
            () -> snapshot.requireStateIdAt(3, 8, 7,
                WarheadSnapshotFeatures.CRATER_VOLUME));
        assertEquals(0, failure.sectionY());
        assertEquals(WarheadSectionCoverage.NOT_CAPTURED, failure.coverage());
    }

    @Test
    void provenAirIsDistinctFromMissingAndMayBeReadAsAir() {
        WarheadChunkSnapshot snapshot = snapshot(WarheadSectionCoverage.PROVEN_ALL_AIR,
            List.of());

        assertTrue(snapshot.covers(WarheadSnapshotFeatures.CRATER_VOLUME, 0, 15));
        snapshot.requireCoverage(WarheadSnapshotFeatures.CRATER_VOLUME);
        assertEquals(Block.getId(Blocks.AIR.defaultBlockState()),
            snapshot.requireStateIdAt(3, 8, 7, WarheadSnapshotFeatures.CRATER_VOLUME));
    }

    @Test
    void capturedPackedSectionSuppliesTheRequiredState() {
        int stone = Block.getId(Blocks.STONE.defaultBlockState());
        WarheadChunkSnapshot snapshot = snapshot(WarheadSectionCoverage.CAPTURED_PACKED,
            List.of(new WarheadPackedSection(0, new int[] {stone}, 0, new long[0])));

        snapshot.requireCoverage(WarheadSnapshotFeatures.CRATER_VOLUME);
        assertEquals(stone, snapshot.requireStateIdAt(3, 8, 7,
            WarheadSnapshotFeatures.CRATER_VOLUME));
    }

    private static WarheadChunkSnapshot snapshot(final WarheadSectionCoverage coverage,
        final List<WarheadPackedSection> sections) {
        int minimumSectionY = -4;
        int sectionCount = 24;
        byte[] coverageBySection = new byte[sectionCount];
        int[] requirements = new int[sectionCount];
        int craterSectionIndex = -minimumSectionY;
        coverageBySection[craterSectionIndex] = coverage.wireId();
        requirements[craterSectionIndex] = WarheadSnapshotFeatures.CRATER_VOLUME;
        int[] top = new int[256];
        Arrays.fill(top, 15);
        int registrySize = Block.BLOCK_STATE_REGISTRY.size();
        int[] flags = new int[registrySize];
        float[] resistance = new float[registrySize];
        int air = Block.getId(Blocks.AIR.defaultBlockState());
        flags[air] = WarheadSnapshotFlags.AIR;
        return new WarheadChunkSnapshot(new ChunkPos(0, 0), 1L, minimumSectionY,
            new long[sectionCount], -64, 319, 0, 15,
            WarheadSnapshotFeatures.CRATER_VOLUME, top, top,
            new WarheadStateMetadata(flags, resistance, air), sections,
            coverageBySection, requirements, new long[0], new int[0]);
    }
}
