package com.andye.warmod.silo;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * The workbench's fourth slot is a view over a complete component set rather
 * than stored craft output. Keeping this transaction separate makes hoppers,
 * item pipes and menu slots obey exactly the same no-duplication rule.
 */
public final class MissileWorkbenchPreview {
    public static final int BODY_SLOT = 0;
    public static final int CHIP_SLOT = 1;
    public static final int PAYLOAD_SLOT = 2;
    public static final int OUTPUT_SLOT = 3;

    private MissileWorkbenchPreview() {}

    /** A legacy persisted output always takes precedence over the virtual preview. */
    public static ItemStack preview(final Container rawInventory) {
        ItemStack legacyOutput = rawInventory.getItem(OUTPUT_SLOT);
        return legacyOutput.isEmpty()
                ? MissileAssembly.assemble(
                        rawInventory.getItem(BODY_SLOT),
                        rawInventory.getItem(CHIP_SLOT),
                        rawInventory.getItem(PAYLOAD_SLOT))
                : legacyOutput.copy();
    }

    /**
     * Takes a real legacy stack normally, or atomically turns one complete set
     * of components into one missile. Nothing is removed when no result exists.
     */
    public static ItemStack extract(final Container rawInventory, final int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        if (!rawInventory.getItem(OUTPUT_SLOT).isEmpty())
            return rawInventory.removeItem(OUTPUT_SLOT, amount);

        ItemStack result = MissileAssembly.assemble(
                rawInventory.getItem(BODY_SLOT),
                rawInventory.getItem(CHIP_SLOT),
                rawInventory.getItem(PAYLOAD_SLOT));
        if (result.isEmpty()) return ItemStack.EMPTY;

        // All three stacks were validated together before any mutation. The
        // preview represents exactly one missile, so an oversized request
        // cannot consume more than one component set.
        rawInventory.removeItem(BODY_SLOT, 1);
        rawInventory.removeItem(CHIP_SLOT, 1);
        rawInventory.removeItem(PAYLOAD_SLOT, 1);
        result.setCount(1);
        return result;
    }
}
