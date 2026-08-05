package com.andye.warmod.logistics;

import net.minecraft.util.StringRepresentable;

public enum PipeConnectionMode implements StringRepresentable {
    NONE("none"),
    PIPE("pipe"),
    INPUT("input"),
    OUTPUT("output");

    private final String serializedName;

    PipeConnectionMode(final String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String displayName() {
        return switch (this) {
            case NONE -> "No connection";
            case PIPE -> "Pipe connection";
            case INPUT -> "Input from inventory";
            case OUTPUT -> "Output to inventory";
        };
    }
}
