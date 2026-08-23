package com.andye.warmod.warhead;

public final class WarheadConstants {
	public static final double TARGET_RANGE_BLOCKS = 1000.0;
	public static final double VISUAL_RANGE_BLOCKS = 3072.0;

	public static final double MINIMUM_SPAWN_HEIGHT_ABOVE_TARGET = 160.0;
	public static final double PREFERRED_SPAWN_HEIGHT_ABOVE_TARGET = 190.0;

	public static final int MINIMUM_FLIGHT_TICKS = 38;
	public static final int MAXIMUM_FLIGHT_TICKS = 64;
	public static final double TRAJECTORY_SPEED_BLOCKS_PER_TICK = 3.5;

	public static final float EXPLOSION_STRENGTH = 16.0F;

	public static final int MAX_ACTIVE_CLIENT_WARHEADS = 32;
	public static final int MAX_ACTIVE_CLIENT_IMPACTS = 64;

	public static final int WARHEAD_VISUAL_LIFETIME_GRACE_TICKS = 40;
	public static final int IMPACT_VISUAL_LIFETIME_TICKS = 260;

	public static final double PRESSURE_RING_MAX_RADIUS = 48.0;
	public static final double DUST_RING_MAX_RADIUS = 40.0;

	private WarheadConstants() {
	}
}
