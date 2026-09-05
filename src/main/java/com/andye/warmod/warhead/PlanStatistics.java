package com.andye.warmod.warhead;

import java.util.HashMap;
import java.util.Map;

public record PlanStatistics(long changedChunks, long changedSections,
    long changedBlocks, long changedBiomeQuarts, long semanticMutations,
    long estimatedBytes, MutationCategoryCounts categories,
    Map<Integer, Long> replacementHistogram) {

    public PlanStatistics {
        if (categories == null || replacementHistogram == null) {
            throw new IllegalArgumentException("Missing plan diagnostics");
        }
        replacementHistogram = Map.copyOf(replacementHistogram);
    }

    public PlanStatistics add(final PlanStatistics other) {
        HashMap<Integer, Long> histogram = new HashMap<>(replacementHistogram);
        other.replacementHistogram.forEach((state, count) -> histogram.merge(state,
            count, Long::sum));
        return new PlanStatistics(changedChunks + other.changedChunks,
            changedSections + other.changedSections,
            changedBlocks + other.changedBlocks,
            changedBiomeQuarts + other.changedBiomeQuarts,
            semanticMutations + other.semanticMutations,
            estimatedBytes + other.estimatedBytes,
            categories.add(other.categories), histogram);
    }

    public static PlanStatistics empty() {
        return new PlanStatistics(0L, 0L, 0L, 0L, 0L, 0L,
            MutationCategoryCounts.empty(), Map.of());
    }
}
