package com.andye.warmod.warhead;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record PreparedImpactPlan(UUID impactId, Vec3 center,
    WarheadFootprint footprint, Long2ObjectMap<PreparedChunkPlan> chunks,
    int finalActivationTick, PlanStatistics statistics) {
    public PreparedImpactPlan {
        if (impactId == null || center == null || !center.isFinite()
            || footprint == null || chunks == null || finalActivationTick < 0
            || finalActivationTick > 19 || statistics == null) {
            throw new IllegalArgumentException("Invalid prepared impact plan");
        }
        chunks = Long2ObjectMaps.unmodifiable(new Long2ObjectOpenHashMap<>(chunks));
    }
}
