package com.andye.warmod.warhead;

/** Independent detached-data requirements for one chunk snapshot. */
final class WarheadSnapshotFeatures {
    static final int CRATER_VOLUME = 1;
    static final int SURFACE = 1 << 1;
    static final int VERTICAL_FEATURES = 1 << 2;
    static final int BIOMES = 1 << 3;
    static final int ALL = CRATER_VOLUME | SURFACE | VERTICAL_FEATURES | BIOMES;

    private WarheadSnapshotFeatures() { }
}
