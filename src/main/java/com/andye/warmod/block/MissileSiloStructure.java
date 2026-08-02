package com.andye.warmod.block;

import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.silo.MissileSiloManager;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MissileSiloStructure {
    private MissileSiloStructure() {
    }

    public static BlockPos position(final BlockPos centre, final MissileSiloPart part, final Direction facing) {
        return centre.offset(part.rotatedOffset(facing));
    }

    public static Set<BlockPos> positions(final BlockPos centre, final Direction facing) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (MissileSiloPart part : MissileSiloPart.values()) result.add(position(centre, part, facing));
        return Set.copyOf(result);
    }

    public static Set<ChunkPos> footprintChunks(final BlockPos centre, final Direction facing) {
        Set<ChunkPos> result = new LinkedHashSet<>();
        for (BlockPos pos : positions(centre, facing)) result.add(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        return Set.copyOf(result);
    }

    public static boolean isComplete(final ServerLevel level, final BlockPos centre, final Direction facing) {
        for (MissileSiloPart part : MissileSiloPart.values()) {
            BlockState state = level.getBlockState(position(centre, part, facing));
            if (!state.is(ModBlocks.MISSILE_SILO) || state.getValue(MissileSiloBlock.PART) != part
                || state.getValue(MissileSiloBlock.FACING) != facing) return false;
        }
        return level.getBlockEntity(centre) instanceof MissileSiloBlockEntity;
    }

    public static void teardown(final ServerLevel level, final BlockPos anyPart, final BlockState state,
        final boolean dropItems) {
        if (!state.is(ModBlocks.MISSILE_SILO)) return;
        Direction facing = state.getValue(MissileSiloBlock.FACING);
        BlockPos centre = state.getValue(MissileSiloBlock.PART).resolveCenter(anyPart, facing);
        MissileSiloBlockEntity silo = level.getBlockEntity(centre) instanceof MissileSiloBlockEntity found ? found : null;
        if (silo != null && silo.teardownInProgress()) return;
        if (silo != null) silo.setTeardownInProgress(true);
        try {
            if (silo != null) MissileSiloManager.unregister(level, silo);
            MissileSiloGuidanceFrameStructure.teardown(level, centre, facing, dropItems);
            if (dropItems) {
                if (silo != null) {
                    if (!silo.reservedMissile().isEmpty()) Block.popResource(level, centre, silo.reservedMissile().copy());
                    if (!silo.missileStack().isEmpty()) Block.popResource(level, centre, silo.missileStack().copy());
                    silo.clearContent();
                }
                Block.popResource(level, centre, new ItemStack(ModItems.MISSILE_SILO));
            }
            for (BlockPos partPos : positions(centre, facing)) {
                if (level.getBlockState(partPos).is(ModBlocks.MISSILE_SILO)) {
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        } finally {
            if (silo != null) silo.setTeardownInProgress(false);
        }
    }
}
