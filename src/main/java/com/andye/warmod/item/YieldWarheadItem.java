package com.andye.warmod.item;

import com.andye.warmod.warhead.WarheadYield;
import net.minecraft.world.item.Item;

/** Ammunition accepted by the artillery cannon. */
public final class YieldWarheadItem extends Item {
    private final WarheadYield yield;
    private final boolean cluster;

    public YieldWarheadItem(final Properties properties, final WarheadYield yield,
        final boolean cluster) {
        super(properties);
        this.yield = yield;
        this.cluster = cluster;
    }

    public WarheadYield yield() { return this.yield; }
    public boolean cluster() { return this.cluster; }
}
