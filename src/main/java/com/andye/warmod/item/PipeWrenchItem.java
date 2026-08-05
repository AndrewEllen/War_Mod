package com.andye.warmod.item;

import com.andye.warmod.block.ItemPipeBlock;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.logistics.PipeConnectionMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class PipeWrenchItem extends Item {
    public PipeWrenchItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        BlockPos position = context.getClickedPos();
        BlockState state = context.getLevel().getBlockState(position);

        if (!state.is(ModBlocks.ITEM_PIPE)) {
            return InteractionResult.PASS;
        }

        Direction side = ItemPipeBlock.targetedSide(
            state,
            position,
            context.getClickLocation(),
            context.getClickedFace()
        );

        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        PipeConnectionMode current = ItemPipeBlock.mode(state, side);
        PipeConnectionMode next = ItemPipeBlock.nextMode(
            level,
            position,
            side,
            current
        );

        level.setBlock(
            position,
            state.setValue(ItemPipeBlock.property(side), next),
            Block.UPDATE_ALL
        );

        synchronizePipeNeighbour(level, position, side, next);

        if (context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(Component.literal(
                side.getSerializedName().toUpperCase()
                    + ": "
                    + next.displayName()
            ));
        }

        return InteractionResult.SUCCESS_SERVER;
    }

    private static void synchronizePipeNeighbour(
        final ServerLevel level,
        final BlockPos position,
        final Direction side,
        final PipeConnectionMode next
    ) {
        BlockPos neighbourPosition = position.relative(side);
        BlockState neighbour = level.getBlockState(neighbourPosition);

        if (!neighbour.is(ModBlocks.ITEM_PIPE)) {
            return;
        }

        Direction opposite = side.getOpposite();
        PipeConnectionMode neighbourMode =
            ItemPipeBlock.mode(neighbour, opposite);

        if (next == PipeConnectionMode.PIPE) {
            if (neighbourMode != PipeConnectionMode.PIPE) {
                level.setBlock(
                    neighbourPosition,
                    neighbour.setValue(
                        ItemPipeBlock.property(opposite),
                        PipeConnectionMode.PIPE
                    ),
                    Block.UPDATE_ALL
                );
            }
        } else if (neighbourMode == PipeConnectionMode.PIPE) {
            level.setBlock(
                neighbourPosition,
                neighbour.setValue(
                    ItemPipeBlock.property(opposite),
                    PipeConnectionMode.NONE
                ),
                Block.UPDATE_ALL
            );
        }
    }
}
