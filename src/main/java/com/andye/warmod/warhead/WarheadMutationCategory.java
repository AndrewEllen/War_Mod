package com.andye.warmod.warhead;

/** Semantic owner of a prepared mutation, retained through commit diagnostics. */
public enum WarheadMutationCategory {
    CRATER_EXCAVATION,
    CRATER_SHELL,
    CRATER_CLEANUP,
    SURFACE,
    VEGETATION,
    STRUCTURE,
    DECORATION,
    OTHER;

    byte wireId() {
        return (byte)ordinal();
    }

    static WarheadMutationCategory fromWireId(final byte id) {
        int index = Byte.toUnsignedInt(id);
        WarheadMutationCategory[] values = values();
        if (index >= values.length) {
            throw new IllegalArgumentException("Unknown mutation category " + index);
        }
        return values[index];
    }
}
