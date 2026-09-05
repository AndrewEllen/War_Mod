package com.andye.warmod.warhead;

import java.util.List;
import net.minecraft.world.level.ChunkPos;

public record PreparedChunkPlan(ChunkPos chunk, long sourceRevision,
    int activationTick, List<PreparedSectionPlan> blockSections,
    List<PreparedBiomeSectionPlan> biomeSections,
    List<PreparedFireMutation> fireMutations, int[] changedColumns,
    long estimatedCost) {
    public PreparedChunkPlan {
        if (chunk == null || activationTick < 0 || activationTick > 15
            || blockSections == null || biomeSections == null
            || fireMutations == null || changedColumns == null
            || estimatedCost < 0L) {
            throw new IllegalArgumentException("Invalid prepared chunk plan");
        }
        blockSections = List.copyOf(blockSections);
        biomeSections = List.copyOf(biomeSections);
        fireMutations = List.copyOf(fireMutations);
        changedColumns = changedColumns.clone();
    }

    @Override
    public int[] changedColumns() {
        return changedColumns.clone();
    }

    int[] changedColumnsUnsafe() {
        return changedColumns;
    }
}
