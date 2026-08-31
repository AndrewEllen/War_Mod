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

/**
 * Builds the bounded five-level fire hierarchy without owning simulation state.
 * Every identity is derived only from a source patch, host position, or fixed
 * world-grid coordinate. Packet budgets select representatives; they never
 * alter the spatial grid and therefore never churn surviving identities.
 */
public final class FireVisualRepresentationBuilder {
    private static final int ANGULAR_COVERAGE_SECTORS = 32;

    private FireVisualRepresentationBuilder() { }

    public static Representation build(final List<FireCellSnapshot> patches,
        final Vec3 viewer) {
        if (viewer == null || !viewer.isFinite() || patches == null || patches.isEmpty())
            return Representation.empty();
        List<FireCellSnapshot> validPatches = patches.stream()
            .filter(FireVisualRepresentationBuilder::validPatch).toList();
        if (validPatches.isEmpty()) return Representation.empty();
        List<HostSample> hosts = aggregateHosts(validPatches);
        Map<Long, Float> hostEnergy = hostEnergy(validPatches);
        ArrayList<FireVisualCell> cells = new ArrayList<>();
        EnumMap<FireVisualBand, Integer> sourceHosts = new EnumMap<>(FireVisualBand.class);
        EnumMap<FireVisualBand, Integer> cellCounts = new EnumMap<>(FireVisualBand.class);
        EnumMap<FireVisualBand, Integer> cellSizes = new EnumMap<>(FireVisualBand.class);
        EnumMap<FireVisualBand, Integer> omittedCells = new EnumMap<>(FireVisualBand.class);

        for (FireVisualBand band : FireVisualBand.values()) {
            int bandHostCount = (int) hosts.stream().filter(host ->
                band.contains(host.position().distanceTo(viewer))).count();
            sourceHosts.put(band, bandHostCount);
            BandResult result = switch (band) {
                case PATCH -> buildPatches(validPatches, viewer, hostEnergy);
                case HOST -> buildHosts(hosts, viewer, hostEnergy);
                case LOCAL, FAR, HORIZON -> buildAggregateBand(band, hosts, viewer);
            };
            cells.addAll(result.cells());
            cellCounts.put(band, result.cells().size());
            cellSizes.put(band, band.preferredCellSize());
            omittedCells.put(band, result.omittedCells());
        }
        cells.sort(Comparator.comparingInt((FireVisualCell cell) -> cell.band().wireId())
            .thenComparingLong(FireVisualCell::id));
        return new Representation(List.copyOf(cells), FireVisualBand.COMPLETE_MASK,
            hosts.size(), Map.copyOf(sourceHosts), Map.copyOf(cellCounts),
            Map.copyOf(cellSizes), Map.copyOf(omittedCells));
    }

    private static BandResult buildPatches(final List<FireCellSnapshot> patches,
        final Vec3 viewer, final Map<Long, Float> hostEnergy) {
        ArrayList<FireVisualCell> candidates = new ArrayList<>();
        for (FireCellSnapshot patch : patches) {
            if (!FireVisualBand.PATCH.contains(patch.anchor().position().distanceTo(viewer)))
                continue;
            candidates.add(patchCell(patch, clumpStrength(patch.anchor().host(), hostEnergy)));
        }
        candidates.sort(Comparator.comparingLong(FireVisualCell::id));
        return new BandResult(List.copyOf(candidates), 0);
    }

    private static BandResult buildHosts(final List<HostSample> hosts,
        final Vec3 viewer, final Map<Long, Float> hostEnergy) {
        ArrayList<FireVisualCell> candidates = new ArrayList<>();
        for (HostSample host : hosts) {
            if (!FireVisualBand.HOST.contains(host.position().distanceTo(viewer))) continue;
            candidates.add(hostCell(host, clumpStrength(host.host(), hostEnergy)));
        }
        return bounded(FireVisualBand.HOST, candidates, viewer);
    }

    private static BandResult buildAggregateBand(final FireVisualBand band,
        final List<HostSample> hosts, final Vec3 viewer) {
        ArrayList<HostSample> bandHosts = new ArrayList<>();
        for (HostSample host : hosts) {
            if (band.contains(host.position().distanceTo(viewer))) bandHosts.add(host);
        }
        int cellSize = band.preferredCellSize();
        Map<CellCoordinate, CellAccumulator> buckets = bucket(bandHosts, cellSize);
        ArrayList<FireVisualCell> candidates = new ArrayList<>(buckets.size());
        for (Map.Entry<CellCoordinate, CellAccumulator> entry : buckets.entrySet()) {
            candidates.add(entry.getValue().finish(band, cellSize, entry.getKey()));
        }
        return bounded(band, candidates, viewer);
    }

    private static BandResult bounded(final FireVisualBand band,
        final List<FireVisualCell> candidates, final Vec3 viewer) {
        if (candidates.size() <= band.cellBudget()) {
            ArrayList<FireVisualCell> ordered = new ArrayList<>(candidates);
            ordered.sort(Comparator.comparingLong(FireVisualCell::id));
            return new BandResult(List.copyOf(ordered), 0);
        }

        LinkedHashMap<Long, FireVisualCell> selected = new LinkedHashMap<>();
        FireVisualCell[] sectorBest = new FireVisualCell[ANGULAR_COVERAGE_SECTORS];
        Comparator<FireVisualCell> ranking = representativeOrder(viewer);
        for (FireVisualCell candidate : candidates) {
            int sector = angularSector(viewer, candidate.centroid());
            FireVisualCell incumbent = sectorBest[sector];
            if (incumbent == null || ranking.compare(candidate, incumbent) < 0)
                sectorBest[sector] = candidate;
        }
        for (FireVisualCell candidate : sectorBest) {
            if (candidate != null && selected.size() < band.cellBudget())
                selected.put(candidate.id(), candidate);
        }
        ArrayList<FireVisualCell> ranked = new ArrayList<>(candidates);
        ranked.sort(ranking);
        for (FireVisualCell candidate : ranked) {
            if (selected.size() >= band.cellBudget()) break;
            selected.putIfAbsent(candidate.id(), candidate);
        }
        ArrayList<FireVisualCell> result = new ArrayList<>(selected.values());
        result.sort(Comparator.comparingLong(FireVisualCell::id));
        return new BandResult(List.copyOf(result), candidates.size() - result.size());
    }

    private static Comparator<FireVisualCell> representativeOrder(final Vec3 viewer) {
        return Comparator.comparingDouble((FireVisualCell cell) ->
            -representativeImportance(cell, viewer)).thenComparingLong(FireVisualCell::id);
    }

    private static double representativeImportance(final FireVisualCell cell,
        final Vec3 viewer) {
        double energy = cell.flameEnergy() + cell.smokeMass() * 0.42
            + cell.coveredArea() * 0.06;
        double distance = Math.max(1.0, cell.centroid().distanceTo(viewer));
        return energy * (0.72 + 96.0 / (96.0 + distance));
    }

    private static int angularSector(final Vec3 viewer, final Vec3 position) {
        double angle = Math.atan2(position.z - viewer.z, position.x - viewer.x);
        if (angle < 0.0) angle += Math.PI * 2.0;
        return Math.min(ANGULAR_COVERAGE_SECTORS - 1,
            (int)Math.floor(angle / (Math.PI * 2.0) * ANGULAR_COVERAGE_SECTORS));
    }

    private static FireVisualCell patchCell(final FireCellSnapshot patch,
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
        float flameEnvelope = flameEnvelopeHeight(patch.intensity(), patch.coverage(),
            patch.heat(), clumpStrength, patch.phase());
        return new FireVisualCell(patchCellId(patch.anchor(), subX, subZ), hostCellId(host),
            FireVisualBand.PATCH, 1, host.getX(), host.getY(), host.getZ(), position,
            new Vec3(extent, extent, extent), 1L << (subZ * 8 + subX),
            flameEnergy, flameEnvelope, smokeMass, Math.max(0.0F, patch.heat()),
            Math.max(0.0F, patch.intensity()), Math.max(0.0F, patch.coverage()),
            clumpStrength, patch.wind(), 1, patch.seed(), patch.anchor().face(),
            patch.phase(), patch.ignitionGameTime());
    }

    private static FireVisualCell hostCell(final HostSample host,
        final float clumpStrength) {
        CellCoordinate local = coordinate(host.host(), FireVisualBand.LOCAL.preferredCellSize());
        return new FireVisualCell(hostCellId(host.host()),
            cellId(FireVisualBand.LOCAL, FireVisualBand.LOCAL.preferredCellSize(), local),
            FireVisualBand.HOST, 1, host.host().getX(), host.host().getY(),
            host.host().getZ(), host.position(), new Vec3(0.5, 0.5, 0.5), 1L << 36,
            host.flameEnergy(), host.flameEnvelopeHeight()
                * (1.0F + clumpStrength * 0.55F), host.smokeMass(), host.maximumHeat(),
            host.averageIntensity(), host.coveredArea(), clumpStrength, host.wind(), 1,
            host.seed(), host.dominantFace(), host.phase(), host.ignitionGameTime());
    }

    private static float flameEnvelopeHeight(final float intensity,
        final float coverage, final float heat, final float clumpStrength,
        final FirePhase phase) {
        float stage = switch (phase) {
            case IGNITION -> 0.45F;
            case GROWING -> 0.65F + Math.min(1.0F, Math.max(0.0F, coverage)) * 0.35F;
            case FLAMING -> 1.0F;
            case DECAYING -> 0.72F;
            case SMOLDERING -> 0.24F;
        };
        return Math.max(0.05F, (0.22F + Math.max(0.0F, intensity) * 1.85F)
            * stage * (1.0F + Math.max(0.0F, clumpStrength) * 0.55F)
            * (0.82F + Math.min(1.2F, Math.max(0.0F, heat)) * 0.18F));
    }

    private static Map<Long, Float> hostEnergy(final List<FireCellSnapshot> patches) {
        Map<Long, Float> energy = new HashMap<>();
        for (FireCellSnapshot patch : patches) {
            if (patch.phase() == FirePhase.SMOLDERING || patch.phase() == FirePhase.DECAYING)
                continue;
            energy.merge(patch.anchor().host().asLong(), patch.heat() * patch.coverage(),
                Math::max);
        }
        return energy;
    }

    private static float clumpStrength(final BlockPos host,
        final Map<Long, Float> hostEnergy) {
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

    private static Map<CellCoordinate, CellAccumulator> bucket(
        final List<HostSample> hosts, final int cellSize) {
        LinkedHashMap<CellCoordinate, CellAccumulator> result = new LinkedHashMap<>();
        for (HostSample host : hosts) {
            CellCoordinate coordinate = coordinate(host.host(), cellSize);
            result.computeIfAbsent(coordinate, ignored -> new CellAccumulator())
                .add(host, cellSize, coordinate);
        }
        return result;
    }

    private static CellCoordinate coordinate(final BlockPos position, final int cellSize) {
        int verticalSize = Math.max(1, cellSize / 2);
        return new CellCoordinate(Math.floorDiv(position.getX(), cellSize),
            Math.floorDiv(position.getY(), verticalSize),
            Math.floorDiv(position.getZ(), cellSize));
    }

    private static CellCoordinate parentCoordinate(final CellCoordinate coordinate,
        final int cellSize, final int parentSize) {
        int verticalSize = Math.max(1, cellSize / 2);
        int parentVerticalSize = Math.max(1, parentSize / 2);
        return new CellCoordinate(Math.floorDiv(coordinate.x() * cellSize, parentSize),
            Math.floorDiv(coordinate.y() * verticalSize, parentVerticalSize),
            Math.floorDiv(coordinate.z() * cellSize, parentSize));
    }

    private static List<HostSample> aggregateHosts(final List<FireCellSnapshot> patches) {
        LinkedHashMap<Long, HostAccumulator> hosts = new LinkedHashMap<>();
        for (FireCellSnapshot patch : patches) {
            hosts.computeIfAbsent(patch.anchor().host().asLong(), ignored ->
                new HostAccumulator(patch.anchor().host())).add(patch);
        }
        return hosts.values().stream().map(HostAccumulator::finish)
            .filter(sample -> sample.flameEnergy() > 0.0F || sample.smokeMass() > 0.0F)
            .toList();
    }

    private static long patchCellId(final com.andye.warmod.fire.FireSurfaceAnchor anchor,
        final int subX, final int subZ) {
        int subY = Mth.clamp((int)Math.floor(anchor.localY() * 8.0F), 0, 7);
        long surface = anchor.host().asLong()
            ^ ((long)anchor.face().ordinal() << 58)
            ^ ((long)subX << 6) ^ ((long)subY << 3) ^ subZ;
        return positiveId(mix(0x50415443485F4944L ^ surface));
    }

    private static long hostCellId(final BlockPos host) {
        return positiveId(mix(0x484F53545F49445FL ^ host.asLong()));
    }

    private static long cellId(final FireVisualBand band, final int cellSize,
        final CellCoordinate coordinate) {
        long value = 0x464952455F43454CL ^ ((long)band.wireId() << 56)
            ^ ((long)cellSize * 0x9E3779B97F4A7C15L)
            ^ ((long)coordinate.x() * 0xC2B2AE3D27D4EB4FL)
            ^ ((long)coordinate.y() * 0x165667B19E3779F9L)
            ^ ((long)coordinate.z() * 0xD1B54A32D192ED03L);
        return positiveId(mix(value));
    }

    private static long positiveId(final long value) {
        long id = value & Long.MAX_VALUE;
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
        Map<FireVisualBand, Integer> cellSizeByBand,
        Map<FireVisualBand, Integer> omittedCellsByBand) {
        private static Representation empty() {
            EnumMap<FireVisualBand, Integer> zeroes = new EnumMap<>(FireVisualBand.class);
            for (FireVisualBand band : FireVisualBand.values()) zeroes.put(band, 0);
            Map<FireVisualBand, Integer> immutable = Map.copyOf(zeroes);
            return new Representation(List.of(), FireVisualBand.COMPLETE_MASK, 0,
                immutable, immutable, immutable, immutable);
        }
    }

    private record BandResult(List<FireVisualCell> cells, int omittedCells) { }
    private record CellCoordinate(int x, int y, int z) { }
    private record HostSample(BlockPos host, Vec3 position, float flameEnergy,
        float flameEnvelopeHeight, float smokeMass, float maximumHeat, float averageIntensity,
        float coveredArea, Vec3 wind, long seed, Direction dominantFace,
        FirePhase phase, long ignitionGameTime) { }

    private static final class HostAccumulator {
        private final BlockPos host;
        private final double[] faceEnergy = new double[Direction.values().length];
        private double weight, x, y, z, windX, windY, windZ;
        private double flameEnergy, smokeMass, intensity, coveredArea;
        private float flameEnvelopeHeight;
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
            flameEnergy += sampleFlame; smokeMass += sampleSmoke;
            flameEnvelopeHeight = Math.max(flameEnvelopeHeight,
                flameEnvelopeHeight(patch.intensity(), patch.coverage(), patch.heat(),
                    0.0F, patch.phase()));
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
            for (int index = 1; index < faceEnergy.length; index++) {
                if (faceEnergy[index] > faceEnergy[face]) face = index;
            }
            return new HostSample(host, new Vec3(x / safe, y / safe, z / safe),
                (float)flameEnergy, flameEnvelopeHeight, (float)smokeMass, maximumHeat,
                (float)(intensity / safe), (float)coveredArea,
                new Vec3(windX / safe, windY / safe, windZ / safe),
                seed == 0L ? mix(host.asLong()) : seed, Direction.values()[face], phase,
                ignitionGameTime == Long.MAX_VALUE ? 0L : ignitionGameTime);
        }
    }

    private static final class CellAccumulator {
        private double weight, x, y, z, windX, windY, windZ;
        private double flameEnergy, smokeMass, intensity, coveredArea;
        private float flameEnvelopeHeight;
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
            flameEnvelopeHeight = Math.max(flameEnvelopeHeight,
                host.flameEnvelopeHeight());
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
            int subX = Mth.clamp((int)(((host.host().getX() - baseX) + 0.5)
                * 8.0 / cellSize), 0, 7);
            int subZ = Mth.clamp((int)(((host.host().getZ() - baseZ) + 0.5)
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
            for (int index = 1; index < faceEnergy.length; index++) {
                if (faceEnergy[index] > faceEnergy[face]) face = index;
            }
            Vec3 extents = new Vec3(
                Math.max(0.5, (maximumX - minimumX + 1) * 0.5),
                Math.max(0.5, (maximumY - minimumY + 1) * 0.5),
                Math.max(0.5, (maximumZ - minimumZ + 1) * 0.5));
            FireVisualBand parentBand = band.parent();
            long parentId = 0L;
            if (parentBand != null) {
                int parentSize = parentBand.preferredCellSize();
                parentId = cellId(parentBand, parentSize,
                    parentCoordinate(coordinate, cellSize, parentSize));
            }
            long id = cellId(band, cellSize, coordinate);
            return new FireVisualCell(id, parentId, band, cellSize,
                coordinate.x(), coordinate.y(), coordinate.z(),
                new Vec3(x / safe, y / safe, z / safe), extents,
                occupancyMask == 0L ? 1L : occupancyMask,
                (float)flameEnergy, flameEnvelopeHeight, (float)smokeMass, maximumHeat,
                (float)(intensity / safe), (float)coveredArea, 0.0F,
                new Vec3(windX / safe, windY / safe, windZ / safe), hosts,
                seed == 0L ? id : seed, Direction.values()[face], phase,
                ignitionGameTime == Long.MAX_VALUE ? 0L : ignitionGameTime);
        }
    }
}
