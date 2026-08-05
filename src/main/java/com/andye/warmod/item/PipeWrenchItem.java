package com.andye.warmod.item;

import com.andye.warmod.block.ItemPipeBlock;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.logistics.PipeConnectionMode;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PipeWrenchItem extends Item {
    private static boolean interactionsRegistered;

    public PipeWrenchItem(final Properties properties) {
        super(properties);
    }

    /**
     * Handles the wrench before an adjacent chest or other inventory consumes
     * the right-click. This makes an ordinary right-click work; crouching is no
     * longer required to reach the pipe connector behind the inventory face.
     */
    public static void registerInteractions() {
        if (interactionsRegistered) {
            return;
        }

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player.isSpectator()
                || player.getItemInHand(hand).getItem()
                    != ModItems.PIPE_WRENCH) {
                return InteractionResult.PASS;
            }

            Target target = resolveTarget(
                level,
                hit.getBlockPos(),
                hit.getLocation(),
                hit.getDirection()
            );

            return configure(level, player, target);
        });
        interactionsRegistered = true;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        Target target = resolveTarget(
            context.getLevel(),
            context.getClickedPos(),
            context.getClickLocation(),
            context.getClickedFace()
        );
        return configure(context.getLevel(), context.getPlayer(), target);
    }

    private static InteractionResult configure(
        final Level level,
        final Player player,
        final Target target
    ) {
        if (target == null) {
            return InteractionResult.PASS;
        }

        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }

        BlockState state = server.getBlockState(target.position());

        if (!state.is(ModBlocks.ITEM_PIPE)) {
            return InteractionResult.PASS;
        }

        PipeConnectionMode current = ItemPipeBlock.mode(
            state,
            target.side()
        );
        PipeConnectionMode next = ItemPipeBlock.nextMode(
            server,
            target.position(),
            target.side(),
            current
        );

        server.setBlock(
            target.position(),
            state.setValue(ItemPipeBlock.property(target.side()), next),
            Block.UPDATE_ALL
        );

        synchronizePipeNeighbour(
            server,
            target.position(),
            target.side(),
            next
        );

        if (player != null) {
            player.sendSystemMessage(Component.literal(
                target.side().getSerializedName().toUpperCase()
                    + ": "
                    + next.displayName()
            ));
        }

        return InteractionResult.SUCCESS_SERVER;
    }

    private static Target resolveTarget(
        final Level level,
        final BlockPos clickedPosition,
        final Vec3 hitLocation,
        final Direction clickedFace
    ) {
        BlockState clickedState = level.getBlockState(clickedPosition);

        if (clickedState.is(ModBlocks.ITEM_PIPE)) {
            return new Target(
                clickedPosition,
                ItemPipeBlock.targetedSide(
                    clickedState,
                    clickedPosition,
                    hitLocation,
                    clickedFace
                )
            );
        }

        BlockPos pipePosition = clickedPosition.relative(clickedFace);
        BlockState pipeState = level.getBlockState(pipePosition);

        if (!pipeState.is(ModBlocks.ITEM_PIPE)) {
            return null;
        }

        return new Target(pipePosition, clickedFace.getOpposite());
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
        PipeConnectionMode neighbourMode = ItemPipeBlock.mode(
            neighbour,
            opposite
        );

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

    private record Target(BlockPos position, Direction side) {
    }
}
