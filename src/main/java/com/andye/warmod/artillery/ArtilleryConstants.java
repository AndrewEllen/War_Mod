package com.andye.warmod.artillery;

public final class ArtilleryConstants {
    /** Chosen with gravity so a same-height 45 degree shot reaches about 1000 blocks. */
    public static final double MAXIMUM_RANGE_BLOCKS = 1_000.0;
    public static final double MAXIMUM_MUZZLE_SPEED_BLOCKS_PER_TICK = 5.0;
    public static final double GRAVITY_BLOCKS_PER_TICK_SQUARED = 0.025;
    public static final double MAXIMUM_APEX_ABOVE_MUZZLE_BLOCKS = 256.0;
    public static final double MINIMUM_HORIZONTAL_RANGE_BLOCKS = 4.0;
    public static final int MAXIMUM_FLIGHT_TICKS = 600;
    public static final int AMMUNITION_SLOTS = 16;
    public static final int CLUSTER_CHILDREN = 4;
    public static final double CLUSTER_SPREAD_RADIUS_BLOCKS = 7.5;
    public static final int CONVENTIONAL_FUSE_TICKS = 200;
    public static final int NUCLEAR_FUSE_TICKS = 600;
    public static final int PRIMED_EXPLOSIVE_MAX_LIFETIME_TICKS = 800;

    private ArtilleryConstants() {
    }
}
