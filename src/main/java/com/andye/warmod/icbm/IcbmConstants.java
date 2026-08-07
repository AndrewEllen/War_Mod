package com.andye.warmod.icbm;

public final class IcbmConstants {
    public static final int IGNITION_TICKS = 8;
    public static final int BOOST_TICKS = 120;
    public static final int MINIMUM_COAST_TICKS = 180;
    public static final int MAXIMUM_COAST_TICKS = 4096;
    public static final int MINIMUM_TERMINAL_TICKS = 76;
    public static final int MAXIMUM_TERMINAL_TICKS = 140;

    public static final double COAST_GRAVITY_BLOCKS_PER_TICK_SQUARED = 0.016;
    public static final double MAXIMUM_BOOST_HORIZONTAL_DRIFT_BLOCKS = 256.0;
    public static final double BOOST_HORIZONTAL_LEAD_FRACTION = 0.14;
    public static final double BOOST_HORIZONTAL_LEAD_MINIMUM_BLOCKS = 56.0;
    public static final double BOOST_CURVE_START_MINIMUM_WORLD_Y = 108.0;
    public static final double BOOST_CURVE_START_MINIMUM_HEIGHT_ABOVE_LAUNCH = 72.0;
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
    public static final double CARRIER_VISUAL_RANGE_BLOCKS = 8192.0;
    public static final double MAXIMUM_STRATEGIC_RANGE_BLOCKS = 100_000.0;
    public static final double MAXIMUM_CARRIER_SPEED_BLOCKS_PER_TICK = 48.0;
    public static final double PREFERRED_CARRIER_SPEED_BLOCKS_PER_TICK = 42.0;

    public static final int MAX_ACTIVE_CLIENT_ICBMS = 24;
    public static final int MAX_ACTIVE_SPENT_STAGES = 48;
    public static final int SPENT_STAGE_MINIMUM_LIFETIME_TICKS = 50;
    public static final int SPENT_STAGE_MAXIMUM_LIFETIME_TICKS = 90;
    public static final int CARRIER_CHUNK_RADIUS = 1;
    public static final int BOOST_TICKET_TAIL_TICKS = 10;
    public static final int SEPARATION_TICKET_LEAD_TICKS = 200;
    public static final int SEPARATION_TICKET_TAIL_TICKS = 40;

    /** Pre-arm the target corridor ten seconds before terminal separation. */
    public static final int FINAL_APPROACH_TICKET_LEAD_TICKS = 200;
    public static final int TERMINAL_STREAM_RADIUS = 1;
    public static final int TERMINAL_STREAM_LOOKAHEAD_TICKS = 48;
    public static final int TERMINAL_TARGET_LEAD_TICKS = 60;

    /** 7x7 chunks centred on the predicted impact. */
    public static final int IMPACT_CHUNK_RADIUS = 3;

    /** Two minutes at the normal 20 TPS server rate. */
    public static final int IMPACT_CHUNK_TAIL_TICKS = 2400;

    /** Load the incoming direction far enough out for 500-block defences. */
    public static final double FINAL_APPROACH_CORRIDOR_BLOCKS = 500.0;

    /** Three chunks wide, centred on the incoming path. */
    public static final int FINAL_APPROACH_CORRIDOR_RADIUS = 1;

    public static final double FINAL_APPROACH_SAMPLE_SPACING_BLOCKS = 16.0;
    public static final int MAX_TERMINAL_STREAM_CHUNKS = 64;
    public static final int TERMINAL_CHUNK_WAIT_TIMEOUT_TICKS = 400;
    public static final double TERMINAL_STREAM_SAMPLE_SPACING_BLOCKS = 8.0;

    private IcbmConstants() {
    }
}
