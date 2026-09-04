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
    private static final int MAX_FLAME_CARDS_PER_CELL = 64;
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
        if (exactPatch(cell)) footprintArea *= 1.0 + clump * 0.22;
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
        int baseMinimum = Math.min(MAX_FLAME_CARDS_PER_CELL, occupied);
        // The physical envelope never grows with camera distance. Fewer cards
        // share its area; their radius is solved from count below.
        double flameRadiusLimit = maximumFlameRadius(cell);
        double smokeRadiusLimit = maximumSmokeRadius(cell);
        int flameMinimum = baseMinimum;
        int smokeMinimum = Math.min(MAX_SMOKE_CARDS_PER_CELL, baseMinimum);
        double flameCount;
        double smokeCount;
        boolean flameVisible = cell.flameEnergy() > 0.012F
            && cell.averageIntensity() > 0.01F && cell.maximumHeat() > 0.04F
            && cell.phase() != FirePhase.SMOLDERING;
        if (!flameVisible) {
            flameCount = 0;
            smokeCount = cell.smokeMass() <= 0.012F ? 0
                : desiredCount(projectedArea, smokeTarget, smokeMinimum,
                    MAX_SMOKE_CARDS_PER_CELL, smokeArea, smokeDepth,
                    smokeRadiusLimit, 0.72);
        } else if (exactPatch(cell)) {
            double density = Mth.clamp(Math.pow(projectedCellDiameter
                / FireVisualLodPolicy.FULL_DETAIL_PIXELS, 1.25), 0.055, 1.0);
            double historicalFlames = (2.0F + cell.averageIntensity() * 15.0F)
                * (0.18F + cell.coveredArea() * 0.82F)
                * (1.0F + (float)clump * 0.42F) * density;
            if (cell.phase() == FirePhase.IGNITION) historicalFlames = Math.min(2,
                historicalFlames);
            flameCount = Mth.clamp(historicalFlames, 1, MAX_FLAME_CARDS_PER_CELL);
            double historicalSmoke = (2.0F + cell.smokeMass() * 24.0F)
                * (1.0F + (float)clump * 0.62F) * density;
            smokeCount = cell.smokeMass() <= 0.012F ? 0
                : Mth.clamp(historicalSmoke, 1, MAX_SMOKE_CARDS_PER_CELL);
        } else {
            flameCount = desiredCount(projectedArea, flameTarget, flameMinimum,
                MAX_FLAME_CARDS_PER_CELL, flameArea, flameDepth,
                flameRadiusLimit, 0.96);
            smokeCount = cell.smokeMass() <= 0.012F ? 0
                : desiredCount(projectedArea, smokeTarget, smokeMinimum,
                    MAX_SMOKE_CARDS_PER_CELL, smokeArea, smokeDepth,
                    smokeRadiusLimit, 0.72);
        }

        List<Card> flames = cards(cell, flameCount, flameArea, flameDepth,
            flameRadiusLimit, weight, false);
        List<Card> smoke = cards(cell, smokeCount, smokeArea, smokeDepth,
            smokeRadiusLimit, weight, true);
        int sparks = !flameVisible || cell.maximumHeat() < 0.22F ? 0 : Math.min(4,
            Math.max(1, (int) Math.ceil(Math.sqrt(cell.flameEnergy()) * quality)));
        return new CellPlan(flames, smoke, sparks, (float) flameArea,
            (float) smokeDepth, weight);
    }

    private static double desiredCount(final double projectedArea,
        final double targetDiameter, final int minimum, final int maximum,
        final double representedArea, final double opticalDepth,
        final double maximumRadius, final double maximumOpacity) {
        double targetArea = Math.PI * 0.25 * targetDiameter * targetDiameter;
        double count = Mth.clamp(projectedArea / Math.max(1.0, targetArea),
            minimum, maximum);
        if (maximumOpacity < 0.9) {
            // Optically thin smoke does not get smaller when its mass is split
            // into more transparent cards. Chasing the radius cap here always
            // escalated faint, distant cells to the maximum particle count.
            double opacityMinimum = opticalDepth / -Math.log(1.0 - maximumOpacity);
            return Mth.clamp(Math.max(count, opacityMinimum), minimum, maximum);
        }
        double opacity = Math.min(0.86, 0.48 + opticalDepth * 0.16);
        double coverageMinimum = representedArea
            / (Math.PI * maximumRadius * maximumRadius * opacity);
        return Mth.clamp(Math.max(count, coverageMinimum), minimum, maximum);
    }

    private static List<Card> cards(final FireVisualCell cell, final double count,
        final double representedArea, final double opticalDepth,
        final double maximumRadius, final float representationWeight,
        final boolean smoke) {
        if (count <= 0) return List.of();
        // Flames keep a readable per-card opacity. Dividing opacity by count
        // cancelled the intended inverse-square-root change in particle size.
        double opacity = smoke ? opacity(opticalDepth, count)
            : Math.min(0.86, 0.48 + opticalDepth * 0.16);
        float radius = (float) Math.min(maximumRadius, Math.max(0.06,
            Math.sqrt(representedArea
                / (Math.PI * count * Math.max(0.025, opacity)))));
        float alpha = Mth.clamp((float) opacity * representationWeight,
            0.0F, smoke ? 0.72F : 0.96F);
        int occupied = Math.max(1, Long.bitCount(cell.occupancyMask()));
        ArrayList<Card> cards = new ArrayList<>((int)Math.ceil(count));
        for (int index = 0; index < count; index++) {
            long identity = exactPatch(cell) || cell.cellSize() == 1 ? index
                : nthSetBit(cell.occupancyMask(), index % occupied)
                    + (long)(index / occupied) * 64;
            long seed = mix(cell.seed() ^ (smoke ? 0x534D4F4B455F4344L
                : 0x464C414D455F4344L) ^ identity * 0x9E3779B97F4A7C15L);
            Vec3 position = occupiedPosition(cell, index, seed);
            if (smoke) {
                position = position.add(0.0, 0.30 + maximumRadius * 0.34, 0.0);
            } else if (exactPatch(cell)) {
                position = position.add(Vec3.atLowerCornerOf(
                    cell.dominantFace().getUnitVec3i()).scale(0.045)).add(0.0, 0.02, 0.0);
            } else {
                position = position.add(0.0, 0.05 + maximumRadius * 0.12, 0.0);
            }
            // A fractional final card fades in continuously at count boundaries;
            // existing radii shrink continuously to conserve the covered area.
            double fraction = Math.min(1.0, count - index);
            float cardAlpha = smoke
                ? (float)(1.0 - Math.exp(-opticalDepth * fraction / count))
                    * representationWeight
                : alpha * (float)fraction;
            cards.add(new Card(position, radius, Math.min(smoke ? 0.72F : 0.96F,
                cardAlpha), seed));
        }
        return List.copyOf(cards);
    }

    /** Stable low-discrepancy placement restricted to occupied 8x8 subcells. */
    public static Vec3 occupiedPosition(final FireVisualCell cell, final int index,
        final long seed) {
        if (exactPatch(cell)) {
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
        if (cell.cellSize() == 1) {
            // A HOST combines several patches from a solid block. Its weighted
            // centroid can lie inside that block, especially on opposite faces.
            // Keep the representative on the exposed dominant face.
            Vec3 c = cell.centroid();
            double a = (radicalInverse(index + 1, 2) - 0.5) * 0.46;
            double b = (radicalInverse(index + 1, 3) - 0.5) * 0.46;
            return switch (cell.dominantFace()) {
                case UP -> new Vec3(c.x + a, cell.cellY() + 1.035, c.z + b);
                case DOWN -> new Vec3(c.x + a, cell.cellY() - 0.035, c.z + b);
                case NORTH -> new Vec3(c.x + a, c.y + b, cell.cellZ() - 0.035);
                case SOUTH -> new Vec3(c.x + a, c.y + b, cell.cellZ() + 1.035);
                case EAST -> new Vec3(cell.cellX() + 1.035, c.y + b, c.z + a);
                case WEST -> new Vec3(cell.cellX() - 0.035, c.y + b, c.z + a);
            };
        }
        int occupiedCount = Math.max(1, Long.bitCount(cell.occupancyMask()));
        // Use a permutation, not a fresh hash for each card: every occupied
        // subcell gets a representative before any subcell gets a second one.
        int rank = index % occupiedCount;
        int bit = nthSetBit(cell.occupancyMask(), rank);
        int subX = bit & 7;
        int subZ = bit >>> 3;
        double subcellSize = cell.cellSize() / 8.0;
        int layer = index / occupiedCount;
        double jitterX = (radicalInverse(layer + 1, 2) - 0.5) * subcellSize * 0.62;
        double jitterZ = (radicalInverse(layer + 1, 3) - 0.5) * subcellSize * 0.62;
        double x = cell.cellX() * (double) cell.cellSize()
            + (subX + 0.5) * subcellSize + jitterX;
        double z = cell.cellZ() * (double) cell.cellSize()
            + (subZ + 0.5) * subcellSize + jitterZ;
        // These anchors represent exposed surfaces. Random downward jitter can
        // bury all cards in a one-block host in the middle LOD.
        double y = cell.dominantFace() == net.minecraft.core.Direction.UP
            ? Math.max(cell.centroid().y,
                cell.cellY() + 1.035)
            : cell.centroid().y;
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

    private static double opacity(final double opticalDepth, final double count) {
        return 1.0 - Math.exp(-Math.max(0.0, opticalDepth) / Math.max(1, count));
    }

    private static double maximumFlameRadius(final FireVisualCell cell) {
        if (exactPatch(cell)) return 0.42;
        double extent = Math.max(cell.extents().x, cell.extents().z);
        return Math.max(0.42, Math.min(1.35, extent * 0.65 + 0.20));
    }

    private static double maximumSmokeRadius(final FireVisualCell cell) {
        if (exactPatch(cell)) return 0.52;
        double extent = Math.max(cell.extents().x, cell.extents().z);
        return Math.max(0.72, Math.min(cell.cellSize() * 0.78, extent * 1.12 + 0.52));
    }

    private static boolean exactPatch(final FireVisualCell cell) {
        return cell.band().exactPatch();
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
