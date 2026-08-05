package com.andye.warmod.phalanx;

public final class PhalanxConstants {
    public static final int MAX_TURRETS_PER_LEVEL = 256;
    public static final int MAX_ACTIVE_BULLETS_PER_LEVEL = 4096;

    public static final int AMMO_SLOT_COUNT = 8;
    public static final int ROUNDS_PER_TURRET = AMMO_SLOT_COUNT * 64;

    public static final int BURST_SIZE = 12;
    public static final int SHOT_INTERVAL_TICKS = 2;
    public static final int BURST_RECOVERY_TICKS = 8;

    public static final int BULLET_LIFETIME_SAFETY_TICKS = 16;

    /**
     * A turret may begin turning and spinning up once a supported target is
     * inside this X/Z radius. Height is deliberately ignored.
     */
    public static final double HORIZONTAL_TRACKING_RADIUS_BLOCKS = 1000.0;

    /**
     * The gun may fire only inside this X/Z radius. The resulting firing
     * cylinder is 800 blocks in diameter and extends from world bottom to top.
     */
    public static final double HORIZONTAL_ENGAGEMENT_RADIUS_BLOCKS = 400.0;

    public static final double MAX_ELEVATION_DEGREES = 80.0;
    public static final double MIN_ELEVATION_DEGREES = -5.0;

    public static final double BULLET_SPEED_BLOCKS_PER_TICK = 12.0;
    public static final double BULLET_GRAVITY_PER_TICK_SQUARED = 0.015;

    public static final double BASE_SPREAD_DEGREES = 1.0;
    public static final double MAX_SPREAD_DEGREES = 5.0;
    public static final double BLOOM_PER_SHOT_DEGREES = 0.22;
    public static final double BLOOM_RECOVERY_DEGREES_PER_TICK = 0.10;

    public static final float INTERCEPTION_EXPLOSION_STRENGTH = 3.0F;
    public static final double INTERCEPTION_CHAIN_RADIUS_BLOCKS = 10.0;

    private PhalanxConstants() {
    }
}
