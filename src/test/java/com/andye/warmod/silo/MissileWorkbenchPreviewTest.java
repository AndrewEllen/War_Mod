package com.andye.warmod.silo;

import static org.junit.jupiter.api.Assertions.*;

import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.warhead.WarheadYield;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Focused extraction contract: previewing alone never spends components. */
final class MissileWorkbenchPreviewTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponents.register();
        ModBlocks.register();
        ModItems.register();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("war_mod")
                    && !item.builtInRegistryHolder().areComponentsBound()) {
                item.builtInRegistryHolder().bindComponents(
                        net.minecraft.core.component.DataComponentMap.builder()
                                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 16)
                                .build());
            }
        }
    }
    @Test
    void previewRetainsInputsThenExtractsExactlyOneCompleteSet() {
        SimpleContainer inventory = completeIcbmSet();
        ItemStack preview = MissileWorkbenchPreview.preview(inventory);
        assertFalse(preview.isEmpty());
        assertEquals(2, inventory.getItem(MissileWorkbenchPreview.BODY_SLOT).getCount());
        assertEquals(2, inventory.getItem(MissileWorkbenchPreview.CHIP_SLOT).getCount());
        assertEquals(2, inventory.getItem(MissileWorkbenchPreview.PAYLOAD_SLOT).getCount());

        ItemStack extracted = MissileWorkbenchPreview.extract(inventory, 64);
        assertTrue(ItemStack.isSameItemSameComponents(preview, extracted));
        assertEquals(1, extracted.getCount());
        assertEquals(1, inventory.getItem(MissileWorkbenchPreview.BODY_SLOT).getCount());
        assertEquals(1, inventory.getItem(MissileWorkbenchPreview.CHIP_SLOT).getCount());
        assertEquals(1, inventory.getItem(MissileWorkbenchPreview.PAYLOAD_SLOT).getCount());
        assertFalse(MissileWorkbenchPreview.preview(inventory).isEmpty());
    }

    @Test
    void legacyStoredOutputWinsWithoutConsumingRetainedInputs() {
        SimpleContainer inventory = completeIcbmSet();
        ItemStack legacy = new ItemStack(ModItems.yieldMissile(WarheadYield.HIGH_EXPLOSIVE, false));
        inventory.setItem(MissileWorkbenchPreview.OUTPUT_SLOT, legacy.copy());

        assertTrue(MissileWorkbenchPreview.preview(inventory).is(legacy.getItem()));
        assertTrue(MissileWorkbenchPreview.extract(inventory, 1).is(legacy.getItem()));
        assertEquals(2, inventory.getItem(MissileWorkbenchPreview.BODY_SLOT).getCount());
        assertEquals(2, inventory.getItem(MissileWorkbenchPreview.CHIP_SLOT).getCount());
        assertEquals(2, inventory.getItem(MissileWorkbenchPreview.PAYLOAD_SLOT).getCount());
    }

    private static SimpleContainer completeIcbmSet() {
        SimpleContainer inventory = new SimpleContainer(4);
        inventory.setItem(MissileWorkbenchPreview.BODY_SLOT, new ItemStack(ModItems.ICBM_BODY, 2));
        inventory.setItem(MissileWorkbenchPreview.CHIP_SLOT, new ItemStack(ModItems.TARGETING_CHIP_TIER_2, 2));
        inventory.setItem(
                MissileWorkbenchPreview.PAYLOAD_SLOT,
                new ItemStack(ModItems.HIGH_EXPLOSIVE_MISSILE_WARHEAD, 2));
        return inventory;
    }
}
