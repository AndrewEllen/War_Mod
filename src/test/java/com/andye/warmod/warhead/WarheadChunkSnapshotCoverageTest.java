package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
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

    @Test
    void verticalOnlyDerivationAcceptsIrrelevantSectionsWithoutSurfaceOrHaloReads() {
        int minimumSectionY = -4;
        int sectionCount = 24;
        byte[] coverage = new byte[sectionCount];
        int[] requirements = new int[sectionCount];
        for (int sectionY = 0; sectionY <= 4; sectionY++) {
            int index = sectionY - minimumSectionY;
            coverage[index] = WarheadSectionCoverage.PROVEN_IRRELEVANT.wireId();
            requirements[index] = WarheadSnapshotFeatures.VERTICAL_FEATURES;
        }
        WarheadChunkSnapshot snapshot = packedSnapshot(
            WarheadSnapshotFeatures.VERTICAL_FEATURES, coverage, requirements,
            List.of(), new long[0], new int[0]);

        snapshot.preflight();
        assertEquals(0, snapshot.relevantCount());
        assertEquals(15, snapshot.verticalSurfaceY(0));
        assertThrows(IllegalStateException.class, () -> snapshot.terrainSurfaceY(0));
        assertThrows(WarheadSnapshotIncompleteException.class,
            () -> snapshot.requireStateIdAt(3, 8, 7,
                WarheadSnapshotFeatures.SURFACE));
    }

    @Test
    void biomeOnlySnapshotUsesItsOwnCapturedSupportHeights() {
        byte[] coverage = new byte[24];
        int[] requirements = new int[24];
        int[] top = filledTop(23);
        WarheadChunkSnapshot snapshot = packedSnapshot(WarheadSnapshotFeatures.BIOMES,
            coverage, requirements, List.of(), new long[0], new int[0], top, metadata());

        snapshot.preflight();
        assertEquals(23, snapshot.biomeSurfaceY(0));
        assertThrows(IllegalStateException.class, () -> snapshot.terrainSurfaceY(0));
        assertThrows(IllegalStateException.class, () -> snapshot.verticalSurfaceY(0));
    }

    @Test
    void surfacePreflightRejectsAnOmittedCrossChunkHaloCell() {
        int minimumSectionY = -4;
        int sectionCount = 24;
        byte[] coverage = new byte[sectionCount];
        int[] requirements = new int[sectionCount];
        for (int sectionY = 0; sectionY <= 1; sectionY++) {
            int index = sectionY - minimumSectionY;
            coverage[index] = WarheadSectionCoverage.PROVEN_ALL_AIR.wireId();
            requirements[index] = WarheadSnapshotFeatures.SURFACE;
        }
        WarheadChunkSnapshot snapshot = packedSnapshot(WarheadSnapshotFeatures.SURFACE,
            coverage, requirements, List.of(), new long[0], new int[0]);

        WarheadSnapshotIncompleteException failure = assertThrows(
            WarheadSnapshotIncompleteException.class, snapshot::preflight);
        assertEquals(WarheadSnapshotFeatures.SURFACE, failure.feature());
        assertEquals(WarheadSectionCoverage.NOT_CAPTURED, failure.coverage());
    }

    @Test
    void lowerBuildLimitTreatsOutOfWorldExposureCellsAsExplicitAir() {
        int minimumSectionY = -4;
        byte[] coverage = new byte[24];
        int[] requirements = new int[24];
        coverage[0] = WarheadSectionCoverage.CAPTURED_PACKED.wireId();
        requirements[0] = WarheadSnapshotFeatures.SURFACE;
        int[] top = filledTop(-64);
        Halo halo = completeHalo(new ChunkPos(0, 0), top,
            Block.getId(Blocks.STONE.defaultBlockState()));
        WarheadChunkSnapshot snapshot = packedSnapshot(WarheadSnapshotFeatures.SURFACE,
            coverage, requirements,
            List.of(new WarheadPackedSection(-4,
                new int[] {Block.getId(Blocks.STONE.defaultBlockState())}, 0, new long[0])),
            halo.positions, halo.states, top, metadata());

        snapshot.preflight();
        assertEquals(-64, snapshot.terrainSurfaceY(0));
        assertEquals(Block.getId(Blocks.AIR.defaultBlockState()),
            snapshot.surfaceStateId(0, 2));
        assertEquals(Block.getId(Blocks.AIR.defaultBlockState()),
            snapshot.requireStateIdAt(0, -65, 0, WarheadSnapshotFeatures.SURFACE));
    }

    @Test
    void surfaceSupportMayUseTheFullEightBlockDescent() {
        byte[] coverage = new byte[24];
        int[] requirements = new int[24];
        coverage[3] = WarheadSectionCoverage.CAPTURED_PACKED.wireId();
        coverage[4] = WarheadSectionCoverage.CAPTURED_PACKED.wireId();
        requirements[3] = WarheadSnapshotFeatures.SURFACE;
        requirements[4] = WarheadSnapshotFeatures.SURFACE;
        int[] top = filledTop(7);
        Halo halo = completeHalo(new ChunkPos(0, 0), top,
            Block.getId(Blocks.AIR.defaultBlockState()));
        WarheadChunkSnapshot snapshot = packedSnapshot(WarheadSnapshotFeatures.SURFACE,
            coverage, requirements, List.of(
                new WarheadPackedSection(-1,
                    new int[] {Block.getId(Blocks.STONE.defaultBlockState())}, 0,
                    new long[0]),
                new WarheadPackedSection(0,
                    new int[] {Block.getId(Blocks.AIR.defaultBlockState())}, 0,
                    new long[0])), halo.positions, halo.states, top, metadata());

        snapshot.preflight();
        assertEquals(-1, snapshot.terrainSurfaceY(0));
    }

    @Test
    void crossBorderWaterCellParticipatesInSurfaceDerivation() {
        byte[] coverage = new byte[24];
        int[] requirements = new int[24];
        coverage[4] = WarheadSectionCoverage.CAPTURED_PACKED.wireId();
        coverage[5] = WarheadSectionCoverage.PROVEN_ALL_AIR.wireId();
        requirements[4] = WarheadSnapshotFeatures.SURFACE;
        requirements[5] = WarheadSnapshotFeatures.SURFACE;
        int[] top = filledTop(15);
        Halo halo = completeHalo(new ChunkPos(0, 0), top,
            Block.getId(Blocks.AIR.defaultBlockState()));
        long waterPosition = BlockPos.asLong(-1, 15, 0);
        for (int index = 0; index < halo.positions.length; index++) {
            if (halo.positions[index] == waterPosition) {
                halo.states[index] = Block.getId(Blocks.WATER.defaultBlockState());
            }
        }
        WarheadChunkSnapshot snapshot = packedSnapshot(WarheadSnapshotFeatures.SURFACE,
            coverage, requirements,
            List.of(new WarheadPackedSection(0,
                new int[] {Block.getId(Blocks.STONE.defaultBlockState())}, 0, new long[0])),
            halo.positions, halo.states, top, metadata());

        snapshot.preflight();
        assertEquals(15, snapshot.terrainSurfaceY(0));
        assertTrue((snapshot.columnFlags(0) & WarheadSnapshotFlags.WATER_NEAR) != 0);
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

    private static WarheadChunkSnapshot packedSnapshot(final int features,
        final byte[] coverage, final int[] requirements,
        final List<WarheadPackedSection> sections, final long[] haloPositions,
        final int[] haloStates) {
        int[] top = new int[256];
        Arrays.fill(top, 15);
        int registrySize = Block.BLOCK_STATE_REGISTRY.size();
        int[] flags = new int[registrySize];
        float[] resistance = new float[registrySize];
        int air = Block.getId(Blocks.AIR.defaultBlockState());
        flags[air] = WarheadSnapshotFlags.AIR;
        return new WarheadChunkSnapshot(new ChunkPos(0, 0), 1L, -4,
            new long[coverage.length], -64, 319, 0, -1, features, top, top,
            new WarheadStateMetadata(flags, resistance, air), sections,
            coverage, requirements, haloPositions, haloStates);
    }

    private static WarheadChunkSnapshot packedSnapshot(final int features,
        final byte[] coverage, final int[] requirements,
        final List<WarheadPackedSection> sections, final long[] haloPositions,
        final int[] haloStates, final int[] top, final WarheadStateMetadata metadata) {
        return new WarheadChunkSnapshot(new ChunkPos(0, 0), 1L, -4,
            new long[coverage.length], -64, 319, 0, -1, features, top, top,
            metadata, sections, coverage, requirements, haloPositions, haloStates);
    }

    private static int[] filledTop(final int y) {
        int[] top = new int[256];
        Arrays.fill(top, y);
        return top;
    }

    private static WarheadStateMetadata metadata() {
        int[] flags = new int[Block.BLOCK_STATE_REGISTRY.size()];
        float[] resistance = new float[flags.length];
        int air = Block.getId(Blocks.AIR.defaultBlockState());
        flags[air] = WarheadSnapshotFlags.AIR;
        flags[Block.getId(Blocks.STONE.defaultBlockState())] =
            WarheadSnapshotFlags.COMMON_ROCK;
        flags[Block.getId(Blocks.WATER.defaultBlockState())] =
            WarheadSnapshotFlags.FLUID | WarheadSnapshotFlags.WATER;
        return new WarheadStateMetadata(flags, resistance, air);
    }

    private static Halo completeHalo(final ChunkPos chunk, final int[] top,
        final int stateId) {
        ArrayList<Long> positions = new ArrayList<>();
        ArrayList<Integer> states = new ArrayList<>();
        int baseX = chunk.getMinBlockX();
        int baseZ = chunk.getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            addHaloColumn(positions, states, baseX - 1, baseZ + localZ,
                top[localZ * 16], stateId);
            addHaloColumn(positions, states, baseX - 2, baseZ + localZ,
                top[localZ * 16], stateId);
            addHaloColumn(positions, states, baseX + 16, baseZ + localZ,
                top[localZ * 16 + 15], stateId);
            addHaloColumn(positions, states, baseX + 17, baseZ + localZ,
                top[localZ * 16 + 15], stateId);
        }
        for (int localX = 0; localX < 16; localX++) {
            addHaloColumn(positions, states, baseX + localX, baseZ - 1,
                top[localX], stateId);
            addHaloColumn(positions, states, baseX + localX, baseZ - 2,
                top[localX], stateId);
            addHaloColumn(positions, states, baseX + localX, baseZ + 16,
                top[15 * 16 + localX], stateId);
            addHaloColumn(positions, states, baseX + localX, baseZ + 17,
                top[15 * 16 + localX], stateId);
        }
        return new Halo(positions.stream().mapToLong(Long::longValue).toArray(),
            states.stream().mapToInt(Integer::intValue).toArray());
    }

    private static void addHaloColumn(final List<Long> positions,
        final List<Integer> states, final int x, final int z, final int top,
        final int stateId) {
        for (int y = top - 12; y <= top + 2; y++) {
            if (y < -64 || y > 319) continue;
            positions.add(BlockPos.asLong(x, y, z));
            states.add(stateId);
        }
    }

    private record Halo(long[] positions, int[] states) { }
}
