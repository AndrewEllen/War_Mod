package com.andye.warmod.warhead.client.render;

import net.minecraft.world.phys.Vec3;

/** Common source contract for merged nuclear particle fields. */
public interface NuclearCloudSource {
    Vec3 offset();
    double ageTicks();
    float visualScale();
    long seed();

    record Basic(Vec3 offset, double ageTicks, float visualScale, long seed)
        implements NuclearCloudSource {
        public Basic {
            if (offset == null || !offset.isFinite()) offset = Vec3.ZERO;
        }
    }
}
