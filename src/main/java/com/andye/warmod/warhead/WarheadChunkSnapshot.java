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
    private static final int VERTICAL_SCAN_ABOVE_SURFACE = 52;
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
    private final Long2IntOpenHashMap haloStateIds;

    private volatile boolean derived;
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
        this.derived = true;
    }

    WarheadChunkSnapshot(final ChunkPos chunk, final long chunkRevision,
        final int minimumSectionY, final long[] sectionRevisions,
        final int minimumBuildY, final int maximumBuildY,
        final int craterMinimumY, final int craterMaximumY, final int features,
        final int[] motionTopY, final int[] oceanTopY,
        final WarheadStateMetadata metadata,
        final List<WarheadPackedSection> packedSections,
        final long[] haloPositions, final int[] haloStateIds) {
        if (chunk == null || sectionRevisions == null || motionTopY.length != 256
            || oceanTopY.length != 256 || metadata == null || packedSections == null
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
        return (features & requiredFeatures) == requiredFeatures
            && (craterMaximum < craterMinimum
                || craterMinimumY <= craterMinimum && craterMaximumY >= craterMaximum);
    }
    int motionTopY(final int column) { return motionTopY[column]; }
    int terrainSurfaceY(final int column) { ensureDerived(); return terrainSurfaceY[column]; }
    int columnFlags(final int column) { ensureDerived(); return columnFlags[column]; }
    int surfaceStateId(final int column, final int layer) {
        ensureDerived(); return surfaceStateIds[layer * 256 + column];
    }
    int surfaceFlags(final int column, final int layer) {
        ensureDerived(); return surfaceFlags[layer * 256 + column];
    }
    int relevantCount() { ensureDerived(); return relevantPositions.length; }
    long relevantPosition(final int index) { ensureDerived(); return relevantPositions[index]; }
    int relevantStateId(final int index) { ensureDerived(); return relevantStateIds[index]; }
    int relevantFlags(final int index) { ensureDerived(); return relevantFlags[index]; }

    long estimatedBytes() {
        long bytes = 192L + sectionRevisions.length * Long.BYTES
            + (long)(motionTopY.length + oceanTopY.length) * Integer.BYTES
            + (long)haloStateIds.size() * (Long.BYTES + Integer.BYTES);
        for (WarheadPackedSection section : packedSections.values()) bytes += section.estimatedBytes();
        if (derived) {
            bytes += (long)(terrainSurfaceY.length + columnFlags.length
                + surfaceStateIds.length + surfaceFlags.length
                + relevantStateIds.length + relevantFlags.length) * Integer.BYTES
                + (long)relevantPositions.length * Long.BYTES;
            if (craterStateIds != null) {
                bytes += (long)(craterStateIds.length + craterFlags.length) * Integer.BYTES
                    + (long)craterResistance.length * Float.BYTES;
            }
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
        return stateIdAt(localX, y, localZ);
    }
    int craterFlags(final int localX, final int y, final int localZ) {
        if (craterFlags != null) return craterFlags[craterIndex(localX, y, localZ)];
        return metadata.flags(stateIdAt(localX, y, localZ));
    }
    float craterResistance(final int localX, final int y, final int localZ) {
        if (craterResistance != null) return craterResistance[craterIndex(localX, y, localZ)];
        return metadata.explosionResistance(stateIdAt(localX, y, localZ));
    }

    private int craterIndex(final int localX, final int y, final int localZ) {
        if (!containsCraterY(y)) throw new IndexOutOfBoundsException("Y outside crater snapshot");
        return (y - craterMinimumY) * 256 + localZ * 16 + localX;
    }
    private int stateIdAt(final int localX, final int y, final int localZ) {
        if (y < minimumBuildY || y > maximumBuildY) return metadata.airStateId();
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            return haloStateIds.get(BlockPos.asLong(chunk.getMinBlockX() + localX,
                y, chunk.getMinBlockZ() + localZ));
        }
        WarheadPackedSection section = packedSections.get(Math.floorDiv(y, 16));
        return section == null ? metadata.airStateId()
            : section.stateId(localX, y & 15, localZ);
    }

    private void ensureDerived() {
        if (derived) return;
        synchronized (this) {
            if (derived) return;
            terrainSurfaceY = new int[256];
            columnFlags = new int[256];
            surfaceStateIds = new int[256 * SURFACE_LAYERS];
            surfaceFlags = new int[surfaceStateIds.length];
            LongArrayList positions = new LongArrayList();
            IntArrayList stateIds = new IntArrayList();
            IntArrayList flags = new IntArrayList();
            int baseX = chunk.getMinBlockX();
            int baseZ = chunk.getMinBlockZ();
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int column = localZ * 16 + localX;
                    int terrainY = hasFeature(WarheadSnapshotFeatures.SURFACE)
                        || hasFeature(WarheadSnapshotFeatures.VERTICAL_FEATURES)
                        ? terrainSupportY(localX, localZ, oceanTopY[column])
                        : oceanTopY[column];
                    terrainSurfaceY[column] = terrainY;
                    if (touchesWater(localX, terrainY, localZ)) {
                        columnFlags[column] |= WarheadSnapshotFlags.WATER_NEAR;
                    }
                    for (int layer = 0; layer < SURFACE_LAYERS; layer++) {
                        int y = terrainY + 1 - layer;
                        int destination = layer * 256 + column;
                        int stateId = stateIdAt(localX, y, localZ);
                        int stateFlags = metadata.flags(stateId);
                        if (exposedToAir(localX, y, localZ)) {
                            stateFlags |= WarheadSnapshotFlags.EXPOSED;
                        }
                        surfaceStateIds[destination] = stateId;
                        surfaceFlags[destination] = stateFlags;
                    }
                    if (!hasFeature(WarheadSnapshotFeatures.VERTICAL_FEATURES)) continue;
                    int columnStart = positions.size();
                    boolean naturalTree = false;
                    int scanMinimum = Math.max(minimumBuildY,
                        terrainY - VERTICAL_SCAN_BELOW_SURFACE);
                    int scanMaximum = Math.min(maximumBuildY,
                        terrainY + VERTICAL_SCAN_ABOVE_SURFACE);
                    for (int y = scanMinimum; y <= scanMaximum; y++) {
                        int stateId = stateIdAt(localX, y, localZ);
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
            derived = true;
        }
    }

    private int terrainSupportY(final int localX, final int localZ, int y) {
        for (int descent = 0; descent <= SURFACE_SUPPORT_DESCENT && y >= minimumBuildY;
            descent++, y--) {
            int flags = metadata.flags(stateIdAt(localX, y, localZ));
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
        int stateId = stateIdAt(localX, y, localZ);
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
        int stateId = stateIdAt(localX, y, localZ);
        return stateId >= 0
            && (metadata.flags(stateId) & WarheadSnapshotFlags.WATER) != 0;
    }
}
