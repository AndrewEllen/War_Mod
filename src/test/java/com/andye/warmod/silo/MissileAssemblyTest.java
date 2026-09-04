package com.andye.warmod.silo;

import static org.junit.jupiter.api.Assertions.*;

import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.block.entity.MissileSiloInventory;
import com.andye.warmod.item.ModItems;
import com.andye.warmod.item.component.ModDataComponents;
import com.andye.warmod.warhead.WarheadYield;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class MissileAssemblyTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponents.register();
        ModBlocks.register();
        ModItems.register();
        // Unit fixtures have no world data-pack component binding phase in 26.2.
        // Bind only our item holders; production binds their complete initializers on world load.
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(item)
                            .getNamespace()
                            .equals("war_mod")
                    && !item.builtInRegistryHolder().areComponentsBound()) {
                item.builtInRegistryHolder()
                        .bindComponents(
                                net.minecraft.core.component.DataComponentMap.builder()
                                        .set(
                                                net.minecraft.core.component.DataComponents
                                                        .MAX_STACK_SIZE,
                                                16)
                                        .build());
            }
        }
    }

    @Test
    void everyPayloadAndChipProducesTheChosenMissile() {
        for (WarheadYield yield : WarheadYield.values()) {
            for (boolean cluster : new boolean[] {false, true}) {
                for (int tier = 1; tier <= 3; tier++) {
                    ItemStack result =
                            MissileAssembly.assemble(
                                    new ItemStack(ModItems.ICBM_BODY),
                                    chip(tier),
                                    new ItemStack(ModItems.missileWarhead(yield, cluster)));
                    assertTrue(result.is(ModItems.yieldMissile(yield, cluster)));
                    assertEquals(tier, MissilePayloadItems.guidanceTier(result));
                    assertEquals(yield, MissilePayloadItems.yield(result).orElseThrow());
                }
            }
        }
    }

    @Test
    void antiAirControllersKeepTheirExistingFallbackBehavior() {
        for (int tier = 1; tier <= 3; tier++) {
            ItemStack ballistic =
                    MissileAssembly.assemble(
                            new ItemStack(ModItems.ANTI_AIR_BODY),
                            chip(tier),
                            new ItemStack(ModItems.ANTI_AIR_CONTROLLER_BALLISTIC));
            ItemStack selfDestruct =
                    MissileAssembly.assemble(
                            new ItemStack(ModItems.ANTI_AIR_BODY),
                            chip(tier),
                            new ItemStack(ModItems.ANTI_AIR_CONTROLLER_SELF_DESTRUCT));
            assertTrue(
                    MissilePayloadItems.antiAirVariant(ballistic)
                            .orElseThrow()
                            .ballisticFallback());
            assertFalse(
                    MissilePayloadItems.antiAirVariant(selfDestruct)
                            .orElseThrow()
                            .ballisticFallback());
            assertEquals(tier, MissilePayloadItems.guidanceTier(ballistic));
            assertEquals(tier, MissilePayloadItems.guidanceTier(selfDestruct));
        }
    }

    @Test
    void incompatibleComponentsDoNotProduceAMissile() {
        assertTrue(
                MissileAssembly.assemble(
                                new ItemStack(ModItems.ICBM_BODY),
                                chip(1),
                                new ItemStack(ModItems.ANTI_AIR_CONTROLLER_BALLISTIC))
                        .isEmpty());
        assertTrue(
                MissileAssembly.assemble(
                                new ItemStack(ModItems.ANTI_AIR_BODY),
                                chip(1),
                                new ItemStack(ModItems.HIGH_EXPLOSIVE_MISSILE_WARHEAD))
                        .isEmpty());
        assertTrue(
                MissileAssembly.assemble(
                                new ItemStack(ModItems.ICBM_BODY),
                                ItemStack.EMPTY,
                                new ItemStack(ModItems.HIGH_EXPLOSIVE_MISSILE_WARHEAD))
                        .isEmpty());
    }

    @Test
    void siloCannotMergeDifferentAccuracyTiers() {
        MissileSiloInventory inventory = new MissileSiloInventory(() -> {});
        ItemStack tierOne =
                MissilePayloadItems.withGuidance(
                        ModItems.yieldMissile(WarheadYield.HIGH_EXPLOSIVE, false), 1);
        ItemStack tierThree =
                MissilePayloadItems.withGuidance(
                        ModItems.yieldMissile(WarheadYield.HIGH_EXPLOSIVE, false), 3);
        assertEquals(1, inventory.insert(tierOne, 1));
        assertEquals(0, inventory.insert(tierThree, 1));
        assertEquals(1, MissilePayloadItems.guidanceTier(inventory.getItem(0)));
    }

    private static ItemStack chip(int tier) {
        return new ItemStack(
                switch (tier) {
                    case 2 -> ModItems.TARGETING_CHIP_TIER_2;
                    case 3 -> ModItems.TARGETING_CHIP_TIER_3;
                    default -> ModItems.TARGETING_CHIP_TIER_1;
                });
    }
}
