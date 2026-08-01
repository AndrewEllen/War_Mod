package com.andye.warmod.item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final ResourceKey<Item> ACOUSTIC_TEST_STICK_KEY = ResourceKey.create(
		Registries.ITEM,
		Identifier.fromNamespaceAndPath("war_mod", "acoustic_test_stick")
	);
	public static final Item ACOUSTIC_TEST_STICK = new AcousticTestStickItem(
		new Item.Properties().setId(ACOUSTIC_TEST_STICK_KEY).stacksTo(1)
	);

	private static boolean registered;

	private ModItems() {
	}

	public static void register() {
		if (registered) {
			return;
		}

		Registry.register(BuiltInRegistries.ITEM, ACOUSTIC_TEST_STICK_KEY, ACOUSTIC_TEST_STICK);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(output -> output.accept(ACOUSTIC_TEST_STICK));
		registered = true;
	}
}
