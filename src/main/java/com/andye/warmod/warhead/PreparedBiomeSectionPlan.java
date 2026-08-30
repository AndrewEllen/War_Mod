package com.andye.warmod.warhead;

public record PreparedBiomeSectionPlan(int sectionY, long quartMask) {
    public PreparedBiomeSectionPlan {
        if (quartMask == 0L) throw new IllegalArgumentException("Empty biome mask");
    }
}
