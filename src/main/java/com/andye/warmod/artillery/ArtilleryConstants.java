package com.andye.warmod.artillery;

/** Fixed ballistic envelope for the field artillery cannon. */
public final class ArtilleryConstants {
    public static final int MAX_AMMUNITION = 16;
    public static final double MAX_RANGE_BLOCKS = 1_000.0;
    public static final double MAX_MUZZLE_SPEED = 8.0;
    public static final double GRAVITY_PER_TICK = 0.05;
    public static final double MAX_APEX_ABOVE_MUZZLE = 384.0;
    /** Shared by the cannon renderer and server launch point so the shell exits the visible tube. */
    public static final double BARREL_PIVOT_HEIGHT = 1.28;
    public static final double BARREL_MUZZLE_OFFSET = 3.25;
    public static final int TARGET_PREPARATION_TIMEOUT_TICKS = 200;
    public static final int STREAM_LOOKAHEAD_TICKS = 12;
    public static final int TARGET_LEAD_TICKS = 48;
    public static final int CHUNK_WAIT_TIMEOUT_TICKS = 200;
    public static final int FIRE_COOLDOWN_TICKS = 8;
    private ArtilleryConstants() { }
}
