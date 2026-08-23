package com.andye.warmod.item;

import com.andye.warmod.WarMod;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import com.andye.warmod.warhead.WarheadYield;

public final class ModCreativeModeTabs {
    public static final ResourceKey<CreativeModeTab> WAR_MOD_KEY = ResourceKey.create(
        Registries.CREATIVE_MODE_TAB,
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "war_mod")
    );
    private static final int ENTRY_COUNT = 66;
    private static boolean registered;

    private ModCreativeModeTabs() {
    }

    public static void register() {
        if (registered) return;
        CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.war_mod.war_mod"))
            .icon(() -> new ItemStack(ModItems.yieldMissile(
                WarheadYield.STRATEGIC_NUCLEAR, false)))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.MISSILE_SILO);
                output.accept(ModItems.ARTILLERY_CANNON);
                output.accept(ModItems.GUIDANCE_TIER_1);
                output.accept(ModItems.GUIDANCE_TIER_2);
                output.accept(ModItems.GUIDANCE_TIER_3);
                output.accept(ModItems.RADAR_STATION);
                output.accept(ModItems.RADAR_DISPLAY_PANEL);
                output.accept(ModItems.PHALANX_TURRET);
                output.accept(ModItems.ITEM_PIPE);
                output.accept(ModItems.PIPE_WRENCH);
                /* Legacy four ICBM IDs remain registered so old worlds load, but
                   the yield-aware replacements are the only creative entries. */
                for (WarheadYield yield : WarheadYield.values()) {
                    output.accept(ModItems.timedTnt(yield, false));
                    output.accept(ModItems.timedTnt(yield, true));
                }
                for (WarheadYield yield : WarheadYield.values()) {
                    output.accept(ModItems.artilleryWarhead(yield, false));
                    output.accept(ModItems.artilleryWarhead(yield, true));
                }
                for (WarheadYield yield : WarheadYield.values()) {
                    output.accept(ModItems.yieldMissile(yield, false));
                    output.accept(ModItems.yieldMissile(yield, true));
                }
                output.accept(ModItems.ANTI_AIR_MISSILE_MK1);
                output.accept(ModItems.ANTI_AIR_MISSILE_MK2);
                output.accept(ModItems.ROCKET_LAUNCHER);
                output.accept(ModItems.HE_ROCKET);
                output.accept(ModItems.ANTI_AIR_GUN_AMMO);
                output.accept(ModItems.TARGET_DESIGNATOR);
                output.accept(ModItems.REMOTE_LAUNCH_DESIGNATOR);
                output.accept(ModItems.RADAR);
                output.accept(ModItems.RADAR_LINKING_TOOL);
                output.accept(ModItems.MASTER_EXPLOSIVE_TEST_STICK);
                output.accept(ModItems.ANTI_AIR_TEST_STICK);
                output.accept(ModItems.FIRE_DEBUG_STICK);
                output.accept(ModItems.FIRE_HOSE);
                output.accept(ModItems.FIRE_EXTINGUISHER);
            }).build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, WAR_MOD_KEY, tab);
        registered = true;
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info("War Mod creative tab registered with {} entries", ENTRY_COUNT);
        }
    }
}
