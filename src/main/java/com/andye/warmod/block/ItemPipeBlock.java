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
import net.minecraft.world.phys.Vec3;
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

    private static final double ARM_MIN = 5.0 / 16.0;
    private static final double ARM_MAX = 11.0 / 16.0;
    private static final double HIT_EPSILON = 0.035;

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
            BlockState neighbour = level.getBlockState(
                position.relative(direction)
            );

            if (!neighbour.is(ModBlocks.ITEM_PIPE)) {
                continue;
            }

            PipeConnectionMode reciprocal = mode(
                neighbour,
                direction.getOpposite()
            );

            if (reciprocal == PipeConnectionMode.NONE
                || reciprocal == PipeConnectionMode.PIPE) {
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

            Direction opposite = direction.getOpposite();
            PipeConnectionMode reciprocal = mode(neighbour, opposite);

            if (reciprocal != PipeConnectionMode.NONE
                && reciprocal != PipeConnectionMode.PIPE) {
                updated = updated.setValue(
                    property(direction),
                    PipeConnectionMode.NONE
                );
                continue;
            }

            updated = updated.setValue(
                property(direction),
                PipeConnectionMode.PIPE
            );

            if (reciprocal == PipeConnectionMode.NONE) {
                server.setBlock(
                    neighbourPosition,
                    neighbour.setValue(
                        property(opposite),
                        PipeConnectionMode.PIPE
                    ),
                    Block.UPDATE_ALL
                );
            }
        }

        if (!updated.equals(server.getBlockState(position))) {
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
        Direction side = targetedSide(
            state,
            position,
            hit.getLocation(),
            hit.getDirection()
        );
        PipeConnectionMode connection = mode(state, side);

        if (!level.isClientSide()) {
            player.sendSystemMessage(Component.literal(
                side.getSerializedName().toUpperCase()
                    + ": "
                    + connection.displayName()
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

    /**
     * Resolves the connector arm actually hit, rather than blindly using the
     * face normal of the small arm's surface.
     */
    public static Direction targetedSide(
        final BlockState state,
        final BlockPos position,
        final Vec3 hitLocation,
        final Direction fallback
    ) {
        double x = hitLocation.x - position.getX();
        double y = hitLocation.y - position.getY();
        double z = hitLocation.z - position.getZ();

        Direction best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (Direction direction : Direction.values()) {
            if (mode(state, direction) == PipeConnectionMode.NONE
                || !insideArm(direction, x, y, z)) {
                continue;
            }

            double score = distanceFromOuterEnd(direction, x, y, z);

            if (score < bestScore) {
                best = direction;
                bestScore = score;
            }
        }

        return best == null ? fallback : best;
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

    private static boolean insideArm(
        final Direction direction,
        final double x,
        final double y,
        final double z
    ) {
        return switch (direction) {
            case WEST -> x <= ARM_MIN + HIT_EPSILON
                && between(y) && between(z);
            case EAST -> x >= ARM_MAX - HIT_EPSILON
                && between(y) && between(z);
            case DOWN -> y <= ARM_MIN + HIT_EPSILON
                && between(x) && between(z);
            case UP -> y >= ARM_MAX - HIT_EPSILON
                && between(x) && between(z);
            case NORTH -> z <= ARM_MIN + HIT_EPSILON
                && between(x) && between(y);
            case SOUTH -> z >= ARM_MAX - HIT_EPSILON
                && between(x) && between(y);
        };
    }

    private static boolean between(final double value) {
        return value >= ARM_MIN - HIT_EPSILON
            && value <= ARM_MAX + HIT_EPSILON;
    }

    private static double distanceFromOuterEnd(
        final Direction direction,
        final double x,
        final double y,
        final double z
    ) {
        return switch (direction) {
            case WEST -> Math.abs(x);
            case EAST -> Math.abs(1.0 - x);
            case DOWN -> Math.abs(y);
            case UP -> Math.abs(1.0 - y);
            case NORTH -> Math.abs(z);
            case SOUTH -> Math.abs(1.0 - z);
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
