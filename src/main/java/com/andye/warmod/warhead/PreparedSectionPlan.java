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
    private final byte[] mutationCategories;
    private final BitSet semanticMask;
    private final BitSet survivalMask;
    private final BitSet supportCheckMask;

    public PreparedSectionPlan(final int sectionY, final long sourceRevision,
        final PreparedMutationPhase phase, final int[] localIndices,
        final int[] expectedStateIds, final int[] finalStateIds,
        final BitSet semanticMask, final BitSet survivalMask) {
        this(sectionY, sourceRevision, phase, localIndices, expectedStateIds,
            finalStateIds, fillOther(localIndices), semanticMask, survivalMask);
    }

    public PreparedSectionPlan(final int sectionY, final long sourceRevision,
        final PreparedMutationPhase phase, final int[] localIndices,
        final int[] expectedStateIds, final int[] finalStateIds,
        final byte[] mutationCategories, final BitSet semanticMask,
        final BitSet survivalMask) {
        this(sectionY, sourceRevision, phase, localIndices, expectedStateIds,
            finalStateIds, mutationCategories, semanticMask, survivalMask,
            new BitSet());
    }

    public PreparedSectionPlan(final int sectionY, final long sourceRevision,
        final PreparedMutationPhase phase, final int[] localIndices,
        final int[] expectedStateIds, final int[] finalStateIds,
        final byte[] mutationCategories, final BitSet semanticMask,
        final BitSet survivalMask, final BitSet supportCheckMask) {
        if (phase == null || localIndices == null || expectedStateIds == null
            || finalStateIds == null || mutationCategories == null
            || semanticMask == null || survivalMask == null
            || supportCheckMask == null
            || localIndices.length != expectedStateIds.length
            || localIndices.length != finalStateIds.length
            || localIndices.length != mutationCategories.length) {
            throw new IllegalArgumentException("Invalid prepared section plan");
        }
        for (byte category : mutationCategories) {
            WarheadMutationCategory.fromWireId(category);
        }
        this.sectionY = sectionY;
        this.sourceRevision = sourceRevision;
        this.phase = phase;
        this.localIndices = localIndices.clone();
        this.expectedStateIds = expectedStateIds.clone();
        this.finalStateIds = finalStateIds.clone();
        this.mutationCategories = mutationCategories.clone();
        this.semanticMask = (BitSet)semanticMask.clone();
        this.survivalMask = (BitSet)survivalMask.clone();
        this.supportCheckMask = (BitSet)supportCheckMask.clone();
    }

    public int sectionY() { return sectionY; }
    public long sourceRevision() { return sourceRevision; }
    public PreparedMutationPhase phase() { return phase; }
    public int mutationCount() { return localIndices.length; }
    int[] localIndicesUnsafe() { return localIndices; }
    int[] expectedStateIdsUnsafe() { return expectedStateIds; }
    int[] finalStateIdsUnsafe() { return finalStateIds; }
    byte[] mutationCategoriesUnsafe() { return mutationCategories; }
    WarheadMutationCategory mutationCategory(final int index) {
        return WarheadMutationCategory.fromWireId(mutationCategories[index]);
    }
    BitSet semanticMaskUnsafe() { return semanticMask; }
    BitSet survivalMaskUnsafe() { return survivalMask; }
    BitSet supportCheckMaskUnsafe() { return supportCheckMask; }

    private static byte[] fillOther(final int[] indices) {
        if (indices == null) return null;
        byte[] categories = new byte[indices.length];
        java.util.Arrays.fill(categories, WarheadMutationCategory.OTHER.wireId());
        return categories;
    }
}
