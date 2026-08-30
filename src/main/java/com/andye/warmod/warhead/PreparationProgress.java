package com.andye.warmod.warhead;

public record PreparationProgress(PreparationState state, int requiredChunks,
    int ticketedChunks, int readyChunks, int snapshottedChunks,
    int compiledChunks, int publishedImpactPlans) {
    public double readyFraction() {
        return requiredChunks <= 0 ? 0.0
            : Math.min(1.0, compiledChunks / (double)requiredChunks);
    }
}
