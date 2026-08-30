package com.andye.warmod.fire;

import com.andye.warmod.fire.network.FireVisualCell;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Shared optical-coverage planner consumed by both the packed CPU renderer and
 * the GPU emitter path.  It is the only place that chooses fire/smoke card
 * counts, sizes, opacity, and stable occupied-subcell positions.
 */
public final class FireRepresentationPlan {
    public static final double TARGET_FLAME_PIXELS = 23.0;
    public static final double TARGET_SMOKE_PIXELS = 50.0;
    private static final int MAX_FLAME_CARDS_PER_CELL = 48;
    private static final int MAX_SMOKE_CARDS_PER_CELL = 32;

    private FireRepresentationPlan() { }

    public static CellPlan plan(final FireVisualCell cell,
        final double projectedCellDiameter, final double qualityScale,
        final float representationWeight) {
        return plan(cell, projectedCellDiameter, qualityScale,
            representationWeight, FireVisualLodPolicy.level(projectedCellDiameter));
    }

    public static CellPlan plan(final FireVisualCell cell,
        final double projectedCellDiameter, final double qualityScale,
        final float representationWeight, final int requestedDetailLevel) {
        if (cell == null || !cell.valid() || representationWeight <= 0.0F)
            return CellPlan.EMPTY;
        double quality = Mth.clamp(qualityScale, 0.25, 4.0);
        float weight = Mth.clamp(representationWeight, 0.0F, 1.0F);
        int occupied = Math.max(1, Long.bitCount(cell.occupancyMask()));
        double clump = Mth.clamp(cell.clumpStrength(), 0.0F, 1.5F);
        double footprintArea = Math.max(0.05,
            Math.min(cell.coveredArea(), cell.hostCount() * 1.5F));
        if (exactNearPatch(cell)) footprintArea *= 1.0 + clump * 0.22;
        double flameArea = footprintArea
            * (0.30 + Mth.clamp(cell.averageIntensity(), 0.0F, 1.2F) * 0.70)
            * (0.32 + Mth.clamp(cell.maximumHeat(), 0.0F, 1.2F) * 0.68)
            * (1.0 + clump * 0.42);
        double flameDepth = Math.max(0.05, cell.flameEnergy() * 0.82);
        double smokeArea = Math.max(0.08, footprintArea * (0.65
            + Math.sqrt(Math.max(0.0, cell.smokeMass())) * 0.82));
        double smokeDepth = Math.max(0.04, cell.smokeMass() * 0.92);

        double projectedArea = Math.PI * 0.25 * Math.max(0.0,
            projectedCellDiameter * projectedCellDiameter);
        double flameTarget = Mth.clamp(TARGET_FLAME_PIXELS / Math.sqrt(quality), 18.0, 32.0);
        double smokeTarget = Mth.clamp(TARGET_SMOKE_PIXELS / Math.sqrt(quality), 36.0, 72.0);
        int baseMinimum = Math.max(1, (int) Math.ceil(occupied / 16.0));
        int detailLevel = Math.max(0, Math.min(3, requestedDetailLevel));
        int flameMinimum = Math.max(baseMinimum, switch (detailLevel) {
            case 0 -> Math.min(8, Math.max(4, (int) Math.ceil(occupied / 4.0)));
            case 1 -> Math.min(4, Math.max(2, (int) Math.ceil(occupied / 8.0)));
            default -> 1;
        });
        int smokeMinimum = Math.max(baseMinimum, switch (detailLevel) {
            case 0 -> Math.min(6, Math.max(3, (int) Math.ceil(occupied / 5.0)));
            case 1 -> Math.min(3, Math.max(2, (int) Math.ceil(occupied / 10.0)));
            default -> 1;
        });
        int flameCount;
        int smokeCount;
        boolean flameVisible = cell.flameEnergy() > 0.012F
            && cell.averageIntensity() > 0.01F && cell.maximumHeat() > 0.04F
            && cell.phase() != FirePhase.SMOLDERING;
        if (!flameVisible) {
            flameCount = 0;
            smokeCount = cell.smokeMass() <= 0.012F ? 0
                : desiredCount(projectedArea, smokeTarget, smokeMinimum,
                    MAX_SMOKE_CARDS_PER_CELL, smokeArea, smokeDepth,
                    maximumSmokeRadius(cell), 0.72);
        } else if (exactNearPatch(cell)) {
            double density = FireVisualLodPolicy.density(detailLevel);
            int historicalFlames = Mth.ceil((2.0F + cell.averageIntensity() * 15.0F)
                * (0.18F + cell.coveredArea() * 0.82F)
                * (1.0F + (float)clump * 0.42F) * density);
            if (cell.phase() == FirePhase.IGNITION) historicalFlames = Math.min(2,
                historicalFlames);
            flameCount = Mth.clamp(historicalFlames, 1, MAX_FLAME_CARDS_PER_CELL);
            int historicalSmoke = Mth.ceil((2.0F + cell.smokeMass() * 24.0F)
                * (1.0F + (float)clump * 0.62F) * density);
            smokeCount = cell.smokeMass() <= 0.012F ? 0
                : Mth.clamp(historicalSmoke, 1, MAX_SMOKE_CARDS_PER_CELL);
        } else {
            flameCount = desiredCount(projectedArea, flameTarget, flameMinimum,
                MAX_FLAME_CARDS_PER_CELL, flameArea, flameDepth,
                maximumFlameRadius(cell), 0.96);
            smokeCount = cell.smokeMass() <= 0.012F ? 0
                : desiredCount(projectedArea, smokeTarget, smokeMinimum,
                    MAX_SMOKE_CARDS_PER_CELL, smokeArea, smokeDepth,
                    maximumSmokeRadius(cell), 0.72);
        }

        List<Card> flames = cards(cell, flameCount, flameArea, flameDepth,
            maximumFlameRadius(cell), weight, false);
        List<Card> smoke = cards(cell, smokeCount, smokeArea, smokeDepth,
            maximumSmokeRadius(cell), weight, true);
        int sparks = !flameVisible || cell.maximumHeat() < 0.22F ? 0 : Math.min(4,
            Math.max(1, (int) Math.ceil(Math.sqrt(cell.flameEnergy()) * quality)));
        return new CellPlan(flames, smoke, sparks, (float) flameArea,
            (float) smokeDepth, weight);
    }

    private static int desiredCount(final double projectedArea,
        final double targetDiameter, final int minimum, final int maximum,
        final double representedArea, final double opticalDepth,
        final double maximumRadius, final double maximumOpacity) {
        double targetArea = Math.PI * 0.25 * targetDiameter * targetDiameter;
        int count = Mth.clamp((int) Math.ceil(projectedArea / Math.max(1.0, targetArea)),
            minimum, maximum);
        while (count < maximum) {
            double opacity = opacity(opticalDepth, count);
            double radius = Math.sqrt(representedArea
                / (Math.PI * count * Math.max(0.025, opacity)));
            if (radius <= maximumRadius && opacity <= maximumOpacity) break;
            count = Math.min(maximum, count * 2);
        }
        return count;
    }

    private static List<Card> cards(final FireVisualCell cell, final int count,
        final double representedArea, final double opticalDepth,
        final double maximumRadius, final float representationWeight,
        final boolean smoke) {
        if (count <= 0) return List.of();
        double opacity = opacity(opticalDepth, count);
        float radius = (float) Math.min(maximumRadius, Math.max(0.06,
            Math.sqrt(representedArea
                / (Math.PI * count * Math.max(0.025, opacity)))));
        float alpha = Mth.clamp((float) opacity * representationWeight,
            0.0F, smoke ? 0.72F : 0.96F);
        ArrayList<Card> cards = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long seed = mix(cell.seed() ^ (smoke ? 0x534D4F4B455F4344L
                : 0x464C414D455F4344L) ^ (long) index * 0x9E3779B97F4A7C15L);
            Vec3 position = occupiedPosition(cell, index, seed);
            if (smoke) {
                position = position.add(0.0, 0.30 + maximumRadius * 0.34, 0.0);
            } else if (exactNearPatch(cell)) {
                position = position.add(Vec3.atLowerCornerOf(
                    cell.dominantFace().getUnitVec3i()).scale(0.045)).add(0.0, 0.02, 0.0);
            } else {
                position = position.add(0.0, 0.05 + maximumRadius * 0.12, 0.0);
            }
            cards.add(new Card(position, radius, alpha, seed));
        }
        return List.copyOf(cards);
    }

    /** Stable low-discrepancy placement restricted to occupied 8x8 subcells. */
    public static Vec3 occupiedPosition(final FireVisualCell cell, final int index,
        final long seed) {
        if (exactNearPatch(cell)) {
            double tangentA = (radicalInverse(index + 1, 2) - 0.5)
                * Math.max(0.10, cell.extents().x * 1.55);
            double tangentB = (radicalInverse(index + 1, 3) - 0.5)
                * Math.max(0.10, cell.extents().z * 1.55);
            return switch (cell.dominantFace()) {
                case UP, DOWN -> cell.centroid().add(tangentA, 0.0, tangentB);
                case NORTH, SOUTH -> cell.centroid().add(tangentA, tangentB, 0.0);
                case EAST, WEST -> cell.centroid().add(0.0, tangentB, tangentA);
            };
        }
        int occupiedCount = Math.max(1, Long.bitCount(cell.occupancyMask()));
        int rank = Math.floorMod(index * 37 + (int) mix(seed), occupiedCount);
        int bit = nthSetBit(cell.occupancyMask(), rank);
        int subX = bit & 7;
        int subZ = bit >>> 3;
        double subcellSize = cell.cellSize() / 8.0;
        double jitterX = (radicalInverse(index + 1, 2) - 0.5) * subcellSize * 0.62;
        double jitterZ = (radicalInverse(index + 1, 3) - 0.5) * subcellSize * 0.62;
        double x = cell.cellX() * (double) cell.cellSize()
            + (subX + 0.5) * subcellSize + jitterX;
        double z = cell.cellZ() * (double) cell.cellSize()
            + (subZ + 0.5) * subcellSize + jitterZ;
        double y = cell.centroid().y + (radicalInverse(index + 1, 5) - 0.5)
            * Math.min(1.0, cell.extents().y);
        return new Vec3(x, y, z);
    }

    public static double equivalentArea(final List<Card> cards) {
        double area = 0.0;
        for (Card card : cards)
            area += Math.PI * card.radius() * card.radius() * card.opacity();
        return area;
    }

    private static int nthSetBit(final long mask, final int rank) {
        int remaining = rank;
        for (int bit = 0; bit < Long.SIZE; bit++) {
            if ((mask & (1L << bit)) == 0L) continue;
            if (remaining-- == 0) return bit;
        }
        return Long.numberOfTrailingZeros(mask == 0L ? 1L : mask);
    }

    private static double radicalInverse(int value, final int base) {
        double inverse = 1.0 / base;
        double factor = inverse;
        double result = 0.0;
        while (value > 0) {
            result += (value % base) * factor;
            value /= base;
            factor *= inverse;
        }
        return result;
    }

    private static double opacity(final double opticalDepth, final int count) {
        return 1.0 - Math.exp(-Math.max(0.0, opticalDepth) / Math.max(1, count));
    }

    private static double maximumFlameRadius(final FireVisualCell cell) {
        if (exactNearPatch(cell)) return 0.30;
        double extent = Math.max(cell.extents().x, cell.extents().z);
        return Math.max(0.48, Math.min(cell.cellSize() * 0.56, extent * 0.86 + 0.28));
    }

    private static double maximumSmokeRadius(final FireVisualCell cell) {
        if (exactNearPatch(cell)) return 0.52;
        double extent = Math.max(cell.extents().x, cell.extents().z);
        return Math.max(0.72, Math.min(cell.cellSize() * 0.78, extent * 1.12 + 0.52));
    }

    private static boolean exactNearPatch(final FireVisualCell cell) {
        return cell.band() == com.andye.warmod.fire.network.FireVisualBand.NEAR
            && cell.cellSize() == 1 && cell.hostCount() == 1;
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public record Card(Vec3 position, float radius, float opacity, long seed) { }
    public record CellPlan(List<Card> flames, List<Card> smoke, int sparkCount,
        float representedFlameArea, float representedSmokeOpticalDepth,
        float representationWeight) {
        private static final CellPlan EMPTY = new CellPlan(List.of(), List.of(),
            0, 0.0F, 0.0F, 0.0F);
    }
}
