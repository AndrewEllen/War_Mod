package com.andye.warmod.item.component;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum IcbmTestDeliveryMode implements StringRepresentable {
    SINGLE("single"), CLUSTER_FOUR("cluster_four");
    public static final Codec<IcbmTestDeliveryMode> CODEC = StringRepresentable.fromEnum(IcbmTestDeliveryMode::values);
    private final String name;
    IcbmTestDeliveryMode(String name) { this.name = name; }
    @Override public String getSerializedName() { return name; }
    public IcbmTestDeliveryMode toggle() { return this == SINGLE ? CLUSTER_FOUR : SINGLE; }
}