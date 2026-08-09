package com.andye.warmod.item;

import com.andye.warmod.artillery.ArtilleryPayload;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Ammunition consumed only by an artillery cannon. */
public final class ArtilleryWarheadItem extends Item {
    private final ArtilleryPayload payload;
    public ArtilleryWarheadItem(final Properties properties, final ArtilleryPayload payload) { super(properties); this.payload = payload; }
    public ArtilleryPayload payload() { return payload; }
    @Override public Component getName(final ItemStack stack) { return Component.literal(payload.displayName("Artillery Shell")); }
    @Override public void appendHoverText(final ItemStack stack, final TooltipContext context, final TooltipDisplay display, final Consumer<Component> tooltip, final TooltipFlag flag) {
        tooltip.accept(Component.literal("Artillery ammunition"));
        tooltip.accept(Component.literal(payload.cluster() ? "Splits into four impacts" : "Single impact"));
    }
}
