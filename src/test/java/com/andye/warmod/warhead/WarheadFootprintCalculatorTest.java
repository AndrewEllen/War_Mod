package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WarheadFootprintCalculatorTest {
    @Test
    void nuclearYieldRadiiMatchTheExistingGameplayFormulas() {
        assertNuclear(WarheadYield.TACTICAL_NUCLEAR, 32.0, 219.0, 281.0);
        assertNuclear(WarheadYield.STRATEGIC_NUCLEAR, 48.0, 265.0, 340.0);
        assertNuclear(WarheadYield.HEAVY_NUCLEAR, 64.0, 328.0, 420.0);
    }

    @Test
    void exactCircleIntersectionRejectsEnclosingSquareCorners() {
        LongSet chunks = WarheadFootprintCalculator.chunksIntersectingCircle(
            8.0, 8.0, 32.0);
        assertTrue(chunks.contains(ChunkPos.pack(2, 0)));
        assertFalse(chunks.contains(ChunkPos.pack(2, 2)));
    }

    @Test
    void exactIntersectionHandlesChunkEdgesAndCorners() {
        LongSet edge = WarheadFootprintCalculator.chunksIntersectingCircle(
            16.0, 8.0, 0.25);
        assertEquals(2, edge.size());
        assertTrue(edge.contains(ChunkPos.pack(0, 0)));
        assertTrue(edge.contains(ChunkPos.pack(1, 0)));

        LongSet corner = WarheadFootprintCalculator.chunksIntersectingCircle(
            16.0, 16.0, 0.25);
        assertEquals(4, corner.size());
        assertTrue(corner.contains(ChunkPos.pack(0, 0)));
        assertTrue(corner.contains(ChunkPos.pack(1, 0)));
        assertTrue(corner.contains(ChunkPos.pack(0, 1)));
        assertTrue(corner.contains(ChunkPos.pack(1, 1)));
    }

    @Test
    void productionFootprintsUseExactCirclesInsteadOfEnclosingSquares() {
        assertEquals(1_033, footprint(WarheadYield.TACTICAL_NUCLEAR).requiredChunkCount());
        assertEquals(1_497, footprint(WarheadYield.STRATEGIC_NUCLEAR).requiredChunkCount());
        assertEquals(2_269, footprint(WarheadYield.HEAVY_NUCLEAR).requiredChunkCount());
    }

    @Test
    void smallEffectsStillAcquireExactlyTheMinimumThreeByThreeWindow() {
        LongSet chunks = WarheadFootprintCalculator.requiredChunks(8.0, 8.0, 0.0);
        assertEquals(9, chunks.size());
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                assertTrue(chunks.contains(ChunkPos.pack(chunkX, chunkZ)));
            }
        }
    }

    @Test
    void requiredChunkSetIsImmutableAndIncludesTheActualImpactAlignment() {
        WarheadFootprint footprint = WarheadFootprintCalculator.calculate(
            WarheadPayloadType.NUCLEAR, WarheadYield.HEAVY_NUCLEAR,
            new Vec3(15.75, 80.0, 16.25));
        assertTrue(footprint.requiredChunks().contains(ChunkPos.pack(0, 1)));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> footprint.requiredChunks().add(ChunkPos.pack(999, 999)));
    }

    private static void assertNuclear(final WarheadYield yield,
        final double craterRadius, final double aftermathRadius,
        final double glassRadius) {
        WarheadFootprint footprint = WarheadFootprintCalculator.calculate(
            WarheadPayloadType.NUCLEAR, yield, new Vec3(8.0, 80.0, 8.0));
        assertEquals(craterRadius, footprint.craterRadius(), 0.0002);
        assertEquals(aftermathRadius, footprint.aftermathRadius(), 0.0001);
        assertEquals(glassRadius, footprint.glassRadius(), 0.0001);
        assertEquals(glassRadius, footprint.maximumMutationRadius(), 0.0001);
        assertTrue(footprint.biomeRadius() <= footprint.maximumMutationRadius());
    }

    private static WarheadFootprint footprint(final WarheadYield yield) {
        return WarheadFootprintCalculator.calculate(WarheadPayloadType.NUCLEAR,
            yield, new Vec3(8.0, 80.0, 8.0));
    }
}
