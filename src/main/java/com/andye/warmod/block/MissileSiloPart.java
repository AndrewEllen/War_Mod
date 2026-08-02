package com.andye.warmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

public enum MissileSiloPart implements StringRepresentable {
    NORTH_WEST(-1, -1), NORTH(0, -1), NORTH_EAST(1, -1),
    WEST(-1, 0), CENTER(0, 0), EAST(1, 0),
    SOUTH_WEST(-1, 1), SOUTH(0, 1), SOUTH_EAST(1, 1);

    private final int localX;
    private final int localZ;

    MissileSiloPart(final int localX, final int localZ) {
        this.localX = localX;
        this.localZ = localZ;
    }

    public int localX() { return this.localX; }
    public int localZ() { return this.localZ; }
    public boolean isCenter() { return this == CENTER; }

    public BlockPos rotatedOffset(final Direction facing) {
        return switch (facing) {
            case SOUTH -> new BlockPos(-this.localX, 0, -this.localZ);
            case EAST -> new BlockPos(-this.localZ, 0, this.localX);
            case WEST -> new BlockPos(this.localZ, 0, -this.localX);
            default -> new BlockPos(this.localX, 0, this.localZ);
        };
    }

    public BlockPos resolveCenter(final BlockPos partPosition, final Direction facing) {
        BlockPos offset = this.rotatedOffset(facing);
        return partPosition.offset(-offset.getX(), 0, -offset.getZ());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
