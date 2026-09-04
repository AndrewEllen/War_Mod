package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/** Pure worker-side compiler. It consumes primitive snapshots and state IDs only. */
final class WarheadPlanCompiler {
    private static final long PHASE_KEY_MASK = 1L;

    private WarheadPlanCompiler() { }

    static PreparedChunkPlan compile(final PreparedImpactSpec impact,
        final WarheadFootprint footprint, final WarheadChunkSnapshot snapshot,
        final WarheadStatePalette palette) {
        ChunkPos chunk = snapshot.chunk();
        if (!footprint.requiredChunks().contains(chunk.pack())) {
            throw new IllegalArgumentException("Chunk is outside impact footprint");
        }
        Builders builders = new Builders(snapshot);
        if (snapshot.hasFeature(WarheadSnapshotFeatures.CRATER_VOLUME)) {
            snapshot.requireCoverage(WarheadSnapshotFeatures.CRATER_VOLUME);
            compileCrater(impact, footprint.terrainProfile(), snapshot, palette, builders);
        }
        if (snapshot.hasFeature(WarheadSnapshotFeatures.SURFACE)) {
            snapshot.requireCoverage(WarheadSnapshotFeatures.SURFACE);
            compileSurface(impact, footprint, snapshot, palette, builders);
        }
        if (snapshot.hasFeature(WarheadSnapshotFeatures.VERTICAL_FEATURES)) {
            snapshot.requireCoverage(WarheadSnapshotFeatures.VERTICAL_FEATURES);
            compileVertical(impact, footprint, snapshot, palette, builders);
        }
        if (snapshot.hasFeature(WarheadSnapshotFeatures.BIOMES)) {
            compileBiomes(impact, footprint, snapshot, builders);
        }
        int activationTick = radialActivationTick(impact.target(),
            footprint.maximumMutationRadius(), chunk);
        return builders.finish(chunk, snapshot.chunkRevision(), activationTick);
    }

    static PlanStatistics statistics(final PreparedChunkPlan plan) {
        long sections = plan.blockSections().stream().filter(section -> section.mutationCount() > 0).count();
        long blocks = plan.blockSections().stream().mapToLong(PreparedSectionPlan::mutationCount).sum();
        long biomeQuarts = plan.biomeSections().stream()
            .mapToLong(section -> Long.bitCount(section.quartMask())).sum();
        long semantic = plan.blockSections().stream()
            .mapToLong(section -> section.semanticMaskUnsafe().cardinality()).sum();
        long[] categoryCounts = new long[WarheadMutationCategory.values().length];
        HashMap<Integer, Long> histogram = new HashMap<>();
        for (PreparedSectionPlan section : plan.blockSections()) {
            byte[] categories = section.mutationCategoriesUnsafe();
            int[] replacements = section.finalStateIdsUnsafe();
            for (int index = 0; index < section.mutationCount(); index++) {
                categoryCounts[Byte.toUnsignedInt(categories[index])]++;
                histogram.merge(replacements[index], 1L, Long::sum);
            }
        }
        MutationCategoryCounts categorized = new MutationCategoryCounts(
            categoryCounts[WarheadMutationCategory.CRATER_EXCAVATION.ordinal()],
            categoryCounts[WarheadMutationCategory.CRATER_SHELL.ordinal()],
            categoryCounts[WarheadMutationCategory.CRATER_CLEANUP.ordinal()],
            categoryCounts[WarheadMutationCategory.SURFACE.ordinal()],
            categoryCounts[WarheadMutationCategory.VEGETATION.ordinal()],
            categoryCounts[WarheadMutationCategory.STRUCTURE.ordinal()],
            categoryCounts[WarheadMutationCategory.DECORATION.ordinal()],
            plan.fireMutations().size(), biomeQuarts,
            categoryCounts[WarheadMutationCategory.OTHER.ordinal()]);
        boolean changed = blocks > 0L || biomeQuarts > 0L || !plan.fireMutations().isEmpty();
        return new PlanStatistics(changed ? 1L : 0L, sections, blocks, biomeQuarts,
            semantic, plan.estimatedCost(), categorized, histogram);
    }

    private static void compileCrater(final PreparedImpactSpec impact,
        final NuclearTerrainProfile profile, final WarheadChunkSnapshot snapshot,
        final WarheadStatePalette palette,
        final Builders builders) {
        int centerX = Mth.floor(impact.target().x);
        int centerY = Mth.floor(impact.target().y);
        int centerZ = Mth.floor(impact.target().z);
        int baseX = snapshot.chunk().getMinBlockX();
        int baseZ = snapshot.chunk().getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                NuclearCraterPolicy.Column column = NuclearCraterPolicy.column(profile,
                    worldX - centerX, worldZ - centerZ);
                if (column == null) continue;
                double dx = worldX + 0.5 - impact.target().x;
                double dz = worldZ + 0.5 - impact.target().z;
                double craterNormalized = Math.sqrt(dx * dx + dz * dz)
                    / Math.max(1.0, profile.horizontalRadius());
                boolean craterFire = impact.customFire()
                    && NuclearFirePolicy.craterCrack(impact, profile, worldX,
                        worldZ, craterNormalized);
                int columnIndex = localZ * 16 + localX;
                int bottomY = Math.max(snapshot.minimumBuildY(), centerY + column.bottomY());
                int topY = Math.min(snapshot.maximumBuildY(), Math.min(centerY + column.topY(),
                    snapshot.motionTopY(columnIndex)));
                if (topY < bottomY) continue;
                for (int y = topY; y >= bottomY; y--) {
                    if (!snapshot.containsCraterY(y)) continue;
                    int flags = snapshot.craterFlags(localX, y, localZ);
                    if ((flags & WarheadSnapshotFlags.AIR) != 0
                        && (flags & WarheadSnapshotFlags.FLUID) == 0) continue;
                    if ((flags & WarheadSnapshotFlags.INDESTRUCTIBLE) != 0) continue;
                    double normalized = NuclearCraterPolicy.normalized(profile, column,
                        y - centerY);
                    if (!NuclearCraterPolicy.acceptsResistance(profile, normalized,
                        snapshot.craterResistance(localX, y, localZ))) continue;
                    int expected = snapshot.craterStateId(localX, y, localZ);
                    int replacement = y == bottomY
                        ? craterFire ? palette.magma()
                            : NuclearCraterPolicy.shellReplacement(profile, impact, palette,
                                worldX, y, worldZ, flags, normalized)
                        : palette.air();
                    boolean semantic = (flags & WarheadSnapshotFlags.SEMANTIC) != 0;
                    builders.add(worldX, y, worldZ, expected, replacement,
                        PreparedMutationPhase.IMMEDIATE_CRATER, semantic, false,
                        y == bottomY ? WarheadMutationCategory.CRATER_SHELL
                            : WarheadMutationCategory.CRATER_EXCAVATION);
                }
                if (craterFire) {
                    long columnHash = impact.seed() ^ ((long)worldX << 32)
                        ^ (worldZ & 0xFFFF_FFFFL) ^ 0x4E55434C45415235L;
                    builders.fire.add(new PreparedFireMutation(worldX, bottomY, worldZ,
                        true, false, true,
                        NuclearFirePolicy.craterIntensity(craterNormalized),
                        columnHash ^ 0x435241434B5F4649L));
                }
            }
        }
    }

    private static void compileSurface(final PreparedImpactSpec impact,
        final WarheadFootprint footprint, final WarheadChunkSnapshot snapshot,
        final WarheadStatePalette palette, final Builders builders) {
        NuclearTerrainProfile profile = footprint.terrainProfile();
        int baseX = snapshot.chunk().getMinBlockX();
        int baseZ = snapshot.chunk().getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                double dx = worldX + 0.5 - impact.target().x;
                double dz = worldZ + 0.5 - impact.target().z;
                double radial = Math.sqrt(dx * dx + dz * dz);
                if (radial > NuclearSurfacePolicy.mutationRadius(
                    footprint.aftermathRadius())) continue;
                NuclearCraterPolicy.Column craterColumn = NuclearCraterPolicy.column(profile,
                    worldX - Mth.floor(impact.target().x),
                    worldZ - Mth.floor(impact.target().z));
                if (craterColumn != null) continue;
                double craterNormalized = radial / Math.max(1.0, footprint.craterRadius());
                double aftermathNormalized = radial / Math.max(1.0, footprint.aftermathRadius());
                if (!NuclearSurfacePolicy.survivesOuterFeather(impact.seed(), worldX,
                    worldZ, aftermathNormalized)) continue;
                int column = localZ * 16 + localX;
                int surfaceY = snapshot.terrainSurfaceY(column);
                if (surfaceY < snapshot.minimumBuildY()) continue;
                long columnHash = impact.seed() ^ ((long)worldX << 32)
                    ^ (worldZ & 0xFFFF_FFFFL) ^ 0x4E55434C45415235L;
                boolean mudPatch = NuclearSurfacePolicy.mudPatch(impact.seed(),
                    worldX, worldZ, aftermathNormalized);
                boolean sulfurPatch = NuclearSurfacePolicy.sulfurPatch(impact.seed(),
                    worldX, worldZ, aftermathNormalized,
                    (snapshot.columnFlags(column) & WarheadSnapshotFlags.WATER_NEAR) != 0);
                boolean craterFire = impact.customFire()
                    && NuclearFirePolicy.craterCrack(impact, profile, worldX,
                        worldZ, craterNormalized);
                int replacementDepth = NuclearSurfacePolicy.replacementDepth(
                    aftermathNormalized);
                for (int depth = 0; depth <= replacementDepth; depth++) {
                    int y = surfaceY - depth;
                    int layer = WarheadChunkSnapshot.SURFACE_LAYER + depth;
                    int flags = snapshot.surfaceFlags(column, layer);
                    if ((flags & WarheadSnapshotFlags.AIR) != 0) continue;
                    int expected = snapshot.surfaceStateId(column, layer);
                    long hash = columnHash ^ BlockPos.asLong(worldX, y, worldZ)
                        ^ depth * 0x9E3779B97F4A7C15L;
                    int replacement = craterFire && depth == 0 ? palette.magma()
                        : NuclearSurfacePolicy.replacement(palette, flags, hash,
                            craterNormalized, aftermathNormalized, depth, mudPatch,
                            sulfurPatch);
                    if (replacement != expected) {
                        builders.add(worldX, y, worldZ, expected, replacement,
                            PreparedMutationPhase.RADIAL_AFTERMATH,
                            (flags & WarheadSnapshotFlags.SEMANTIC) != 0, false,
                            craterFire && depth == 0
                                ? WarheadMutationCategory.CRATER_SHELL
                                : WarheadMutationCategory.SURFACE);
                    }
                }

                if (craterFire) {
                    builders.fire.add(new PreparedFireMutation(worldX, surfaceY, worldZ,
                        true, false, true,
                        NuclearFirePolicy.craterIntensity(craterNormalized),
                        columnHash ^ 0x435241434B5F4649L));
                } else if ((impact.customFire() && NuclearFirePolicy.firePocket(
                    impact.seed(), worldX, worldZ, aftermathNormalized))
                    || (!impact.customFire() && NuclearFirePolicy.legacyFirePocket(
                        impact.seed(), worldX, worldZ, aftermathNormalized))) {
                    builders.fire.add(new PreparedFireMutation(worldX, surfaceY, worldZ,
                        false, false, impact.customFire(),
                        NuclearFirePolicy.pocketIntensity(impact, aftermathNormalized),
                        columnHash ^ 0x464952455F504F53L));
                } else if (aftermathNormalized > 0.30 && aftermathNormalized < 0.96) {
                    compileAshDecoration(impact, snapshot, palette, builders, column,
                        worldX, surfaceY, worldZ, columnHash, aftermathNormalized);
                }
            }
        }
    }

    private static void compileVertical(final PreparedImpactSpec impact,
        final WarheadFootprint footprint, final WarheadChunkSnapshot snapshot,
        final WarheadStatePalette palette, final Builders builders) {
        NuclearTerrainProfile profile = footprint.terrainProfile();
        for (int index = 0; index < snapshot.relevantCount(); index++) {
            long packed = snapshot.relevantPosition(index);
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            boolean craterOwned = NuclearCraterPolicy.ownsCell(profile,
                impact.target(), x, y, z);
            if (craterOwned) continue;
            double dx = x + 0.5 - impact.target().x;
            double dz = z + 0.5 - impact.target().z;
            double radial = Math.sqrt(dx * dx + dz * dz);
            int flags = snapshot.relevantFlags(index);
            int expected = snapshot.relevantStateId(index);
            if (isStructuralCleanupCell(impact, profile, snapshot, x, y, z,
                radial, flags)) {
                boolean forceRemoval = (flags & (WarheadSnapshotFlags.LEAVES
                    | WarheadSnapshotFlags.FRAGILE)) != 0;
                builders.addCleanup(x, y, z, expected, palette.air(),
                    (flags & WarheadSnapshotFlags.SEMANTIC) != 0, !forceRemoval);
                continue;
            }
            int replacement = Integer.MIN_VALUE;
            boolean survival = false;
            if ((flags & WarheadSnapshotFlags.GLASS) != 0) {
                replacement = NuclearStructurePolicy.glass(impact, profile, palette,
                    radial, packed);
            }
            if (radial <= NuclearSurfacePolicy.mutationRadius(
                footprint.aftermathRadius())) {
                double normalized = radial / Math.max(1.0, footprint.aftermathRadius());
                if (NuclearSurfacePolicy.survivesOuterFeather(impact.seed(), x, z,
                    normalized)) {
                    if ((flags & WarheadSnapshotFlags.SNOW) != 0) {
                        replacement = palette.air();
                    } else if ((flags & WarheadSnapshotFlags.FRAGILE) != 0) {
                        NuclearVegetationPolicy.Mutation mutation =
                            NuclearVegetationPolicy.fragile(impact, palette, flags,
                                snapshot.verticalFlagsAtWorld(x, y - 1, z), normalized,
                                packed);
                        if (mutation != null) {
                            replacement = mutation.replacementStateId();
                            survival = mutation.requiresSurvivalCheck();
                        }
                    } else if ((flags & WarheadSnapshotFlags.LEAVES) != 0) {
                        replacement = NuclearVegetationPolicy.leaves(impact, palette,
                            normalized, packed);
                        if (replacement != palette.air() && impact.customFire()) {
                            addTreeFire(impact, builders, x, y, z, normalized, packed, 0.58);
                        }
                    } else if ((flags & WarheadSnapshotFlags.LOG) != 0) {
                        if ((flags & WarheadSnapshotFlags.NATURAL_TREE) != 0) {
                            int column = (z & 15) * 16 + (x & 15);
                            replacement = NuclearVegetationPolicy.naturalLog(impact,
                                palette, flags, y, snapshot.verticalSurfaceY(column),
                                normalized, packed);
                            if (replacement != Integer.MIN_VALUE
                                && replacement != palette.air()) {
                                addTreeFire(impact, builders, x, y, z, normalized, packed, 1.0);
                                compileTreeRemnants(impact, snapshot, palette, builders,
                                    x, y, z, normalized);
                            }
                        } else {
                            replacement = NuclearStructurePolicy.structuralLog(impact,
                                palette, flags, normalized, packed);
                        }
                    } else if ((flags & WarheadSnapshotFlags.PLANK) != 0) {
                        replacement = NuclearStructurePolicy.plank(impact, palette,
                            normalized, packed);
                    } else if ((flags & WarheadSnapshotFlags.COBBLE) != 0) {
                        replacement = NuclearStructurePolicy.cobble(impact, palette,
                            normalized, packed);
                    }
                }
            }
            if (replacement != Integer.MIN_VALUE && replacement != expected) {
                builders.add(x, y, z, expected, replacement,
                    PreparedMutationPhase.RADIAL_AFTERMATH,
                    (flags & WarheadSnapshotFlags.SEMANTIC) != 0, survival,
                    verticalCategory(flags));
            }
        }
    }

    static boolean craterOwnsCell(final NuclearTerrainProfile profile,
        final net.minecraft.world.phys.Vec3 center, final int x, final int y,
        final int z) {
        return NuclearCraterPolicy.ownsCell(profile, center, x, y, z);
    }

    private static void compileBiomes(final PreparedImpactSpec impact,
        final WarheadFootprint footprint, final WarheadChunkSnapshot snapshot,
        final Builders builders) {
        int baseX = snapshot.chunk().getMinBlockX();
        int baseZ = snapshot.chunk().getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                double dx = worldX + 0.5 - impact.target().x;
                double dz = worldZ + 0.5 - impact.target().z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                long hash = impact.seed() ^ ((long)worldX << 32)
                    ^ (worldZ & 0xFFFF_FFFFL) ^ 0x42494F4D455F4544L;
                if (!NuclearBiomePolicy.survivesFeather(distance,
                    footprint.biomeRadius(), NuclearPolicyHash.unit(hash))) continue;
                int verticalHeight = NuclearBiomePolicy.verticalHeight(distance,
                    footprint.biomeRadius(), impact.yield().visualScale());
                if (verticalHeight < 0) continue;
                int surfaceY = snapshot.biomeSurfaceY(localZ * 16 + localX);
                if (surfaceY < snapshot.minimumBuildY()) continue;
                int minimumQuartY = QuartPos.fromBlock(surfaceY + NuclearBiomeDome.BOTTOM_OFFSET);
                int maximumQuartY = QuartPos.fromBlock(surfaceY + verticalHeight);
                int localQuartX = QuartPos.fromBlock(worldX) & 3;
                int localQuartZ = QuartPos.fromBlock(worldZ) & 3;
                for (int quartY = minimumQuartY; quartY <= maximumQuartY; quartY++) {
                    int blockY = QuartPos.toBlock(quartY);
                    if (blockY < snapshot.minimumBuildY() || blockY > snapshot.maximumBuildY()) continue;
                    int sectionY = SectionPos.blockToSectionCoord(blockY);
                    int bit = (quartY & 3) * 16 + localQuartZ * 4 + localQuartX;
                    builders.biomeMasks.put(sectionY,
                        builders.biomeMasks.get(sectionY) | (1L << bit));
                }
            }
        }
    }

    private static void addTreeFire(final PreparedImpactSpec impact,
        final Builders builders, final int x, final int y, final int z,
        final double normalized, final long packed, final double chanceScale) {
        NuclearFirePolicy.TreeFire fire = NuclearFirePolicy.treeFire(impact,
            normalized, packed, chanceScale);
        if (fire == null) return;
        builders.fire.add(new PreparedFireMutation(x, y, z, false, true,
            impact.customFire(), fire.intensity(), fire.seed()));
    }

    private static void compileTreeRemnants(final PreparedImpactSpec impact,
        final WarheadChunkSnapshot snapshot, final WarheadStatePalette palette,
        final Builders builders, final int x, final int y, final int z,
        final double normalized) {
        if (!snapshot.hasPackedBacking()) return;
        BlockPos log = new BlockPos(x, y, z);
        NuclearDecorationPolicy.Remnants remnants =
            NuclearDecorationPolicy.treeRemnants(impact, palette, log, normalized);
        if (remnants.hasFan()) {
            BlockPos fan = log.relative(remnants.fanDirection());
            addAirDecoration(snapshot, palette, builders, fan,
                remnants.fanStateId());
        }
        if (remnants.hangingMoss()) {
            addAirDecoration(snapshot, palette, builders, log.below(),
                palette.decoration().paleHangingMoss());
        }
    }

    private static boolean isStructuralCleanupCell(final PreparedImpactSpec impact,
        final NuclearTerrainProfile profile, final WarheadChunkSnapshot snapshot,
        final int x, final int y, final int z, final double radial,
        final int flags) {
        if (radial > profile.horizontalRadius() * 1.08
            || (flags & (WarheadSnapshotFlags.LEAVES | WarheadSnapshotFlags.FRAGILE
                | WarheadSnapshotFlags.SURVIVAL_SENSITIVE)) == 0) return false;
        int column = (z & 15) * 16 + (x & 15);
        int craterFloor = snapshot.terrainSurfaceY(column);
        int centerY = Mth.floor(impact.target().y);
        int start = Math.max(snapshot.minimumBuildY(),
            Math.min(craterFloor + 1, centerY - 10));
        int end = Math.min(snapshot.maximumBuildY(),
            Math.max(craterFloor + 56,
                Mth.floor(impact.target().y + profile.upwardRadius() + 36.0)));
        return y >= start && y <= end;
    }

    private static void addAirDecoration(final WarheadChunkSnapshot snapshot,
        final WarheadStatePalette palette, final Builders builders,
        final BlockPos position, final int replacement) {
        if (!builders.owns(position.getX(), position.getZ())
            || position.getY() < snapshot.minimumBuildY()
            || position.getY() > snapshot.maximumBuildY()) return;
        int flags = snapshot.verticalFlagsAtWorld(position.getX(), position.getY(),
            position.getZ());
        if ((flags & WarheadSnapshotFlags.AIR) == 0) return;
        int expected = snapshot.verticalStateIdAtWorld(position.getX(), position.getY(),
            position.getZ());
        builders.add(position.getX(), position.getY(), position.getZ(), expected,
            replacement, PreparedMutationPhase.RADIAL_AFTERMATH, false, true,
            WarheadMutationCategory.DECORATION);
    }

    private static void compileAshDecoration(final PreparedImpactSpec impact,
        final WarheadChunkSnapshot snapshot, final WarheadStatePalette palette,
        final Builders builders, final int column, final int x, final int surfaceY,
        final int z, final long hash, final double normalized) {
        if ((snapshot.surfaceFlags(column, WarheadChunkSnapshot.ABOVE_LAYER)
            & WarheadSnapshotFlags.AIR) == 0) return;
        int state = NuclearDecorationPolicy.ash(palette, hash, normalized);
        if (state == NuclearSurfacePolicy.NO_CHANGE) return;
        builders.add(x, surfaceY + 1, z,
            snapshot.surfaceStateId(column, WarheadChunkSnapshot.ABOVE_LAYER), state,
            PreparedMutationPhase.RADIAL_AFTERMATH, false, true,
            WarheadMutationCategory.DECORATION);
    }

    static int radialActivationTick(final net.minecraft.world.phys.Vec3 center,
        final double maximumRadius, final ChunkPos chunk) {
        double minimumX = chunk.getMinBlockX();
        double maximumX = chunk.getMaxBlockX() + 1.0;
        double minimumZ = chunk.getMinBlockZ();
        double maximumZ = chunk.getMaxBlockZ() + 1.0;
        double nearestX = Mth.clamp(center.x, minimumX, maximumX);
        double nearestZ = Mth.clamp(center.z, minimumZ, maximumZ);
        double distance = Math.sqrt((nearestX - center.x) * (nearestX - center.x)
            + (nearestZ - center.z) * (nearestZ - center.z));
        return Mth.clamp(Mth.ceil(distance / Math.max(1.0, maximumRadius) * 15.0), 0, 15);
    }

    private static long phaseKey(final int sectionY, final PreparedMutationPhase phase) {
        return ((long)sectionY << 1) | (phase == PreparedMutationPhase.RADIAL_AFTERMATH ? 1L : 0L);
    }

    private static WarheadMutationCategory verticalCategory(final int flags) {
        if ((flags & (WarheadSnapshotFlags.GLASS | WarheadSnapshotFlags.PLANK
            | WarheadSnapshotFlags.COBBLE)) != 0) {
            return WarheadMutationCategory.STRUCTURE;
        }
        if ((flags & WarheadSnapshotFlags.LOG) != 0
            && (flags & WarheadSnapshotFlags.NATURAL_TREE) == 0) {
            return WarheadMutationCategory.STRUCTURE;
        }
        return WarheadMutationCategory.VEGETATION;
    }

    private static final class Builders {
        private final WarheadChunkSnapshot snapshot;
        private final Long2ObjectOpenHashMap<SectionBuilder> sections =
            new Long2ObjectOpenHashMap<>();
        private final Int2LongOpenHashMap biomeMasks = new Int2LongOpenHashMap();
        private final ArrayList<PreparedFireMutation> fire = new ArrayList<>();
        private final BitSet changedColumns = new BitSet(256);

        private Builders(final WarheadChunkSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private boolean owns(final int x, final int z) {
            return Math.floorDiv(x, 16) == snapshot.chunk().x()
                && Math.floorDiv(z, 16) == snapshot.chunk().z();
        }

        private void add(final int x, final int y, final int z, final int expected,
            final int replacement, final PreparedMutationPhase phase,
            final boolean semantic, final boolean survival,
            final WarheadMutationCategory category) {
            add(x, y, z, expected, replacement, phase, semantic, survival,
                false, category);
        }

        private void addCleanup(final int x, final int y, final int z,
            final int expected, final int replacement, final boolean semantic,
            final boolean supportCheck) {
            add(x, y, z, expected, replacement,
                PreparedMutationPhase.IMMEDIATE_CRATER, semantic, false,
                supportCheck, WarheadMutationCategory.CRATER_CLEANUP);
        }

        private void add(final int x, final int y, final int z, final int expected,
            final int replacement, final PreparedMutationPhase phase,
            final boolean semantic, final boolean survival,
            final boolean supportCheck, final WarheadMutationCategory category) {
            if (replacement == Integer.MIN_VALUE || expected == replacement) return;
            if (!owns(x, z)) {
                throw new IllegalArgumentException("Prepared mutation escaped chunk ownership");
            }
            int sectionY = SectionPos.blockToSectionCoord(y);
            long key = phaseKey(sectionY, phase);
            SectionBuilder builder = sections.computeIfAbsent(key,
                ignored -> new SectionBuilder(sectionY,
                    snapshot.sectionRevision(sectionY), phase));
            int localIndex = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
            if (builder.add(localIndex, expected, replacement, semantic, survival,
                supportCheck, category)) {
                changedColumns.set((z & 15) * 16 + (x & 15));
            }
        }

        private PreparedChunkPlan finish(final ChunkPos chunk, final long sourceRevision,
            final int activationTick) {
            ArrayList<PreparedSectionPlan> blockSections = new ArrayList<>(sections.size());
            for (SectionBuilder section : sections.values()) blockSections.add(section.finish());
            blockSections.sort(Comparator.comparing(PreparedSectionPlan::phase)
                .thenComparingInt(PreparedSectionPlan::sectionY));
            ArrayList<PreparedBiomeSectionPlan> biomes = new ArrayList<>(biomeMasks.size());
            for (var entry : biomeMasks.int2LongEntrySet()) {
                if (entry.getLongValue() != 0L) {
                    biomes.add(new PreparedBiomeSectionPlan(entry.getIntKey(), entry.getLongValue()));
                }
            }
            biomes.sort(Comparator.comparingInt(PreparedBiomeSectionPlan::sectionY));
            int[] columns = changedColumns.stream().toArray();
            long mutations = blockSections.stream().mapToLong(PreparedSectionPlan::mutationCount).sum();
            long bytes = mutations * 13L + columns.length * 4L + fire.size() * 40L
                + biomes.size() * 16L;
            return new PreparedChunkPlan(chunk, sourceRevision, activationTick,
                blockSections, biomes, List.copyOf(fire), columns, bytes);
        }
    }

    private static final class SectionBuilder {
        private final int sectionY;
        private final long sourceRevision;
        private final PreparedMutationPhase phase;
        private final IntArrayList indices = new IntArrayList();
        private final IntArrayList expected = new IntArrayList();
        private final IntArrayList replacements = new IntArrayList();
        private final it.unimi.dsi.fastutil.bytes.ByteArrayList categories =
            new it.unimi.dsi.fastutil.bytes.ByteArrayList();
        private final BitSet used = new BitSet(4096);
        private final BitSet semantic = new BitSet();
        private final BitSet survival = new BitSet();
        private final BitSet supportCheck = new BitSet();

        private SectionBuilder(final int sectionY, final long sourceRevision,
            final PreparedMutationPhase phase) {
            this.sectionY = sectionY;
            this.sourceRevision = sourceRevision;
            this.phase = phase;
        }

        private boolean add(final int localIndex, final int expectedState,
            final int replacement, final boolean semanticMutation,
            final boolean survivalMutation, final boolean supportCheckMutation,
            final WarheadMutationCategory category) {
            if (used.get(localIndex)) return false;
            used.set(localIndex);
            int planIndex = indices.size();
            indices.add(localIndex);
            expected.add(expectedState);
            replacements.add(replacement);
            categories.add(category.wireId());
            if (semanticMutation) semantic.set(planIndex);
            if (survivalMutation) survival.set(planIndex);
            if (supportCheckMutation) supportCheck.set(planIndex);
            return true;
        }

        private PreparedSectionPlan finish() {
            return new PreparedSectionPlan(sectionY, sourceRevision, phase,
                indices.toIntArray(), expected.toIntArray(), replacements.toIntArray(),
                categories.toByteArray(), semantic, survival, supportCheck);
        }
    }
}
