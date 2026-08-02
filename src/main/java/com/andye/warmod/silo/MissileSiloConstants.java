package com.andye.warmod.silo;

public final class MissileSiloConstants {
    public static final int STRUCTURE_WIDTH = 3;
    public static final int STRUCTURE_DEPTH = 3;
    public static final int MAX_MISSILES = 16;
    public static final int MISSILE_ITEM_STACK_SIZE = 16;
    public static final int MAX_FORCE_LOADED_SILOS_PER_LEVEL = 256;
    public static final int MAX_PENDING_LAUNCHES_PER_LEVEL = 64;
    public static final int TARGET_PREPARATION_TIMEOUT_TICKS = 400;
    public static final int LAUNCHING_STATE_TICKS = 20;
    public static final int PRE_RELOAD_COOLDOWN_TICKS = 20;
    public static final int POST_LAUNCH_COOLDOWN_TICKS = PRE_RELOAD_COOLDOWN_TICKS;
    public static final int RELOAD_ANIMATION_TICKS = 60;
    public static final double RELOAD_START_OFFSET_Y = -2.8;
    public static final double RELOAD_END_OFFSET_Y = 0.0;
    public static final double MISSILE_COLLISION_WIDTH = 0.85;
    public static final double MISSILE_COLLISION_HEIGHT = 2.8;
    public static final double COLLISION_STEP_BLOCKS = 0.20;

    private MissileSiloConstants() {
    }
}
