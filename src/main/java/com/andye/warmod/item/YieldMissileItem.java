package com.andye.warmod.item;

import com.andye.warmod.warhead.WarheadYield;
import net.minecraft.world.item.Item;

/** Strategic missile item carrying an explicit production yield and delivery mode. */
public final class YieldMissileItem extends Item {
    private final WarheadYield yield;
    private final boolean cluster;

    public YieldMissileItem(final Properties properties, final WarheadYield yield,
        final boolean cluster) {
        super(properties);
        this.yield = yield;
        this.cluster = cluster;
    }

    public WarheadYield yield() { return this.yield; }
    public boolean cluster() { return this.cluster; }
}
