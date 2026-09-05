package com.andye.warmod.warhead;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public record PreparedImpactSpec(UUID impactId, Vec3 target,
    WarheadPayloadType payload, WarheadYield yield, long seed,
    boolean customFire) {
    public PreparedImpactSpec {
        if (impactId == null || target == null || !target.isFinite()
            || payload == null || yield == null || yield.payloadType() != payload) {
            throw new IllegalArgumentException("Invalid prepared impact specification");
        }
    }
}
