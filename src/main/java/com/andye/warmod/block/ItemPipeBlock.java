package com.andye.warmod.block;

import com.andye.warmod.block.entity.ItemPipeBlockEntity;
import com.andye.warmod.block.entity.ModBlockEntities;
import com.andye.warmod.logistics.PipeConnectionMode;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class ItemPipeBlock extends BaseEntityBlock {
    public static final MapCodec<ItemPipeBlock> CODEC =
        simpleCodec(ItemPipeBlock::new);

    public static final EnumProperty<PipeConnectionMode> DOWN = property("down");
    public static final EnumProperty<PipeConnectionMode> UP = property("up");
    public static final EnumProperty<PipeConnectionMode> NORTH = property("north");
    public static final EnumProperty<PipeConnectionMode> SOUTH = property("south");
    public static final EnumProperty<PipeConnectionMode> WEST = property("west");
    public static final EnumProperty<PipeConnectionMode> EAST = property("east");

    private static final Map<Direction, EnumProperty<PipeConnectionMode>> PROPERTIES =
        createProperties();

    private static final VoxelShape CORE =
        Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);

    private static final Map<Direction, VoxelShape> ARMS = createArms();

    public ItemPipeBlock(final BlockBehaviour.Properties properties) {
        super(properties);

        BlockState state = stateDefinition.any();

        for (Direction direction : Direction.values()) {
            state = state.setValue(property(direction), PipeConnectionMode.NONE);
        }

        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends ItemPipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
        final StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(DOWN, UP, NORTH, SOUTH, WEST, EAST);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
        final BlockPos position,
        final BlockState state
    ) {
        return new ItemPipeBlockEntity(position, state);
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
                ModBlockEntities.ITEM_PIPE,
                ItemPipeBlockEntity::serverTick
            );
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        Level level = context.getLevel();
        BlockPos position = context.getClickedPos();

        for (Direction direction : Direction.values()) {
            if (level.getBlockState(position.relative(direction))
                .is(ModBlocks.ITEM_PIPE)) {
                state = state.setValue(
                    property(direction),
                    PipeConnectionMode.PIPE
                );
            }
        }

        return state;
    }

    @Override
    public void setPlacedBy(
        final Level level,
        final BlockPos position,
        final BlockState state,
        final @Nullable LivingEntity placer,
        final ItemStack stack
    ) {
        super.setPlacedBy(level, position, state, placer, stack);

        if (!(level instanceof ServerLevel server)) {
            return;
        }

        BlockState updated = server.getBlockState(position);

        for (Direction direction : Direction.values()) {
            BlockPos neighbourPosition = position.relative(direction);
            BlockState neighbour = server.getBlockState(neighbourPosition);

            if (!neighbour.is(ModBlocks.ITEM_PIPE)) {
                continue;
            }

            updated = updated.setValue(
                property(direction),
                PipeConnectionMode.PIPE
            );

            if (mode(neighbour, direction.getOpposite())
                == PipeConnectionMode.NONE) {
                server.setBlock(
                    neighbourPosition,
                    neighbour.setValue(
                        property(direction.getOpposite()),
                        PipeConnectionMode.PIPE
                    ),
                    Block.UPDATE_ALL
                );
            }
        }

        if (updated != server.getBlockState(position)) {
            server.setBlock(position, updated, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(
        final BlockState state,
        final ServerLevel level,
        final BlockPos position,
        final boolean movedByPiston
    ) {
        super.affectNeighborsAfterRemoval(state, level, position, movedByPiston);

        for (Direction direction : Direction.values()) {
            BlockPos neighbourPosition = position.relative(direction);
            BlockState neighbour = level.getBlockState(neighbourPosition);

            if (neighbour.is(ModBlocks.ITEM_PIPE)
                && mode(neighbour, direction.getOpposite())
                    == PipeConnectionMode.PIPE) {
                level.setBlock(
                    neighbourPosition,
                    neighbour.setValue(
                        property(direction.getOpposite()),
                        PipeConnectionMode.NONE
                    ),
                    Block.UPDATE_ALL
                );
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
        final BlockState state,
        final Level level,
        final BlockPos position,
        final Player player,
        final BlockHitResult hit
    ) {
        Direction side = hit.getDirection();
        PipeConnectionMode mode = mode(state, side);

        if (!level.isClientSide()) {
            player.sendSystemMessage(Component.literal(
                side.getSerializedName().toUpperCase()
                    + ": "
                    + mode.displayName()
                    + " — use a Pipe Wrench to change it"
            ));
        }

        return level.isClientSide()
            ? InteractionResult.SUCCESS
            : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected VoxelShape getShape(
        final BlockState state,
        final BlockGetter level,
        final BlockPos position,
        final CollisionContext context
    ) {
        VoxelShape shape = CORE;

        for (Direction direction : Direction.values()) {
            if (mode(state, direction) != PipeConnectionMode.NONE) {
                shape = Shapes.or(shape, ARMS.get(direction));
            }
        }

        return shape;
    }

    public static PipeConnectionMode mode(
        final BlockState state,
        final Direction direction
    ) {
        return state.getValue(property(direction));
    }

    public static EnumProperty<PipeConnectionMode> property(
        final Direction direction
    ) {
        return PROPERTIES.get(direction);
    }

    public static PipeConnectionMode nextMode(
        final Level level,
        final BlockPos position,
        final Direction side,
        final PipeConnectionMode current
    ) {
        boolean pipeNeighbour = level.getBlockState(position.relative(side))
            .is(ModBlocks.ITEM_PIPE);

        if (pipeNeighbour) {
            return current == PipeConnectionMode.NONE
                ? PipeConnectionMode.PIPE
                : PipeConnectionMode.NONE;
        }

        return switch (current) {
            case NONE, PIPE -> PipeConnectionMode.INPUT;
            case INPUT -> PipeConnectionMode.OUTPUT;
            case OUTPUT -> PipeConnectionMode.NONE;
        };
    }

    private static EnumProperty<PipeConnectionMode> property(
        final String name
    ) {
        return EnumProperty.create(name, PipeConnectionMode.class);
    }

    private static Map<Direction, EnumProperty<PipeConnectionMode>> createProperties() {
        EnumMap<Direction, EnumProperty<PipeConnectionMode>> values =
            new EnumMap<>(Direction.class);
        values.put(Direction.DOWN, DOWN);
        values.put(Direction.UP, UP);
        values.put(Direction.NORTH, NORTH);
        values.put(Direction.SOUTH, SOUTH);
        values.put(Direction.WEST, WEST);
        values.put(Direction.EAST, EAST);
        return Map.copyOf(values);
    }

    private static Map<Direction, VoxelShape> createArms() {
        EnumMap<Direction, VoxelShape> values = new EnumMap<>(Direction.class);
        values.put(Direction.DOWN, Block.box(5.0, 0.0, 5.0, 11.0, 5.0, 11.0));
        values.put(Direction.UP, Block.box(5.0, 11.0, 5.0, 11.0, 16.0, 11.0));
        values.put(Direction.NORTH, Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 5.0));
        values.put(Direction.SOUTH, Block.box(5.0, 5.0, 11.0, 11.0, 11.0, 16.0));
        values.put(Direction.WEST, Block.box(0.0, 5.0, 5.0, 5.0, 11.0, 11.0));
        values.put(Direction.EAST, Block.box(11.0, 5.0, 5.0, 16.0, 11.0, 11.0));
        return Map.copyOf(values);
    }
}
