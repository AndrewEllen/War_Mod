package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;

/**
 * Main-thread bulk copier. Classification, adjacency and geometry run later on
 * workers from compact palette data; no world reference escapes in the result.
 */
final class WarheadWorldSnapshotter {
    private static final int SURFACE_SUPPORT_DESCENT = 8;
    private static final int VERTICAL_SCAN_ABOVE_SURFACE = 64;
    private static final int HALO_BELOW_SURFACE_TOP = SURFACE_SUPPORT_DESCENT
        + WarheadChunkSnapshot.SURFACE_LAYERS - 1;
    private static final int HALO_ABOVE_SURFACE_TOP = 2;
    private static final Strategy<BlockState> BLOCK_STRATEGY =
        Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);

    private WarheadWorldSnapshotter() { }

    static WarheadChunkSnapshot capture(final ServerLevel level, final ChunkPos position,
        final List<WarheadSnapshotRequirement> requirements,
        final WarheadStateMetadata metadata) {
        return capture(level, position, requirements, metadata, false);
    }

    /**
     * Main-thread fallback capture. The ordinary path copies only the exact read
     * domains. A fallback instead packs every in-chunk section so a manifest bug
     * cannot turn into an endlessly repeated deterministic retry.
     */
    static WarheadChunkSnapshot captureFallback(final ServerLevel level,
        final ChunkPos position, final List<WarheadSnapshotRequirement> requirements,
        final WarheadStateMetadata metadata) {
        return capture(level, position, requirements, metadata, true);
    }

    private static WarheadChunkSnapshot capture(final ServerLevel level,
        final ChunkPos position, final List<WarheadSnapshotRequirement> requirements,
        final WarheadStateMetadata metadata, final boolean exhaustive) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(position.x(), position.z());
        if (chunk == null || requirements == null || requirements.isEmpty()
            || metadata == null) return null;
        WarheadChunkRevisionAccess revisions = (WarheadChunkRevisionAccess)(Object)chunk;
        long startingRevision = revisions.war_mod$getChunkRevision();
        int minimumBuildY = level.dimensionType().minY();
        int maximumBuildY = minimumBuildY + level.dimensionType().height() - 1;
        int minimumSectionY = level.getMinSectionY();
        long[] sectionRevisions = new long[level.getSectionsCount()];
        for (int index = 0; index < sectionRevisions.length; index++) {
            sectionRevisions[index] = revisions.war_mod$getSectionRevision(minimumSectionY + index);
        }

        int features = requiredFeatures(position, requirements);
        int[] craterBand = requiredCraterBand(position, requirements,
            minimumBuildY, maximumBuildY);
        int craterMinimumY = craterBand[0];
        int craterMaximumY = craterBand[1];
        int[] motionTopY = new int[256];
        int[] oceanTopY = new int[256];
        int[] surfaceSupportY = (features & WarheadSnapshotFeatures.SURFACE) != 0
            ? new int[256] : null;
        int minimumSurfaceY = maximumBuildY;
        int maximumSurfaceY = minimumBuildY;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int column = localZ * 16 + localX;
                /* ChunkAccess#getHeight already returns the highest occupied block
                 * (Heightmap#getFirstAvailable - 1). Subtracting again excluded the
                 * actual surface from crater excavation and made surface derivation
                 * begin in the substrate, leaving one-block caps and untouched sand. */
                motionTopY[column] = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,
                    localX, localZ);
                /* Keep the primitive heightmap sample domain-neutral. Surface support
                 * is derived only by the surface domain on the detached snapshot;
                 * vertical-only and biome-only work never invokes that derivation. */
                oceanTopY[column] = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,
                    localX, localZ);
                if (surfaceSupportY != null) {
                    surfaceSupportY[column] = terrainSupportY(chunk, localX, localZ,
                        oceanTopY[column], minimumBuildY, metadata);
                }
                minimumSurfaceY = Math.min(minimumSurfaceY, oceanTopY[column]);
                maximumSurfaceY = Math.max(maximumSurfaceY, oceanTopY[column]);
            }
        }

        boolean[] captureSection = new boolean[level.getSectionsCount()];
        int[] requiredFeaturesBySection = new int[level.getSectionsCount()];
        byte[] sectionCoverage = new byte[level.getSectionsCount()];
        if ((features & WarheadSnapshotFeatures.CRATER_VOLUME) != 0) {
            markSections(level, captureSection, requiredFeaturesBySection,
                craterMinimumY, craterMaximumY, WarheadSnapshotFeatures.CRATER_VOLUME);
        }
        if ((features & WarheadSnapshotFeatures.SURFACE) != 0) {
            markSections(level, captureSection, requiredFeaturesBySection,
                minimumSurfaceY - HALO_BELOW_SURFACE_TOP,
                maximumSurfaceY + HALO_ABOVE_SURFACE_TOP,
                WarheadSnapshotFeatures.SURFACE);
            /* A column made only of excluded surface material can exhaust the
             * support search and deliberately resolve to minY - 1. Its first
             * surface/water read is then minY, independent of the heightmap. */
            markSections(level, captureSection, requiredFeaturesBySection,
                minimumBuildY, minimumBuildY + 1,
                WarheadSnapshotFeatures.SURFACE);
        }
        if ((features & WarheadSnapshotFeatures.VERTICAL_FEATURES) != 0) {
            int minimumVerticalSection = Math.floorDiv(
                minimumSurfaceY - SURFACE_SUPPORT_DESCENT, 16);
            int maximumVerticalSection = Math.floorDiv(
                maximumSurfaceY + VERTICAL_SCAN_ABOVE_SURFACE, 16);
            for (int sectionY = minimumVerticalSection;
                sectionY <= maximumVerticalSection; sectionY++) {
                int index = level.getSectionIndexFromSectionY(sectionY);
                if (index < 0 || index >= captureSection.length) continue;
                requiredFeaturesBySection[index] |= WarheadSnapshotFeatures.VERTICAL_FEATURES;
                if (captureSection[index] || exhaustive) {
                    captureSection[index] = true;
                    continue;
                }
                LevelChunkSection section = chunk.getSection(index);
                if (section.maybeHas(state -> metadata.relevantVertical(Block.getId(state)))) {
                    captureSection[index] = true;
                } else {
                    sectionCoverage[index] =
                        WarheadSectionCoverage.PROVEN_IRRELEVANT.wireId();
                }
            }
        }
        if (exhaustive) {
            for (int index = 0; index < captureSection.length; index++) {
                captureSection[index] = true;
                requiredFeaturesBySection[index] |= features;
                sectionCoverage[index] = WarheadSectionCoverage.NOT_CAPTURED.wireId();
            }
        }

        ArrayList<WarheadPackedSection> packedSections = new ArrayList<>();
        for (int index = 0; index < captureSection.length; index++) {
            if (!captureSection[index]) continue;
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir()) {
                sectionCoverage[index] = WarheadSectionCoverage.PROVEN_ALL_AIR.wireId();
                continue;
            }
            PalettedContainerRO.PackedData<BlockState> packed =
                section.getStates().pack(BLOCK_STRATEGY);
            int[] palette = packed.paletteEntries().stream()
                .mapToInt(Block::getId).toArray();
            long[] storage = packed.storage().map(stream -> stream.toArray())
                .orElseGet(() -> new long[0]);
            packedSections.add(new WarheadPackedSection(
                level.getSectionYFromSectionIndex(index), palette,
                packed.bitsPerEntry(), storage));
            sectionCoverage[index] = WarheadSectionCoverage.CAPTURED_PACKED.wireId();
        }
        Long2IntOpenHashMap halo = new Long2IntOpenHashMap();
        halo.defaultReturnValue(-1);
        if ((features & WarheadSnapshotFeatures.SURFACE) != 0) {
            if (!surfaceHaloReady(level, position)) return null;
            captureSurfaceHalo(level, position, surfaceSupportY,
                metadata.airStateId(), halo);
        }
        long[] haloPositions = new long[halo.size()];
        int[] haloStateIds = new int[halo.size()];
        int haloIndex = 0;
        for (Long2IntMap.Entry entry : halo.long2IntEntrySet()) {
            haloPositions[haloIndex] = entry.getLongKey();
            haloStateIds[haloIndex++] = entry.getIntValue();
        }
        if (startingRevision != revisions.war_mod$getChunkRevision()) return null;
        return new WarheadChunkSnapshot(position, startingRevision, minimumSectionY,
            sectionRevisions, minimumBuildY, maximumBuildY,
            craterMinimumY, craterMaximumY, features, motionTopY, oceanTopY,
            metadata, packedSections, sectionCoverage, requiredFeaturesBySection,
            haloPositions, haloStateIds);
    }

    static int requiredFeatures(final ChunkPos chunk,
        final List<WarheadSnapshotRequirement> requirements) {
        int features = 0;
        long packed = chunk.pack();
        for (WarheadSnapshotRequirement requirement : requirements) {
            if (!requirement.footprint().requiredChunks().contains(packed)) continue;
            PreparedImpactSpec impact = requirement.impact();
            WarheadFootprint footprint = requirement.footprint();
            if (intersects(chunk, impact, footprint.craterRadius())) {
                features |= WarheadSnapshotFeatures.CRATER_VOLUME;
            }
            if (intersects(chunk, impact, NuclearSurfacePolicy.mutationRadius(
                footprint.aftermathRadius()))) {
                features |= WarheadSnapshotFeatures.SURFACE;
            }
            if (intersects(chunk, impact,
                Math.max(footprint.aftermathRadius(), footprint.glassRadius()))) {
                features |= WarheadSnapshotFeatures.VERTICAL_FEATURES;
            }
            if (intersects(chunk, impact, footprint.biomeRadius())) {
                features |= WarheadSnapshotFeatures.BIOMES;
            }
        }
        return features;
    }

    private static boolean intersects(final ChunkPos chunk,
        final PreparedImpactSpec impact, final double radius) {
        return radius > 0.0 && WarheadFootprintCalculator.chunkIntersectsCircle(
            chunk.x(), chunk.z(), impact.target().x, impact.target().z, radius + 1.0);
    }

    private static int[] requiredCraterBand(final ChunkPos chunk,
        final List<WarheadSnapshotRequirement> requirements,
        final int minimumBuildY, final int maximumBuildY) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (WarheadSnapshotRequirement requirement : requirements) {
            PreparedImpactSpec impact = requirement.impact();
            NuclearTerrainProfile profile = requirement.footprint().terrainProfile();
            if (!WarheadFootprintCalculator.chunkIntersectsCircle(chunk.x(), chunk.z(),
                impact.target().x, impact.target().z, profile.horizontalRadius() + 1.0)) continue;
            int centerY = Mth.floor(impact.target().y);
            minimum = Math.min(minimum, centerY - Mth.ceil(profile.downwardRadius()) - 1);
            maximum = Math.max(maximum, centerY + Mth.ceil(profile.upwardRadius()) + 1);
        }
        if (maximum < minimum) return new int[] {0, -1};
        return new int[] {Math.max(minimumBuildY, minimum),
            Math.min(maximumBuildY, maximum)};
    }

    private static void markSections(final ServerLevel level, final boolean[] sections,
        final int[] requiredFeaturesBySection, final int minimumY, final int maximumY,
        final int requiredFeatures) {
        if (maximumY < minimumY) return;
        int minimumSection = Math.floorDiv(minimumY, 16);
        int maximumSection = Math.floorDiv(maximumY, 16);
        for (int sectionY = minimumSection; sectionY <= maximumSection; sectionY++) {
            int index = level.getSectionIndexFromSectionY(sectionY);
            if (index >= 0 && index < sections.length) {
                sections[index] = true;
                requiredFeaturesBySection[index] |= requiredFeatures;
            }
        }
    }

    /** Copies only the two-cell X/Z border needed for worker-side exposure and water checks. */
    private static void captureSurfaceHalo(final ServerLevel level, final ChunkPos chunk,
        final int[] surfaceTopY, final int airStateId,
        final Long2IntOpenHashMap destination) {
        int baseX = chunk.getMinBlockX();
        int baseZ = chunk.getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            int row = localZ * 16;
            copyHaloColumn(level, baseX - 1, baseZ + localZ,
                airStateId, destination, surfaceTopY[row], surfaceTopY[row + 1]);
            copyHaloColumn(level, baseX - 2, baseZ + localZ,
                airStateId, destination, surfaceTopY[row]);
            copyHaloColumn(level, baseX + 16, baseZ + localZ,
                airStateId, destination, surfaceTopY[row + 15], surfaceTopY[row + 14]);
            copyHaloColumn(level, baseX + 17, baseZ + localZ,
                airStateId, destination, surfaceTopY[row + 15]);
        }
        for (int localX = 0; localX < 16; localX++) {
            copyHaloColumn(level, baseX + localX, baseZ - 1,
                airStateId, destination, surfaceTopY[localX], surfaceTopY[16 + localX]);
            copyHaloColumn(level, baseX + localX, baseZ - 2,
                airStateId, destination, surfaceTopY[localX]);
            copyHaloColumn(level, baseX + localX, baseZ + 16,
                airStateId, destination, surfaceTopY[15 * 16 + localX],
                surfaceTopY[14 * 16 + localX]);
            copyHaloColumn(level, baseX + localX, baseZ + 17,
                airStateId, destination, surfaceTopY[15 * 16 + localX]);
        }
    }

    private static void copyHaloColumn(final ServerLevel level, final int worldX,
        final int worldZ, final int airStateId,
        final Long2IntOpenHashMap destination, final int... surfaceTopY) {
        LevelChunk neighbour = level.getChunkSource().getChunkNow(
            Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
        if (neighbour == null) return;
        int minimumBuildY = level.dimensionType().minY();
        int maximumBuildY = minimumBuildY + level.dimensionType().height() - 1;
        for (int topY : surfaceTopY) {
            int minimumY = topY - HALO_BELOW_SURFACE_TOP;
            int maximumY = topY + HALO_ABOVE_SURFACE_TOP;
            for (int y = minimumY; y <= maximumY; y++) {
                int stateId = airStateId;
                if (y >= minimumBuildY && y <= maximumBuildY) {
                    int sectionIndex = level.getSectionIndex(y);
                    if (sectionIndex >= 0 && sectionIndex < neighbour.getSectionsCount()) {
                        stateId = Block.getId(neighbour.getSection(sectionIndex).getBlockState(
                            worldX & 15, y & 15, worldZ & 15));
                    }
                }
                destination.put(BlockPos.asLong(worldX, y, worldZ), stateId);
            }
        }
    }

    /** Exact surface-domain support policy. This is intentionally never called
     * for a vertical-only or biome-only snapshot. */
    private static int terrainSupportY(final LevelChunk chunk, final int localX,
        final int localZ, int y, final int minimumBuildY,
        final WarheadStateMetadata metadata) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int worldX = chunk.getPos().getMinBlockX() + localX;
        int worldZ = chunk.getPos().getMinBlockZ() + localZ;
        for (int descent = 0; descent <= SURFACE_SUPPORT_DESCENT && y >= minimumBuildY;
            descent++, y--) {
            int flags = metadata.flags(Block.getId(chunk.getBlockState(
                cursor.set(worldX, y, worldZ))));
            if ((flags & (WarheadSnapshotFlags.AIR | WarheadSnapshotFlags.FLUID
                | WarheadSnapshotFlags.LEAVES | WarheadSnapshotFlags.LOG
                | WarheadSnapshotFlags.PLANK | WarheadSnapshotFlags.GLASS
                | WarheadSnapshotFlags.COBBLE | WarheadSnapshotFlags.FRAGILE)) == 0) {
                return y;
            }
        }
        return minimumBuildY - 1;
    }

    /** Surface derivation owns a two-cell X/Z read halo. Never compile from a
     * partial halo: a lease must make every cardinal neighbour resident first. */
    private static boolean surfaceHaloReady(final ServerLevel level,
        final ChunkPos chunk) {
        return level.getChunkSource().getChunkNow(chunk.x() - 1, chunk.z()) != null
            && level.getChunkSource().getChunkNow(chunk.x() + 1, chunk.z()) != null
            && level.getChunkSource().getChunkNow(chunk.x(), chunk.z() - 1) != null
            && level.getChunkSource().getChunkNow(chunk.x(), chunk.z() + 1) != null;
    }
}
