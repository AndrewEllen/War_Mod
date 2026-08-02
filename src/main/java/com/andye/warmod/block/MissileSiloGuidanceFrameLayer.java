package com.andye.warmod.block;
import net.minecraft.util.StringRepresentable;
public enum MissileSiloGuidanceFrameLayer implements StringRepresentable { LOWER, UPPER; @Override public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);} }