package com.andye.warmod.warhead;

public record PlanStatistics(long changedChunks, long changedSections,
    long changedBlocks, long changedBiomeQuarts, long semanticMutations,
    long estimatedBytes) {
    public PlanStatistics add(final PlanStatistics other) {
        return new PlanStatistics(changedChunks + other.changedChunks,
            changedSections + other.changedSections,
            changedBlocks + other.changedBlocks,
            changedBiomeQuarts + other.changedBiomeQuarts,
            semanticMutations + other.semanticMutations,
            estimatedBytes + other.estimatedBytes);
    }

    public static PlanStatistics empty() {
        return new PlanStatistics(0L, 0L, 0L, 0L, 0L, 0L);
    }
}
