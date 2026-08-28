package com.andye.warmod.radar.station.client;

/** Shared authored pivots for the 3x3 Blockbench radar model. */
public final class RadarStationVisualGeometry {
    public static final float MODEL_SCALE = 11.0F / 112.0F;
    public static final float YAW_PIVOT_MODEL_Y = 18.0F;
    public static final float DISH_PIVOT_MODEL_Y = 25.0F;
    public static final float YAW_PIVOT_Y = YAW_PIVOT_MODEL_Y * MODEL_SCALE;
    public static final float DISH_PIVOT_Y = DISH_PIVOT_MODEL_Y * MODEL_SCALE;
    public static final float DISH_ELEVATION_ANGLE = 18.0F;
    public static final float MODEL_YAW_OFFSET_DEGREES = 180.0F;
    private RadarStationVisualGeometry() { }
}
