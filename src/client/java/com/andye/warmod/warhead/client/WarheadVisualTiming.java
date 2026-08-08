package com.andye.warmod.warhead.client;

import com.andye.warmod.warhead.WarheadPayloadType;

/** Client-only visual clock adjustments. Gameplay, server timing and audio arrival remain unscaled. */
public final class WarheadVisualTiming {
    public static final double NUCLEAR_TIME_SCALE = 1.33;

    private WarheadVisualTiming() { }

    public static double age(final WarheadPayloadType payloadType, final double physicalAge) {
        return payloadType == WarheadPayloadType.NUCLEAR
            ? physicalAge * NUCLEAR_TIME_SCALE : physicalAge;
    }
}
