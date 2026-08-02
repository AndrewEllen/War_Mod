package com.andye.warmod.silo;

public enum MissileSiloLaunchFailure {
    NONE,
    INVALID_STRUCTURE,
    INVALID_TARGET,
    NO_AMMUNITION,
    BUSY,
    TARGET_LOADING_TIMEOUT,
    FLIGHT_REJECTED,
    TOO_MANY_PENDING_LAUNCHES
}
