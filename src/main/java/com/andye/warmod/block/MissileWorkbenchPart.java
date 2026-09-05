package com.andye.warmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/** The controller is the local left half; the companion extends along local +X. */
public enum MissileWorkbenchPart implements StringRepresentable {
    LEFT,
    RIGHT;

    public boolean isController() {
        return this == LEFT;
    }

    public BlockPos controllerPosition(final BlockPos position, final Direction facing) {
        return isController() ? position : position.relative(facing.getCounterClockWise());
    }

    public BlockPos companionPosition(final BlockPos controller, final Direction facing) {
        return controller.relative(facing.getClockWise());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
