package com.andye.warmod.warhead;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;

/** State IDs are captured on the server thread; workers never dereference live registries. */
record WarheadStatePalette(int air, int magma, int tintedGlass, int blackGlass,
    int grayGlass, int lightGrayGlass, int whiteTerracotta, int sandstone,
    int terracotta, int redSandstone, int basalt, int blackstone, int deepslate,
    int cobbledDeepslate, int tuff, int gravel, int coarseDirt, int rootedDirt,
    int podzol, int mycelium, int paleMoss, int mud, int sulfur,
    int potentSulfur, int paleLeaves, int paleLogX, int paleLogY, int paleLogZ,
    int paleWood, int deadBush, int shortDryGrass, int tallDryGrass, int calcite,
    WarheadDecorationPalette decoration) {

    static WarheadStatePalette capture() {
        BlockState paleLeaves = Blocks.PALE_OAK_LEAVES.defaultBlockState()
            .setValue(BlockStateProperties.PERSISTENT, true);
        BlockState paleLog = Blocks.PALE_OAK_LOG.defaultBlockState();
        return new WarheadStatePalette(id(Blocks.AIR.defaultBlockState()),
            id(Blocks.MAGMA_BLOCK.defaultBlockState()),
            id(Blocks.TINTED_GLASS.defaultBlockState()),
            id(Blocks.STAINED_GLASS.black().defaultBlockState()),
            id(Blocks.STAINED_GLASS.gray().defaultBlockState()),
            id(Blocks.STAINED_GLASS.lightGray().defaultBlockState()),
            id(Blocks.DYED_TERRACOTTA.white().defaultBlockState()),
            id(Blocks.SANDSTONE.defaultBlockState()),
            id(Blocks.TERRACOTTA.defaultBlockState()),
            id(Blocks.RED_SANDSTONE.defaultBlockState()),
            id(Blocks.BASALT.defaultBlockState()), id(Blocks.BLACKSTONE.defaultBlockState()),
            id(Blocks.DEEPSLATE.defaultBlockState()),
            id(Blocks.COBBLED_DEEPSLATE.defaultBlockState()), id(Blocks.TUFF.defaultBlockState()),
            id(Blocks.GRAVEL.defaultBlockState()), id(Blocks.COARSE_DIRT.defaultBlockState()),
            id(Blocks.ROOTED_DIRT.defaultBlockState()), id(Blocks.PODZOL.defaultBlockState()),
            id(Blocks.MYCELIUM.defaultBlockState()), id(Blocks.PALE_MOSS_BLOCK.defaultBlockState()),
            id(Blocks.MUD.defaultBlockState()), id(Blocks.SULFUR.defaultBlockState()),
            id(Blocks.POTENT_SULFUR.defaultBlockState()), id(paleLeaves),
            id(paleLog.setValue(BlockStateProperties.AXIS, Direction.Axis.X)),
            id(paleLog.setValue(BlockStateProperties.AXIS, Direction.Axis.Y)),
            id(paleLog.setValue(BlockStateProperties.AXIS, Direction.Axis.Z)),
            id(Blocks.PALE_OAK_WOOD.defaultBlockState()), id(Blocks.DEAD_BUSH.defaultBlockState()),
            id(Blocks.SHORT_DRY_GRASS.defaultBlockState()),
            id(Blocks.TALL_DRY_GRASS.defaultBlockState()),
            id(Blocks.CALCITE.defaultBlockState()), WarheadDecorationPalette.capture());
    }

    int paleLog(final int flags) {
        if ((flags & WarheadSnapshotFlags.AXIS_X) != 0) return paleLogX;
        if ((flags & WarheadSnapshotFlags.AXIS_Z) != 0) return paleLogZ;
        return paleLogY;
    }

    private static int id(final BlockState state) {
        return Block.getId(state);
    }
}
