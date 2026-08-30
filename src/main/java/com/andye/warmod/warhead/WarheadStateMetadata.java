package com.andye.warmod.warhead;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable state-ID metadata captured once, never once per copied block. */
final class WarheadStateMetadata {
    private final int[] flags;
    private final float[] explosionResistance;
    private final int airStateId;

    private WarheadStateMetadata(final int[] flags,
        final float[] explosionResistance, final int airStateId) {
        this.flags = flags;
        this.explosionResistance = explosionResistance;
        this.airStateId = airStateId;
    }

    static WarheadStateMetadata capture(final ServerLevel level) {
        int size = Block.BLOCK_STATE_REGISTRY.size();
        int[] flags = new int[size];
        float[] resistance = new float[size];
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            int id = Block.getId(state);
            if (id < 0 || id >= size) continue;
            boolean indestructible;
            try {
                indestructible = state.getDestroySpeed(level, BlockPos.ZERO) < 0.0F;
            } catch (RuntimeException failure) {
                indestructible = true;
            }
            flags[id] = WarheadSnapshotFlags.classify(state, indestructible, false);
            resistance[id] = Math.max(state.getBlock().getExplosionResistance(),
                state.getFluidState().getExplosionResistance());
        }
        return new WarheadStateMetadata(flags, resistance,
            Block.getId(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
    }

    int flags(final int stateId) {
        return stateId >= 0 && stateId < flags.length
            ? flags[stateId] : WarheadSnapshotFlags.SEMANTIC;
    }

    float explosionResistance(final int stateId) {
        return stateId >= 0 && stateId < explosionResistance.length
            ? explosionResistance[stateId] : Float.POSITIVE_INFINITY;
    }

    boolean relevantVertical(final int stateId) {
        return WarheadSnapshotFlags.relevantVertical(flags(stateId));
    }

    int airStateId() { return airStateId; }
}
