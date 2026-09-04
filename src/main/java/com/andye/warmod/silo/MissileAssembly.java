package com.andye.warmod.silo;

import com.andye.warmod.item.ModItems;
import com.andye.warmod.warhead.WarheadYield;

import net.minecraft.world.item.ItemStack;

/** Shared slot validation and assembly contract for menus, hoppers and pipes. */
public final class MissileAssembly {
    private MissileAssembly() {}

    public static int chipTier(ItemStack chip) {
        if (chip.is(ModItems.TARGETING_CHIP_TIER_1)) return 1;
        if (chip.is(ModItems.TARGETING_CHIP_TIER_2)) return 2;
        return chip.is(ModItems.TARGETING_CHIP_TIER_3) ? 3 : 0;
    }

    public static boolean accepts(int slot, ItemStack stack) {
        if (slot == 0) return stack.is(ModItems.ICBM_BODY) || stack.is(ModItems.ANTI_AIR_BODY);
        if (slot == 1) return chipTier(stack) > 0;
        if (slot != 2) return false;
        if (stack.is(ModItems.ANTI_AIR_CONTROLLER_BALLISTIC)
                || stack.is(ModItems.ANTI_AIR_CONTROLLER_SELF_DESTRUCT)) return true;
        for (WarheadYield yield : WarheadYield.values())
            for (boolean cluster : new boolean[] {false, true})
                if (stack.is(ModItems.missileWarhead(yield, cluster))) return true;
        return false;
    }

    public static ItemStack assemble(ItemStack body, ItemStack chip, ItemStack payload) {
        int tier = chipTier(chip);
        if (tier == 0 || body.isEmpty() || payload.isEmpty()) return ItemStack.EMPTY;
        if (body.is(ModItems.ANTI_AIR_BODY)) {
            if (payload.is(ModItems.ANTI_AIR_CONTROLLER_BALLISTIC))
                return MissilePayloadItems.withGuidance(ModItems.ANTI_AIR_MISSILE_MK1, tier);
            if (payload.is(ModItems.ANTI_AIR_CONTROLLER_SELF_DESTRUCT))
                return MissilePayloadItems.withGuidance(ModItems.ANTI_AIR_MISSILE_MK2, tier);
        } else if (body.is(ModItems.ICBM_BODY)) {
            for (WarheadYield yield : WarheadYield.values())
                for (boolean cluster : new boolean[] {false, true})
                    if (payload.is(ModItems.missileWarhead(yield, cluster)))
                        return MissilePayloadItems.withGuidance(
                                ModItems.yieldMissile(yield, cluster), tier);
        }
        return ItemStack.EMPTY;
    }
}
