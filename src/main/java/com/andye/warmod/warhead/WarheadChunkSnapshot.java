package com.andye.warmod.warhead;

import net.minecraft.world.level.ChunkPos;

/** Immutable primitive world snapshot safe to hand to preparation workers. */
final class WarheadChunkSnapshot {
    static final int SURFACE_LAYERS = 5;
    static final int ABOVE_LAYER = 0;
    static final int SURFACE_LAYER = 1;

    private final ChunkPos chunk;
    private final long chunkRevision;
    private final int minimumSectionY;
    private final long[] sectionRevisions;
    private final int minimumBuildY;
    private final int maximumBuildY;
    private final int craterMinimumY;
    private final int craterMaximumY;
    private final int[] motionTopY;
    private final int[] terrainSurfaceY;
    private final int[] columnFlags;
    private final int[] surfaceStateIds;
    private final int[] surfaceFlags;
    private final int[] craterStateIds;
    private final int[] craterFlags;
    private final float[] craterResistance;
    private final long[] relevantPositions;
    private final int[] relevantStateIds;
    private final int[] relevantFlags;

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
        this.motionTopY = motionTopY.clone();
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
    }

    ChunkPos chunk() { return chunk; }
    long chunkRevision() { return chunkRevision; }
    int minimumBuildY() { return minimumBuildY; }
    int maximumBuildY() { return maximumBuildY; }
    int craterMinimumY() { return craterMinimumY; }
    int craterMaximumY() { return craterMaximumY; }
    int motionTopY(final int column) { return motionTopY[column]; }
    int terrainSurfaceY(final int column) { return terrainSurfaceY[column]; }
    int columnFlags(final int column) { return columnFlags[column]; }
    int surfaceStateId(final int column, final int layer) {
        return surfaceStateIds[layer * 256 + column];
    }
    int surfaceFlags(final int column, final int layer) {
        return surfaceFlags[layer * 256 + column];
    }
    int relevantCount() { return relevantPositions.length; }
    long relevantPosition(final int index) { return relevantPositions[index]; }
    int relevantStateId(final int index) { return relevantStateIds[index]; }
    int relevantFlags(final int index) { return relevantFlags[index]; }
    long estimatedBytes() {
        return 192L
            + sectionRevisions.length * Long.BYTES
            + (long)(motionTopY.length + terrainSurfaceY.length + columnFlags.length
                + surfaceStateIds.length + surfaceFlags.length + craterStateIds.length
                + craterFlags.length + relevantStateIds.length + relevantFlags.length)
                * Integer.BYTES
            + (long)craterResistance.length * Float.BYTES
            + (long)relevantPositions.length * Long.BYTES;
    }
    long sectionRevision(final int sectionY) {
        int index = sectionY - minimumSectionY;
        return index < 0 || index >= sectionRevisions.length ? 0L : sectionRevisions[index];
    }
    boolean containsCraterY(final int y) {
        return y >= craterMinimumY && y <= craterMaximumY;
    }
    int craterStateId(final int localX, final int y, final int localZ) {
        return craterStateIds[craterIndex(localX, y, localZ)];
    }
    int craterFlags(final int localX, final int y, final int localZ) {
        return craterFlags[craterIndex(localX, y, localZ)];
    }
    float craterResistance(final int localX, final int y, final int localZ) {
        return craterResistance[craterIndex(localX, y, localZ)];
    }

    private int craterIndex(final int localX, final int y, final int localZ) {
        if (!containsCraterY(y)) throw new IndexOutOfBoundsException("Y outside crater snapshot");
        return (y - craterMinimumY) * 256 + localZ * 16 + localX;
    }
}
