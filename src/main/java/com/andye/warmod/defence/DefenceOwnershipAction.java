package com.andye.warmod.defence;

public enum DefenceOwnershipAction {
    CLAIM,
    UNCLAIM,
    ADD_ALLY,
    REMOVE_ALLY;

    public static DefenceOwnershipAction byNetworkId(final int id) {
        DefenceOwnershipAction[] values = values();
        if (id < 0 || id >= values.length) throw new IllegalArgumentException("Unknown ownership action " + id);
        return values[id];
    }
}
