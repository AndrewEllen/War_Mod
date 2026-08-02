package com.andye.warmod.icbm;

public final class IcbmConstants {
	public static final int IGNITION_TICKS = 8;
	public static final int BOOST_TICKS = 120;
	public static final int MINIMUM_COAST_TICKS = 180;
	public static final int MAXIMUM_COAST_TICKS = 360;
	public static final int MINIMUM_TERMINAL_TICKS = 76;
	public static final int MAXIMUM_TERMINAL_TICKS = 140;
	public static final double COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED = 0.016;
	public static final double MAXIMUM_BOOST_HORIZONTAL_DRIFT_BLOCKS = 2.0;
	public static final double BOOST_ASCENT_CONTROL_DISTANCE = 72.0;
	public static final double COAST_APPROACH_CONTROL_DISTANCE = 128.0;
	public static final double COAST_TERMINAL_CONTROL_HEIGHT = 64.0;
	public static final double MINIMUM_VIRTUAL_LAUNCH_DISTANCE = 500.0;
	public static final double MAXIMUM_VIRTUAL_LAUNCH_DISTANCE = 560.0;
	public static final double MAXIMUM_VIRTUAL_SIDE_OFFSET = 16.0;
	public static final double MINIMUM_BURNOUT_HEIGHT_ABOVE_LAUNCH = 300.0;
	public static final double PREFERRED_BURNOUT_HEIGHT_ABOVE_LAUNCH = 360.0;
	public static final double MINIMUM_SEPARATION_HEIGHT_ABOVE_TARGET = 400.0;
	public static final double PREFERRED_SEPARATION_HEIGHT_ABOVE_TARGET = 480.0;
	public static final double SEPARATION_HORIZONTAL_OFFSET = 128.0;
	public static final int MAX_ACTIVE_CLIENT_ICBMS = 24;
	public static final int MAX_ACTIVE_SPENT_STAGES = 48;
	public static final int SPENT_STAGE_MINIMUM_LIFETIME_TICKS = 50;
	public static final int SPENT_STAGE_MAXIMUM_LIFETIME_TICKS = 90;
	public static final double CARRIER_VISUAL_RANGE_BLOCKS = 8192.0;
	public static final double MAXIMUM_COMMAND_ROUTE_LENGTH = 32768.0;
	public static final int CARRIER_CHUNK_RADIUS = 1;
	public static final int TARGET_CHUNK_RADIUS = 2;
	public static final int TERMINAL_TICKET_LEAD_TICKS = 80;
	public static final int TERMINAL_TICKET_TAIL_TICKS = 120;
	private IcbmConstants() { }
}