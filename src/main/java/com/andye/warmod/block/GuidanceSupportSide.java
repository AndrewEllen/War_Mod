package com.andye.warmod.block;

import net.minecraft.util.StringRepresentable;

public enum GuidanceSupportSide implements StringRepresentable {
    LEFT,
    RIGHT;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}