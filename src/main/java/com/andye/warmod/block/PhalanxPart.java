package com.andye.warmod.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;

public enum PhalanxPart implements StringRepresentable {
    BASE_00(0, 0, 0), BASE_10(1, 0, 0), BASE_01(0, 0, 1), BASE_11(1, 0, 1),
    TOP_00(0, 1, 0), TOP_10(1, 1, 0), TOP_01(0, 1, 1), TOP_11(1, 1, 1);

    private static final List<PhalanxPart> COMPACT_STRUCTURE = List.of(BASE_00, TOP_00);
    private final int x; private final int y; private final int z;
    PhalanxPart(final int x, final int y, final int z) { this.x = x; this.y = y; this.z = z; }
    public static List<PhalanxPart> compactStructure() { return COMPACT_STRUCTURE; }
    public boolean compactBase() { return this == BASE_00; }
    public boolean compactHead() { return this == TOP_00; }
    public BlockPos offset() { return new BlockPos(x, y, z); }
    public BlockPos controller(final BlockPos position) { return position.offset(-x, -y, -z); }
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
}