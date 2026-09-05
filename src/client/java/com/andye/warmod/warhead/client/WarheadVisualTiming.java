package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;

/** Client access to the shared nuclear impact animation and terrain-front clock. */
public final class WarheadVisualTiming {
    public static final double NUCLEAR_TIME_SCALE = WarheadVisualMath.NUCLEAR_TIME_SCALE;

    private WarheadVisualTiming() { }

    public static double age(final WarheadPayloadType payloadType, final double physicalAge) {
        return payloadType == WarheadPayloadType.NUCLEAR
            ? physicalAge * NUCLEAR_TIME_SCALE : physicalAge;
    }
}
