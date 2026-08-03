package com.andye.warmod.antiair;

public final class AntiAirConstants {
    public static final double DEFENDED_TRAJECTORY_RADIUS_BLOCKS = 500.0;
    public static final double MAX_POWERED_INTERCEPT_ARC_BLOCKS = 500.0;
    public static final double INTERCEPT_FUSE_RADIUS_BLOCKS = 10.0;
    public static final double INTERCEPT_FUSE_RADIUS_SQUARED = 100.0;
    public static final double MAXIMUM_INTERCEPTOR_SPEED_BLOCKS_PER_TICK = 6.0;
    public static final double DEBUG_NO_TARGET_CEILING_Y = 1000.0;
    public static final int INTERCEPT_SAMPLE_STEP_TICKS = 2;
    public static final int MINIMUM_INTERCEPT_LEAD_TICKS = 12;
    public static final int MK_II_SELF_DESTRUCT_DELAY_TICKS = 6;
    public static final int MAXIMUM_FALLBACK_LIFETIME_TICKS = 600;
    public static final int MAXIMUM_ACTIVE_INTERCEPTORS_PER_LEVEL = 2048;
    public static final int IGNITION_TICKS = 10;
    public static final int BOOST_TICKS = 55;
    public static final double FALLBACK_SONIC_BOOM_SPEED_BLOCKS_PER_TICK = 4.0;

    private AntiAirConstants() { }
}