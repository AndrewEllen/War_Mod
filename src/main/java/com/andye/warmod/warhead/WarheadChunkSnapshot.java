package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** Immutable primitive world snapshot safe to hand to preparation workers. */
final class WarheadChunkSnapshot {
    static final int SURFACE_LAYERS = 5;
    static final int ABOVE_LAYER = 0;
    static final int SURFACE_LAYER = 1;
    private static final int VERTICAL_SCAN_BELOW_SURFACE = 8;
    private static final int VERTICAL_SCAN_ABOVE_SURFACE = 64;
    private static final int SURFACE_SUPPORT_DESCENT = 8;

    private final ChunkPos chunk;
    private final long chunkRevision;
    private final int minimumSectionY;
    private final long[] sectionRevisions;
    private final int minimumBuildY;
    private final int maximumBuildY;
    private final int craterMinimumY;
    private final int craterMaximumY;
    private final int features;
    private final int[] motionTopY;
    private final int[] oceanTopY;
    private final WarheadStateMetadata metadata;
    private final Int2ObjectOpenHashMap<WarheadPackedSection> packedSections;
    private final byte[] sectionCoverage;
    private final int[] requiredFeaturesBySection;
    private final Long2IntOpenHashMap haloStateIds;

    private volatile boolean surfaceDerived;
    private volatile boolean verticalDerived;
    private int[] terrainSurfaceY;
    private int[] columnFlags;
    private int[] surfaceStateIds;
    private int[] surfaceFlags;
    private int[] craterStateIds;
    private int[] craterFlags;
    private float[] craterResistance;
    private long[] relevantPositions;
    private int[] relevantStateIds;
    private int[] relevantFlags;

    /** Compatibility constructor retained for primitive compiler fixtures. */
    WarheadChunkSnapshot(final ChunkPos chunk, final long chunkRevision,
        final int minimumSectionY, final long[] sectionRevisions,
        final int minimumBuildY, final int maximumBuildY,
        final int craterMinimumY, final int craterMaximumY,
        final int[] motionTopY, final int[] terrainSurfaceY, final int[] columnFlags,
        final int[] surfaceStateIds, final int[] surfaceFlags,
        final int[] craterStateIds, final int[] craterFlags,
        final float[] craterResistance, final long[] relevantPositions,
        final int[] relevantStateIds, final int[] relevantFlags) {
        int craterHeight = Math.max(0, craterMaximumY - craterMinimumY + 1);
        if (chunk == null || sectionRevisions == null || motionTopY.length != 256
            || terrainSurfaceY.length != 256 || columnFlags.length != 256
            || surfaceStateIds.length != 256 * SURFACE_LAYERS
            || surfaceFlags.length != surfaceStateIds.length
            || craterStateIds.length != 256 * craterHeight
            || craterFlags.length != craterStateIds.length
            || craterResistance.length != craterStateIds.length
            || relevantPositions.length != relevantStateIds.length
            || relevantPositions.length != relevantFlags.length) {
            throw new IllegalArgumentException("Malformed warhead chunk snapshot");
        }
        this.chunk = chunk;
        this.chunkRevision = chunkRevision;
        this.minimumSectionY = minimumSectionY;
        this.sectionRevisions = sectionRevisions.clone();
        this.minimumBuildY = minimumBuildY;
        this.maximumBuildY = maximumBuildY;
        this.craterMinimumY = craterMinimumY;
        this.craterMaximumY = craterMaximumY;
        this.features = WarheadSnapshotFeatures.ALL;
        this.motionTopY = motionTopY.clone();
        this.oceanTopY = terrainSurfaceY.clone();
        this.metadata = null;
        this.packedSections = new Int2ObjectOpenHashMap<>();
        this.sectionCoverage = new byte[sectionRevisions.length];
        java.util.Arrays.fill(this.sectionCoverage,
            WarheadSectionCoverage.CAPTURED_PACKED.wireId());
        this.requiredFeaturesBySection = new int[sectionRevisions.length];
        java.util.Arrays.fill(this.requiredFeaturesBySection, WarheadSnapshotFeatures.ALL);
        this.haloStateIds = new Long2IntOpenHashMap();
        this.haloStateIds.defaultReturnValue(-1);
        this.terrainSurfaceY = terrainSurfaceY.clone();
        this.columnFlags = columnFlags.clone();
        this.surfaceStateIds = surfaceStateIds.clone();
        this.surfaceFlags = surfaceFlags.clone();
        this.craterStateIds = craterStateIds.clone();
        this.craterFlags = craterFlags.clone();
        this.craterResistance = craterResistance.clone();
        this.relevantPositions = relevantPositions.clone();
        this.relevantStateIds = relevantStateIds.clone();
        this.relevantFlags = relevantFlags.clone();
        this.surfaceDerived = true;
        this.verticalDerived = true;
    }

    WarheadChunkSnapshot(final ChunkPos chunk, final long chunkRevision,
        final int minimumSectionY, final long[] sectionRevisions,
        final int minimumBuildY, final int maximumBuildY,
        final int craterMinimumY, final int craterMaximumY, final int features,
        final int[] motionTopY, final int[] oceanTopY,
        final WarheadStateMetadata metadata,
        final List<WarheadPackedSection> packedSections,
        final byte[] sectionCoverage, final int[] requiredFeaturesBySection,
        final long[] haloPositions, final int[] haloStateIds) {
        if (chunk == null || sectionRevisions == null || motionTopY.length != 256
            || oceanTopY.length != 256 || metadata == null || packedSections == null
            || sectionCoverage == null || requiredFeaturesBySection == null
            || sectionCoverage.length != sectionRevisions.length
            || requiredFeaturesBySection.length != sectionRevisions.length
            || haloPositions == null || haloStateIds == null
            || haloPositions.length != haloStateIds.length) {
            throw new IllegalArgumentException("Malformed packed warhead chunk snapshot");
        }
        this.chunk = chunk;
        this.chunkRevision = chunkRevision;
        this.minimumSectionY = minimumSectionY;
        this.sectionRevisions = sectionRevisions.clone();
        this.minimumBuildY = minimumBuildY;
        this.maximumBuildY = maximumBuildY;
        this.craterMinimumY = craterMinimumY;
        this.craterMaximumY = craterMaximumY;
        this.features = features;
        this.motionTopY = motionTopY.clone();
        this.oceanTopY = oceanTopY.clone();
        this.metadata = metadata;
        this.packedSections = new Int2ObjectOpenHashMap<>();
        for (WarheadPackedSection section : packedSections) {
            this.packedSections.put(section.sectionY(), section);
        }
        this.sectionCoverage = sectionCoverage.clone();
        this.requiredFeaturesBySection = requiredFeaturesBySection.clone();
        this.haloStateIds = new Long2IntOpenHashMap(haloPositions.length);
        this.haloStateIds.defaultReturnValue(-1);
        for (int index = 0; index < haloPositions.length; index++) {
            this.haloStateIds.put(haloPositions[index], haloStateIds[index]);
        }
    }

    ChunkPos chunk() { return chunk; }
    long chunkRevision() { return chunkRevision; }
    int minimumBuildY() { return minimumBuildY; }
    int maximumBuildY() { return maximumBuildY; }
    int craterMinimumY() { return craterMinimumY; }
    int craterMaximumY() { return craterMaximumY; }
    boolean hasFeature(final int feature) { return (features & feature) != 0; }
    boolean covers(final int requiredFeatures, final int craterMinimum,
        final int craterMaximum) {
        if ((features & requiredFeatures) != requiredFeatures
            || !(craterMaximum < craterMinimum
                || craterMinimumY <= craterMinimum && craterMaximumY >= craterMaximum)) {
            return false;
        }
        for (int index = 0; index < sectionCoverage.length; index++) {
            int required = requiredFeaturesBySection[index] & requiredFeatures;
            if (required == 0) continue;
            WarheadSectionCoverage coverage = WarheadSectionCoverage.fromWireId(
                sectionCoverage[index]);
            if (coverage == WarheadSectionCoverage.NOT_CAPTURED
                || coverage == WarheadSectionCoverage.PROVEN_IRRELEVANT
                    && (required & ~WarheadSnapshotFeatures.VERTICAL_FEATURES) != 0) {
                return false;
            }
        }
        return true;
    }

    void requireCoverage(final int feature) {
        for (int index = 0; index < sectionCoverage.length; index++) {
            if ((requiredFeaturesBySection[index] & feature) == 0) continue;
            WarheadSectionCoverage coverage = WarheadSectionCoverage.fromWireId(
                sectionCoverage[index]);
            boolean allowed = coverage == WarheadSectionCoverage.CAPTURED_PACKED
                || coverage == WarheadSectionCoverage.PROVEN_ALL_AIR
                || feature == WarheadSnapshotFeatures.VERTICAL_FEATURES
                    && coverage == WarheadSectionCoverage.PROVEN_IRRELEVANT;
            if (!allowed) throw new WarheadSnapshotIncompleteException(chunk,
                minimumSectionY + index, feature, coverage);
        }
    }

    WarheadSectionCoverage sectionCoverage(final int sectionY) {
        int index = sectionY - minimumSectionY;
        return index < 0 || index >= sectionCoverage.length
            ? WarheadSectionCoverage.NOT_CAPTURED
            : WarheadSectionCoverage.fromWireId(sectionCoverage[index]);
    }
    int motionTopY(final int column) { return motionTopY[column]; }
    int verticalSurfaceY(final int column) {
        if (!hasFeature(WarheadSnapshotFeatures.VERTICAL_FEATURES)) {
            throw new IllegalStateException("Snapshot does not own the vertical domain");
        }
        return oceanTopY[column];
    }
    int biomeSurfaceY(final int column) {
        if (!hasFeature(WarheadSnapshotFeatures.BIOMES)) {
            throw new IllegalStateException("Snapshot does not own the biome domain");
        }
        return oceanTopY[column];
    }
    int terrainSurfaceY(final int column) {
        ensureSurfaceDerived();
        return terrainSurfaceY[column];
    }
    int columnFlags(final int column) {
        ensureSurfaceDerived();
        return columnFlags[column];
    }
    int surfaceStateId(final int column, final int layer) {
        ensureSurfaceDerived();
        return surfaceStateIds[layer * 256 + column];
    }
    int surfaceFlags(final int column, final int layer) {
        ensureSurfaceDerived();
        return surfaceFlags[layer * 256 + column];
    }
    int relevantCount() { ensureVerticalDerived(); return relevantPositions.length; }
    long relevantPosition(final int index) {
        ensureVerticalDerived();
        return relevantPositions[index];
    }
    int relevantStateId(final int index) {
        ensureVerticalDerived();
        return relevantStateIds[index];
    }
    int relevantFlags(final int index) {
        ensureVerticalDerived();
        return relevantFlags[index];
    }

    /** Resolves every derived read before a snapshot is handed to a worker. */
    void preflight() {
        if (hasFeature(WarheadSnapshotFeatures.CRATER_VOLUME)) {
            requireCoverage(WarheadSnapshotFeatures.CRATER_VOLUME);
        }
        if (hasFeature(WarheadSnapshotFeatures.SURFACE)) {
            requireCoverage(WarheadSnapshotFeatures.SURFACE);
            requireSurfaceHaloCoverage();
            ensureSurfaceDerived();
        }
        if (hasFeature(WarheadSnapshotFeatures.VERTICAL_FEATURES)) {
            requireCoverage(WarheadSnapshotFeatures.VERTICAL_FEATURES);
            ensureVerticalDerived();
        }
        if (hasFeature(WarheadSnapshotFeatures.BIOMES)) {
            requireCoverage(WarheadSnapshotFeatures.BIOMES);
            requireValidColumnHeights(WarheadSnapshotFeatures.BIOMES);
        }
    }

    private void requireValidColumnHeights(final int feature) {
        for (int y : oceanTopY) {
            if (y < minimumBuildY - 1 || y > maximumBuildY) {
                throw new WarheadSnapshotIncompleteException(chunk, Math.floorDiv(y, 16),
                    feature, WarheadSectionCoverage.NOT_CAPTURED);
            }
        }
    }

    private void requireSurfaceHaloCoverage() {
        int belowTop = SURFACE_SUPPORT_DESCENT + SURFACE_LAYERS - 1;
        int baseX = chunk.getMinBlockX();
        int baseZ = chunk.getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            requireHaloColumn(baseX - 1, baseZ + localZ, oceanTopY[localZ * 16], belowTop);
            requireHaloColumn(baseX - 2, baseZ + localZ, oceanTopY[localZ * 16], belowTop);
            requireHaloColumn(baseX + 16, baseZ + localZ,
                oceanTopY[localZ * 16 + 15], belowTop);
            requireHaloColumn(baseX + 17, baseZ + localZ,
                oceanTopY[localZ * 16 + 15], belowTop);
        }
        for (int localX = 0; localX < 16; localX++) {
            requireHaloColumn(baseX + localX, baseZ - 1, oceanTopY[localX], belowTop);
            requireHaloColumn(baseX + localX, baseZ - 2, oceanTopY[localX], belowTop);
            requireHaloColumn(baseX + localX, baseZ + 16,
                oceanTopY[15 * 16 + localX], belowTop);
            requireHaloColumn(baseX + localX, baseZ + 17,
                oceanTopY[15 * 16 + localX], belowTop);
        }
    }

    private void requireHaloColumn(final int worldX, final int worldZ,
        final int surfaceTopY, final int belowTop) {
        for (int y = surfaceTopY - belowTop; y <= surfaceTopY + 2; y++) {
            if (y < minimumBuildY || y > maximumBuildY) continue;
            if (!haloStateIds.containsKey(BlockPos.asLong(worldX, y, worldZ))) {
                throw new WarheadSnapshotIncompleteException(chunk, Math.floorDiv(y, 16),
                    WarheadSnapshotFeatures.SURFACE,
                    WarheadSectionCoverage.NOT_CAPTURED);
            }
        }
    }

    boolean hasPackedBacking() { return metadata != null; }

    int verticalStateIdAtWorld(final int worldX, final int y, final int worldZ) {
        if (metadata == null) return -1;
        return verticalStateIdAt(worldX - chunk.getMinBlockX(), y,
            worldZ - chunk.getMinBlockZ());
    }

    int verticalFlagsAtWorld(final int worldX, final int y, final int worldZ) {
        int stateId = verticalStateIdAtWorld(worldX, y, worldZ);
        return stateId < 0 ? 0 : metadata.flags(stateId);
    }

    long estimatedBytes() {
        long bytes = 192L + sectionRevisions.length * Long.BYTES
            + (long)(motionTopY.length + oceanTopY.length) * Integer.BYTES
            + sectionCoverage.length + (long)requiredFeaturesBySection.length * Integer.BYTES
            + (long)haloStateIds.size() * (Long.BYTES + Integer.BYTES);
        for (WarheadPackedSection section : packedSections.values()) bytes += section.estimatedBytes();
        if (surfaceDerived) {
            bytes += (long)(terrainSurfaceY.length + columnFlags.length
                + surfaceStateIds.length + surfaceFlags.length) * Integer.BYTES;
        }
        if (verticalDerived) {
            bytes += (long)(relevantStateIds.length + relevantFlags.length) * Integer.BYTES
                + (long)relevantPositions.length * Long.BYTES;
        }
        if (craterStateIds != null) {
            bytes += (long)(craterStateIds.length + craterFlags.length) * Integer.BYTES
                + (long)craterResistance.length * Float.BYTES;
        }
        return bytes;
    }
    int copiedSectionCount() { return packedSections.size(); }
    long copiedBlockStateIdCount() {
        return (long)packedSections.size() * 4_096L + haloStateIds.size();
    }

    long sectionRevision(final int sectionY) {
        int index = sectionY - minimumSectionY;
        return index < 0 || index >= sectionRevisions.length ? 0L : sectionRevisions[index];
    }
    boolean containsCraterY(final int y) { return y >= craterMinimumY && y <= craterMaximumY; }
    int craterStateId(final int localX, final int y, final int localZ) {
        if (craterStateIds != null) return craterStateIds[craterIndex(localX, y, localZ)];
        return requireStateIdAt(localX, y, localZ,
            WarheadSnapshotFeatures.CRATER_VOLUME);
    }
    int craterFlags(final int localX, final int y, final int localZ) {
        if (craterFlags != null) return craterFlags[craterIndex(localX, y, localZ)];
        return metadata.flags(requireStateIdAt(localX, y, localZ,
            WarheadSnapshotFeatures.CRATER_VOLUME));
    }
    float craterResistance(final int localX, final int y, final int localZ) {
        if (craterResistance != null) return craterResistance[craterIndex(localX, y, localZ)];
        return metadata.explosionResistance(requireStateIdAt(localX, y, localZ,
            WarheadSnapshotFeatures.CRATER_VOLUME));
    }

    private int craterIndex(final int localX, final int y, final int localZ) {
        if (!containsCraterY(y)) throw new IndexOutOfBoundsException("Y outside crater snapshot");
        return (y - craterMinimumY) * 256 + localZ * 16 + localX;
    }
    int requireStateIdAt(final int localX, final int y, final int localZ,
        final int feature) {
        if (y < minimumBuildY || y > maximumBuildY) return metadata.airStateId();
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            int stateId = haloStateIds.get(BlockPos.asLong(chunk.getMinBlockX() + localX,
                y, chunk.getMinBlockZ() + localZ));
            if (stateId >= 0) return stateId;
            throw new WarheadSnapshotIncompleteException(chunk, Math.floorDiv(y, 16),
                feature, WarheadSectionCoverage.NOT_CAPTURED);
        }
        int sectionY = Math.floorDiv(y, 16);
        WarheadPackedSection section = packedSections.get(sectionY);
        if (section != null) return section.stateId(localX, y & 15, localZ);
        WarheadSectionCoverage coverage = sectionCoverage(sectionY);
        if (coverage == WarheadSectionCoverage.PROVEN_ALL_AIR) return metadata.airStateId();
        throw new WarheadSnapshotIncompleteException(chunk, sectionY, feature, coverage);
    }

    private int verticalStateIdAt(final int localX, final int y, final int localZ) {
        int sectionY = Math.floorDiv(y, 16);
        if (sectionCoverage(sectionY) == WarheadSectionCoverage.PROVEN_IRRELEVANT) {
            return metadata.airStateId();
        }
        return requireStateIdAt(localX, y, localZ,
            WarheadSnapshotFeatures.VERTICAL_FEATURES);
    }

    private void ensureSurfaceDerived() {
        if (surfaceDerived) return;
        synchronized (this) {
            if (surfaceDerived) return;
            if (!hasFeature(WarheadSnapshotFeatures.SURFACE)) {
                throw new IllegalStateException("Snapshot does not own the surface domain");
            }
            requireCoverage(WarheadSnapshotFeatures.SURFACE);
            terrainSurfaceY = new int[256];
            columnFlags = new int[256];
            surfaceStateIds = new int[256 * SURFACE_LAYERS];
            surfaceFlags = new int[surfaceStateIds.length];
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int column = localZ * 16 + localX;
                    int terrainY = terrainSupportY(localX, localZ, oceanTopY[column],
                        WarheadSnapshotFeatures.SURFACE);
                    terrainSurfaceY[column] = terrainY;
                    if (touchesWater(localX, terrainY, localZ)) {
                        columnFlags[column] |= WarheadSnapshotFlags.WATER_NEAR;
                    }
                    for (int layer = 0; layer < SURFACE_LAYERS; layer++) {
                        int y = terrainY + 1 - layer;
                        int destination = layer * 256 + column;
                        int stateId = requireStateIdAt(localX, y, localZ,
                            WarheadSnapshotFeatures.SURFACE);
                        int stateFlags = metadata.flags(stateId);
                        if (exposedToAir(localX, y, localZ)) {
                            stateFlags |= WarheadSnapshotFlags.EXPOSED;
                        }
                        surfaceStateIds[destination] = stateId;
                        surfaceFlags[destination] = stateFlags;
                    }
                }
            }
            surfaceDerived = true;
        }
    }

    private void ensureVerticalDerived() {
        if (verticalDerived) return;
        synchronized (this) {
            if (verticalDerived) return;
            if (!hasFeature(WarheadSnapshotFeatures.VERTICAL_FEATURES)) {
                throw new IllegalStateException("Snapshot does not own the vertical domain");
            }
            requireCoverage(WarheadSnapshotFeatures.VERTICAL_FEATURES);
            LongArrayList positions = new LongArrayList();
            IntArrayList stateIds = new IntArrayList();
            IntArrayList flags = new IntArrayList();
            int baseX = chunk.getMinBlockX();
            int baseZ = chunk.getMinBlockZ();
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int column = localZ * 16 + localX;
                    // OCEAN_FLOOR is captured independently for this domain. A vertical-only
                    // snapshot must not execute surface support, exposure, or water derivation.
                    int terrainY = verticalSurfaceY(column);
                    int columnStart = positions.size();
                    boolean naturalTree = false;
                    int scanMinimum = Math.max(minimumBuildY,
                        terrainY - VERTICAL_SCAN_BELOW_SURFACE);
                    int scanMaximum = Math.min(maximumBuildY,
                        terrainY + VERTICAL_SCAN_ABOVE_SURFACE);
                    for (int y = scanMinimum; y <= scanMaximum; y++) {
                        int stateId = verticalStateIdAt(localX, y, localZ);
                        int stateFlags = metadata.flags(stateId);
                        if (!WarheadSnapshotFlags.relevantVertical(stateFlags)) continue;
                        naturalTree |= (stateFlags & WarheadSnapshotFlags.LEAVES) != 0;
                        positions.add(BlockPos.asLong(baseX + localX, y, baseZ + localZ));
                        stateIds.add(stateId);
                        flags.add(stateFlags);
                    }
                    if (naturalTree) {
                        for (int index = columnStart; index < flags.size(); index++) {
                            int stateFlags = flags.getInt(index);
                            if ((stateFlags & WarheadSnapshotFlags.LOG) != 0) {
                                flags.set(index, stateFlags | WarheadSnapshotFlags.NATURAL_TREE);
                            }
                        }
                    }
                }
            }
            relevantPositions = positions.toLongArray();
            relevantStateIds = stateIds.toIntArray();
            relevantFlags = flags.toIntArray();
            verticalDerived = true;
        }
    }

    private int terrainSupportY(final int localX, final int localZ, int y,
        final int feature) {
        for (int descent = 0; descent <= SURFACE_SUPPORT_DESCENT && y >= minimumBuildY;
            descent++, y--) {
            int flags = metadata.flags(requireStateIdAt(localX, y, localZ,
                feature));
            if ((flags & (WarheadSnapshotFlags.AIR | WarheadSnapshotFlags.FLUID
                | WarheadSnapshotFlags.LEAVES | WarheadSnapshotFlags.LOG
                | WarheadSnapshotFlags.PLANK | WarheadSnapshotFlags.GLASS
                | WarheadSnapshotFlags.COBBLE | WarheadSnapshotFlags.FRAGILE)) == 0) return y;
        }
        return minimumBuildY - 1;
    }
    private boolean exposedToAir(final int localX, final int y, final int localZ) {
        return isAir(localX + 1, y, localZ) || isAir(localX - 1, y, localZ)
            || isAir(localX, y + 1, localZ) || isAir(localX, y - 1, localZ)
            || isAir(localX, y, localZ + 1) || isAir(localX, y, localZ - 1);
    }
    private boolean isAir(final int localX, final int y, final int localZ) {
        int stateId = requireStateIdAt(localX, y, localZ,
            WarheadSnapshotFeatures.SURFACE);
        return stateId >= 0
            && (metadata.flags(stateId) & WarheadSnapshotFlags.AIR) != 0;
    }
    private boolean touchesWater(final int localX, final int surfaceY, final int localZ) {
        if (isWater(localX, surfaceY + 1, localZ)) return true;
        for (int distance = 1; distance <= 2; distance++) {
            if (isWater(localX + distance, surfaceY, localZ)
                || isWater(localX - distance, surfaceY, localZ)
                || isWater(localX, surfaceY, localZ + distance)
                || isWater(localX, surfaceY, localZ - distance)
                || isWater(localX + distance, surfaceY + 1, localZ)
                || isWater(localX - distance, surfaceY + 1, localZ)
                || isWater(localX, surfaceY + 1, localZ + distance)
                || isWater(localX, surfaceY + 1, localZ - distance)) return true;
        }
        return false;
    }
    private boolean isWater(final int localX, final int y, final int localZ) {
        int stateId = requireStateIdAt(localX, y, localZ,
            WarheadSnapshotFeatures.SURFACE);
        return stateId >= 0
            && (metadata.flags(stateId) & WarheadSnapshotFlags.WATER) != 0;
    }
}
