package com.andye.warmod.warhead;

record WarheadSnapshotRequirement(PreparedImpactSpec impact, WarheadFootprint footprint) {
    WarheadSnapshotRequirement {
        if (impact == null || footprint == null) throw new IllegalArgumentException("Invalid snapshot requirement");
    }
}
