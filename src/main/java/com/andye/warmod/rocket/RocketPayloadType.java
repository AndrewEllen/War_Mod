package com.andye.warmod.rocket;

import com.andye.warmod.warhead.WarheadPayloadType;
import org.jspecify.annotations.Nullable;

public enum RocketPayloadType {
    HE(3.0, 14, .9, null),
    CONVENTIONAL_ICBM(2.3, 24, 1.7, WarheadPayloadType.CONVENTIONAL),
    NUCLEAR_ICBM(1.9, 40, 2.1, WarheadPayloadType.NUCLEAR);

    private final double cruiseSpeed;
    private final int cooldown;
    private final double length;
    private final @Nullable WarheadPayloadType warhead;

    RocketPayloadType(final double cruiseSpeed, final int cooldown, final double length,
            final @Nullable WarheadPayloadType warhead) {
        this.cruiseSpeed = cruiseSpeed;
        this.cooldown = cooldown;
        this.length = length;
        this.warhead = warhead;
    }

    /** Speed reached at motor burnout; launches begin much slower than this. */
    public double speed() { return cruiseSpeed; }
    public double launchSpeed() { return Math.min(0.62, cruiseSpeed * 0.26); }
    public int cooldown() { return cooldown; }
    public double length() { return length; }
    public @Nullable WarheadPayloadType warhead() { return warhead; }

    public static RocketPayloadType byId(final int id) {
        var values = values();
        return values[Math.max(0, Math.min(values.length - 1, id))];
    }
}
