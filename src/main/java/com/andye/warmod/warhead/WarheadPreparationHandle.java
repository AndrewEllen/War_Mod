package com.andye.warmod.warhead;

import java.util.List;
import java.util.UUID;

public interface WarheadPreparationHandle {
    UUID id();

    PreparationState state();

    PreparationProgress progress();

    boolean ready();

    void retarget(List<PreparedImpactSpec> impacts, long expectedImpactTick);

    PreparedImpactPlan consumeReadyPlan(UUID impactId);

    void cancel(CancellationReason reason);
}
