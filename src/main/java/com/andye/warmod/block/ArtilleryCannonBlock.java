package com.andye.warmod.block;

import com.andye.warmod.artillery.ArtilleryPayloadItems;
import com.andye.warmod.block.entity.ArtilleryCannonBlockEntity;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.item.TargetDesignatorItem;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.menu.ArtilleryCannonMenu;
import com.andye.warmod.menu.ModMenus;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public final class ArtilleryCannonBlock extends BaseEntityBlock {
    public static final MapCodec<ArtilleryCannonBlock> CODEC = simpleCodec(ArtilleryCannonBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public ArtilleryCannonBlock(final Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends ArtilleryCannonBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(FACING); }
    @Override protected RenderShape getRenderShape(final BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(final BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }
    @Override public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) { return new ArtilleryCannonBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) { return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.ARTILLERY_CANNON, ArtilleryCannonBlockEntity::serverTick); }
    @Override protected InteractionResult useItemOn(final ItemStack held, final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand, final BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ArtilleryCannonBlockEntity cannon)) return InteractionResult.FAIL;
        if (held.getItem() instanceof TargetDesignatorItem && player.isShiftKeyDown()) {
            TargetCoordinates target = held.get(ModDataComponents.TARGET_COORDINATES);
            if (target == null || !target.dimension().equals(level.dimension())) player.sendSystemMessage(Component.literal("Target designator has no valid same-dimension target"));
            else cannon.setTarget(target);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (ArtilleryPayloadItems.isWarhead(held)) {
            int inserted = cannon.insert(held, player.isShiftKeyDown() ? held.getCount() : 1);
            if (inserted > 0) held.consume(inserted, player); else player.sendSystemMessage(Component.literal("Artillery accepts one warhead type, up to 16"));
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ArtilleryCannonBlockEntity cannon && player instanceof ServerPlayer serverPlayer) serverPlayer.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<ModMenus.ArtilleryOpeningData>() {
            @Override public ModMenus.ArtilleryOpeningData getScreenOpeningData(final ServerPlayer viewer) { return new ModMenus.ArtilleryOpeningData(pos); }
            @Override public Component getDisplayName() { return Component.literal("Artillery Cannon"); }
            @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(final int id, final net.minecraft.world.entity.player.Inventory inventory, final Player viewer) { return new ArtilleryCannonMenu(id, inventory, cannon); }
        });
        return InteractionResult.SUCCESS_SERVER;
    }
    @Override protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos, final net.minecraft.world.level.block.Block block, final net.minecraft.world.level.redstone.Orientation orientation, final boolean movedByPiston) { if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ArtilleryCannonBlockEntity cannon) cannon.markRedstoneCheck(); }
}
