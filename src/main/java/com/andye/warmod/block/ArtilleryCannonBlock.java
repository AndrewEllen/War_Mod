package com.andye.warmod.block;

import com.andye.warmod.block.entity.ArtilleryCannonBlockEntity;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.item.TargetDesignatorItem;
import com.andye.warmod.item.YieldWarheadItem;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.menu.ArtilleryCannonMenu;
import com.andye.warmod.menu.ModMenus;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class ArtilleryCannonBlock extends BaseEntityBlock implements WorldlyContainerHolder {
    public static final MapCodec<ArtilleryCannonBlock> CODEC = simpleCodec(ArtilleryCannonBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(1, 0, 1, 15, 7, 15),
        Block.box(4, 7, 4, 12, 13, 12),
        Block.box(6, 11, 6, 10, 16, 10));

    public ArtilleryCannonBlock(final BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends ArtilleryCannonBlock> codec() { return CODEC; }
    @Override public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ArtilleryCannonBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level,
        final BlockState state, final BlockEntityType<T> type) {
        return level.isClientSide() ? null
            : createTickerHelper(type, ModBlockEntities.ARTILLERY_CANNON,
                ArtilleryCannonBlockEntity::serverTick);
    }
    @Override public WorldlyContainer getContainer(final BlockState state,
        final LevelAccessor level, final BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ArtilleryCannonBlockEntity cannon ? cannon : null;
    }

    @Override
    public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state,
        final @Nullable LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ArtilleryCannonBlockEntity cannon) {
            cannon.initialize(placer instanceof Player player ? player : null);
        }
    }

    @Override
    protected InteractionResult useItemOn(final ItemStack held, final BlockState state,
        final Level level, final BlockPos pos, final Player player, final InteractionHand hand,
        final BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ArtilleryCannonBlockEntity cannon)) {
            return InteractionResult.FAIL;
        }
        if (held.getItem() instanceof TargetDesignatorItem && player.isShiftKeyDown()) {
            TargetCoordinates target = held.get(ModDataComponents.TARGET_COORDINATES);
            if (target == null || !target.isValid() || !target.dimension().equals(level.dimension())) {
                player.sendSystemMessage(Component.literal("Target designator has no valid same-dimension target"));
            } else {
                cannon.setStoredTarget(target);
                player.sendSystemMessage(Component.literal("Artillery target programmed"));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.getItem() instanceof YieldWarheadItem) {
            for (int slot = 0; slot < cannon.getContainerSize(); slot++) {
                if (!cannon.canPlaceItem(slot, held)) continue;
                cannon.setItem(slot, held.copyWithCount(1));
                held.consume(1, player);
                return InteractionResult.SUCCESS_SERVER;
            }
            player.sendSystemMessage(Component.literal("Artillery magazine is full"));
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level,
        final BlockPos pos, final Player player, final BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ArtilleryCannonBlockEntity cannon)) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new ExtendedMenuProvider<ModMenus.ArtilleryOpeningData>() {
                @Override public ModMenus.ArtilleryOpeningData getScreenOpeningData(final ServerPlayer viewer) {
                    return new ModMenus.ArtilleryOpeningData(pos);
                }
                @Override public Component getDisplayName() { return Component.literal("Artillery Cannon"); }
                @Override public AbstractContainerMenu createMenu(final int id, final Inventory inventory,
                    final Player viewer) { return new ArtilleryCannonMenu(id, inventory, cannon); }
            });
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override protected boolean hasAnalogOutputSignal(final BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(final BlockState state, final Level level,
        final BlockPos pos, final Direction direction) {
        if (!(level.getBlockEntity(pos) instanceof ArtilleryCannonBlockEntity cannon)) return 0;
        int rounds = cannon.roundsLoaded();
        return rounds == 0 ? 0 : 1 + (rounds - 1) * 14 / 15;
    }
    @Override protected VoxelShape getShape(final BlockState state,
        final net.minecraft.world.level.BlockGetter level, final BlockPos pos,
        final CollisionContext context) { return SHAPE; }
}
