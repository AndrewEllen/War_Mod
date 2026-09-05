package com.andye.warmod.warhead.network;

/** Monotonic authoritative lifecycle for terminal-warhead visuals. */
public enum WarheadNetworkState {
    FLIGHT,
    WAITING_FOR_WORLD,
    IMPACTED,
    REMOVED;

    public boolean terminal() { return this == IMPACTED || this == REMOVED; }
}
