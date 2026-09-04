package com.andye.warmod.silo;

public final class MissileSiloConstants {
    public static final int STRUCTURE_WIDTH = 5;
    public static final int STRUCTURE_DEPTH = 5;
    public static final int MAX_MISSILES = 16;
    public static final int MISSILE_ITEM_STACK_SIZE = 16;
    public static final int MAX_FORCE_LOADED_SILOS_PER_LEVEL = 256;
    public static final int MAX_PENDING_LAUNCHES_PER_LEVEL = 64;
    public static final int TARGET_PREPARATION_TIMEOUT_TICKS = 400;
    public static final int OPENING_ANIMATION_TICKS = 24;
    /** Hold fully open until even the 5.2-block ICBM has cleared the door plane. */
    public static final int DOOR_CLOSE_DELAY_TICKS = 36;
    public static final int DOOR_CLOSE_ANIMATION_TICKS = 20;
    public static final int LAUNCHING_STATE_TICKS =
            DOOR_CLOSE_DELAY_TICKS + DOOR_CLOSE_ANIMATION_TICKS;
    public static final int PRE_RELOAD_COOLDOWN_TICKS = 20;
    public static final int POST_LAUNCH_COOLDOWN_TICKS = PRE_RELOAD_COOLDOWN_TICKS;
    public static final int RELOAD_ANIMATION_TICKS = 60;
    /** Model-centre offset: the 5.2-block ICBM nose begins behind the recessed throat plane. */
    public static final double MISSILE_HIDDEN_CENTER_OFFSET_Y = -3.0;
    public static final int VIRTUAL_LAUNCH_SHAFT_DEPTH_BLOCKS = 6;
    public static final double MISSILE_COLLISION_WIDTH = 0.85;
    public static final double MISSILE_COLLISION_HEIGHT = 5.2;
    public static final double COLLISION_STEP_BLOCKS = 0.20;

    private MissileSiloConstants() {
    }
}
