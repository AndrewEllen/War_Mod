package com.andye.warmod.fire.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.fire.FireCellSnapshot;
import com.andye.warmod.fire.FirePhase;
import com.andye.warmod.fire.FireSurfaceAnchor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class FireVisualRepresentationBuilderTest {
    @Test
    void tenThousandHostsRetainEveryOccupiedAngularSectorWithinPacketCapacity() {
        ArrayList<FireCellSnapshot> patches = new ArrayList<>(10_000);
        for (int sector = 0; sector < 100; sector++) {
            double angle = sector / 100.0 * Math.PI * 2.0;
            for (int radial = 0; radial < 100; radial++) {
                double radius = 100.0 + radial * 12.0;
                patches.add(patch(sector * 100L + radial + 1L,
                    MthRound(Math.cos(angle) * radius),
                    MthRound(Math.sin(angle) * radius)));
            }
        }

        FireVisualRepresentationBuilder.Representation result =
            FireVisualRepresentationBuilder.build(patches, new Vec3(0.0, 64.0, 0.0));

        assertEquals(10_000, result.uniqueHostCount());
        assertTrue(result.cells().size() <= ClientboundFireStatePayload.MAX_CELLS);
        assertEquals(FireVisualBand.COMPLETE_MASK, result.completeBandMask());
        Set<Integer> representedSectors = new HashSet<>();
        for (FireVisualCell cell : result.cells()) {
            double angle = Math.atan2(cell.centroid().z, cell.centroid().x);
            if (angle < 0.0) angle += Math.PI * 2.0;
            representedSectors.add(Math.min(31,
                (int) Math.floor(angle / (Math.PI * 2.0) * 32.0)));
        }
        assertEquals(32, representedSectors.size());
        for (FireVisualBand band : FireVisualBand.values())
            assertTrue(result.cellsByBand().get(band) <= band.cellBudget());
    }

    @Test
    void cellIdsStayStableForMovementInsideTheSameBand() {
        List<FireCellSnapshot> patches = new ArrayList<>();
        for (int x = -24; x <= 24; x += 4)
            for (int z = 220; z <= 260; z += 4)
                patches.add(patch(((long) x << 32) ^ z, x, z));
        Set<Long> first = ids(FireVisualRepresentationBuilder.build(patches,
            new Vec3(0.0, 64.0, 0.0)).cells());
        Set<Long> moved = ids(FireVisualRepresentationBuilder.build(patches,
            new Vec3(1.0, 64.0, 1.0)).cells());
        assertEquals(first, moved);
    }

    @Test
    void aggregationPreservesFlameEnergyAndSmokeMass() {
        ArrayList<FireCellSnapshot> patches = new ArrayList<>();
        for (int index = 0; index < 400; index++)
            patches.add(patch(index + 1L, index % 20 - 10, 180 + index / 20));
        FireVisualRepresentationBuilder.Representation result =
            FireVisualRepresentationBuilder.build(patches, new Vec3(0.0, 64.0, 0.0));
        List<FireVisualCell> mid = result.cells().stream()
            .filter(cell -> cell.band() == FireVisualBand.LOCAL).toList();
        assertFalse(mid.isEmpty());
        assertEquals(400 * 0.74, mid.stream().mapToDouble(FireVisualCell::flameEnergy).sum(),
            0.01);
        assertEquals(400 * 0.60, mid.stream().mapToDouble(FireVisualCell::smokeMass).sum(),
            0.01);
    }

    @Test
    void exactNearPatchKeepsItsStableIdAndSurfaceAnchor() {
        BlockPos host = new BlockPos(4, 63, 7);
        FireSurfaceAnchor anchor = new FireSurfaceAnchor(host, Direction.EAST,
            1.0F, 0.25F, 0.75F);
        FireCellSnapshot source = new FireCellSnapshot(77L, anchor, 0.8F, 0.9F,
            0.7F, 0.6F, FirePhase.FLAMING, 123L, 10L, Vec3.ZERO);
        FireVisualCell first = FireVisualRepresentationBuilder.build(List.of(source),
            new Vec3(4.0, 64.0, 7.0)).cells().stream()
            .filter(cell -> cell.band() == FireVisualBand.PATCH).findFirst().orElseThrow();
        FireVisualCell second = FireVisualRepresentationBuilder.build(List.of(source),
            new Vec3(5.0, 64.0, 7.0)).cells().stream()
            .filter(cell -> cell.band() == FireVisualBand.PATCH).findFirst().orElseThrow();
        assertEquals(first.id(), second.id());
        assertEquals(anchor.position(), first.centroid());
        assertEquals(Direction.EAST, first.dominantFace());
        assertEquals(1, first.cellSize());
    }

    @Test
    void everyNearPatchSurvivesBeyondSinglePacketCapacity() {
        ArrayList<FireCellSnapshot> patches = new ArrayList<>();
        long id = 1L;
        for (int x = -36; x <= 36 && patches.size() < 1_500; x++) {
            for (int z = -36; z <= 36 && patches.size() < 1_500; z++) {
                patches.add(patch(id++, x, z));
            }
        }

        var result = FireVisualRepresentationBuilder.build(patches,
            new Vec3(0.0, 64.0, 0.0));
        List<FireVisualCell> near = result.cells().stream()
            .filter(cell -> cell.band() == FireVisualBand.PATCH).toList();

        assertEquals(patches.size(), near.size());
        assertEquals(patches.size(), ids(near).size());
        assertTrue(result.cells().size() > ClientboundFireStatePayload.MAX_CELLS,
            "large complete representations must be paged, not truncated");
        assertEquals(0, result.omittedCellsByBand().get(FireVisualBand.PATCH));
    }

    @Test
    void populationChangesDoNotResizeAggregateBandGrids() {
        ArrayList<FireCellSnapshot> sparse = new ArrayList<>();
        ArrayList<FireCellSnapshot> dense = new ArrayList<>();
        for (int index = 0; index < 40; index++)
            sparse.add(patch(index + 1L, index - 20, 220 + index % 4));
        dense.addAll(sparse);
        for (int index = 40; index < 400; index++)
            dense.add(patch(index + 1L, index % 40 - 20, 210 + index / 40));
        var sparseResult = FireVisualRepresentationBuilder.build(sparse,
            new Vec3(0.0, 64.0, 0.0));
        var denseResult = FireVisualRepresentationBuilder.build(dense,
            new Vec3(0.0, 64.0, 0.0));
        for (FireVisualBand band : List.of(FireVisualBand.LOCAL, FireVisualBand.FAR,
            FireVisualBand.HORIZON)) {
            assertEquals(band.preferredCellSize(), sparseResult.cellSizeByBand().get(band));
            assertEquals(band.preferredCellSize(), denseResult.cellSizeByBand().get(band));
        }
        Set<Long> sparseMid = new HashSet<>();
        for (FireVisualCell cell : sparseResult.cells())
            if (cell.band() == FireVisualBand.LOCAL) sparseMid.add(cell.id());
        Set<Long> denseMid = new HashSet<>();
        for (FireVisualCell cell : denseResult.cells())
            if (cell.band() == FireVisualBand.LOCAL) denseMid.add(cell.id());
        assertTrue(denseMid.containsAll(sparseMid));
    }

    @Test
    void hierarchyUsesKnownGoodFixedGridsAndExplicitParentIds() {
        List<FireCellSnapshot> patches = List.of(patch(1L, 4, 60),
            patch(2L, 170, 0), patch(3L, 340, 0), patch(4L, 720, 0));
        var result = FireVisualRepresentationBuilder.build(patches,
            new Vec3(0.0, 64.0, 0.0));

        assertEquals(1, FireVisualBand.PATCH.preferredCellSize());
        assertEquals(1, FireVisualBand.HOST.preferredCellSize());
        assertEquals(2, FireVisualBand.LOCAL.preferredCellSize());
        assertEquals(8, FireVisualBand.FAR.preferredCellSize());
        assertEquals(32, FireVisualBand.HORIZON.preferredCellSize());
        for (FireVisualCell cell : result.cells()) {
            if (cell.band() == FireVisualBand.HORIZON) assertEquals(0L, cell.parentId());
            else assertTrue(cell.parentId() > 0L, () -> "missing parent for " + cell.band());
            assertTrue(cell.parentId() != cell.id());
        }
    }

    private static FireCellSnapshot patch(final long id, final int x, final int z) {
        BlockPos host = new BlockPos(x, 63, z);
        return new FireCellSnapshot(id, FireSurfaceAnchor.center(host, Direction.UP),
            0.8F, 0.9F, 1.0F, 0.6F, FirePhase.FLAMING, id * 31L,
            10L, Vec3.ZERO);
    }

    private static int MthRound(final double value) { return (int) Math.round(value); }

    private static Set<Long> ids(final List<FireVisualCell> cells) {
        HashSet<Long> result = new HashSet<>();
        for (FireVisualCell cell : cells) result.add(cell.id());
        return result;
    }
}
