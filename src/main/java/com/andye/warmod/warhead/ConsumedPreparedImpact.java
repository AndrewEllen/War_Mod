package com.andye.warmod.warhead;

import java.util.UUID;

public record ConsumedPreparedImpact(UUID preparationId, PreparedImpactPlan plan) {
    public ConsumedPreparedImpact {
        if (preparationId == null || plan == null) {
            throw new IllegalArgumentException("Invalid consumed prepared impact");
        }
    }
}
