package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class PreparedPlanModelTest {
    @Test
    void sectionConstructorDefensivelyCopiesMutationArraysAndMasks() {
        int[] indices = {1, 7};
        int[] expected = {10, 11};
        int[] replacements = {20, 21};
        BitSet semantic = new BitSet();
        semantic.set(1);
        PreparedSectionPlan plan = new PreparedSectionPlan(4, 9L,
            PreparedMutationPhase.RADIAL_AFTERMATH, indices, expected, replacements,
            semantic, new BitSet());
        indices[0] = 99;
        expected[0] = 99;
        replacements[0] = 99;
        semantic.clear();
        assertArrayEquals(new int[] {1, 7}, plan.localIndicesUnsafe());
        assertArrayEquals(new int[] {10, 11}, plan.expectedStateIdsUnsafe());
        assertArrayEquals(new int[] {20, 21}, plan.finalStateIdsUnsafe());
        assertEquals(1, plan.semanticMaskUnsafe().cardinality());
    }

    @Test
    void impactPlanCopiesAndFreezesItsChunkMap() {
        ChunkPos chunk = new ChunkPos(0, 0);
        PreparedChunkPlan chunkPlan = new PreparedChunkPlan(chunk, 1L, 15,
            List.of(), List.of(), List.of(), new int[0], 0L);
        Long2ObjectOpenHashMap<PreparedChunkPlan> chunks = new Long2ObjectOpenHashMap<>();
        chunks.put(chunk.pack(), chunkPlan);
        WarheadFootprint footprint = WarheadFootprintCalculator.calculate(
            WarheadPayloadType.NUCLEAR, WarheadYield.TACTICAL_NUCLEAR,
            new Vec3(8.0, 64.0, 8.0));
        PreparedImpactPlan plan = new PreparedImpactPlan(UUID.randomUUID(),
            new Vec3(8.0, 64.0, 8.0), footprint, chunks, 15,
            PlanStatistics.empty());
        chunks.clear();
        assertEquals(1, plan.chunks().size());
        assertThrows(UnsupportedOperationException.class,
            () -> plan.chunks().clear());
    }

    @Test
    void radialApplicationReservesFourTicksForLightingAndTrackingSync() {
        assertEquals(0, WarheadPreparedCommitManager.applicationTick(0));
        assertEquals(15, WarheadPreparedCommitManager.applicationTick(18));
        assertEquals(15, WarheadPreparedCommitManager.applicationTick(19));
    }

    @Test
    void sparsePaletteChangesUseExactLightingWhileDenseCraterChangesRebuild() {
        assertFalse(WarheadPreparedCommitManager.usesExactLightChecks(0, 0));
        assertTrue(WarheadPreparedCommitManager.usesExactLightChecks(512, 3));
        assertTrue(WarheadPreparedCommitManager.usesExactLightChecks(4_096, 1));
        assertFalse(WarheadPreparedCommitManager.usesExactLightChecks(513, 2));
    }
}
