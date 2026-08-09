package com.andye.warmod.item;

import com.andye.warmod.artillery.ArtilleryPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Per-yield silo missile. The silo resolves its payload through MissilePayloadItems. */
public final class YieldMissileItem extends Item {
    private final ArtilleryPayload payload;
    public YieldMissileItem(final Properties properties, final ArtilleryPayload payload) { super(properties); this.payload = payload; }
    public ArtilleryPayload payload() { return payload; }
    @Override public Component getName(final ItemStack stack) { return Component.literal(payload.displayName("ICBM")); }
}
