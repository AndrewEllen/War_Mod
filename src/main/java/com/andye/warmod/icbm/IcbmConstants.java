package com.andye.warmod.icbm;

import com.andye.warmod.warhead.WarheadConstants;

public final class IcbmConstants {
	public static final int IGNITION_TICKS=6, BOOST_TICKS=90, MINIMUM_COAST_TICKS=140, MAXIMUM_COAST_TICKS=280;
	public static final int MINIMUM_TERMINAL_TICKS=56, MAXIMUM_TERMINAL_TICKS=100;
	public static final double COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED=0.022;
	public static final double PREFERRED_LAUNCH_DISTANCE_BEHIND_PLAYER=480.0, PREFERRED_LAUNCH_SIDE_OFFSET=12.0;
	public static final double FINAL_FALLBACK_LAUNCH_DISTANCE=12.0;
	public static final double MINIMUM_BURNOUT_HEIGHT_ABOVE_LAUNCH=210.0, PREFERRED_BURNOUT_HEIGHT_ABOVE_LAUNCH=250.0;
	public static final double MINIMUM_SEPARATION_HEIGHT_ABOVE_TARGET=220.0, PREFERRED_SEPARATION_HEIGHT_ABOVE_TARGET=270.0;
	public static final double SEPARATION_HORIZONTAL_OFFSET=52.0;
	public static final int MAX_ACTIVE_CLIENT_ICBMS=24, MAX_ACTIVE_SPENT_STAGES=48;
	public static final int SPENT_STAGE_MINIMUM_LIFETIME_TICKS=35, SPENT_STAGE_MAXIMUM_LIFETIME_TICKS=60;
	public static final double VISUAL_RANGE_BLOCKS=WarheadConstants.VISUAL_RANGE_BLOCKS;
	private IcbmConstants() { }
}
