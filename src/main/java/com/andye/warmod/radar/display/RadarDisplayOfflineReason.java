package com.andye.warmod.radar.display;

public enum RadarDisplayOfflineReason {
    NONE,
    UNLINKED,
    INVALID_STRUCTURE,
    STATION_MISSING,
    STATION_REPLACED,
    WRONG_DIMENSION,
    OUT_OF_LINK_RANGE,
    STATION_STRUCTURE_INVALID;

    public static RadarDisplayOfflineReason fromNetworkId(final int id) {
        RadarDisplayOfflineReason[] values = values();

        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException(
                "Invalid Radar Display offline reason: " + id
            );
        }

        return values[id];
    }
}
