package com.andye.warmod.block;

import com.andye.warmod.block.entity.MissileWorkbenchBlockEntity;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.menu.MissileWorkbenchMenu;
import com.andye.warmod.menu.ModMenus;
import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.Nullable;

public final class MissileWorkbenchBlock extends BaseEntityBlock implements WorldlyContainerHolder {
    public static final MapCodec<MissileWorkbenchBlock> CODEC =
            simpleCodec(MissileWorkbenchBlock::new);

    public MissileWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MissileWorkbenchBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MissileWorkbenchBlockEntity(pos, state);
    }

    @Override
    public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MissileWorkbenchBlockEntity bench
                ? bench
                : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(
                        type,
                        ModBlockEntities.MISSILE_WORKBENCH,
                        MissileWorkbenchBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof MissileWorkbenchBlockEntity bench)
            server.openMenu(
                    new ExtendedMenuProvider<ModMenus.ArtilleryOpeningData>() {
                        public ModMenus.ArtilleryOpeningData getScreenOpeningData(
                                ServerPlayer viewer) {
                            return new ModMenus.ArtilleryOpeningData(pos);
                        }

                        public Component getDisplayName() {
                            return Component.literal("Missile Workbench");
                        }

                        public AbstractContainerMenu createMenu(
                                int id, Inventory inventory, Player viewer) {
                            return new MissileWorkbenchMenu(id, inventory, bench);
                        }
                    });
        return InteractionResult.SUCCESS;
    }
}
