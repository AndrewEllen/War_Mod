package com.andye.warmod.warhead;

import java.util.BitSet;

/** Compact parallel arrays; no boxed object is allocated per changed cell. */
public final class PreparedSectionPlan {
    private final int sectionY;
    private final long sourceRevision;
    private final PreparedMutationPhase phase;
    private final int[] localIndices;
    private final int[] expectedStateIds;
    private final int[] finalStateIds;
    private final BitSet semanticMask;
    private final BitSet survivalMask;

    public PreparedSectionPlan(final int sectionY, final long sourceRevision,
        final PreparedMutationPhase phase, final int[] localIndices,
        final int[] expectedStateIds, final int[] finalStateIds,
        final BitSet semanticMask, final BitSet survivalMask) {
        if (phase == null || localIndices == null || expectedStateIds == null
            || finalStateIds == null || semanticMask == null || survivalMask == null
            || localIndices.length != expectedStateIds.length
            || localIndices.length != finalStateIds.length) {
            throw new IllegalArgumentException("Invalid prepared section plan");
        }
        this.sectionY = sectionY;
        this.sourceRevision = sourceRevision;
        this.phase = phase;
        this.localIndices = localIndices.clone();
        this.expectedStateIds = expectedStateIds.clone();
        this.finalStateIds = finalStateIds.clone();
        this.semanticMask = (BitSet)semanticMask.clone();
        this.survivalMask = (BitSet)survivalMask.clone();
    }

    public int sectionY() { return sectionY; }
    public long sourceRevision() { return sourceRevision; }
    public PreparedMutationPhase phase() { return phase; }
    public int mutationCount() { return localIndices.length; }
    int[] localIndicesUnsafe() { return localIndices; }
    int[] expectedStateIdsUnsafe() { return expectedStateIds; }
    int[] finalStateIdsUnsafe() { return finalStateIds; }
    BitSet semanticMaskUnsafe() { return semanticMask; }
    BitSet survivalMaskUnsafe() { return survivalMask; }
}
