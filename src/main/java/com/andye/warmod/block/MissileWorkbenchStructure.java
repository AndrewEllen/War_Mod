package com.andye.warmod.block;

import com.andye.warmod.item.ModItems;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Atomic removal for the two workbench halves, with one item drop and one inventory drop. */
public final class MissileWorkbenchStructure {
    private static final Set<String> TEARDOWN = new HashSet<>();

    private MissileWorkbenchStructure() { }

    public static void teardown(final ServerLevel level, final BlockPos anyPosition,
        final BlockState state, final boolean dropWorkbench) {
        if (!state.is(ModBlocks.MISSILE_WORKBENCH)) return;
        Direction facing = state.getValue(MissileWorkbenchBlock.FACING);
        BlockPos controller = state.getValue(MissileWorkbenchBlock.PART)
            .controllerPosition(anyPosition, facing);
        String key = level.dimension().identifier() + ":" + controller.asLong();
        synchronized (TEARDOWN) {
            if (!TEARDOWN.add(key)) return;
        }
        try {
            if (dropWorkbench) {
                Block.popResource(level, controller, new ItemStack(ModItems.MISSILE_WORKBENCH));
            }
            removeIfWorkbenchHalf(level, controller, MissileWorkbenchPart.LEFT, facing);
            removeIfWorkbenchHalf(level, controller.relative(facing.getClockWise()),
                MissileWorkbenchPart.RIGHT, facing);
        } finally {
            synchronized (TEARDOWN) {
                TEARDOWN.remove(key);
            }
        }
    }

    private static void removeIfWorkbenchHalf(final ServerLevel level, final BlockPos position,
        final MissileWorkbenchPart expectedPart, final Direction facing) {
        BlockState state = level.getBlockState(position);
        if (state.is(ModBlocks.MISSILE_WORKBENCH)
                && state.getValue(MissileWorkbenchBlock.PART) == expectedPart
                && state.getValue(MissileWorkbenchBlock.FACING) == facing) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
