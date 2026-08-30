package com.andye.warmod.fire.network;

import com.andye.warmod.fire.FireCellSnapshot;
import com.andye.warmod.fire.FirePhase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Builds the bounded existing fire-network representation; it owns no state. */
public final class FireVisualRepresentationBuilder {
    private static final int NEAR_FALLBACK_BUDGET = 80;
    private static final int NEAR_FALLBACK_CELL_SIZE = 64;

    private FireVisualRepresentationBuilder() { }

    public static Representation build(final List<FireCellSnapshot> patches,
        final Vec3 viewer) {
        if (viewer == null || !viewer.isFinite() || patches == null || patches.isEmpty())
            return Representation.empty();
        List<HostSample> hosts = aggregateHosts(patches);
        ArrayList<FireVisualCell> cells = new ArrayList<>(
            com.andye.warmod.fire.network.ClientboundFireStatePayload.MAX_CELLS);
        EnumMap<FireVisualBand, Integer> sourceHosts = new EnumMap<>(FireVisualBand.class);
        EnumMap<FireVisualBand, Integer> cellCounts = new EnumMap<>(FireVisualBand.class);
        EnumMap<FireVisualBand, Integer> cellSizes = new EnumMap<>(FireVisualBand.class);
        for (FireVisualBand band : FireVisualBand.values()) {
            if (band == FireVisualBand.NEAR) {
                BandResult result = buildNear(patches, viewer);
                cells.addAll(result.cells());
                sourceHosts.put(band, (int) hosts.stream().filter(host ->
                    band.contains(host.position().distanceTo(viewer))).count());
                cellCounts.put(band, result.cells().size());
                cellSizes.put(band, band.preferredCellSize());
                continue;
            }
            ArrayList<HostSample> bandHosts = new ArrayList<>();
            for (HostSample host : hosts) {
                if (band.contains(host.position().distanceTo(viewer))) bandHosts.add(host);
            }
            sourceHosts.put(band, bandHosts.size());
            BandResult result = buildBand(band, bandHosts);
            cells.addAll(result.cells());
            cellCounts.put(band, result.cells().size());
            cellSizes.put(band, result.cellSize());
        }
        cells.sort(Comparator.comparingInt((FireVisualCell cell) -> cell.band().wireId())
            .thenComparingLong(FireVisualCell::id));
        if (cells.size() > ClientboundFireStatePayload.MAX_CELLS)
            throw new IllegalStateException("Fire representation exceeded packet capacity: "
                + cells.size());
        return new Representation(List.copyOf(cells), FireVisualBand.COMPLETE_MASK,
            hosts.size(), Map.copyOf(sourceHosts), Map.copyOf(cellCounts),
            Map.copyOf(cellSizes));
    }

    private static BandResult buildBand(final FireVisualBand band,
        final List<HostSample> hosts) {
        int cellSize = band.preferredCellSize();
        Map<CellCoordinate, CellAccumulator> buckets = bucket(hosts, cellSize);
        if (buckets.size() > band.cellBudget())
            throw new IllegalStateException("Fixed " + band + " fire grid exceeded budget: "
                + buckets.size() + "/" + band.cellBudget());
        ArrayList<FireVisualCell> cells = new ArrayList<>(buckets.size());
        for (Map.Entry<CellCoordinate, CellAccumulator> entry : buckets.entrySet())
            cells.add(entry.getValue().finish(band, cellSize, entry.getKey()));
        return new BandResult(List.copyOf(cells), cellSize);
    }

    private static BandResult buildNear(final List<FireCellSnapshot> patches,
        final Vec3 viewer) {
        ArrayList<FireCellSnapshot> near = new ArrayList<>();
        for (FireCellSnapshot patch : patches) {
            if (validPatch(patch)
                && FireVisualBand.NEAR.contains(patch.anchor().position().distanceTo(viewer))) {
                near.add(patch);
            }
        }
        near.sort(Comparator.comparingDouble((FireCellSnapshot patch) ->
            patch.anchor().position().distanceToSqr(viewer)).thenComparingLong(
                FireCellSnapshot::id));
        int exactBudget = FireVisualBand.NEAR.cellBudget() - NEAR_FALLBACK_BUDGET;
        int exactCount = Math.min(exactBudget, near.size());
        Map<Long, Float> hostEnergy = hostEnergy(patches);
        ArrayList<FireVisualCell> cells = new ArrayList<>(FireVisualBand.NEAR.cellBudget());
        for (int index = 0; index < exactCount; index++) {
            FireCellSnapshot patch = near.get(index);
            cells.add(exactCell(patch, clumpStrength(patch, hostEnergy)));
        }
        if (exactCount < near.size()) {
            List<HostSample> fallbackHosts = aggregateHosts(near.subList(exactCount, near.size()));
            Map<CellCoordinate, CellAccumulator> fallback = bucket(fallbackHosts,
                NEAR_FALLBACK_CELL_SIZE);
            if (fallback.size() > NEAR_FALLBACK_BUDGET) {
                throw new IllegalStateException("Fixed near fallback grid exceeded budget: "
                    + fallback.size() + "/" + NEAR_FALLBACK_BUDGET);
            }
            for (Map.Entry<CellCoordinate, CellAccumulator> entry : fallback.entrySet()) {
                cells.add(entry.getValue().finish(FireVisualBand.NEAR,
                    NEAR_FALLBACK_CELL_SIZE, entry.getKey()));
            }
        }
        return new BandResult(List.copyOf(cells), FireVisualBand.NEAR.preferredCellSize());
    }

    private static FireVisualCell exactCell(final FireCellSnapshot patch,
        final float clumpStrength) {
        BlockPos host = patch.anchor().host();
        Vec3 position = patch.anchor().position();
        float flameEnergy = Math.max(0.0F, patch.intensity() * patch.coverage()
            * (0.25F + patch.heat() * 0.75F));
        float smokeMass = Math.max(0.0F, patch.smoke() * patch.coverage());
        int subX = Mth.clamp((int)Math.floor(patch.anchor().localX() * 8.0F), 0, 7);
        int subZ = Mth.clamp((int)Math.floor(patch.anchor().localZ() * 8.0F), 0, 7);
        float extent = Mth.clamp((0.08F + patch.coverage() * 0.50F)
            * (1.0F + clumpStrength * 0.22F), 0.16F, 0.72F);
        long id = exactCellId(patch.id());
        return new FireVisualCell(id, FireVisualBand.NEAR, 1,
            host.getX(), host.getY(), host.getZ(), position,
            new Vec3(extent, extent, extent), 1L << (subZ * 8 + subX),
            flameEnergy, smokeMass, Math.max(0.0F, patch.heat()),
            Math.max(0.0F, patch.intensity()), Math.max(0.0F, patch.coverage()),
            clumpStrength, patch.wind(), 1, patch.seed(), patch.anchor().face(), patch.phase(),
            patch.ignitionGameTime());
    }

    private static Map<Long, Float> hostEnergy(final List<FireCellSnapshot> patches) {
        Map<Long, Float> energy = new HashMap<>();
        for (FireCellSnapshot patch : patches) {
            if (!validPatch(patch) || patch.phase() == FirePhase.SMOLDERING
                || patch.phase() == FirePhase.DECAYING) continue;
            energy.merge(patch.anchor().host().asLong(), patch.heat() * patch.coverage(),
                Math::max);
        }
        return energy;
    }

    private static float clumpStrength(final FireCellSnapshot patch,
        final Map<Long, Float> hostEnergy) {
        BlockPos host = patch.anchor().host();
        float energy = 0.0F;
        int burningHosts = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Float sample = hostEnergy.get(BlockPos.asLong(host.getX() + dx,
                        host.getY() + dy, host.getZ() + dz));
                    if (sample == null) continue;
                    energy += sample;
                    burningHosts++;
                }
            }
        }
        float density = Mth.clamp((burningHosts - 6) / 12.0F, 0.0F, 1.5F);
        float average = burningHosts == 0 ? 0.0F : energy / burningHosts;
        return density * Mth.clamp(average * 1.25F, 0.0F, 1.0F);
    }

    private static boolean validPatch(final FireCellSnapshot patch) {
        return patch != null && patch.anchor() != null && patch.phase() != null
            && patch.wind() != null && patch.wind().isFinite()
            && patch.anchor().position().isFinite();
    }

    private static long exactCellId(final long patchId) {
        long id = mix(0x4E4541525F504154L ^ patchId) & Long.MAX_VALUE;
        return id == 0L ? 1L : id;
    }

    private static Map<CellCoordinate, CellAccumulator> bucket(
        final List<HostSample> hosts, final int cellSize) {
        int verticalSize = Math.max(1, cellSize / 2);
        LinkedHashMap<CellCoordinate, CellAccumulator> result = new LinkedHashMap<>();
        for (HostSample host : hosts) {
            BlockPos position = host.host();
            CellCoordinate coordinate = new CellCoordinate(
                Math.floorDiv(position.getX(), cellSize),
                Math.floorDiv(position.getY(), verticalSize),
                Math.floorDiv(position.getZ(), cellSize));
            result.computeIfAbsent(coordinate, ignored -> new CellAccumulator())
                .add(host, cellSize, coordinate);
        }
        return result;
    }

    private static List<HostSample> aggregateHosts(final List<FireCellSnapshot> patches) {
        LinkedHashMap<Long, HostAccumulator> hosts = new LinkedHashMap<>();
        for (FireCellSnapshot patch : patches) {
            if (!validPatch(patch)) continue;
            hosts.computeIfAbsent(patch.anchor().host().asLong(), ignored ->
                new HostAccumulator(patch.anchor().host())).add(patch);
        }
        return hosts.values().stream().map(HostAccumulator::finish)
            .filter(sample -> sample.flameEnergy() > 0.0F || sample.smokeMass() > 0.0F)
            .toList();
    }

    private static long cellId(final FireVisualBand band, final int cellSize,
        final CellCoordinate coordinate) {
        long value = 0x464952455F43454CL ^ ((long) band.wireId() << 60)
            ^ ((long) cellSize * 0x9E3779B97F4A7C15L)
            ^ ((long) coordinate.x() * 0xC2B2AE3D27D4EB4FL)
            ^ ((long) coordinate.y() * 0x165667B19E3779F9L)
            ^ ((long) coordinate.z() * 0xD1B54A32D192ED03L);
        long id = mix(value) & Long.MAX_VALUE;
        return id == 0L ? 1L : id;
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public record Representation(List<FireVisualCell> cells, int completeBandMask,
        int uniqueHostCount, Map<FireVisualBand, Integer> sourceHostsByBand,
        Map<FireVisualBand, Integer> cellsByBand,
        Map<FireVisualBand, Integer> cellSizeByBand) {
        private static Representation empty() {
            EnumMap<FireVisualBand, Integer> zeroes = new EnumMap<>(FireVisualBand.class);
            for (FireVisualBand band : FireVisualBand.values()) zeroes.put(band, 0);
            return new Representation(List.of(), FireVisualBand.COMPLETE_MASK, 0,
                Map.copyOf(zeroes), Map.copyOf(zeroes), Map.copyOf(zeroes));
        }
    }

    private record BandResult(List<FireVisualCell> cells, int cellSize) { }
    private record CellCoordinate(int x, int y, int z) { }
    private record HostSample(BlockPos host, Vec3 position, float flameEnergy,
        float smokeMass, float maximumHeat, float averageIntensity,
        float coveredArea, Vec3 wind, long seed, Direction dominantFace,
        FirePhase phase, long ignitionGameTime) { }

    private static final class HostAccumulator {
        private final BlockPos host;
        private final double[] faceEnergy = new double[Direction.values().length];
        private double weight, x, y, z, windX, windY, windZ;
        private double flameEnergy, smokeMass, intensity, coveredArea;
        private float maximumHeat;
        private long seed;
        private long ignitionGameTime = Long.MAX_VALUE;
        private FirePhase phase = FirePhase.SMOLDERING;
        private double phaseEnergy = -1.0;

        private HostAccumulator(final BlockPos host) { this.host = host.immutable(); }

        private void add(final FireCellSnapshot patch) {
            double sampleWeight = Math.max(0.02,
                patch.coverage() * (0.30 + patch.heat() * 0.70));
            Vec3 position = patch.anchor().position();
            weight += sampleWeight;
            x += position.x * sampleWeight; y += position.y * sampleWeight;
            z += position.z * sampleWeight;
            windX += patch.wind().x * sampleWeight;
            windY += patch.wind().y * sampleWeight;
            windZ += patch.wind().z * sampleWeight;
            double sampleFlame = Math.max(0.0,
                patch.intensity() * patch.coverage() * (0.25 + patch.heat() * 0.75));
            double sampleSmoke = Math.max(0.0, patch.smoke() * patch.coverage());
            flameEnergy += sampleFlame;
            smokeMass += sampleSmoke;
            coveredArea += Math.max(0.0, patch.coverage());
            intensity += patch.intensity() * sampleWeight;
            maximumHeat = Math.max(maximumHeat, patch.heat());
            faceEnergy[patch.anchor().face().ordinal()] += sampleFlame + sampleSmoke * 0.25;
            seed ^= mix(patch.seed() ^ patch.id());
            ignitionGameTime = Math.min(ignitionGameTime, patch.ignitionGameTime());
            if (sampleFlame + sampleSmoke > phaseEnergy) {
                phaseEnergy = sampleFlame + sampleSmoke;
                phase = patch.phase();
            }
        }

        private HostSample finish() {
            double safe = Math.max(0.01, weight);
            int face = 0;
            for (int index = 1; index < faceEnergy.length; index++)
                if (faceEnergy[index] > faceEnergy[face]) face = index;
            return new HostSample(host, new Vec3(x / safe, y / safe, z / safe),
                (float) flameEnergy, (float) smokeMass, maximumHeat,
                (float) (intensity / safe), (float) coveredArea,
                new Vec3(windX / safe, windY / safe, windZ / safe),
                seed == 0L ? mix(host.asLong()) : seed, Direction.values()[face], phase,
                ignitionGameTime == Long.MAX_VALUE ? 0L : ignitionGameTime);
        }
    }

    private static final class CellAccumulator {
        private double weight, x, y, z, windX, windY, windZ;
        private double flameEnergy, smokeMass, intensity, coveredArea;
        private float maximumHeat;
        private int minimumX = Integer.MAX_VALUE, minimumY = Integer.MAX_VALUE,
            minimumZ = Integer.MAX_VALUE;
        private int maximumX = Integer.MIN_VALUE, maximumY = Integer.MIN_VALUE,
            maximumZ = Integer.MIN_VALUE;
        private int hosts;
        private long occupancyMask;
        private long seed;
        private long ignitionGameTime = Long.MAX_VALUE;
        private final double[] faceEnergy = new double[Direction.values().length];
        private FirePhase phase = FirePhase.SMOLDERING;
        private double phaseEnergy = -1.0;

        private void add(final HostSample host, final int cellSize,
            final CellCoordinate coordinate) {
            double sampleWeight = Math.max(0.02,
                host.flameEnergy() + host.smokeMass() * 0.45 + host.coveredArea() * 0.05);
            weight += sampleWeight;
            x += host.position().x * sampleWeight; y += host.position().y * sampleWeight;
            z += host.position().z * sampleWeight;
            windX += host.wind().x * sampleWeight;
            windY += host.wind().y * sampleWeight;
            windZ += host.wind().z * sampleWeight;
            flameEnergy += host.flameEnergy(); smokeMass += host.smokeMass();
            intensity += host.averageIntensity() * sampleWeight;
            coveredArea += host.coveredArea();
            maximumHeat = Math.max(maximumHeat, host.maximumHeat());
            minimumX = Math.min(minimumX, host.host().getX());
            minimumY = Math.min(minimumY, host.host().getY());
            minimumZ = Math.min(minimumZ, host.host().getZ());
            maximumX = Math.max(maximumX, host.host().getX());
            maximumY = Math.max(maximumY, host.host().getY());
            maximumZ = Math.max(maximumZ, host.host().getZ());
            int baseX = coordinate.x() * cellSize;
            int baseZ = coordinate.z() * cellSize;
            int subX = Mth.clamp((int) (((host.host().getX() - baseX) + 0.5)
                * 8.0 / cellSize), 0, 7);
            int subZ = Mth.clamp((int) (((host.host().getZ() - baseZ) + 0.5)
                * 8.0 / cellSize), 0, 7);
            occupancyMask |= 1L << (subZ * 8 + subX);
            faceEnergy[host.dominantFace().ordinal()] += sampleWeight;
            seed ^= mix(host.seed() ^ host.host().asLong());
            hosts++;
            ignitionGameTime = Math.min(ignitionGameTime, host.ignitionGameTime());
            if (host.flameEnergy() + host.smokeMass() > phaseEnergy) {
                phaseEnergy = host.flameEnergy() + host.smokeMass();
                phase = host.phase();
            }
        }

        private FireVisualCell finish(final FireVisualBand band, final int cellSize,
            final CellCoordinate coordinate) {
            double safe = Math.max(0.01, weight);
            int face = 0;
            for (int index = 1; index < faceEnergy.length; index++)
                if (faceEnergy[index] > faceEnergy[face]) face = index;
            Vec3 extents = new Vec3(
                Math.max(0.5, (maximumX - minimumX + 1) * 0.5),
                Math.max(0.5, (maximumY - minimumY + 1) * 0.5),
                Math.max(0.5, (maximumZ - minimumZ + 1) * 0.5));
            return new FireVisualCell(cellId(band, cellSize, coordinate), band, cellSize,
                coordinate.x(), coordinate.y(), coordinate.z(),
                new Vec3(x / safe, y / safe, z / safe), extents,
                occupancyMask == 0L ? 1L : occupancyMask,
                (float) flameEnergy, (float) smokeMass, maximumHeat,
                (float) (intensity / safe), (float) coveredArea, 0.0F,
                new Vec3(windX / safe, windY / safe, windZ / safe), hosts,
                seed == 0L ? cellId(band, cellSize, coordinate) : seed,
                Direction.values()[face], phase,
                ignitionGameTime == Long.MAX_VALUE ? 0L : ignitionGameTime);
        }
    }
}
