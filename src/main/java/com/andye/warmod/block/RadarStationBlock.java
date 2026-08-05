package com.andye.warmod.block;

import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.block.entity.RadarStationBlockEntity;
import com.andye.warmod.radar.station.network.RadarStationNetworking;
import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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

public final class RadarStationBlock extends BaseEntityBlock {
    public static final MapCodec<RadarStationBlock> CODEC =
        simpleCodec(RadarStationBlock::new);
    public static final EnumProperty<RadarStationPart> PART =
        EnumProperty.create("part", RadarStationPart.class);
    public static final Property<Direction> FACING =
        HorizontalDirectionalBlock.FACING;

    private static final VoxelShape MAST =
        Block.box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0);
    private static final VoxelShape SUPPORT =
        Block.box(3.0, 0.0, 3.0, 13.0, 10.0, 13.0);

    public RadarStationBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(
            stateDefinition.any()
                .setValue(PART, RadarStationPart.BOTTOM_CENTER)
                .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected MapCodec<? extends RadarStationBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
        final StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(PART, FACING);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
        final BlockPos position,
        final BlockState state
    ) {
        return state.getValue(PART).centre()
            ? new RadarStationBlockEntity(position, state)
            : null;
    }

    public static @Nullable RadarStationBlockEntity resolve(
        final BlockGetter level,
        final BlockPos position,
        final BlockState state
    ) {
        if (!state.is(ModBlocks.RADAR_STATION)) {
            return null;
        }

        BlockPos centre = state.getValue(PART).resolveCentre(
            position,
            state.getValue(FACING)
        );

        return level.getBlockEntity(centre)
                instanceof RadarStationBlockEntity radar
            ? radar
            : null;
    }

    @Override
    protected VoxelShape getShape(
        final BlockState state,
        final BlockGetter level,
        final BlockPos position,
        final CollisionContext context
    ) {
        RadarStationPart part = state.getValue(PART);

        if (part.layer() == 0) {
            return Shapes.block();
        }

        if (part == RadarStationPart.MIDDLE_CENTER) {
            return MAST;
        }

        return part.layer() == 1 && part.corner()
            ? SUPPORT
            : Shapes.empty();
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

        RadarStationBlockEntity radar = resolve(level, position, state);

        if (radar == null) {
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown()) {
            player.sendSystemMessage(Component.literal(
                "Radar "
                    + radar.radarId().toString().substring(0, 8)
                    + " | Warning "
                    + (int)radar.warningRadius()
                    + " | Fire "
                    + (int)radar.fireRadius()
                    + " | Block "
                    + radar.redstoneSignal()
                    + "/15 | Comparator "
                    + radar.comparatorSignal()
                    + "/15"
            ));
        } else {
            RadarStationNetworking.open(
                (net.minecraft.server.level.ServerPlayer)player,
                radar
            );
        }

        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(
        final BlockState state,
        final BlockGetter level,
        final BlockPos position,
        final Direction direction
    ) {
        RadarStationBlockEntity radar = resolve(level, position, state);
        return radar == null ? 0 : radar.redstoneSignal();
    }

    @Override
    protected int getDirectSignal(
        final BlockState state,
        final BlockGetter level,
        final BlockPos position,
        final Direction direction
    ) {
        return getSignal(state, level, position, direction);
    }

    @Override
    protected boolean hasAnalogOutputSignal(final BlockState state) {
        return true;
    }

    protected int getAnalogOutputSignal(
        final BlockState state,
        final Level level,
        final BlockPos position
    ) {
        RadarStationBlockEntity radar = resolve(level, position, state);
        return radar == null ? 0 : radar.comparatorSignal();
    }

    protected int getAnalogOutputSignal(
        final BlockState state,
        final Level level,
        final BlockPos position,
        final Direction direction
    ) {
        return getAnalogOutputSignal(state, level, position);
    }

    @Override
    public BlockState playerWillDestroy(
        final Level level,
        final BlockPos position,
        final BlockState state,
        final Player player
    ) {
        if (level instanceof ServerLevel server) {
            RadarStationStructure.teardown(server, position, state, true);
        }

        return state;
    }

    @Override
    public void playerDestroy(
        final Level level,
        final Player player,
        final BlockPos position,
        final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final ItemStack tool
    ) {
    }

    @Override
    protected void onExplosionHit(
        final BlockState state,
        final ServerLevel level,
        final BlockPos position,
        final Explosion explosion,
        final BiConsumer<ItemStack, BlockPos> hit
    ) {
        RadarStationStructure.teardown(level, position, state, true);
    }
}
