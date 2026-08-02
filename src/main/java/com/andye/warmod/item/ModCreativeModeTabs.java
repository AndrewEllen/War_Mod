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

public final class ModCreativeModeTabs {
    public static final ResourceKey<CreativeModeTab> WAR_MOD_KEY = ResourceKey.create(
        Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "war_mod"));
    private static final int ENTRY_COUNT = 16;
    private static boolean registered;

    private ModCreativeModeTabs() { }

    public static void register() {
        if (registered) return;
        CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.war_mod.war_mod"))
            .icon(() -> new ItemStack(ModItems.NUCLEAR_ICBM))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.MISSILE_SILO);
                output.accept(ModItems.GUIDANCE_TIER_1);
                output.accept(ModItems.GUIDANCE_TIER_2);
                output.accept(ModItems.GUIDANCE_TIER_3);
                output.accept(ModItems.RADAR_STATION);
                output.accept(ModItems.CONVENTIONAL_ICBM);
                output.accept(ModItems.NUCLEAR_ICBM);
                output.accept(ModItems.ROCKET_LAUNCHER);
                output.accept(ModItems.HE_ROCKET);
                output.accept(ModItems.TARGET_DESIGNATOR);
                output.accept(ModItems.REMOTE_LAUNCH_DESIGNATOR);
                output.accept(ModItems.RADAR);
                output.accept(ModItems.ACOUSTIC_TEST_STICK);
                output.accept(ModItems.ICBM_TEST_STICK);
                output.accept(ModItems.NUCLEAR_TEST_STICK);
                output.accept(ModItems.NUCLEAR_ICBM_TEST_STICK);
            }).build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, WAR_MOD_KEY, tab);
        registered = true;
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            WarMod.LOGGER.info("War Mod creative tab registered with {} entries", ENTRY_COUNT);
        }
    }
}