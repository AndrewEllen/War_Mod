package com.andye.warmod.warhead;

public enum CancellationReason {
    INTERCEPTED,
    RETARGETED,
    TIMEOUT,
    MALFORMED_DATA,
    ENTITY_REMOVED,
    DIMENSION_UNLOAD,
    SERVER_STOP,
    LAUNCH_FAILED,
    CLUSTER_CHILD_CANCELLED,
    UNPREPARED_FALLBACK,
    EXPLICIT
}
