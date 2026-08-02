package com.andye.warmod.item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import com.andye.warmod.block.MissileSiloBlockItem;
import com.andye.warmod.block.MissileSiloGuidanceFrameItem;
import com.andye.warmod.block.RadarStationBlockItem;
import com.andye.warmod.silo.MissileSiloConstants;

public final class ModItems {
	public static final ResourceKey<Item> ACOUSTIC_TEST_STICK_KEY=key("acoustic_test_stick"),ICBM_TEST_STICK_KEY=key("icbm_test_stick"),NUCLEAR_TEST_STICK_KEY=key("nuclear_test_stick"),NUCLEAR_ICBM_TEST_STICK_KEY=key("nuclear_icbm_test_stick"),RADAR_KEY=key("radar"),MISSILE_SILO_KEY=key("missile_silo"),CONVENTIONAL_ICBM_KEY=key("conventional_icbm"),NUCLEAR_ICBM_KEY=key("nuclear_icbm"),TARGET_DESIGNATOR_KEY=key("target_designator"),REMOTE_LAUNCH_DESIGNATOR_KEY=key("remote_launch_designator"),ROCKET_LAUNCHER_KEY=key("rocket_launcher"),HE_ROCKET_KEY=key("he_rocket"),MISSILE_SILO_GUIDANCE_FRAME_KEY=key("missile_silo_guidance_frame"),RADAR_STATION_KEY=key("radar_station");
	public static final Item ACOUSTIC_TEST_STICK=new AcousticTestStickItem(properties(ACOUSTIC_TEST_STICK_KEY));
	public static final Item ICBM_TEST_STICK=new IcbmTestStickItem(properties(ICBM_TEST_STICK_KEY));
	public static final Item NUCLEAR_TEST_STICK=new NuclearTestStickItem(properties(NUCLEAR_TEST_STICK_KEY));
	public static final Item NUCLEAR_ICBM_TEST_STICK=new NuclearIcbmTestStickItem(properties(NUCLEAR_ICBM_TEST_STICK_KEY));
	public static final Item RADAR=new RadarItem(properties(RADAR_KEY));
	public static final Item MISSILE_SILO=new MissileSiloBlockItem(properties(MISSILE_SILO_KEY));
	public static final Item CONVENTIONAL_ICBM=new ConventionalIcbmItem(new Item.Properties().setId(CONVENTIONAL_ICBM_KEY).stacksTo(MissileSiloConstants.MISSILE_ITEM_STACK_SIZE));
	public static final Item NUCLEAR_ICBM=new NuclearIcbmItem(new Item.Properties().setId(NUCLEAR_ICBM_KEY).stacksTo(MissileSiloConstants.MISSILE_ITEM_STACK_SIZE));
	public static final Item TARGET_DESIGNATOR=new TargetDesignatorItem(properties(TARGET_DESIGNATOR_KEY));
	public static final Item REMOTE_LAUNCH_DESIGNATOR=new RemoteLaunchDesignatorItem(properties(REMOTE_LAUNCH_DESIGNATOR_KEY));
	public static final Item ROCKET_LAUNCHER=new RocketLauncherItem(properties(ROCKET_LAUNCHER_KEY));
	public static final Item HE_ROCKET=new HighExplosiveRocketItem(new Item.Properties().setId(HE_ROCKET_KEY).stacksTo(64));
	public static final Item MISSILE_SILO_GUIDANCE_FRAME=new MissileSiloGuidanceFrameItem(new Item.Properties().setId(MISSILE_SILO_GUIDANCE_FRAME_KEY).stacksTo(16));
	public static final Item RADAR_STATION=new RadarStationBlockItem(new Item.Properties().setId(RADAR_STATION_KEY).stacksTo(1));
	private static boolean registered;private ModItems(){}
	public static void register(){if(registered)return;Registry.register(BuiltInRegistries.ITEM,ACOUSTIC_TEST_STICK_KEY,ACOUSTIC_TEST_STICK);Registry.register(BuiltInRegistries.ITEM,ICBM_TEST_STICK_KEY,ICBM_TEST_STICK);Registry.register(BuiltInRegistries.ITEM,NUCLEAR_TEST_STICK_KEY,NUCLEAR_TEST_STICK);Registry.register(BuiltInRegistries.ITEM,NUCLEAR_ICBM_TEST_STICK_KEY,NUCLEAR_ICBM_TEST_STICK);Registry.register(BuiltInRegistries.ITEM,RADAR_KEY,RADAR);Registry.register(BuiltInRegistries.ITEM,MISSILE_SILO_KEY,MISSILE_SILO);Registry.register(BuiltInRegistries.ITEM,CONVENTIONAL_ICBM_KEY,CONVENTIONAL_ICBM);Registry.register(BuiltInRegistries.ITEM,NUCLEAR_ICBM_KEY,NUCLEAR_ICBM);Registry.register(BuiltInRegistries.ITEM,TARGET_DESIGNATOR_KEY,TARGET_DESIGNATOR);Registry.register(BuiltInRegistries.ITEM,REMOTE_LAUNCH_DESIGNATOR_KEY,REMOTE_LAUNCH_DESIGNATOR);Registry.register(BuiltInRegistries.ITEM,ROCKET_LAUNCHER_KEY,ROCKET_LAUNCHER);Registry.register(BuiltInRegistries.ITEM,HE_ROCKET_KEY,HE_ROCKET);Registry.register(BuiltInRegistries.ITEM,MISSILE_SILO_GUIDANCE_FRAME_KEY,MISSILE_SILO_GUIDANCE_FRAME);Registry.register(BuiltInRegistries.ITEM,RADAR_STATION_KEY,RADAR_STATION);CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output->{output.accept(ACOUSTIC_TEST_STICK);output.accept(ICBM_TEST_STICK);output.accept(NUCLEAR_TEST_STICK);output.accept(NUCLEAR_ICBM_TEST_STICK);output.accept(RADAR);output.accept(TARGET_DESIGNATOR);output.accept(REMOTE_LAUNCH_DESIGNATOR);});CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output->{output.accept(CONVENTIONAL_ICBM);output.accept(NUCLEAR_ICBM);output.accept(ROCKET_LAUNCHER);output.accept(HE_ROCKET);output.accept(MISSILE_SILO_GUIDANCE_FRAME);output.accept(RADAR_STATION);});CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output->output.accept(MISSILE_SILO));registered=true;}
	private static ResourceKey<Item> key(final String path){return ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("war_mod",path));}
	private static Item.Properties properties(final ResourceKey<Item> key){return new Item.Properties().setId(key).stacksTo(1);}
}
