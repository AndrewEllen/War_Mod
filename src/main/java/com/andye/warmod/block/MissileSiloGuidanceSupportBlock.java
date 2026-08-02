package com.andye.warmod.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MissileSiloGuidanceSupportBlock extends Block {
    public static final MapCodec<MissileSiloGuidanceSupportBlock> CODEC =
        simpleCodec(MissileSiloGuidanceSupportBlock::new);
    public static final EnumProperty<GuidanceSupportSide> SIDE =
        EnumProperty.create("side", GuidanceSupportSide.class);
    public static final EnumProperty<GuidanceSupportPart> PART =
        EnumProperty.create("part", GuidanceSupportPart.class);
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 3);
    public static final net.minecraft.world.level.block.state.properties.Property<Direction> FACING =
        HorizontalDirectionalBlock.FACING;

    private static final VoxelShape LOWER = Shapes.or(
        Block.box(2, 0, 2, 7, 16, 7),
        Block.box(1, 0, 1, 9, 3, 9),
        Block.box(5, 5, 5, 16, 9, 11));
    private static final VoxelShape UPPER = Shapes.or(
        Block.box(2, 0, 2, 7, 16, 7),
        Block.box(4, 4, 4, 16, 9, 12),
        Block.box(3, 11, 3, 11, 16, 11));

    public MissileSiloGuidanceSupportBlock(final BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(SIDE, GuidanceSupportSide.LEFT)
            .setValue(PART, GuidanceSupportPart.FRONT_LOWER)
            .setValue(TIER, 1)
            .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDE, PART, TIER, FACING);
    }

    @Override
    protected VoxelShape getShape(final BlockState state,
        final net.minecraft.world.level.BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return state.getValue(PART).name().endsWith("LOWER") ? LOWER : UPPER;
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos,
        final BlockState state, final Player player) {
        if (level instanceof ServerLevel server) {
            MissileSiloGuidanceFrameStructure.removeFromPart(server, pos, state, true);
        }
        return state;
    }

    @Override
    public void playerDestroy(final Level level, final Player player, final BlockPos pos,
        final BlockState state, final net.minecraft.world.level.block.entity.BlockEntity blockEntity,
        final ItemStack tool) {
    }

    @Override
    protected void onExplosionHit(final BlockState state, final ServerLevel level, final BlockPos pos,
        final Explosion explosion, final BiConsumer<ItemStack, BlockPos> onHit) {
        MissileSiloGuidanceFrameStructure.removeFromPart(level, pos, state, true);
    }
}