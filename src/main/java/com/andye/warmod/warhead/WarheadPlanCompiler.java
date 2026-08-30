package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
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
        compileCrater(impact, snapshot, palette, builders);
        compileSurface(impact, footprint, snapshot, palette, builders);
        compileVertical(impact, footprint, snapshot, palette, builders);
        compileBiomes(impact, footprint, snapshot, builders);
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
        boolean changed = blocks > 0L || biomeQuarts > 0L || !plan.fireMutations().isEmpty();
        return new PlanStatistics(changed ? 1L : 0L, sections, blocks, biomeQuarts,
            semantic, plan.estimatedCost());
    }

    private static void compileCrater(final PreparedImpactSpec impact,
        final WarheadChunkSnapshot snapshot, final WarheadStatePalette palette,
        final Builders builders) {
        StrategicExplosionProfile profile = StrategicExplosionProfiles.get(impact.yield());
        int centerX = Mth.floor(impact.target().x);
        int centerY = Mth.floor(impact.target().y);
        int centerZ = Mth.floor(impact.target().z);
        int baseX = snapshot.chunk().getMinBlockX();
        int baseZ = snapshot.chunk().getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                Column column = craterColumn(profile, worldX - centerX, worldZ - centerZ);
                if (column == null) continue;
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
                    int yOffset = y - centerY;
                    double verticalRadius = yOffset < 0
                        ? profile.downwardRadius() : profile.upwardRadius();
                    double vertical = Math.abs(yOffset) / Math.max(1.0, verticalRadius);
                    double normalized = Math.sqrt(Math.min(1.0,
                        column.radial() * column.radial() + vertical * vertical));
                    if (normalized > profile.guaranteedVoidScale()) {
                        float threshold = profile.maximumDestroyResistance()
                            * (float)Math.max(0.08,
                                1.0 - normalized * profile.edgeResistanceScale());
                        if (snapshot.craterResistance(localX, y, localZ) > threshold) continue;
                    }
                    int expected = snapshot.craterStateId(localX, y, localZ);
                    int replacement = y == bottomY
                        ? craterShell(profile, impact, palette, worldX, y, worldZ,
                            flags, normalized)
                        : palette.air();
                    boolean semantic = (flags & WarheadSnapshotFlags.SEMANTIC) != 0;
                    builders.add(worldX, y, worldZ, expected, replacement,
                        PreparedMutationPhase.IMMEDIATE_CRATER, semantic, false);
                }
                if (impact.customFire()
                    && NuclearCrackField.contains(impact.seed(), impact.target().x,
                        impact.target().z, worldX + 0.5, worldZ + 0.5,
                        profile.horizontalRadius() * 0.94)) {
                    float intensity = (float)Mth.clamp(0.62
                        + (1.0 - Math.min(1.0, column.radial())) * 0.36, 0.10, 1.0);
                    builders.fire.add(new PreparedFireMutation(worldX, bottomY, worldZ,
                        true, false, true, intensity,
                        impact.seed() ^ BlockPos.asLong(worldX, bottomY, worldZ)
                            ^ 0x435241434B5F4649L));
                }
            }
        }
    }

    private static void compileSurface(final PreparedImpactSpec impact,
        final WarheadFootprint footprint, final WarheadChunkSnapshot snapshot,
        final WarheadStatePalette palette, final Builders builders) {
        StrategicExplosionProfile profile = StrategicExplosionProfiles.get(impact.yield());
        int baseX = snapshot.chunk().getMinBlockX();
        int baseZ = snapshot.chunk().getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                double dx = worldX + 0.5 - impact.target().x;
                double dz = worldZ + 0.5 - impact.target().z;
                double radial = Math.sqrt(dx * dx + dz * dz);
                if (radial > footprint.aftermathRadius()
                    || radial <= profile.horizontalRadius() * 1.04) continue;
                double craterNormalized = radial / Math.max(1.0, footprint.craterRadius());
                double aftermathNormalized = radial / Math.max(1.0, footprint.aftermathRadius());
                int column = localZ * 16 + localX;
                int surfaceY = snapshot.terrainSurfaceY(column);
                if (surfaceY < snapshot.minimumBuildY()) continue;
                long columnHash = impact.seed() ^ ((long)worldX << 32)
                    ^ (worldZ & 0xFFFF_FFFFL) ^ 0x4E55434C45415235L;
                boolean mudPatch = aftermathNormalized > 0.22 && aftermathNormalized < 0.92
                    && clusteredPatch(impact.seed(), worldX, worldZ, 11, 0.105,
                        1.7, 3.2, 0x4D55445F50415443L);
                boolean sulfurPatch = aftermathNormalized < 0.88
                    && clusteredPatch(impact.seed(), worldX, worldZ, 9, 0.36,
                        2.5, 5.2, 0x53554C4655525041L)
                    && (snapshot.columnFlags(column) & WarheadSnapshotFlags.WATER_NEAR) != 0;
                int replacementDepth = aftermathNormalized < 0.45 ? 3
                    : aftermathNormalized < 0.78 ? 2 : 1;
                for (int depth = 0; depth <= replacementDepth; depth++) {
                    int y = surfaceY - depth;
                    int layer = WarheadChunkSnapshot.SURFACE_LAYER + depth;
                    int flags = snapshot.surfaceFlags(column, layer);
                    if ((flags & WarheadSnapshotFlags.AIR) != 0) continue;
                    int expected = snapshot.surfaceStateId(column, layer);
                    long hash = columnHash ^ BlockPos.asLong(worldX, y, worldZ)
                        ^ depth * 0x9E3779B97F4A7C15L;
                    int replacement = surfaceReplacement(palette, flags, hash,
                        craterNormalized, aftermathNormalized, depth, mudPatch, sulfurPatch);
                    if (replacement != expected) {
                        builders.add(worldX, y, worldZ, expected, replacement,
                            PreparedMutationPhase.RADIAL_AFTERMATH,
                            (flags & WarheadSnapshotFlags.SEMANTIC) != 0, false);
                    }
                }

                if ((impact.customFire() && firePocket(impact.seed(), worldX, worldZ,
                    aftermathNormalized)) || (!impact.customFire()
                        && legacyFirePocket(impact.seed(), worldX, worldZ,
                            aftermathNormalized))) {
                    double heat = Mth.clamp((0.96 - aftermathNormalized) / 0.76, 0.0, 1.0);
                    float intensity = impact.customFire()
                        ? (float)Mth.clamp(0.30 + heat * 0.58
                            + impact.yield().visualScale() * 0.025, 0.10, 1.0)
                        : Mth.clamp(0.72F + impact.yield().visualScale() * 0.07F,
                            0.10F, 1.0F);
                    builders.fire.add(new PreparedFireMutation(worldX, surfaceY, worldZ,
                        false, false, impact.customFire(), intensity,
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
        StrategicExplosionProfile profile = StrategicExplosionProfiles.get(impact.yield());
        for (int index = 0; index < snapshot.relevantCount(); index++) {
            long packed = snapshot.relevantPosition(index);
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            if (craterOwnsCell(profile, impact.target(), x, y, z)) continue;
            double dx = x + 0.5 - impact.target().x;
            double dz = z + 0.5 - impact.target().z;
            double radial = Math.sqrt(dx * dx + dz * dz);
            int flags = snapshot.relevantFlags(index);
            int expected = snapshot.relevantStateId(index);
            int replacement = expected;
            boolean survival = false;
            if ((flags & WarheadSnapshotFlags.GLASS) != 0
                && radial <= footprint.glassRadius()) {
                double normalized = radial / Math.max(1.0, footprint.glassRadius());
                double chance = normalized <= 0.72 ? 1.0
                    : Mth.clamp((1.0 - normalized) / 0.28, 0.0, 1.0);
                if (unit(impact.seed() ^ packed ^ 0x474C4153535F4E55L) < chance) {
                    replacement = palette.air();
                }
            }
            if (radial <= footprint.aftermathRadius()) {
                double normalized = radial / Math.max(1.0, footprint.aftermathRadius());
                long hash = impact.seed() ^ packed;
                if ((flags & WarheadSnapshotFlags.SNOW) != 0) {
                    replacement = palette.air();
                } else if ((flags & WarheadSnapshotFlags.FRAGILE) != 0) {
                    double chance = normalized <= 0.78 ? 1.0
                        : Mth.clamp((1.0 - normalized) / 0.22, 0.0, 1.0);
                    if (unit(hash ^ 0x46524147494C455FL) < chance) {
                        double selector = unit(hash ^ 0x4452595F504C414EL);
                        replacement = selector < 0.18 ? palette.tallDryGrass()
                            : selector < 0.38 ? palette.shortDryGrass()
                            : selector < 0.52 ? palette.deadBush() : palette.air();
                        survival = replacement != palette.air();
                    }
                } else if ((flags & WarheadSnapshotFlags.LEAVES) != 0) {
                    replacement = leafReplacement(impact, palette, normalized, hash);
                    if (replacement != palette.air() && impact.customFire()) {
                        addTreeFire(impact, builders, x, y, z, normalized, packed, 0.58);
                    }
                } else if ((flags & WarheadSnapshotFlags.LOG) != 0) {
                    if ((flags & WarheadSnapshotFlags.NATURAL_TREE) != 0) {
                        if (normalized <= 0.34) replacement = palette.air();
                        else if (normalized <= 0.62
                            || unit(hash ^ 0x4C4F47535F415348L)
                                < Mth.clamp((0.94 - normalized) / 0.38, 0.0, 1.0) * 0.72) {
                            replacement = palette.paleLog(flags);
                            addTreeFire(impact, builders, x, y, z, normalized, packed, 1.0);
                        }
                    } else {
                        double chance = normalized <= 0.64 ? 1.0
                            : Mth.clamp((0.90 - normalized) / 0.26, 0.0, 1.0);
                        if (unit(hash ^ 0x5354525543544C47L) < chance) {
                            replacement = palette.paleLog(flags);
                        }
                    }
                } else if ((flags & WarheadSnapshotFlags.PLANK) != 0) {
                    double chance = normalized <= 0.58 ? 1.0
                        : Mth.clamp((0.84 - normalized) / 0.26, 0.0, 1.0);
                    if (unit(hash ^ 0x504C414E4B5F4153L) < chance) replacement = palette.paleWood();
                } else if ((flags & WarheadSnapshotFlags.COBBLE) != 0) {
                    double chance = normalized <= 0.50 ? 1.0
                        : Mth.clamp((0.76 - normalized) / 0.26, 0.0, 1.0);
                    if (unit(hash ^ 0x434F42424C455F44L) < chance) {
                        replacement = palette.cobbledDeepslate();
                    }
                }
            }
            if (replacement != expected) {
                builders.add(x, y, z, expected, replacement,
                    PreparedMutationPhase.RADIAL_AFTERMATH,
                    (flags & WarheadSnapshotFlags.SEMANTIC) != 0, survival);
            }
        }
    }

    static boolean craterOwnsCell(final StrategicExplosionProfile profile,
        final net.minecraft.world.phys.Vec3 center, final int x, final int y,
        final int z) {
        int centerX = Mth.floor(center.x);
        int centerY = Mth.floor(center.y);
        int centerZ = Mth.floor(center.z);
        Column column = craterColumn(profile, x - centerX, z - centerZ);
        return column != null && y >= centerY + column.bottomY()
            && y <= centerY + column.topY();
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
                if (!NuclearBiomeDome.survivesFeather(distance,
                    footprint.biomeRadius(), unit(hash))) continue;
                int verticalHeight = NuclearBiomeDome.verticalHeight(distance,
                    footprint.biomeRadius(), impact.yield().visualScale());
                if (verticalHeight < 0) continue;
                int surfaceY = snapshot.terrainSurfaceY(localZ * 16 + localX);
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

    private static int craterShell(final StrategicExplosionProfile profile,
        final PreparedImpactSpec impact, final WarheadStatePalette palette,
        final int x, final int y, final int z, final int flags, final double normalized) {
        long packed = BlockPos.asLong(x, y, z);
        long hash = mix(impact.seed() ^ packed ^ 0x4352415445525F53L);
        double selector = unit(hash);
        if (normalized <= 0.94 && NuclearCrackField.contains(impact.seed(),
            impact.target().x, impact.target().z, x + 0.5, z + 0.5,
            profile.horizontalRadius() * 0.94)) return palette.magma();
        if ((flags & WarheadSnapshotFlags.SAND) != 0) {
            if (selector < 0.20) return palette.tintedGlass();
            if (selector < 0.38) return palette.blackGlass();
            if (selector < 0.56) return palette.grayGlass();
            if (selector < 0.78) return palette.whiteTerracotta();
            return palette.sandstone();
        }
        if ((flags & WarheadSnapshotFlags.RED_SAND) != 0) {
            if (selector < 0.28) return palette.blackGlass();
            if (selector < 0.52) return palette.grayGlass();
            if (selector < 0.78) return palette.terracotta();
            return palette.redSandstone();
        }
        if (selector < 0.22) return palette.basalt();
        if (selector < 0.40) return palette.blackstone();
        if (selector < 0.64) return palette.deepslate();
        if (selector < 0.84) return palette.cobbledDeepslate();
        return palette.tuff();
    }

    private static int surfaceReplacement(final WarheadStatePalette palette,
        final int flags, final long hash, final double craterNormalized,
        final double aftermathNormalized, final int depth,
        final boolean mudPatch, final boolean sulfurPatch) {
        if (depth == 0 && sulfurPatch
            && (flags & WarheadSnapshotFlags.NATURAL_SURFACE) != 0) {
            return unit(hash ^ 0x504F54454E545F53L) < 0.085
                ? palette.potentSulfur() : palette.sulfur();
        }
        if (depth == 0 && mudPatch && (flags & WarheadSnapshotFlags.SOIL) != 0) {
            return palette.mud();
        }
        if ((flags & WarheadSnapshotFlags.SNOW) != 0) return palette.air();
        if ((flags & WarheadSnapshotFlags.SAND) != 0) {
            return craterNormalized <= 1.65
                ? fusedSand(palette, hash, false, craterNormalized)
                : outerFusedSand(palette, hash, false);
        }
        if ((flags & WarheadSnapshotFlags.RED_SAND) != 0) {
            return craterNormalized <= 1.65
                ? fusedSand(palette, hash, true, craterNormalized)
                : outerFusedSand(palette, hash, true);
        }
        if ((flags & WarheadSnapshotFlags.SOIL) != 0 && craterNormalized <= 1.70) {
            return scorchedSoil(palette, hash, craterNormalized);
        }
        double edgeFalloff = Math.pow(Math.max(0.0, 1.0 - aftermathNormalized), 0.65);
        double outerChance = aftermathNormalized <= 0.78 ? 1.0 : 0.18 + edgeFalloff * 0.82;
        if ((flags & WarheadSnapshotFlags.SOIL) != 0
            && unit(hash ^ 0x4F555445525F4153L) < outerChance) {
            return outerScorchedSoil(palette, hash);
        }
        if ((flags & WarheadSnapshotFlags.COMMON_ROCK) != 0
            && (depth == 0 || (flags & WarheadSnapshotFlags.EXPOSED) != 0)) {
            double chance = craterNormalized <= 1.38 ? 1.0
                : Mth.clamp((0.58 - aftermathNormalized) / 0.34, 0.0, 0.82);
            if (unit(hash ^ 0x524F434B5F534341L) < chance) {
                return darkCraterRock(palette, hash, craterNormalized);
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int fusedSand(final WarheadStatePalette p, final long hash,
        final boolean red, final double normalized) {
        double selector = unit(hash ^ 0x46555345445F534EL);
        double heat = Mth.clamp(1.25 - normalized, 0.0, 1.0);
        if (!red) {
            if (selector < 0.10 + heat * 0.10) return p.tintedGlass();
            if (selector < 0.22 + heat * 0.14) return p.blackGlass();
            if (selector < 0.34 + heat * 0.12) return p.grayGlass();
            if (selector < 0.46 + heat * 0.10) return p.lightGrayGlass();
            if (selector < 0.68) return p.whiteTerracotta();
            if (selector < 0.84) return p.tuff();
            return p.sandstone();
        }
        if (selector < 0.20 + heat * 0.16) return p.blackGlass();
        if (selector < 0.34 + heat * 0.12) return p.grayGlass();
        if (selector < 0.72) return p.terracotta();
        if (selector < 0.88) return p.redSandstone();
        return p.gravel();
    }

    private static int outerFusedSand(final WarheadStatePalette p,
        final long hash, final boolean red) {
        double selector = unit(hash ^ 0x4F5554455253414EL);
        if (red) {
            if (selector < 0.62) return p.terracotta();
            if (selector < 0.84) return p.redSandstone();
            return p.gravel();
        }
        if (selector < 0.48) return p.whiteTerracotta();
        if (selector < 0.72) return p.sandstone();
        if (selector < 0.90) return p.gravel();
        return p.lightGrayGlass();
    }

    private static int scorchedSoil(final WarheadStatePalette p, final long hash,
        final double normalized) {
        double selector = unit(hash ^ 0x53434F524348534FL);
        if (normalized < 0.82) {
            if (selector < 0.28) return p.tuff();
            if (selector < 0.50) return p.coarseDirt();
            if (selector < 0.68) return p.paleMoss();
            if (selector < 0.84) return p.rootedDirt();
            return p.podzol();
        }
        if (selector < 0.30) return p.podzol();
        if (selector < 0.56) return p.coarseDirt();
        if (selector < 0.72) return p.paleMoss();
        if (selector < 0.88) return p.tuff();
        return p.rootedDirt();
    }

    private static int outerScorchedSoil(final WarheadStatePalette p, final long hash) {
        double selector = unit(hash ^ 0x4F55544552534F49L);
        if (selector < 0.28) return p.coarseDirt();
        if (selector < 0.47) return p.podzol();
        if (selector < 0.61) return p.mycelium();
        if (selector < 0.75) return p.paleMoss();
        if (selector < 0.88) return p.tuff();
        return p.rootedDirt();
    }

    private static int darkCraterRock(final WarheadStatePalette p, final long hash,
        final double normalized) {
        double selector = unit(hash ^ 0x4441524B5F524F43L);
        if (normalized < 0.58) {
            if (selector < 0.28) return p.basalt();
            if (selector < 0.48) return p.blackstone();
            if (selector < 0.72) return p.deepslate();
            return p.cobbledDeepslate();
        }
        if (selector < 0.26) return p.cobbledDeepslate();
        if (selector < 0.50) return p.deepslate();
        if (selector < 0.72) return p.tuff();
        if (selector < 0.88) return p.basalt();
        return p.blackstone();
    }

    private static int leafReplacement(final PreparedImpactSpec impact,
        final WarheadStatePalette palette, final double normalized, final long hash) {
        if (normalized <= 0.70) {
            double retention = impact.customFire() && normalized > 0.30
                ? 0.48 + Mth.clamp((normalized - 0.30) / 0.40, 0.0, 1.0) * 0.24 : 0.0;
            return unit(hash ^ 0x43524F574E5F4649L) < retention
                ? palette.paleLeaves() : palette.air();
        }
        double outer = Mth.clamp((1.0 - normalized) / 0.30, 0.0, 1.0);
        double strip = outer * 0.72;
        double pale = 0.10 + outer * 0.64;
        double selector = unit(hash ^ 0x4C45415645535F4EL);
        if (selector < strip) return palette.air();
        if (selector < strip + pale) return palette.paleLeaves();
        return Integer.MIN_VALUE;
    }

    private static void addTreeFire(final PreparedImpactSpec impact,
        final Builders builders, final int x, final int y, final int z,
        final double normalized, final long packed, final double chanceScale) {
        double heat = Mth.clamp((0.94 - normalized) / 0.60, 0.0, 1.0);
        double chance = impact.customFire()
            ? (0.08 + 0.58 * heat * heat) * chanceScale
            : 0.22 * Mth.clamp((0.82 - normalized) / 0.48, 0.0, 1.0);
        long seed = impact.seed() ^ packed ^ 0x545245455F464952L;
        if (unit(seed) >= chance) return;
        builders.fire.add(new PreparedFireMutation(x, y, z, false, true,
            impact.customFire(), (float)Mth.clamp(0.35 + heat * 0.65, 0.10, 1.0), seed));
    }

    private static void compileAshDecoration(final PreparedImpactSpec impact,
        final WarheadChunkSnapshot snapshot, final WarheadStatePalette palette,
        final Builders builders, final int column, final int x, final int surfaceY,
        final int z, final long hash, final double normalized) {
        if ((snapshot.surfaceFlags(column, WarheadChunkSnapshot.ABOVE_LAYER)
            & WarheadSnapshotFlags.AIR) == 0) return;
        double fade = Mth.clamp((1.0 - normalized) / 0.70, 0.0, 1.0);
        double selector = unit(hash ^ 0x4153485F4445434FL);
        if (selector >= 0.012 + fade * 0.046) return;
        double kind = unit(hash ^ 0x4153485F4B494E44L);
        int state = kind < 0.33 ? palette.deadBush()
            : kind < 0.66 ? palette.shortDryGrass() : palette.tallDryGrass();
        builders.add(x, surfaceY + 1, z,
            snapshot.surfaceStateId(column, WarheadChunkSnapshot.ABOVE_LAYER), state,
            PreparedMutationPhase.RADIAL_AFTERMATH, false, true);
    }

    private static boolean firePocket(final long seed, final int x, final int z,
        final double normalized) {
        if (normalized >= 0.92) return false;
        return clusteredPatch(seed, x, z, 12,
            0.045 + 0.145 * Mth.clamp((0.96 - normalized) / 0.76, 0.0, 1.0),
            2.0, 3.8, 0x46495245504F434BL);
    }

    private static boolean legacyFirePocket(final long seed, final int x, final int z,
        final double normalized) {
        if (normalized >= 0.86) return false;
        return clusteredPatch(seed, x, z, 14,
            0.11 * Mth.clamp(1.05 - normalized, 0.22, 1.0),
            1.8, 3.3, 0x46495245504F434BL);
    }

    private static boolean clusteredPatch(final long seed, final int x, final int z,
        final int cellSize, final double selectionChance, final double minimumRadius,
        final double maximumRadius, final long salt) {
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        long hash = seed ^ ((long)cellX << 32) ^ (cellZ & 0xFFFF_FFFFL) ^ salt;
        if (unit(hash) >= selectionChance) return false;
        double centerX = cellX * cellSize + 1.0
            + unit(hash ^ 0x58434C5553544552L) * (cellSize - 2.0);
        double centerZ = cellZ * cellSize + 1.0
            + unit(hash ^ 0x5A434C5553544552L) * (cellSize - 2.0);
        double dx = x + 0.5 - centerX;
        double dz = z + 0.5 - centerZ;
        double radius = minimumRadius + unit(hash ^ 0x52434C5553544552L)
            * (maximumRadius - minimumRadius);
        return dx * dx + dz * dz <= radius * radius;
    }

    private static Column craterColumn(final StrategicExplosionProfile profile,
        final int x, final int z) {
        double radial = Math.sqrt((x * (double)x + z * (double)z)
            / (profile.horizontalRadius() * profile.horizontalRadius()));
        if (radial > 1.0) return null;
        double angle = Math.atan2(z, x);
        double broadNoise = Math.sin(angle * 5.0 + profile.yield().ordinal() * 1.7) * 0.48
            + Math.sin(angle * 11.0 - radial * 8.0) * 0.22
            + (unit(((long)x << 32) ^ (z & 0xFFFF_FFFFL)
                ^ profile.yield().ordinal()) - 0.5) * 0.30;
        double adjusted = radial / (1.0 + broadNoise * profile.boundaryRoughness());
        if (adjusted > 1.0) return null;
        double verticalFactor = Math.sqrt(Math.max(0.0, 1.0 - adjusted * adjusted));
        int bottom = -Math.max(1, (int)Math.floor(profile.downwardRadius() * verticalFactor));
        int top = Math.max(1, (int)Math.floor(profile.upwardRadius() * verticalFactor));
        return new Column(bottom, top, adjusted);
    }

    private static int radialActivationTick(final net.minecraft.world.phys.Vec3 center,
        final double maximumRadius, final ChunkPos chunk) {
        double minimumX = chunk.getMinBlockX();
        double maximumX = chunk.getMaxBlockX() + 1.0;
        double minimumZ = chunk.getMinBlockZ();
        double maximumZ = chunk.getMaxBlockZ() + 1.0;
        double nearestX = Mth.clamp(center.x, minimumX, maximumX);
        double nearestZ = Mth.clamp(center.z, minimumZ, maximumZ);
        double distance = Math.sqrt((nearestX - center.x) * (nearestX - center.x)
            + (nearestZ - center.z) * (nearestZ - center.z));
        return Mth.clamp(Mth.ceil(distance / Math.max(1.0, maximumRadius) * 19.0), 0, 19);
    }

    private static long phaseKey(final int sectionY, final PreparedMutationPhase phase) {
        return ((long)sectionY << 1) | (phase == PreparedMutationPhase.RADIAL_AFTERMATH ? 1L : 0L);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(final long value) {
        return (mix(value) >>> 11) * 0x1.0p-53;
    }

    private record Column(int bottomY, int topY, double radial) { }

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

        private void add(final int x, final int y, final int z, final int expected,
            final int replacement, final PreparedMutationPhase phase,
            final boolean semantic, final boolean survival) {
            if (replacement == Integer.MIN_VALUE || expected == replacement) return;
            int sectionY = SectionPos.blockToSectionCoord(y);
            long key = phaseKey(sectionY, phase);
            SectionBuilder builder = sections.computeIfAbsent(key,
                ignored -> new SectionBuilder(sectionY,
                    snapshot.sectionRevision(sectionY), phase));
            int localIndex = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
            if (builder.add(localIndex, expected, replacement, semantic, survival)) {
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
            long bytes = mutations * 12L + columns.length * 4L + fire.size() * 40L
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
        private final BitSet used = new BitSet(4096);
        private final BitSet semantic = new BitSet();
        private final BitSet survival = new BitSet();

        private SectionBuilder(final int sectionY, final long sourceRevision,
            final PreparedMutationPhase phase) {
            this.sectionY = sectionY;
            this.sourceRevision = sourceRevision;
            this.phase = phase;
        }

        private boolean add(final int localIndex, final int expectedState,
            final int replacement, final boolean semanticMutation,
            final boolean survivalMutation) {
            if (used.get(localIndex)) return false;
            used.set(localIndex);
            int planIndex = indices.size();
            indices.add(localIndex);
            expected.add(expectedState);
            replacements.add(replacement);
            if (semanticMutation) semantic.set(planIndex);
            if (survivalMutation) survival.set(planIndex);
            return true;
        }

        private PreparedSectionPlan finish() {
            return new PreparedSectionPlan(sectionY, sourceRevision, phase,
                indices.toIntArray(), expected.toIntArray(), replacements.toIntArray(),
                semantic, survival);
        }
    }
}
