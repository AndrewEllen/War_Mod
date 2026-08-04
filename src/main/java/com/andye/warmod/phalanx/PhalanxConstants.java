package com.andye.warmod.phalanx;

public final class PhalanxConstants {
    public static final int MAX_TURRETS_PER_LEVEL = 256;
    public static final int MAX_ACTIVE_BULLETS_PER_LEVEL = 4096;

    public static final int ROUNDS_PER_TURRET = 128;
    public static final int BURST_SIZE = 12;
    public static final int SHOT_INTERVAL_TICKS = 2;
    public static final int BURST_RECOVERY_TICKS = 8;

    /**
     * Extra server simulation time after the predicted interception time.
     */
    public static final int BULLET_LIFETIME_SAFETY_TICKS = 16;

    /**
     * Firing range is an X/Z cylinder around the turret. Height is not part of
     * the range check.
     *
     * Radius: 400 blocks
     * Diameter: 800 blocks
     */
    public static final double
        HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS = 400.0;

    /**
     * Tracking itself has no artificial distance limit. The turret can rotate
     * and spin up before a target enters the firing cylinder.
     */
    public static final double
        UNLIMITED_TRACKING_RANGE_BLOCKS =
            Double.POSITIVE_INFINITY;

    public static final double MAX_ELEVATION_DEGREES = 80.0;
    public static final double MIN_ELEVATION_DEGREES = -5.0;

    public static final double
        BULLET_SPEED_BLOCKS_PER_TICK = 12.0;

    public static final double
        BULLET_GRAVITY_PER_TICK_SQUARED = 0.015;

    public static final double BASE_SPREAD_DEGREES = 1.5;
    public static final double MAX_SPREAD_DEGREES = 5.0;
    public static final double BLOOM_PER_SHOT_DEGREES = 0.22;

    public static final double
        BLOOM_RECOVERY_DEGREES_PER_TICK = 0.10;

    /**
     * A successful point-defence hit creates one small HE-style airburst.
     */
    public static final float
        INTERCEPTION_EXPLOSION_STRENGTH = 3.0F;

    /**
     * Other supported airborne targets inside this radius are also destroyed
     * by the same airburst.
     */
    public static final double
        INTERCEPTION_CHAIN_RADIUS_BLOCKS = 10.0;

    private PhalanxConstants() {
    }
}
