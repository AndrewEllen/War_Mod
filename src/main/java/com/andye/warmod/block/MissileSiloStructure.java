package com.andye.warmod.block;

import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.silo.MissileSiloManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Set;

public final class MissileSiloStructure {
    private MissileSiloStructure() {}

    public static BlockPos position(
            final BlockPos centre, final MissileSiloPart part, final Direction facing) {
        return centre.offset(part.rotatedOffset(facing));
    }

    public static Set<BlockPos> positions(final BlockPos centre, final Direction facing) {
        // Ticket recovery runs before the core chunk is necessarily loaded. A five-wide
        // footprint safely covers either format without loading a chunk to infer its size.
        return positions(centre, facing, true);
    }

    public static Set<BlockPos> positions(
            final BlockPos centre, final Direction facing, final boolean large) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (MissileSiloPart part : MissileSiloPart.values()) {
            if (part.belongsTo(large)) result.add(position(centre, part, facing));
        }
        return Set.copyOf(result);
    }

    public static boolean isLarge(
            final net.minecraft.world.level.BlockGetter level, final BlockPos centre) {
        BlockState state = level.getBlockState(centre);
        return state.is(ModBlocks.MISSILE_SILO) && state.getValue(MissileSiloBlock.LARGE);
    }

    public static Set<ChunkPos> footprintChunks(final BlockPos centre, final Direction facing) {
        Set<ChunkPos> result = new LinkedHashSet<>();
        for (BlockPos pos : positions(centre, facing))
            result.add(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        return Set.copyOf(result);
    }

    public static boolean isComplete(
            final ServerLevel level, final BlockPos centre, final Direction facing) {
        boolean large = isLarge(level, centre);
        for (MissileSiloPart part : MissileSiloPart.values()) {
            if (!part.belongsTo(large)) continue;
            BlockState state = level.getBlockState(position(centre, part, facing));
            if (!state.is(ModBlocks.MISSILE_SILO)
                    || state.getValue(MissileSiloBlock.PART) != part
                    || state.getValue(MissileSiloBlock.FACING) != facing
                    || state.getValue(MissileSiloBlock.LARGE) != large) return false;
        }
        return level.getBlockEntity(centre) instanceof MissileSiloBlockEntity;
    }

    public static void teardown(
            final ServerLevel level,
            final BlockPos anyPart,
            final BlockState state,
            final boolean dropItems) {
        if (!state.is(ModBlocks.MISSILE_SILO)) return;
        Direction facing = state.getValue(MissileSiloBlock.FACING);
        boolean large = state.getValue(MissileSiloBlock.LARGE);
        BlockPos centre = state.getValue(MissileSiloBlock.PART).resolveCenter(anyPart, facing);
        MissileSiloBlockEntity silo =
                level.getBlockEntity(centre) instanceof MissileSiloBlockEntity found ? found : null;
        if (silo != null && silo.teardownInProgress()) return;
        if (silo != null) silo.setTeardownInProgress(true);
        try {
            if (silo != null) MissileSiloManager.unregister(level, silo);
            if (!large)
                MissileSiloGuidanceFrameStructure.teardown(level, centre, facing, dropItems);
            if (dropItems) {
                if (silo != null) {
                    if (!silo.reservedMissile().isEmpty())
                        Block.popResource(level, centre, silo.reservedMissile().copy());
                    if (!silo.missileStack().isEmpty())
                        Block.popResource(level, centre, silo.missileStack().copy());
                    silo.clearContent();
                }
                Block.popResource(level, centre, new ItemStack(ModItems.MISSILE_SILO));
            }
            for (BlockPos partPos : positions(centre, facing, large)) {
                BlockState partState = level.getBlockState(partPos);
                if (partState.is(ModBlocks.MISSILE_SILO)
                        && partState.getValue(MissileSiloBlock.LARGE) == large
                        && partState.getValue(MissileSiloBlock.FACING) == facing
                        && partState
                                .getValue(MissileSiloBlock.PART)
                                .resolveCenter(partPos, facing)
                                .equals(centre)) {
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        } finally {
            if (silo != null) silo.setTeardownInProgress(false);
        }
    }
}
