package com.andye.warmod.artillery;

import com.andye.warmod.item.ArtilleryWarheadItem;
import net.minecraft.world.item.ItemStack;

public final class ArtilleryPayloadItems {
    private ArtilleryPayloadItems() { }
    public static ArtilleryPayload payload(final ItemStack stack) {
        return stack.getItem() instanceof ArtilleryWarheadItem item ? item.payload() : null;
    }
    public static boolean isWarhead(final ItemStack stack) { return payload(stack) != null; }
    public static boolean compatible(final ItemStack existing, final ItemStack incoming) {
        return existing.isEmpty() ? isWarhead(incoming) : existing.is(incoming.getItem());
    }
}
