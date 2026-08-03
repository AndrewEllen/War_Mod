package com.andye.warmod.radar.station;

import com.mojang.serialization.Codec;
import java.util.Locale;
import java.util.Optional;

public enum RadarRedstoneMode {
    ANALOG_DISTANCE("analog_distance"),
    INTERCEPT_TRIGGER_ONLY("intercept_trigger_only");

    public static final Codec<RadarRedstoneMode> CODEC = Codec.STRING.xmap(
        value -> fromSerializedName(value).orElse(ANALOG_DISTANCE), RadarRedstoneMode::serializedName);

    private final String serializedName;

    RadarRedstoneMode(String serializedName) { this.serializedName = serializedName; }

    public String serializedName() { return serializedName; }

    public static Optional<RadarRedstoneMode> fromSerializedName(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.toLowerCase(Locale.ROOT);
        for (RadarRedstoneMode mode : values()) if (mode.serializedName.equals(normalized)) return Optional.of(mode);
        return Optional.empty();
    }

    public static Optional<RadarRedstoneMode> fromNetworkId(int value) {
        return value >= 0 && value < values().length ? Optional.of(values()[value]) : Optional.empty();
    }

    public static boolean isRegistered(RadarRedstoneMode value) {
        if (value == null) return false;
        for (RadarRedstoneMode candidate : values()) if (candidate == value) return true;
        return false;
    }
}