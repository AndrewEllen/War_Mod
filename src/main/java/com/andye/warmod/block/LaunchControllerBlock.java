package com.andye.warmod.block;

import com.andye.warmod.block.entity.LaunchControllerBlockEntity;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.item.RemoteLaunchDesignatorItem;
import com.andye.warmod.item.ControllerLinkingToolItem;
import com.andye.warmod.menu.LaunchControllerMenu;
import com.andye.warmod.menu.ModMenus;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class LaunchControllerBlock extends BaseEntityBlock {
    public static final MapCodec<LaunchControllerBlock> CODEC =
        simpleCodec(LaunchControllerBlock::new);
    public static final Property<Direction> FACING =
        HorizontalDirectionalBlock.FACING;

    public LaunchControllerBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends LaunchControllerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
        final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder
    ) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(
            FACING,
            context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
        final BlockPos position,
        final BlockState state
    ) {
        return new LaunchControllerBlockEntity(position, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        final Level level,
        final BlockState state,
        final BlockEntityType<T> type
    ) {
        return level.isClientSide()
            ? null
            : createTickerHelper(
                type,
                ModBlockEntities.LAUNCH_CONTROLLER,
                LaunchControllerBlockEntity::serverTick
            );
    }

    @Override
    protected InteractionResult useItemOn(
        final ItemStack held,
        final BlockState state,
        final Level level,
        final BlockPos position,
        final Player player,
        final InteractionHand hand,
        final BlockHitResult hit
    ) {
        if (held.getItem() instanceof ControllerLinkingToolItem) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (!(level.getBlockEntity(position)
                instanceof LaunchControllerBlockEntity controller)) {
                return InteractionResult.FAIL;
            }
            return ControllerLinkingToolItem.selectController(
                held,
                controller,
                player
            );
        }
        if (!(held.getItem() instanceof RemoteLaunchDesignatorItem)
            || !player.isShiftKeyDown()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(position)
            instanceof LaunchControllerBlockEntity controller)) {
            return InteractionResult.FAIL;
        }
        return RemoteLaunchDesignatorItem.useOnController(
            held,
            controller,
            player
        );
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos position,
        final Player player,
        final BlockHitResult hit
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
            || !(level.getBlockEntity(position)
                instanceof LaunchControllerBlockEntity controller)) {
            return InteractionResult.FAIL;
        }
        serverPlayer.openMenu(
            new ExtendedMenuProvider<ModMenus.LaunchControllerOpeningData>() {
                @Override
                public ModMenus.LaunchControllerOpeningData getScreenOpeningData(
                    final ServerPlayer viewer
                ) {
                    return new ModMenus.LaunchControllerOpeningData(
                        position,
                        controller.controllerId()
                    );
                }

                @Override
                public Component getDisplayName() {
                    return Component.literal("Launch Controller");
                }

                @Override
                public AbstractContainerMenu createMenu(
                    final int id,
                    final Inventory inventory,
                    final Player viewer
                ) {
                    return new LaunchControllerMenu(id, inventory, controller);
                }
            }
        );
        return InteractionResult.SUCCESS_SERVER;
    }
}
