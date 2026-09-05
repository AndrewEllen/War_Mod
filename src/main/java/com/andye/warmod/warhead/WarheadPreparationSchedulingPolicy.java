package com.andye.warmod.warhead;

/** Pure ordering rule for the coordinator's shared snapshot/compiler budgets. */
final class WarheadPreparationSchedulingPolicy {
    private WarheadPreparationSchedulingPolicy() { }

    static int priority(final PreparationState state, final int activeCommits) {
        return activeCommits > 0
            || state == PreparationState.IMPACT_SEALED
            || state == PreparationState.COMMITTING ? 0 : 1;
    }

    static int startIndex(final int itemCount, final long turn) {
        if (itemCount <= 1) return 0;
        return Math.floorMod(turn, itemCount);
    }
}
