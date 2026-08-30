package com.andye.warmod.warhead;

import net.minecraft.world.phys.Vec3;

record WarheadPreparationLeaseTarget(Vec3 center, WarheadFootprint footprint) {
    WarheadPreparationLeaseTarget {
        if (center == null || !center.isFinite() || footprint == null) {
            throw new IllegalArgumentException("Invalid preparation lease target");
        }
    }
}
