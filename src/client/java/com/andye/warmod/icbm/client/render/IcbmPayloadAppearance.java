package com.andye.warmod.icbm.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;

public enum IcbmPayloadAppearance {
    CONVENTIONAL(151, 119, 59),
    NUCLEAR(224, 186, 41);

    private final int red;
    private final int green;
    private final int blue;

    IcbmPayloadAppearance(final int red, final int green, final int blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public int red() { return red; }
    public int green() { return green; }
    public int blue() { return blue; }

    public static IcbmPayloadAppearance from(final WarheadPayloadType type) {
        return type == WarheadPayloadType.NUCLEAR ? NUCLEAR : CONVENTIONAL;
    }
}