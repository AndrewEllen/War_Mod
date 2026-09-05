package com.andye.warmod.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andye.warmod.fire.FireRepresentationPlan.Card;
import com.andye.warmod.fire.FireRepresentationPlan.CellPlan;
import com.andye.warmod.fire.network.FireVisualBand;
import com.andye.warmod.fire.network.FireVisualCell;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class FireRepresentationPlanTest {
    @Test
    void occupiedBitInsertionDoesNotMoveOrReseedSurvivingCards() {
        FireVisualCell old = cell();
        FireVisualCell expanded = new FireVisualCell(old.id(), old.band(), old.cellSize(),
            old.cellX(), old.cellY(), old.cellZ(), old.centroid(), old.extents(),
            old.occupancyMask() | 1L, old.flameEnergy(), old.smokeMass(),
            old.maximumHeat(), old.averageIntensity(), old.coveredArea(),
            old.clumpStrength(), old.wind(), old.hostCount(), old.seed(),
            old.dominantFace(), old.phase(), old.ignitionGameTime());
        var before = FireRepresentationPlan.plan(old, 12.0, 1.0, 1.0F).flames();
        var after = FireRepresentationPlan.plan(expanded, 12.0, 1.0, 1.0F).flames();
        for (Card card : before) {
            var surviving = after.stream().filter(next -> next.seed() == card.seed()).findFirst();
            if (surviving.isPresent()) assertEquals(card.position(), surviving.get().position());
        }
        assertTrue(before.stream().limit(3).allMatch(card -> after.stream()
            .anyMatch(next -> next.seed() == card.seed())));
    }

    @Test
    void fractionalCardsPreserveCoverageAcrossCountThresholds() {
        CellPlan before = FireRepresentationPlan.plan(cell(), 69.99, 1.0, 1.0F);
        CellPlan after = FireRepresentationPlan.plan(cell(), 70.01, 1.0, 1.0F);
        assertEquals(FireRepresentationPlan.equivalentArea(before.flames()),
            FireRepresentationPlan.equivalentArea(after.flames()), 0.001);
        assertTrue(Math.abs(before.flames().getFirst().radius()
            - after.flames().getFirst().radius()) < 0.001);
    }
    @Test
    void projectedDetailChangesPreserveAreaAndOpticalDepth() {
        FireVisualCell cell = cell();
        CellPlan far = FireRepresentationPlan.plan(cell, 12.0, 1.0, 1.0F);
        CellPlan near = FireRepresentationPlan.plan(cell, 160.0, 1.0, 1.0F);
        assertTrue(near.flames().size() > far.flames().size());
        double farArea = FireRepresentationPlan.equivalentArea(far.flames());
        assertEquals(farArea, FireRepresentationPlan.equivalentArea(near.flames()),
            farArea * 0.10);
        assertEquals(opticalDepth(far.smoke()), opticalDepth(near.smoke()), 0.001);
        int common = Math.min(far.flames().size(), near.flames().size());
        for (int index = 0; index < common; index++)
            assertEquals(far.flames().get(index).position(),
                near.flames().get(index).position());
    }

    @Test
    void representativesStayInsideOccupiedSourceSubcells() {
        FireVisualCell cell = cell();
        CellPlan plan = FireRepresentationPlan.plan(cell, 100.0, 2.0, 1.0F);
        for (Card card : plan.flames()) assertOccupied(cell, card.position());
        for (Card card : plan.smoke()) assertOccupied(cell,
            card.position().add(0.0, -0.30 - card.radius() * 0.34, 0.0));
    }

    @Test
    void parentChildCrossfadeDoesNotCreateEnergyStep() {
        FireVisualCell cell = cell();
        CellPlan full = FireRepresentationPlan.plan(cell, 80.0, 1.0, 1.0F);
        CellPlan outgoing = FireRepresentationPlan.plan(cell, 80.0, 1.0, 0.5F);
        CellPlan incoming = FireRepresentationPlan.plan(cell, 80.0, 1.0, 0.5F);
        assertEquals(FireRepresentationPlan.equivalentArea(full.flames()),
            FireRepresentationPlan.equivalentArea(outgoing.flames())
                + FireRepresentationPlan.equivalentArea(incoming.flames()), 0.001);
    }

    @Test
    void exactNearPatchUsesClumpDensityAndItsSurfaceNormal() {
        FireVisualCell isolated = exactNearCell(0.0F);
        FireVisualCell clumped = exactNearCell(1.0F);
        CellPlan isolatedPlan = FireRepresentationPlan.plan(isolated, 20.0,
            1.0, 1.0F, 0);
        CellPlan clumpedPlan = FireRepresentationPlan.plan(clumped, 20.0,
            1.0, 1.0F, 0);
        assertTrue(clumpedPlan.flames().size() > isolatedPlan.flames().size());
        for (Card card : clumpedPlan.flames()) {
            assertTrue(card.position().x > clumped.centroid().x,
                "east-face flames must sit outside the clicked surface");
        }
    }

    @Test
    void retiringSmokeCannotRegrowMinimumFlameCards() {
        FireVisualCell source = exactNearCell(0.5F);
        FireVisualCell smokeOnly = new FireVisualCell(source.id(), source.band(),
            source.cellSize(), source.cellX(), source.cellY(), source.cellZ(),
            source.centroid(), source.extents(), source.occupancyMask(), 0.0F,
            source.smokeMass(), 0.0F, 0.0F, source.coveredArea(),
            source.clumpStrength(), source.wind(), source.hostCount(), source.seed(),
            source.dominantFace(), FirePhase.SMOLDERING, source.ignitionGameTime());
        CellPlan plan = FireRepresentationPlan.plan(smokeOnly, 120.0,
            1.0, 1.0F, 0);
        assertTrue(plan.flames().isEmpty());
        assertTrue(!plan.smoke().isEmpty());
    }

    @Test
    void closeDetailAddsOverlapWithoutChangingDistantPlans() {
        FireVisualCell near = exactNearCell(0.5F);
        CellPlan baseline = FireRepresentationPlan.plan(near, 80, 1, 1, 0);
        CellPlan close = FireRepresentationPlan.plan(near, 80, 1, 1, 0, 1);
        assertTrue(close.flames().size() > baseline.flames().size());
        assertTrue(close.representedFlameArea() > baseline.representedFlameArea());
        assertTrue(close.flames().getFirst().radius() <= baseline.flames().getFirst().radius(),
            "Near density comes from overlapping detail rather than oversized cards");
        assertTrue(close.flames().getFirst().opacity() < baseline.flames().getFirst().opacity(),
            "Close flames retain the requested layered transparency");
        assertEquals(FireRepresentationPlan.plan(cell(), 12, 1, 1, 3),
            FireRepresentationPlan.plan(cell(), 12, 1, 1, 3, 1));
        assertEquals(1.0F, FireRepresentationPlan.closeDetailWeight(15));
        assertEquals(0.0F, FireRepresentationPlan.closeDetailWeight(30));
        assertTrue(Math.abs(FireRepresentationPlan.closeDetailWeight(22.49)
            - FireRepresentationPlan.closeDetailWeight(22.51)) < 0.003F);
    }

    private static FireVisualCell cell() {
        return new FireVisualCell(7L, FireVisualBand.FAR, 8, 3, 8, -2,
            new Vec3(28.0, 64.0, -12.0), new Vec3(4.0, 2.0, 4.0),
            (1L << 9) | (1L << 18) | (1L << 45),
            12.0F, 8.0F, 0.95F, 0.82F, 9.0F, 0.5F, Vec3.ZERO,
            9, 12345L, Direction.UP, FirePhase.FLAMING, 4L);
    }

    private static FireVisualCell exactNearCell(final float clump) {
        return new FireVisualCell(17L, FireVisualBand.PATCH, 1, 1, 64, 0,
            new Vec3(1.0, 64.4, 0.5), new Vec3(0.45, 0.45, 0.45), 1L << 20,
            0.82F, 0.68F, 0.95F, 0.92F, 0.82F, clump, Vec3.ZERO,
            1, 998L, Direction.EAST, FirePhase.FLAMING, 2L);
    }

    private static double opticalDepth(final List<Card> cards) {
        double depth = 0.0;
        for (Card card : cards) depth += -Math.log(Math.max(1.0E-6, 1.0 - card.opacity()));
        return depth;
    }

    private static void assertOccupied(final FireVisualCell cell, final Vec3 position) {
        double subcell = cell.cellSize() / 8.0;
        int subX = Math.max(0, Math.min(7, (int) Math.floor(
            (position.x - cell.cellX() * (double) cell.cellSize()) / subcell)));
        int subZ = Math.max(0, Math.min(7, (int) Math.floor(
            (position.z - cell.cellZ() * (double) cell.cellSize()) / subcell)));
        assertTrue((cell.occupancyMask() & (1L << (subZ * 8 + subX))) != 0L,
            () -> "Card escaped occupied subcell at " + position);
    }
}
