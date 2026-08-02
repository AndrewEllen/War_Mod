package com.andye.warmod.icbm.client.render;

public final class IcbmVisualGeometry {
    public static final float BODY_RADIUS = 0.27F;
    public static final float BODY_LENGTH = 1.95F;
    public static final float NOSE_LENGTH = 0.52F;
    public static final float NOZZLE_LENGTH = 0.20F;
    public static final float FIN_ROOT_POSITION = -1.10F;
    public static final float FIN_SPAN = 0.24F;
    public static final float FIN_THICKNESS = 0.045F;
    public static final float PAYLOAD_BAND_POSITION = 0.48F;
    public static final float TOTAL_VISUAL_HEIGHT = 2.80F;
    public static final float BODY_BOTTOM = -TOTAL_VISUAL_HEIGHT * 0.5F + NOZZLE_LENGTH;
    public static final float BODY_TOP = BODY_BOTTOM + BODY_LENGTH;
    public static final float NOSE_TIP = BODY_TOP + NOSE_LENGTH;

    private IcbmVisualGeometry() { }
}