package com.andye.warmod.silo;

import com.andye.warmod.item.ModItems;
import com.andye.warmod.warhead.WarheadPayloadType;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public final class MissilePayloadItems {
    private MissilePayloadItems() {
    }

    public static Optional<WarheadPayloadType> payloadType(final ItemStack stack) {
        if (stack.is(ModItems.CONVENTIONAL_ICBM)) return Optional.of(WarheadPayloadType.CONVENTIONAL);
        if (stack.is(ModItems.NUCLEAR_ICBM)) return Optional.of(WarheadPayloadType.NUCLEAR);
        return Optional.empty();
    }

    public static boolean isMissile(final ItemStack stack) {
        return payloadType(stack).isPresent();
    }

    public static boolean compatible(final ItemStack existing, final ItemStack incoming) {
        return existing.isEmpty() ? isMissile(incoming)
            : payloadType(existing).equals(payloadType(incoming));
    }
}
