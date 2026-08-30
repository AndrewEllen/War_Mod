package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WarheadPlanCompilerTest {
    static { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void identicalPrimitiveSnapshotSeedAndYieldProduceIdenticalPlans() {
        PreparedImpactSpec impact = impact(0x1234_5678_9ABCL);
        WarheadFootprint footprint = WarheadFootprintCalculator.calculate(
            impact.payload(), impact.yield(), impact.target());
        WarheadChunkSnapshot snapshot = stoneSnapshot();
        WarheadStatePalette palette = WarheadStatePalette.capture();

        PreparedChunkPlan first = WarheadPlanCompiler.compile(impact, footprint,
            snapshot, palette);
        PreparedChunkPlan second = WarheadPlanCompiler.compile(impact, footprint,
            snapshot, palette);

        assertPlansEqual(first, second);
        assertTrue(WarheadPlanCompiler.statistics(first).changedBlocks() > 0L);
        assertFalse(first.biomeSections().isEmpty());
        PreparedChunkPlan otherSeed = WarheadPlanCompiler.compile(
            impact(0x55AA_1020_3040L), footprint, snapshot, palette);
        assertNotEquals(fingerprint(first), fingerprint(otherSeed));
    }

    @Test
    void craterGeometryOwnsOverlappingVerticalMutations() {
        NuclearTerrainProfile profile = NuclearTerrainProfile.forYield(
            WarheadYield.TACTICAL_NUCLEAR);
        Vec3 center = new Vec3(8.0, 64.0, 8.0);
        assertTrue(WarheadPlanCompiler.craterOwnsCell(profile, center, 8, 64, 8));
        assertFalse(WarheadPlanCompiler.craterOwnsCell(profile, center, 8, 100, 8));
        assertFalse(WarheadPlanCompiler.craterOwnsCell(profile, center, 100, 64, 100));
    }

    @Test
    void workerInputSnapshotContainsNoLiveWorldReference() {
        for (Field field : WarheadChunkSnapshot.class.getDeclaredFields()) {
            assertFalse(Level.class.isAssignableFrom(field.getType()), field.getName());
        }
    }

    @Test
    void directPaletteClassificationIsRestrictedToInertTerrain() {
        assertFalse(WarheadSnapshotFlags.requiresSemanticPath(
            Blocks.STONE.defaultBlockState(), WarheadSnapshotFlags.COMMON_ROCK));
        assertTrue(WarheadSnapshotFlags.requiresSemanticPath(
            Blocks.WATER.defaultBlockState(), WarheadSnapshotFlags.FLUID));
        assertTrue(WarheadSnapshotFlags.requiresSemanticPath(
            Blocks.REDSTONE_WIRE.defaultBlockState(), 0));
        assertTrue(WarheadSnapshotFlags.requiresSemanticPath(
            Blocks.CHEST.defaultBlockState(), 0));
        assertTrue(WarheadSnapshotFlags.requiresSemanticPath(
            Blocks.TNT.defaultBlockState(), WarheadSnapshotFlags.TNT));
    }

    @Test
    void surfaceCompilerPreservesTheSnapshotSemanticClassification() {
        PreparedImpactSpec impact = impact(0x1122_3344_5566L);
        WarheadFootprint footprint = WarheadFootprintCalculator.calculate(
            impact.payload(), impact.yield(), impact.target());
        PreparedChunkPlan plan = WarheadPlanCompiler.compile(impact, footprint,
            semanticSoilSnapshot(new ChunkPos(4, 0)), WarheadStatePalette.capture());

        assertTrue(plan.blockSections().stream()
            .anyMatch(section -> !section.semanticMaskUnsafe().isEmpty()));
    }

    private static PreparedImpactSpec impact(final long seed) {
        return new PreparedImpactSpec(UUID.fromString(
            "f2ad6f28-5158-4f93-8ed5-3eb0ce68c350"),
            new Vec3(8.0, 64.0, 8.0), WarheadPayloadType.NUCLEAR,
            WarheadYield.TACTICAL_NUCLEAR, seed, false);
    }

    private static WarheadChunkSnapshot stoneSnapshot() {
        int air = Block.getId(Blocks.AIR.defaultBlockState());
        int stone = Block.getId(Blocks.STONE.defaultBlockState());
        int minimumCraterY = 38;
        int maximumCraterY = 84;
        int craterCells = 256 * (maximumCraterY - minimumCraterY + 1);
        int[] motion = new int[256];
        int[] terrain = new int[256];
        Arrays.fill(motion, 64);
        Arrays.fill(terrain, 64);
        int[] surfaceStates = new int[256 * WarheadChunkSnapshot.SURFACE_LAYERS];
        int[] surfaceFlags = new int[surfaceStates.length];
        Arrays.fill(surfaceStates, stone);
        Arrays.fill(surfaceFlags, WarheadSnapshotFlags.SOIL
            | WarheadSnapshotFlags.NATURAL_SURFACE);
        for (int column = 0; column < 256; column++) {
            surfaceStates[column] = air;
            surfaceFlags[column] = WarheadSnapshotFlags.AIR;
        }
        int[] craterStates = new int[craterCells];
        int[] craterFlags = new int[craterCells];
        float[] resistance = new float[craterCells];
        Arrays.fill(craterStates, stone);
        Arrays.fill(craterFlags, WarheadSnapshotFlags.COMMON_ROCK);
        Arrays.fill(resistance, 6.0F);
        long[] sectionRevisions = new long[24];
        Arrays.fill(sectionRevisions, 7L);
        return new WarheadChunkSnapshot(new ChunkPos(0, 0), 11L, -4,
            sectionRevisions, -64, 319, minimumCraterY, maximumCraterY,
            motion, terrain, new int[256], surfaceStates, surfaceFlags,
            craterStates, craterFlags, resistance, new long[0], new int[0],
            new int[0]);
    }

    private static WarheadChunkSnapshot semanticSoilSnapshot(final ChunkPos chunk) {
        int air = Block.getId(Blocks.AIR.defaultBlockState());
        int dirt = Block.getId(Blocks.DIRT.defaultBlockState());
        int[] motion = new int[256];
        int[] terrain = new int[256];
        Arrays.fill(motion, 64);
        Arrays.fill(terrain, 64);
        int[] surfaceStates = new int[256 * WarheadChunkSnapshot.SURFACE_LAYERS];
        int[] surfaceFlags = new int[surfaceStates.length];
        Arrays.fill(surfaceStates, dirt);
        Arrays.fill(surfaceFlags, WarheadSnapshotFlags.SOIL
            | WarheadSnapshotFlags.NATURAL_SURFACE | WarheadSnapshotFlags.SEMANTIC);
        for (int column = 0; column < 256; column++) {
            surfaceStates[column] = air;
            surfaceFlags[column] = WarheadSnapshotFlags.AIR;
        }
        long[] sectionRevisions = new long[24];
        Arrays.fill(sectionRevisions, 4L);
        return new WarheadChunkSnapshot(chunk, 5L, -4, sectionRevisions,
            -64, 319, 0, -1, motion, terrain, new int[256], surfaceStates,
            surfaceFlags, new int[0], new int[0], new float[0], new long[0],
            new int[0], new int[0]);
    }

    private static void assertPlansEqual(final PreparedChunkPlan left,
        final PreparedChunkPlan right) {
        assertEquals(left.chunk(), right.chunk());
        assertEquals(left.sourceRevision(), right.sourceRevision());
        assertEquals(left.activationTick(), right.activationTick());
        assertEquals(left.biomeSections(), right.biomeSections());
        assertEquals(left.fireMutations(), right.fireMutations());
        assertArrayEquals(left.changedColumnsUnsafe(), right.changedColumnsUnsafe());
        assertEquals(left.blockSections().size(), right.blockSections().size());
        for (int index = 0; index < left.blockSections().size(); index++) {
            PreparedSectionPlan a = left.blockSections().get(index);
            PreparedSectionPlan b = right.blockSections().get(index);
            assertEquals(a.sectionY(), b.sectionY());
            assertEquals(a.sourceRevision(), b.sourceRevision());
            assertEquals(a.phase(), b.phase());
            assertArrayEquals(a.localIndicesUnsafe(), b.localIndicesUnsafe());
            assertArrayEquals(a.expectedStateIdsUnsafe(), b.expectedStateIdsUnsafe());
            assertArrayEquals(a.finalStateIdsUnsafe(), b.finalStateIdsUnsafe());
            assertEquals(a.semanticMaskUnsafe(), b.semanticMaskUnsafe());
            assertEquals(a.survivalMaskUnsafe(), b.survivalMaskUnsafe());
        }
    }

    private static long fingerprint(final PreparedChunkPlan plan) {
        long hash = plan.activationTick();
        for (PreparedSectionPlan section : plan.blockSections()) {
            hash = hash * 31L + section.sectionY();
            hash = hash * 31L + Arrays.hashCode(section.localIndicesUnsafe());
            hash = hash * 31L + Arrays.hashCode(section.finalStateIdsUnsafe());
            hash = hash * 31L + section.semanticMaskUnsafe().hashCode();
        }
        for (PreparedBiomeSectionPlan biome : plan.biomeSections()) {
            hash = hash * 31L + biome.hashCode();
        }
        for (PreparedFireMutation fire : plan.fireMutations()) hash = hash * 31L + fire.hashCode();
        return hash;
    }
}
