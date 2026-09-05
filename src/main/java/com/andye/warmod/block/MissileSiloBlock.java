package com.andye.warmod.block;

import com.andye.warmod.block.entity.MissileSiloBlockEntity;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.item.TargetDesignatorItem;
import com.andye.warmod.item.ControllerLinkingToolItem;
import com.andye.warmod.item.component.TargetCoordinates;
import com.andye.warmod.menu.MissileSiloMenu;
import com.andye.warmod.menu.ModMenus;
import com.andye.warmod.silo.MissilePayloadItems;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

public final class MissileSiloBlock extends BaseEntityBlock implements WorldlyContainerHolder {
    public static final MapCodec<MissileSiloBlock> CODEC = simpleCodec(MissileSiloBlock::new);
    public static final EnumProperty<MissileSiloPart> PART =
            EnumProperty.create("part", MissileSiloPart.class);
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LARGE =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("large");
    public static final Property<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape CENTRE_SHAPE =
            Shapes.or(
                    Block.box(0, 0, 0, 16, 6, 3), Block.box(0, 0, 13, 16, 6, 16),
                    Block.box(0, 0, 3, 3, 6, 13), Block.box(13, 0, 3, 16, 6, 13));
    private static final VoxelShape[][] LARGE_SHAPES = createLargeShapes();

    private static VoxelShape[][] createLargeShapes() {
        // Match the model's surface slabs, throat rim and curb without filling the opening.
        VoxelShape surface =
                Shapes.or(
                        ring(40, 20, 0, 2),
                        ring(38.333, 20, 2, 4),
                        ring(20, 15, 0, 4),
                        ring(23.333, 18.333, 3, 6),
                        ring(38.333, 33.333, 4, 7));
        VoxelShape[][] shapes = new VoxelShape[5][5];
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                shapes[x + 2][z + 2] =
                        Shapes.join(
                                surface.move(0.5 - x, 0, 0.5 - z),
                                Shapes.block(),
                                net.minecraft.world.phys.shapes.BooleanOp.AND);
            }
        }
        return shapes;
    }

    private static VoxelShape ring(double outer, double inner, double bottom, double top) {
        return Shapes.or(
                Block.box(-outer, bottom, -outer, outer, top, -inner),
                Block.box(-outer, bottom, inner, outer, top, outer),
                Block.box(-outer, bottom, -inner, -inner, top, inner),
                Block.box(inner, bottom, -inner, outer, top, inner));
    }

    public MissileSiloBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition
                        .any()
                        .setValue(PART, MissileSiloPart.CENTER)
                        .setValue(FACING, Direction.NORTH)
                        .setValue(LARGE, false));
    }

    @Override
    protected MapCodec<? extends MissileSiloBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, LARGE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return state.getValue(PART).isCenter() ? new MissileSiloBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        return level.isClientSide() || !state.getValue(PART).isCenter()
                ? null
                : createTickerHelper(
                        type, ModBlockEntities.MISSILE_SILO, MissileSiloBlockEntity::serverTick);
    }

    public static @Nullable MissileSiloBlockEntity resolve(
            final LevelAccessor level, final BlockPos pos, final BlockState state) {
        if (!state.is(ModBlocks.MISSILE_SILO)) return null;
        BlockPos centre = state.getValue(PART).resolveCenter(pos, state.getValue(FACING));
        return level.getBlockEntity(centre) instanceof MissileSiloBlockEntity silo ? silo : null;
    }

    @Override
    public WorldlyContainer getContainer(
            final BlockState state, final LevelAccessor level, final BlockPos pos) {
        return resolve(level, pos, state);
    }

    @Override
    protected InteractionResult useItemOn(
            final ItemStack held,
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Player player,
            final InteractionHand hand,
            final BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        MissileSiloBlockEntity silo = resolve(level, pos, state);
        if (silo == null) return InteractionResult.FAIL;
        if (held.getItem() instanceof ControllerLinkingToolItem) {
            return ControllerLinkingToolItem.addSilo(held, silo, player);
        }
        if (held.getItem() instanceof TargetDesignatorItem && player.isShiftKeyDown()) {
            TargetCoordinates target =
                    held.get(com.andye.warmod.item.component.ModDataComponents.TARGET_COORDINATES);
            if (target == null || !target.dimension().equals(level.dimension())) {
                player.sendSystemMessage(
                        Component.literal("Target designator has no valid same-dimension target"));
                return InteractionResult.SUCCESS_SERVER;
            }
            silo.setStoredTarget(target);
            player.sendSystemMessage(
                    Component.literal("Silo target programmed: " + format(target.position())));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.getItem() instanceof com.andye.warmod.item.RemoteLaunchDesignatorItem
                && player.isShiftKeyDown()) {
            com.andye.warmod.item.RemoteLaunchDesignatorItem.link(held, silo, player);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (MissilePayloadItems.isMissile(held)) {
            int moved = silo.insert(held, player.isShiftKeyDown() ? held.getCount() : 1);
            if (moved > 0) held.consume(moved, player);
            else
                player.sendSystemMessage(
                        Component.literal("Missile type incompatible or silo full"));
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Player player,
            final BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        MissileSiloBlockEntity silo = resolve(level, pos, state);
        if (silo == null) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            ItemStack extracted = silo.removeItem(0, 1);
            if (!extracted.isEmpty() && !player.getInventory().add(extracted))
                player.drop(extracted, false);
        } else if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new ExtendedMenuProvider<ModMenus.SiloOpeningData>() {
                        @Override
                        public ModMenus.SiloOpeningData getScreenOpeningData(
                                final ServerPlayer viewer) {
                            return new ModMenus.SiloOpeningData(silo.getBlockPos(), silo.siloId());
                        }

                        @Override
                        public Component getDisplayName() {
                            return Component.literal("Missile Silo");
                        }

                        @Override
                        public AbstractContainerMenu createMenu(
                                final int id, final Inventory inventory, final Player viewer) {
                            return new MissileSiloMenu(id, inventory, silo);
                        }
                    });
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final Direction direction) {
        MissileSiloBlockEntity silo = resolve(level, pos, state);
        int count = silo == null ? 0 : silo.missileStack().getCount();
        return count == 0 ? 0 : count >= 16 ? 15 : 1 + (count - 1) * 14 / 15;
    }

    @Override
    protected VoxelShape getShape(
            final BlockState state,
            final net.minecraft.world.level.BlockGetter level,
            final BlockPos pos,
            final CollisionContext context) {
        // The large model leaves the middle visually open. Keep the controller block
        // selectable so a player can still break the structure from its centre.
        if (state.getValue(PART).isCenter()) return Shapes.block();
        if (state.getValue(LARGE)) {
            BlockPos offset = state.getValue(PART).rotatedOffset(state.getValue(FACING));
            return LARGE_SHAPES[offset.getX() + 2][offset.getZ() + 2];
        }
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(
            final BlockState state,
            final net.minecraft.world.level.BlockGetter level,
            final BlockPos pos,
            final CollisionContext context) {
        if (!state.getValue(LARGE)) {
            return state.getValue(PART).isCenter() ? CENTRE_SHAPE : Shapes.block();
        }

        BlockPos offset = state.getValue(PART).rotatedOffset(state.getValue(FACING));
        VoxelShape surface = LARGE_SHAPES[offset.getX() + 2][offset.getZ() + 2];
        if (Math.abs(offset.getX()) > 1 || Math.abs(offset.getZ()) > 1
                || largeDoorsOpen(level, pos, state)) return surface;

        // The two closed leaves span the central 3x3 throat at curb height. This
        // supports players while the silo is idle without filling the open throat.
        return Shapes.or(surface, Block.box(0, 0, 0, 16, 6, 16));
    }

    private static boolean largeDoorsOpen(
            final net.minecraft.world.level.BlockGetter level,
            final BlockPos pos,
            final BlockState state) {
        BlockPos centre = state.getValue(PART).resolveCenter(pos, state.getValue(FACING));
        if (!(level.getBlockEntity(centre) instanceof MissileSiloBlockEntity silo)) return false;
        return silo.siloState() == MissileSiloState.PREPARING
                || silo.siloState() == MissileSiloState.LAUNCHING;
    }

    @Override
    public BlockState playerWillDestroy(
            final Level level, final BlockPos pos, final BlockState state, final Player player) {
        if (level instanceof ServerLevel server)
            MissileSiloStructure.teardown(server, pos, state, true);
        return state;
    }

    @Override
    public void playerDestroy(
            final Level level,
            final Player player,
            final BlockPos pos,
            final BlockState state,
            final @Nullable BlockEntity blockEntity,
            final ItemStack tool) {}

    @Override
    protected void onExplosionHit(
            final BlockState state,
            final ServerLevel level,
            final BlockPos pos,
            final Explosion explosion,
            final BiConsumer<ItemStack, BlockPos> onHit) {
        MissileSiloStructure.teardown(level, pos, state, true);
    }

    public static int maximumIncomingSignal(
            final ServerLevel level, final BlockPos centre, final Direction facing) {
        int maximum = 0;
        for (BlockPos part :
                MissileSiloStructure.positions(
                        centre, facing, MissileSiloStructure.isLarge(level, centre))) {
            maximum = Math.max(maximum, level.getBestNeighborSignal(part));
        }
        return Math.min(15, maximum);
    }

    public static boolean anyPartPowered(
            final ServerLevel level, final BlockPos centre, final Direction facing) {
        for (BlockPos pos :
                MissileSiloStructure.positions(
                        centre, facing, MissileSiloStructure.isLarge(level, centre)))
            if (level.hasNeighborSignal(pos)) return true;
        return false;
    }

    private static String format(final net.minecraft.world.phys.Vec3 pos) {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", pos.x, pos.y, pos.z);
    }
}
