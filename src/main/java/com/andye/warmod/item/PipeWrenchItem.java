package com.andye.warmod.item;

import com.andye.warmod.block.ItemPipeBlock;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.logistics.PipeConnectionMode;
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
        BlockState state = context.getLevel().getBlockState(
            context.getClickedPos()
        );

        if (!state.is(ModBlocks.ITEM_PIPE)) {
            return InteractionResult.PASS;
        }

        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        var side = context.getClickedFace();
        PipeConnectionMode current = ItemPipeBlock.mode(state, side);
        PipeConnectionMode next = ItemPipeBlock.nextMode(
            level,
            context.getClickedPos(),
            side,
            current
        );

        level.setBlock(
            context.getClickedPos(),
            state.setValue(ItemPipeBlock.property(side), next),
            Block.UPDATE_ALL
        );

        if (next == PipeConnectionMode.PIPE) {
            var neighbourPosition = context.getClickedPos().relative(side);
            BlockState neighbour = level.getBlockState(neighbourPosition);

            if (neighbour.is(ModBlocks.ITEM_PIPE)) {
                level.setBlock(
                    neighbourPosition,
                    neighbour.setValue(
                        ItemPipeBlock.property(side.getOpposite()),
                        PipeConnectionMode.PIPE
                    ),
                    Block.UPDATE_ALL
                );
            }
        } else if (current == PipeConnectionMode.PIPE) {
            var neighbourPosition = context.getClickedPos().relative(side);
            BlockState neighbour = level.getBlockState(neighbourPosition);

            if (neighbour.is(ModBlocks.ITEM_PIPE)
                && ItemPipeBlock.mode(neighbour, side.getOpposite())
                    == PipeConnectionMode.PIPE) {
                level.setBlock(
                    neighbourPosition,
                    neighbour.setValue(
                        ItemPipeBlock.property(side.getOpposite()),
                        PipeConnectionMode.NONE
                    ),
                    Block.UPDATE_ALL
                );
            }
        }

        if (context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(Component.literal(
                side.getSerializedName().toUpperCase()
                    + ": "
                    + next.displayName()
            ));
        }

        return InteractionResult.SUCCESS_SERVER;
    }
}
