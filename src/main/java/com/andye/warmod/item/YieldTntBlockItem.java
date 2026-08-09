package com.andye.warmod.item;

import com.andye.warmod.artillery.ArtilleryPayload;
import com.andye.warmod.block.TimedWarheadTntBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Inventory representation of a placeable yield-specific TNT block. */
public final class YieldTntBlockItem extends BlockItem {
    private final ArtilleryPayload payload;

    public YieldTntBlockItem(final TimedWarheadTntBlock block, final Properties properties,
        final ArtilleryPayload payload) {
        super(block, properties);
        this.payload = payload;
    }

    public ArtilleryPayload payload() {
        return payload;
    }

    @Override
    public Component getName(final ItemStack stack) {
        return Component.literal(payload.displayName("TNT"));
    }
}