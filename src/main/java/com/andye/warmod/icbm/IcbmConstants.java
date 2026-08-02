package com.andye.warmod.icbm;

public final class IcbmConstants {
	public static final int IGNITION_TICKS = 8;
	public static final int BOOST_TICKS = 120;
	public static final int MINIMUM_COAST_TICKS = 180;
	public static final int MAXIMUM_COAST_TICKS = 360;
	public static final int MINIMUM_TERMINAL_TICKS = 76;
	public static final int MAXIMUM_TERMINAL_TICKS = 140;
	public static final double COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED = 0.016;
	public static final double MINIMUM_VIRTUAL_LAUNCH_DISTANCE = 500.0;
	public static final double MAXIMUM_VIRTUAL_LAUNCH_DISTANCE = 580.0;
	public static final double MAXIMUM_VIRTUAL_SIDE_OFFSET = 18.0;
	public static final double FINAL_FALLBACK_LAUNCH_DISTANCE = 12.0;
	public static final double MINIMUM_BURNOUT_HEIGHT_ABOVE_LAUNCH = 300.0;
	public static final double PREFERRED_BURNOUT_HEIGHT_ABOVE_LAUNCH = 360.0;
	public static final double MINIMUM_SEPARATION_HEIGHT_ABOVE_TARGET = 300.0;
	public static final double PREFERRED_SEPARATION_HEIGHT_ABOVE_TARGET = 360.0;
	public static final double SEPARATION_HORIZONTAL_OFFSET = 72.0;
	public static final int MAX_ACTIVE_CLIENT_ICBMS = 24;
	public static final int MAX_ACTIVE_SPENT_STAGES = 48;
	public static final int SPENT_STAGE_MINIMUM_LIFETIME_TICKS = 50;
	public static final int SPENT_STAGE_MAXIMUM_LIFETIME_TICKS = 90;
	public static final double VISUAL_RANGE_BLOCKS = 2048.0;
	private IcbmConstants() { }
}