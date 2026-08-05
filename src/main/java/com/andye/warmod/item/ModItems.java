package com.andye.warmod.item;

import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.block.MissileSiloBlockItem;
import com.andye.warmod.block.MissileSiloGuidanceSupportItem;
import com.andye.warmod.block.PhalanxTurretBlockItem;
import com.andye.warmod.block.RadarStationBlockItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final ResourceKey<Item> ACOUSTIC_TEST_STICK_KEY = key("acoustic_test_stick");
    public static final ResourceKey<Item> ICBM_TEST_STICK_KEY = key("icbm_test_stick");
    public static final ResourceKey<Item> NUCLEAR_TEST_STICK_KEY = key("nuclear_test_stick");
    public static final ResourceKey<Item> NUCLEAR_ICBM_TEST_STICK_KEY = key("nuclear_icbm_test_stick");
    public static final ResourceKey<Item> MASTER_EXPLOSIVE_TEST_STICK_KEY = key("master_explosive_test_stick");
    public static final ResourceKey<Item> RADAR_KEY = key("radar");
    public static final ResourceKey<Item> MISSILE_SILO_KEY = key("missile_silo");
    public static final ResourceKey<Item> CONVENTIONAL_ICBM_KEY = key("conventional_icbm");
    public static final ResourceKey<Item> NUCLEAR_ICBM_KEY = key("nuclear_icbm");
    public static final ResourceKey<Item> CONVENTIONAL_CLUSTER_ICBM_KEY = key("conventional_cluster_icbm");
    public static final ResourceKey<Item> NUCLEAR_CLUSTER_ICBM_KEY = key("nuclear_cluster_icbm");
    public static final ResourceKey<Item> ANTI_AIR_MISSILE_MK1_KEY = key("anti_air_missile_mk1");
    public static final ResourceKey<Item> ANTI_AIR_MISSILE_MK2_KEY = key("anti_air_missile_mk2");
    public static final ResourceKey<Item> ANTI_AIR_TEST_STICK_KEY = key("anti_air_test_stick");
    public static final ResourceKey<Item> TARGET_DESIGNATOR_KEY = key("target_designator");
    public static final ResourceKey<Item> REMOTE_LAUNCH_DESIGNATOR_KEY = key("remote_launch_designator");
    public static final ResourceKey<Item> ROCKET_LAUNCHER_KEY = key("rocket_launcher");
    public static final ResourceKey<Item> HE_ROCKET_KEY = key("he_rocket");
    public static final ResourceKey<Item> GUIDANCE_TIER_1_KEY = key("missile_silo_guidance_support_tier_1");
    public static final ResourceKey<Item> GUIDANCE_TIER_2_KEY = key("missile_silo_guidance_support_tier_2");
    public static final ResourceKey<Item> GUIDANCE_TIER_3_KEY = key("missile_silo_guidance_support_tier_3");
    public static final ResourceKey<Item> RADAR_STATION_KEY = key("radar_station");
    public static final ResourceKey<Item> PHALANX_TURRET_KEY = key("phalanx_turret");
    public static final ResourceKey<Item> ANTI_AIR_GUN_AMMO_KEY = key("anti_air_gun_ammo");
    public static final ResourceKey<Item> RADAR_DISPLAY_PANEL_KEY = key("radar_display_panel");
    public static final ResourceKey<Item> RADAR_LINKING_TOOL_KEY = key("radar_linking_tool");
    public static final ResourceKey<Item> ITEM_PIPE_KEY = key("item_pipe");
    public static final ResourceKey<Item> PIPE_WRENCH_KEY = key("pipe_wrench");

    public static final Item ACOUSTIC_TEST_STICK = new AcousticTestStickItem(properties(ACOUSTIC_TEST_STICK_KEY, 1));
    public static final Item ICBM_TEST_STICK = new IcbmTestStickItem(properties(ICBM_TEST_STICK_KEY, 1));
    public static final Item NUCLEAR_TEST_STICK = new NuclearTestStickItem(properties(NUCLEAR_TEST_STICK_KEY, 1));
    public static final Item NUCLEAR_ICBM_TEST_STICK = new NuclearIcbmTestStickItem(properties(NUCLEAR_ICBM_TEST_STICK_KEY, 1));
    public static final Item MASTER_EXPLOSIVE_TEST_STICK = new MasterExplosiveStickItem(properties(MASTER_EXPLOSIVE_TEST_STICK_KEY, 1));
    public static final Item RADAR = new RadarItem(properties(RADAR_KEY, 1));
    public static final Item MISSILE_SILO = new MissileSiloBlockItem(properties(MISSILE_SILO_KEY, 1));
    public static final Item CONVENTIONAL_ICBM = new ConventionalIcbmItem(properties(CONVENTIONAL_ICBM_KEY, 16));
    public static final Item NUCLEAR_ICBM = new NuclearIcbmItem(properties(NUCLEAR_ICBM_KEY, 16));
    public static final Item CONVENTIONAL_CLUSTER_ICBM = new ConventionalIcbmItem(properties(CONVENTIONAL_CLUSTER_ICBM_KEY, 16));
    public static final Item NUCLEAR_CLUSTER_ICBM = new NuclearIcbmItem(properties(NUCLEAR_CLUSTER_ICBM_KEY, 16));
    public static final Item ANTI_AIR_MISSILE_MK1 = new AntiAirMissileItem(properties(ANTI_AIR_MISSILE_MK1_KEY, 16), AntiAirMissileVariant.MK_I);
    public static final Item ANTI_AIR_MISSILE_MK2 = new AntiAirMissileItem(properties(ANTI_AIR_MISSILE_MK2_KEY, 16), AntiAirMissileVariant.MK_II);
    public static final Item ANTI_AIR_TEST_STICK = new AntiAirTestStickItem(properties(ANTI_AIR_TEST_STICK_KEY, 1));
    public static final Item TARGET_DESIGNATOR = new TargetDesignatorItem(properties(TARGET_DESIGNATOR_KEY, 1));
    public static final Item REMOTE_LAUNCH_DESIGNATOR = new RemoteLaunchDesignatorItem(properties(REMOTE_LAUNCH_DESIGNATOR_KEY, 1));
    public static final Item ROCKET_LAUNCHER = new RocketLauncherItem(properties(ROCKET_LAUNCHER_KEY, 1));
    public static final Item HE_ROCKET = new HighExplosiveRocketItem(properties(HE_ROCKET_KEY, 64));
    public static final Item GUIDANCE_TIER_1 = new MissileSiloGuidanceSupportItem(properties(GUIDANCE_TIER_1_KEY, 16), 1);
    public static final Item GUIDANCE_TIER_2 = new MissileSiloGuidanceSupportItem(properties(GUIDANCE_TIER_2_KEY, 16), 2);
    public static final Item GUIDANCE_TIER_3 = new MissileSiloGuidanceSupportItem(properties(GUIDANCE_TIER_3_KEY, 16), 3);
    public static final Item RADAR_STATION = new RadarStationBlockItem(properties(RADAR_STATION_KEY, 1));
    public static final Item PHALANX_TURRET = new PhalanxTurretBlockItem(properties(PHALANX_TURRET_KEY, 1));
    public static final Item ANTI_AIR_GUN_AMMO = new Item(properties(ANTI_AIR_GUN_AMMO_KEY, 64));
    public static final Item RADAR_DISPLAY_PANEL = new BlockItem(
        com.andye.warmod.block.ModBlocks.RADAR_DISPLAY_PANEL,
        properties(RADAR_DISPLAY_PANEL_KEY, 64)
    );
    public static final Item RADAR_LINKING_TOOL = new RadarLinkingToolItem(properties(RADAR_LINKING_TOOL_KEY, 1));
    public static final Item ITEM_PIPE = new BlockItem(
        com.andye.warmod.block.ModBlocks.ITEM_PIPE,
        properties(ITEM_PIPE_KEY, 64)
    );
    public static final Item PIPE_WRENCH = new PipeWrenchItem(properties(PIPE_WRENCH_KEY, 1));

    private static boolean registered;

    private ModItems() {
    }

    public static void register() {
        if (registered) return;
        register(ACOUSTIC_TEST_STICK_KEY, ACOUSTIC_TEST_STICK);
        register(ICBM_TEST_STICK_KEY, ICBM_TEST_STICK);
        register(NUCLEAR_TEST_STICK_KEY, NUCLEAR_TEST_STICK);
        register(NUCLEAR_ICBM_TEST_STICK_KEY, NUCLEAR_ICBM_TEST_STICK);
        register(MASTER_EXPLOSIVE_TEST_STICK_KEY, MASTER_EXPLOSIVE_TEST_STICK);
        register(RADAR_KEY, RADAR);
        register(MISSILE_SILO_KEY, MISSILE_SILO);
        register(CONVENTIONAL_ICBM_KEY, CONVENTIONAL_ICBM);
        register(NUCLEAR_ICBM_KEY, NUCLEAR_ICBM);
        register(CONVENTIONAL_CLUSTER_ICBM_KEY, CONVENTIONAL_CLUSTER_ICBM);
        register(NUCLEAR_CLUSTER_ICBM_KEY, NUCLEAR_CLUSTER_ICBM);
        register(ANTI_AIR_MISSILE_MK1_KEY, ANTI_AIR_MISSILE_MK1);
        register(ANTI_AIR_MISSILE_MK2_KEY, ANTI_AIR_MISSILE_MK2);
        register(ANTI_AIR_TEST_STICK_KEY, ANTI_AIR_TEST_STICK);
        register(TARGET_DESIGNATOR_KEY, TARGET_DESIGNATOR);
        register(REMOTE_LAUNCH_DESIGNATOR_KEY, REMOTE_LAUNCH_DESIGNATOR);
        register(ROCKET_LAUNCHER_KEY, ROCKET_LAUNCHER);
        register(HE_ROCKET_KEY, HE_ROCKET);
        register(GUIDANCE_TIER_1_KEY, GUIDANCE_TIER_1);
        register(GUIDANCE_TIER_2_KEY, GUIDANCE_TIER_2);
        register(GUIDANCE_TIER_3_KEY, GUIDANCE_TIER_3);
        register(RADAR_STATION_KEY, RADAR_STATION);
        register(PHALANX_TURRET_KEY, PHALANX_TURRET);
        register(ANTI_AIR_GUN_AMMO_KEY, ANTI_AIR_GUN_AMMO);
        register(RADAR_DISPLAY_PANEL_KEY, RADAR_DISPLAY_PANEL);
        register(RADAR_LINKING_TOOL_KEY, RADAR_LINKING_TOOL);
        register(ITEM_PIPE_KEY, ITEM_PIPE);
        register(PIPE_WRENCH_KEY, PIPE_WRENCH);
        registered = true;
    }

    public static Item guidanceSupport(final int tier) {
        return switch (tier) {
            case 2 -> GUIDANCE_TIER_2;
            case 3 -> GUIDANCE_TIER_3;
            default -> GUIDANCE_TIER_1;
        };
    }

    private static void register(final ResourceKey<Item> key, final Item item) {
        Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static ResourceKey<Item> key(final String path) {
        return ResourceKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("war_mod", path)
        );
    }

    private static Item.Properties properties(final ResourceKey<Item> key, final int size) {
        return new Item.Properties().setId(key).stacksTo(size);
    }
}
