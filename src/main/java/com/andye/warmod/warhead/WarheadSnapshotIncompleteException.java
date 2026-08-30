package com.andye.warmod.warhead;

import net.minecraft.world.level.ChunkPos;

/** A compiler-required section was not represented by a detached snapshot. */
final class WarheadSnapshotIncompleteException extends IllegalStateException {
    private final ChunkPos chunk;
    private final int sectionY;
    private final int feature;
    private final WarheadSectionCoverage coverage;

    WarheadSnapshotIncompleteException(final ChunkPos chunk, final int sectionY,
        final int feature, final WarheadSectionCoverage coverage) {
        super("SNAPSHOT_INCOMPLETE chunk=" + chunk + " sectionY=" + sectionY
            + " feature=" + WarheadSnapshotFeatures.name(feature)
            + " coverage=" + coverage.name().toLowerCase(java.util.Locale.ROOT));
        this.chunk = chunk;
        this.sectionY = sectionY;
        this.feature = feature;
        this.coverage = coverage;
    }

    ChunkPos chunk() { return chunk; }
    int sectionY() { return sectionY; }
    int feature() { return feature; }
    WarheadSectionCoverage coverage() { return coverage; }
}
