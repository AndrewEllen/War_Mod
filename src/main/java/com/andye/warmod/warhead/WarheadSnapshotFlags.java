package com.andye.warmod.warhead;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;

/** Primitive classification flags captured on the server thread for worker use. */
final class WarheadSnapshotFlags {
    static final int AIR = 1;
    static final int FLUID = 1 << 1;
    static final int INDESTRUCTIBLE = 1 << 2;
    static final int SEMANTIC = 1 << 3;
    static final int TNT = 1 << 4;
    static final int SAND = 1 << 5;
    static final int RED_SAND = 1 << 6;
    static final int SOIL = 1 << 7;
    static final int NATURAL_SURFACE = 1 << 8;
    static final int COMMON_ROCK = 1 << 9;
    static final int SNOW = 1 << 10;
    static final int LEAVES = 1 << 11;
    static final int LOG = 1 << 12;
    static final int PLANK = 1 << 13;
    static final int GLASS = 1 << 14;
    static final int COBBLE = 1 << 15;
    static final int FRAGILE = 1 << 16;
    static final int EXPOSED = 1 << 17;
    static final int NATURAL_TREE = 1 << 18;
    static final int WATER_NEAR = 1 << 19;
    static final int AXIS_X = 1 << 20;
    static final int AXIS_Z = 1 << 21;
    static final int WATER = 1 << 22;
    static final int SUGAR_CANE = 1 << 23;
    static final int AQUATIC_PLANT = 1 << 24;
    static final int DOUBLE_UPPER = 1 << 25;
    static final int BUSH = 1 << 26;
    static final int SULFUR = 1 << 27;
    static final int SURVIVAL_SENSITIVE = 1 << 28;

    private WarheadSnapshotFlags() { }

    static int classify(final BlockState state, final boolean indestructible,
        final boolean exposed) {
        int flags = 0;
        if (state.isAir()) flags |= AIR;
        if (!state.getFluidState().isEmpty()) flags |= FLUID;
        if (state.getFluidState().is(FluidTags.WATER)) flags |= WATER;
        if (indestructible) flags |= INDESTRUCTIBLE;
        if (state.is(Blocks.TNT)) flags |= TNT;
        /* Keep vanilla sand explicit so the fused palette does not depend solely
         * on data-tag availability while a snapshot is being classified. */
        if (isRegularSand(state)) {
            flags |= SAND | NATURAL_SURFACE;
        }
        if (state.is(Blocks.RED_SAND)) flags |= RED_SAND | NATURAL_SURFACE;
        if (isSoil(state)) flags |= SOIL | NATURAL_SURFACE;
        if (state.is(Blocks.MUD) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)) {
            flags |= NATURAL_SURFACE;
        }
        if (isCommonRock(state)) flags |= COMMON_ROCK;
        if (isSnowLike(state)) flags |= SNOW | FRAGILE;
        if (state.is(BlockTags.LEAVES)) flags |= LEAVES;
        if (state.is(BlockTags.LOGS)) {
            flags |= LOG;
            if (state.hasProperty(BlockStateProperties.AXIS)) {
                Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
                if (axis == Direction.Axis.X) flags |= AXIS_X;
                else if (axis == Direction.Axis.Z) flags |= AXIS_Z;
            }
        }
        if (state.is(BlockTags.PLANKS)) flags |= PLANK;
        if (state.getBlock().getDescriptionId().contains("glass")) flags |= GLASS;
        if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)) {
            flags |= COBBLE;
        }
        if (isFragile(state)) flags |= FRAGILE;
        if (state.is(Blocks.SUGAR_CANE)) flags |= SUGAR_CANE;
        if (isAquaticPlant(state)) flags |= AQUATIC_PLANT;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            flags |= DOUBLE_UPPER;
        }
        if (state.is(Blocks.BUSH)) flags |= BUSH;
        if (state.is(Blocks.SULFUR)) flags |= SULFUR;
        if (exposed) flags |= EXPOSED;

        // Palette writes deliberately bypass BlockBehaviour callbacks and neighbour
        // propagation. Keep that path limited to inert terrain/material states; all
        // fluids, redstone-like states, block entities, mod blocks, and unclassified
        // vanilla structures must use the semantic mutation path on the server thread.
        if (requiresSemanticPath(state, flags)) flags |= SEMANTIC;
        return flags;
    }

    static boolean requiresSemanticPath(final BlockState state, final int flags) {
        int inertTerrain = AIR | SAND | RED_SAND | SOIL | NATURAL_SURFACE
            | COMMON_ROCK | SNOW | LEAVES | LOG | PLANK | GLASS | COBBLE | FRAGILE;
        boolean bulkSafe = (flags & inertTerrain) != 0;
        boolean callbackSensitive = (flags & (FLUID | TNT)) != 0
            || state.hasBlockEntity()
            || state.isSignalSource()
            || state.hasAnalogOutputSignal()
            || !"minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock())
                .getNamespace());
        return !bulkSafe || callbackSensitive;
    }

    static boolean relevantVertical(final int flags) {
        return (flags & (SNOW | LEAVES | LOG | PLANK | GLASS | COBBLE | FRAGILE
            | SURVIVAL_SENSITIVE)) != 0;
    }

    static boolean isRegularSand(final BlockState state) {
        return !state.is(Blocks.RED_SAND)
            && (state.is(Blocks.SAND) || state.is(BlockTags.SAND));
    }

    private static boolean isSoil(final BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL)
            || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)
            || state.is(Blocks.CRIMSON_NYLIUM) || state.is(Blocks.WARPED_NYLIUM);
    }

    private static boolean isCommonRock(final BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
            || state.is(BlockTags.BASE_STONE_NETHER)
            || state.is(BlockTags.TERRACOTTA)
            || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE)
            || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
            || state.is(Blocks.END_STONE) || state.is(Blocks.BLACKSTONE)
            || state.is(Blocks.BASALT) || state.is(Blocks.POLISHED_BASALT)
            || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.CALCITE)
            || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.ICE)
            || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    private static boolean isSnowLike(final BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean isFragile(final BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
            || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
            || state.is(Blocks.VINE) || isSnowLike(state)
            || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
            || state.is(Blocks.BUSH) || state.is(Blocks.FIREFLY_BUSH)
            || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.DEAD_BUSH)
            || state.is(Blocks.SHORT_DRY_GRASS) || state.is(Blocks.TALL_DRY_GRASS)
            || state.is(Blocks.LEAF_LITTER) || state.is(Blocks.SUGAR_CANE)
            || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
            || state.is(Blocks.KELP)
            || state.is(Blocks.KELP_PLANT) || state.is(Blocks.PALE_HANGING_MOSS)
            || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)
            || state.getBlock().getDescriptionId().contains("sapling");
    }

    private static boolean isAquaticPlant(final BlockState state) {
        return state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
            || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }
}
