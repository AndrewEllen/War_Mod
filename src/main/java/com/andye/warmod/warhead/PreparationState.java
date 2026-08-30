package com.andye.warmod.warhead;

public enum PreparationState {
    REQUESTED,
    ACQUIRING_CHUNKS,
    SNAPSHOTTING,
    COMPILING,
    READY,
    IMPACT_SEALED,
    COMMITTING,
    COMPLETE,
    REVALIDATING,
    RECOMPILING,
    CANCELLED
}
