package com.andye.warmod.warhead;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record WarheadPreparationRequest(UUID preparationId,
    UUID radarRootTrackId, ResourceKey<Level> dimension,
    List<PreparedImpactSpec> impacts, long expectedImpactTick,
    WarheadDeliveryMode deliveryMode) {
    public WarheadPreparationRequest {
        if (preparationId == null || radarRootTrackId == null || dimension == null
            || impacts == null || impacts.isEmpty() || deliveryMode == null) {
            throw new IllegalArgumentException("Invalid warhead preparation request");
        }
        impacts = List.copyOf(impacts);
        HashSet<UUID> ids = new HashSet<>();
        for (PreparedImpactSpec impact : impacts) {
            if (impact == null || !ids.add(impact.impactId())) {
                throw new IllegalArgumentException("Impact IDs must be unique");
            }
        }
    }
}
