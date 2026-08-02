package com.andye.warmod.block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
public enum MissileSiloGuidanceFramePart implements StringRepresentable {
 NORTH_WEST(-1,-1),NORTH(0,-1),NORTH_EAST(1,-1),WEST(-1,0),EAST(1,0),SOUTH_WEST(-1,1),SOUTH(0,1),SOUTH_EAST(1,1);
 private final int x,z; MissileSiloGuidanceFramePart(int x,int z){this.x=x;this.z=z;}
 public BlockPos rotatedOffset(Direction facing){return switch(facing){case SOUTH->new BlockPos(-x,0,-z);case EAST->new BlockPos(-z,0,x);case WEST->new BlockPos(z,0,-x);default->new BlockPos(x,0,z);};}
 public BlockPos resolveCentre(BlockPos position,Direction facing){BlockPos o=rotatedOffset(facing);return position.offset(-o.getX(),0,-o.getZ());}
 public boolean corner(){return x!=0&&z!=0;} @Override public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}
}