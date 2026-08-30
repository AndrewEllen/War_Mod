package com.andye.warmod.warhead;

public enum PreparationState {
    REQUESTED,
    ACQUIRING_CHUNKS,
    SNAPSHOTTING,
    COMPILING,
    READY,
    COMMITTING,
    COMPLETE,
    REVALIDATING,
    RECOMPILING,
    CANCELLED
}
