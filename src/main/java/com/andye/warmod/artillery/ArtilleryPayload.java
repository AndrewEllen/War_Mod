package com.andye.warmod.artillery;

import com.andye.warmod.warhead.WarheadYield;

public record ArtilleryPayload(WarheadYield yield, boolean cluster) {
    public ArtilleryPayload {
        if (yield == null) throw new IllegalArgumentException("yield");
    }
    public String displayName(final String delivery) {
        return yield.displayName() + (cluster ? " Cluster " : " ") + delivery;
    }
}
