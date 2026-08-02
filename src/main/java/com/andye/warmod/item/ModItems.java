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
	public static final ResourceKey<Item> ACOUSTIC_TEST_STICK_KEY=key("acoustic_test_stick"),ICBM_TEST_STICK_KEY=key("icbm_test_stick"),NUCLEAR_TEST_STICK_KEY=key("nuclear_test_stick"),NUCLEAR_ICBM_TEST_STICK_KEY=key("nuclear_icbm_test_stick"),RADAR_KEY=key("radar");
	public static final Item ACOUSTIC_TEST_STICK=new AcousticTestStickItem(properties(ACOUSTIC_TEST_STICK_KEY));
	public static final Item ICBM_TEST_STICK=new IcbmTestStickItem(properties(ICBM_TEST_STICK_KEY));
	public static final Item NUCLEAR_TEST_STICK=new NuclearTestStickItem(properties(NUCLEAR_TEST_STICK_KEY));
	public static final Item NUCLEAR_ICBM_TEST_STICK=new NuclearIcbmTestStickItem(properties(NUCLEAR_ICBM_TEST_STICK_KEY));
	public static final Item RADAR=new RadarItem(properties(RADAR_KEY));
	private static boolean registered;private ModItems(){}
	public static void register(){if(registered)return;Registry.register(BuiltInRegistries.ITEM,ACOUSTIC_TEST_STICK_KEY,ACOUSTIC_TEST_STICK);Registry.register(BuiltInRegistries.ITEM,ICBM_TEST_STICK_KEY,ICBM_TEST_STICK);Registry.register(BuiltInRegistries.ITEM,NUCLEAR_TEST_STICK_KEY,NUCLEAR_TEST_STICK);Registry.register(BuiltInRegistries.ITEM,NUCLEAR_ICBM_TEST_STICK_KEY,NUCLEAR_ICBM_TEST_STICK);Registry.register(BuiltInRegistries.ITEM,RADAR_KEY,RADAR);CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output->{output.accept(ACOUSTIC_TEST_STICK);output.accept(ICBM_TEST_STICK);output.accept(NUCLEAR_TEST_STICK);output.accept(NUCLEAR_ICBM_TEST_STICK);output.accept(RADAR);});registered=true;}
	private static ResourceKey<Item> key(final String path){return ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("war_mod",path));}
	private static Item.Properties properties(final ResourceKey<Item> key){return new Item.Properties().setId(key).stacksTo(1);}
}
