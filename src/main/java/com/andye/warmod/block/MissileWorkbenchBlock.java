package com.andye.warmod.block;

import com.andye.warmod.block.entity.MissileWorkbenchBlockEntity;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.menu.MissileWorkbenchMenu;
import com.andye.warmod.menu.ModMenus;
import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

public final class MissileWorkbenchBlock extends BaseEntityBlock implements WorldlyContainerHolder {
    public static final MapCodec<MissileWorkbenchBlock> CODEC =
            simpleCodec(MissileWorkbenchBlock::new);
    public static final EnumProperty<MissileWorkbenchPart> PART =
            EnumProperty.create("part", MissileWorkbenchPart.class);
    public static final Property<Direction> FACING = HorizontalDirectionalBlock.FACING;
    // The model has an inset perimeter, so it must not advertise itself as a
    // full occluder. The guaranteed solid plinth remains a physical support.
    private static final VoxelShape PLINTH_SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public MissileWorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(PART, MissileWorkbenchPart.LEFT)
                .setValue(FACING, Direction.NORTH));
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
    protected VoxelShape getCollisionShape(final BlockState state,
            final net.minecraft.world.level.BlockGetter level, final BlockPos pos,
            final CollisionContext context) {
        return PLINTH_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos companion = context.getClickedPos().relative(facing.getClockWise());
        Level level = context.getLevel();
        if (level.isOutsideBuildHeight(companion)
                || !level.getBlockState(companion).canBeReplaced()
                || level.getBlockEntity(companion) != null) return null;
        return defaultBlockState().setValue(PART, MissileWorkbenchPart.LEFT)
                .setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(final Level level, final BlockPos position,
            final BlockState state, final @Nullable LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (state.getValue(PART) != MissileWorkbenchPart.LEFT) return;
        BlockPos companion = position.relative(state.getValue(FACING).getClockWise());
        BlockState companionState = state.setValue(PART, MissileWorkbenchPart.RIGHT);
        if (level.getBlockState(companion).canBeReplaced()) {
            level.setBlock(companion, companionState, Block.UPDATE_ALL);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART).isController()
                ? new MissileWorkbenchBlockEntity(pos, state) : null;
    }

    public static @Nullable MissileWorkbenchBlockEntity resolve(
            final LevelAccessor level, final BlockPos pos, final BlockState state) {
        if (!state.is(ModBlocks.MISSILE_WORKBENCH)) return null;
        BlockPos controller = state.getValue(PART)
                .controllerPosition(pos, state.getValue(FACING));
        return level.getBlockEntity(controller) instanceof MissileWorkbenchBlockEntity bench
                ? bench : null;
    }

    @Override
    public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
        return resolve(level, pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || !state.getValue(PART).isController()
                ? null
                : createTickerHelper(
                        type,
                        ModBlockEntities.MISSILE_WORKBENCH,
                        MissileWorkbenchBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        MissileWorkbenchBlockEntity bench = resolve(level, pos, state);
        if (player instanceof ServerPlayer server && bench != null)
            server.openMenu(
                    new ExtendedMenuProvider<ModMenus.ArtilleryOpeningData>() {
                        public ModMenus.ArtilleryOpeningData getScreenOpeningData(
                                ServerPlayer viewer) {
                            return new ModMenus.ArtilleryOpeningData(bench.getBlockPos());
                        }

                        public Component getDisplayName() {
                            return Component.literal("Missile Workbench");
                        }

                        public AbstractContainerMenu createMenu(
                                int id, Inventory inventory, Player viewer) {
                            return new MissileWorkbenchMenu(id, inventory, bench);
                        }
                    });
        return bench == null ? InteractionResult.FAIL : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos,
            final BlockState state, final Player player) {
        if (level instanceof ServerLevel server) {
            MissileWorkbenchStructure.teardown(server, pos, state, true);
        }
        return state;
    }

    @Override
    public void playerDestroy(final Level level, final Player player, final BlockPos pos,
            final BlockState state, final @Nullable BlockEntity blockEntity,
            final ItemStack tool) {
        // teardown already emits exactly one workbench item and the controller inventory.
    }

    @Override
    protected void onExplosionHit(final BlockState state, final ServerLevel level,
            final BlockPos pos, final Explosion explosion,
            final BiConsumer<ItemStack, BlockPos> onHit) {
        MissileWorkbenchStructure.teardown(level, pos, state, true);
    }
}
