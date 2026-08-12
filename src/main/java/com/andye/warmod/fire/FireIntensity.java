package com.andye.warmod.fire;

import com.mojang.serialization.Codec;
import java.util.Locale;

public enum FireIntensity {
    SMALL("Small", 0.42F, 70, 18),
    MEDIUM("Structure", 0.70F, 120, 11),
    INFERNO("Inferno", 1.00F, 190, 6);

    public static final Codec<FireIntensity> CODEC = Codec.STRING.xmap(
        value -> {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return MEDIUM;
            }
        },
        value -> value.name().toLowerCase(Locale.ROOT)
    );

    private final String displayName;
    private final float heat;
    private final int surfaceBurnTicks;
    private final int spreadIntervalTicks;

    FireIntensity(final String displayName, final float heat, final int surfaceBurnTicks,
        final int spreadIntervalTicks) {
        this.displayName = displayName;
        this.heat = heat;
        this.surfaceBurnTicks = surfaceBurnTicks;
        this.spreadIntervalTicks = spreadIntervalTicks;
    }

    public String displayName() { return displayName; }
    public float heat() { return heat; }
    public int surfaceBurnTicks() { return surfaceBurnTicks; }
    public int spreadIntervalTicks() { return spreadIntervalTicks; }
    public FireIntensity next() { return values()[(ordinal() + 1) % values().length]; }
}
