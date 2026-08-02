package com.andye.warmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

public enum GuidanceSupportPart implements StringRepresentable {
    FRONT_LOWER(true, false),
    FRONT_UPPER(true, true),
    REAR_LOWER(false, false),
    REAR_UPPER(false, true);

    private final boolean front;
    private final boolean upper;

    GuidanceSupportPart(final boolean front, final boolean upper) {
        this.front = front;
        this.upper = upper;
    }

    public BlockPos position(final BlockPos centre, final Direction facing, final GuidanceSupportSide side) {
        Direction right = facing.getClockWise();
        Direction lateral = side == GuidanceSupportSide.RIGHT ? right : right.getOpposite();
        Direction longitudinal = front ? facing : facing.getOpposite();
        return centre.relative(lateral).relative(longitudinal).above(upper ? 2 : 1);
    }

    public BlockPos resolveCentre(final BlockPos position, final Direction facing, final GuidanceSupportSide side) {
        Direction right = facing.getClockWise();
        Direction lateral = side == GuidanceSupportSide.RIGHT ? right : right.getOpposite();
        Direction longitudinal = front ? facing : facing.getOpposite();
        return position.relative(lateral.getOpposite()).relative(longitudinal.getOpposite()).below(upper ? 2 : 1);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}