package com.andye.warmod.warhead;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;

/** Registry-free state IDs needed by the detailed aftermath policies. */
final class WarheadDecorationPalette {
    private final int[] deadCoralBlocks;
    private final int[] deadCoralFans;
    private final int[] deadCoralWallFans;
    private final int paleMossCarpet;
    private final int paleHangingMoss;
    private final int witherRose;
    private final int closedEyeblossom;
    private final int sulfurSpikeDry;
    private final int sulfurSpikeWaterlogged;

    private WarheadDecorationPalette(final int[] deadCoralBlocks,
        final int[] deadCoralFans, final int[] deadCoralWallFans,
        final int paleMossCarpet, final int paleHangingMoss,
        final int witherRose, final int closedEyeblossom,
        final int sulfurSpikeDry, final int sulfurSpikeWaterlogged) {
        this.deadCoralBlocks = deadCoralBlocks.clone();
        this.deadCoralFans = deadCoralFans.clone();
        this.deadCoralWallFans = deadCoralWallFans.clone();
        this.paleMossCarpet = paleMossCarpet;
        this.paleHangingMoss = paleHangingMoss;
        this.witherRose = witherRose;
        this.closedEyeblossom = closedEyeblossom;
        this.sulfurSpikeDry = sulfurSpikeDry;
        this.sulfurSpikeWaterlogged = sulfurSpikeWaterlogged;
    }

    static WarheadDecorationPalette capture() {
        BlockState[] blocks = {
            Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState(),
            Blocks.DEAD_FIRE_CORAL_BLOCK.defaultBlockState(),
            Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState(),
            Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState()
        };
        BlockState[] fans = {
            Blocks.DEAD_BRAIN_CORAL_FAN.defaultBlockState(),
            Blocks.DEAD_BUBBLE_CORAL_FAN.defaultBlockState(),
            Blocks.DEAD_FIRE_CORAL_FAN.defaultBlockState(),
            Blocks.DEAD_HORN_CORAL_FAN.defaultBlockState(),
            Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState()
        };
        BlockState[] wallFans = {
            Blocks.DEAD_BRAIN_CORAL_WALL_FAN.defaultBlockState(),
            Blocks.DEAD_BUBBLE_CORAL_WALL_FAN.defaultBlockState(),
            Blocks.DEAD_FIRE_CORAL_WALL_FAN.defaultBlockState(),
            Blocks.DEAD_HORN_CORAL_WALL_FAN.defaultBlockState(),
            Blocks.DEAD_TUBE_CORAL_WALL_FAN.defaultBlockState()
        };
        int[] blockIds = new int[5];
        int[] fanIds = new int[5];
        int[] wallIds = new int[20];
        Direction[] directions = {Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST};
        for (int index = 0; index < 5; index++) {
            blockIds[index] = id(blocks[index]);
            BlockState fan = withoutWater(fans[index]);
            fanIds[index] = id(fan);
            for (int direction = 0; direction < directions.length; direction++) {
                BlockState wall = withoutWater(wallFans[index])
                    .setValue(BlockStateProperties.HORIZONTAL_FACING,
                        directions[direction]);
                wallIds[index * 4 + direction] = id(wall);
            }
        }
        BlockState moss = Blocks.PALE_HANGING_MOSS.defaultBlockState()
            .setValue(BlockStateProperties.TIP, true);
        BlockState spike = Blocks.SULFUR_SPIKE.defaultBlockState()
            .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)
            .setValue(BlockStateProperties.SPELEOTHEM_THICKNESS,
                SpeleothemThickness.TIP);
        return new WarheadDecorationPalette(blockIds, fanIds, wallIds,
            id(Blocks.PALE_MOSS_CARPET.defaultBlockState()), id(moss),
            id(Blocks.WITHER_ROSE.defaultBlockState()),
            id(Blocks.CLOSED_EYEBLOSSOM.defaultBlockState()),
            id(spike.setValue(BlockStateProperties.WATERLOGGED, false)),
            id(spike.setValue(BlockStateProperties.WATERLOGGED, true)));
    }

    int deadCoralBlock(final int selector) {
        return deadCoralBlocks[Math.floorMod(selector, deadCoralBlocks.length)];
    }

    int deadCoralFan(final int selector) {
        return deadCoralFans[Math.floorMod(selector, deadCoralFans.length)];
    }

    int deadCoralWallFan(final int selector, final Direction direction) {
        return deadCoralWallFans[Math.floorMod(selector, 5) * 4
            + horizontalIndex(direction)];
    }

    int paleMossCarpet() { return paleMossCarpet; }
    int paleHangingMoss() { return paleHangingMoss; }
    int witherRose() { return witherRose; }
    int closedEyeblossom() { return closedEyeblossom; }
    int sulfurSpike(final boolean waterlogged) {
        return waterlogged ? sulfurSpikeWaterlogged : sulfurSpikeDry;
    }

    private static BlockState withoutWater(final BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
            ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
    }

    private static int horizontalIndex(final Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Direction is not horizontal");
        };
    }

    private static int id(final BlockState state) {
        return Block.getId(state);
    }
}
