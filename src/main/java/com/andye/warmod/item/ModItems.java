package com.andye.warmod.item;

import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.block.MissileSiloBlockItem;
import com.andye.warmod.block.MissileSiloGuidanceSupportItem;
import com.andye.warmod.block.PhalanxTurretBlockItem;
import com.andye.warmod.block.RadarStationBlockItem;
import com.andye.warmod.artillery.ArtilleryPayload;
import com.andye.warmod.firearm.FirearmType;
import com.andye.warmod.block.ModBlocks;
import com.andye.warmod.warhead.WarheadYield;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final ResourceKey<Item> MASTER_EXPLOSIVE_TEST_STICK_KEY = key("master_explosive_test_stick");
    public static final ResourceKey<Item> ANTI_AIR_TEST_STICK_KEY = key("anti_air_test_stick");
    public static final ResourceKey<Item> RADAR_KEY = key("radar");
    public static final ResourceKey<Item> MISSILE_SILO_KEY = key("missile_silo");
    public static final ResourceKey<Item> CONVENTIONAL_ICBM_KEY = key("conventional_icbm");
    public static final ResourceKey<Item> NUCLEAR_ICBM_KEY = key("nuclear_icbm");
    public static final ResourceKey<Item> CONVENTIONAL_CLUSTER_ICBM_KEY = key("conventional_cluster_icbm");
    public static final ResourceKey<Item> NUCLEAR_CLUSTER_ICBM_KEY = key("nuclear_cluster_icbm");
    public static final ResourceKey<Item> ANTI_AIR_MISSILE_MK1_KEY = key("anti_air_missile_mk1");
    public static final ResourceKey<Item> ANTI_AIR_MISSILE_MK2_KEY = key("anti_air_missile_mk2");
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
    public static final ResourceKey<Item> CONTROLLER_LINKING_TOOL_KEY = key("controller_linking_tool");
    public static final ResourceKey<Item> ITEM_PIPE_KEY = key("item_pipe");
    public static final ResourceKey<Item> PIPE_WRENCH_KEY = key("pipe_wrench");
    public static final ResourceKey<Item> ARTILLERY_CANNON_KEY = key("artillery_cannon");
    public static final ResourceKey<Item> LAUNCH_CONTROLLER_KEY = key("launch_controller");
    public static final ResourceKey<Item> FIRE_DEBUG_STICK_KEY = key("fire_debug_stick");
    public static final ResourceKey<Item> FIRE_HOSE_KEY = key("fire_hose");
    public static final ResourceKey<Item> FIRE_EXTINGUISHER_KEY = key("fire_extinguisher");
    public static final ResourceKey<Item> PISTOL_KEY = key("pistol");
    public static final ResourceKey<Item> ASSAULT_RIFLE_KEY = key("assault_rifle");
    public static final ResourceKey<Item> SNIPER_RIFLE_KEY = key("sniper_rifle");
    public static final ResourceKey<Item> PISTOL_AMMO_KEY = key("pistol_ammo");
    public static final ResourceKey<Item> RIFLE_AMMO_KEY = key("rifle_ammo");
    public static final ResourceKey<Item> SNIPER_AMMO_KEY = key("sniper_ammo");

    public static final Item MASTER_EXPLOSIVE_TEST_STICK = new MasterExplosiveStickItem(properties(MASTER_EXPLOSIVE_TEST_STICK_KEY, 1));
    public static final Item ANTI_AIR_TEST_STICK = new AntiAirTestStickItem(properties(ANTI_AIR_TEST_STICK_KEY, 1));
    public static final Item RADAR = new RadarItem(properties(RADAR_KEY, 1));
    public static final Item MISSILE_SILO = new MissileSiloBlockItem(properties(MISSILE_SILO_KEY, 1));
    public static final Item CONVENTIONAL_ICBM = new ConventionalIcbmItem(properties(CONVENTIONAL_ICBM_KEY, 16));
    public static final Item NUCLEAR_ICBM = new NuclearIcbmItem(properties(NUCLEAR_ICBM_KEY, 16));
    public static final Item CONVENTIONAL_CLUSTER_ICBM = new ConventionalIcbmItem(properties(CONVENTIONAL_CLUSTER_ICBM_KEY, 16));
    public static final Item NUCLEAR_CLUSTER_ICBM = new NuclearIcbmItem(properties(NUCLEAR_CLUSTER_ICBM_KEY, 16));
    public static final Item ANTI_AIR_MISSILE_MK1 = new AntiAirMissileItem(properties(ANTI_AIR_MISSILE_MK1_KEY, 16), AntiAirMissileVariant.MK_I);
    public static final Item ANTI_AIR_MISSILE_MK2 = new AntiAirMissileItem(properties(ANTI_AIR_MISSILE_MK2_KEY, 16), AntiAirMissileVariant.MK_II);
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
    public static final Item CONTROLLER_LINKING_TOOL = new ControllerLinkingToolItem(
        properties(CONTROLLER_LINKING_TOOL_KEY, 1)
    );
    public static final Item ITEM_PIPE = new BlockItem(
        com.andye.warmod.block.ModBlocks.ITEM_PIPE,
        properties(ITEM_PIPE_KEY, 64)
    );
    public static final Item PIPE_WRENCH = new PipeWrenchItem(properties(PIPE_WRENCH_KEY, 1));
    public static final Item ARTILLERY_CANNON = new BlockItem(ModBlocks.ARTILLERY_CANNON, properties(ARTILLERY_CANNON_KEY, 1));
    public static final Item LAUNCH_CONTROLLER = new BlockItem(ModBlocks.LAUNCH_CONTROLLER, properties(LAUNCH_CONTROLLER_KEY, 64));
    public static final Item FIRE_DEBUG_STICK = new FireDebugStickItem(properties(FIRE_DEBUG_STICK_KEY, 1));
    public static final Item FIRE_HOSE = new FireHoseItem(properties(FIRE_HOSE_KEY, 1));
    public static final Item FIRE_EXTINGUISHER = new FireExtinguisherItem(properties(FIRE_EXTINGUISHER_KEY, 1));
    public static final Item PISTOL = new FirearmItem(properties(PISTOL_KEY, 1), FirearmType.PISTOL);
    public static final Item ASSAULT_RIFLE = new FirearmItem(properties(ASSAULT_RIFLE_KEY, 1), FirearmType.ASSAULT_RIFLE);
    public static final Item SNIPER_RIFLE = new FirearmItem(properties(SNIPER_RIFLE_KEY, 1), FirearmType.SNIPER_RIFLE);
    public static final Item PISTOL_AMMO = new Item(magazineProperties(PISTOL_AMMO_KEY,
        FirearmType.PISTOL.magazineCapacity()));
    public static final Item RIFLE_AMMO = new Item(magazineProperties(RIFLE_AMMO_KEY,
        FirearmType.ASSAULT_RIFLE.magazineCapacity()));
    public static final Item SNIPER_AMMO = new Item(magazineProperties(SNIPER_AMMO_KEY,
        FirearmType.SNIPER_RIFLE.magazineCapacity()));
    private static final Map<WarheadYield, Map<PayloadKind, Item>> YIELD_ITEMS = createYieldItems();

    public static final Item ICBM_BODY = new Item(properties(key("icbm_body"), 64));
    public static final Item ANTI_AIR_BODY = new Item(properties(key("anti_air_body"), 64));
    public static final Item TARGETING_CHIP_TIER_1 = new Item(properties(key("targeting_chip_tier_1"), 64));
    public static final Item TARGETING_CHIP_TIER_2 = new Item(properties(key("targeting_chip_tier_2"), 64));
    public static final Item TARGETING_CHIP_TIER_3 = new Item(properties(key("targeting_chip_tier_3"), 64));
    public static final Item ANTI_AIR_CONTROLLER_BALLISTIC = new Item(properties(key("anti_air_controller_ballistic"), 64));
    public static final Item ANTI_AIR_CONTROLLER_SELF_DESTRUCT = new Item(properties(key("anti_air_controller_self_destruct"), 64));
    public static final Item MISSILE_WORKBENCH = new BlockItem(ModBlocks.MISSILE_WORKBENCH, properties(key("missile_workbench"), 64));
    public static final Item HIGH_EXPLOSIVE_MISSILE_WARHEAD = new Item(properties(key("high_explosive_missile_warhead"), 64));
    public static final Item HIGH_EXPLOSIVE_CLUSTER_MISSILE_WARHEAD = new Item(properties(key("high_explosive_cluster_missile_warhead"), 64));
    public static final Item HIGH_CAPACITY_HE_MISSILE_WARHEAD = new Item(properties(key("high_capacity_he_missile_warhead"), 64));
    public static final Item HIGH_CAPACITY_HE_CLUSTER_MISSILE_WARHEAD = new Item(properties(key("high_capacity_he_cluster_missile_warhead"), 64));
    public static final Item CONVENTIONAL_MISSILE_WARHEAD = new Item(properties(key("conventional_missile_warhead"), 64));
    public static final Item CONVENTIONAL_CLUSTER_MISSILE_WARHEAD = new Item(properties(key("conventional_cluster_missile_warhead"), 64));
    public static final Item HEAVY_CONVENTIONAL_MISSILE_WARHEAD = new Item(properties(key("heavy_conventional_missile_warhead"), 64));
    public static final Item HEAVY_CONVENTIONAL_CLUSTER_MISSILE_WARHEAD = new Item(properties(key("heavy_conventional_cluster_missile_warhead"), 64));
    public static final Item TACTICAL_NUCLEAR_MISSILE_WARHEAD = new Item(properties(key("tactical_nuclear_missile_warhead"), 64));
    public static final Item TACTICAL_NUCLEAR_CLUSTER_MISSILE_WARHEAD = new Item(properties(key("tactical_nuclear_cluster_missile_warhead"), 64));
    public static final Item STRATEGIC_NUCLEAR_MISSILE_WARHEAD = new Item(properties(key("strategic_nuclear_missile_warhead"), 64));
    public static final Item STRATEGIC_NUCLEAR_CLUSTER_MISSILE_WARHEAD = new Item(properties(key("strategic_nuclear_cluster_missile_warhead"), 64));
    public static final Item HEAVY_NUCLEAR_MISSILE_WARHEAD = new Item(properties(key("heavy_nuclear_missile_warhead"), 64));
    public static final Item HEAVY_NUCLEAR_CLUSTER_MISSILE_WARHEAD = new Item(properties(key("heavy_nuclear_cluster_missile_warhead"), 64));
    private static boolean registered;

    private ModItems() {
    }

    public static void register() {
        if (registered) return;
        register(MASTER_EXPLOSIVE_TEST_STICK_KEY, MASTER_EXPLOSIVE_TEST_STICK);
        register(ANTI_AIR_TEST_STICK_KEY, ANTI_AIR_TEST_STICK);
        register(RADAR_KEY, RADAR);
        register(MISSILE_SILO_KEY, MISSILE_SILO);
        register(CONVENTIONAL_ICBM_KEY, CONVENTIONAL_ICBM);
        register(NUCLEAR_ICBM_KEY, NUCLEAR_ICBM);
        register(CONVENTIONAL_CLUSTER_ICBM_KEY, CONVENTIONAL_CLUSTER_ICBM);
        register(NUCLEAR_CLUSTER_ICBM_KEY, NUCLEAR_CLUSTER_ICBM);
        register(ANTI_AIR_MISSILE_MK1_KEY, ANTI_AIR_MISSILE_MK1);
        register(ANTI_AIR_MISSILE_MK2_KEY, ANTI_AIR_MISSILE_MK2);
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
        register(CONTROLLER_LINKING_TOOL_KEY, CONTROLLER_LINKING_TOOL);
        register(ITEM_PIPE_KEY, ITEM_PIPE);
        register(PIPE_WRENCH_KEY, PIPE_WRENCH);
        register(ARTILLERY_CANNON_KEY, ARTILLERY_CANNON);
        register(LAUNCH_CONTROLLER_KEY, LAUNCH_CONTROLLER);
        register(FIRE_DEBUG_STICK_KEY, FIRE_DEBUG_STICK);
        register(FIRE_HOSE_KEY, FIRE_HOSE);
        register(FIRE_EXTINGUISHER_KEY, FIRE_EXTINGUISHER);
        register(PISTOL_KEY, PISTOL);
        register(ASSAULT_RIFLE_KEY, ASSAULT_RIFLE);
        register(SNIPER_RIFLE_KEY, SNIPER_RIFLE);
        register(PISTOL_AMMO_KEY, PISTOL_AMMO);
        register(RIFLE_AMMO_KEY, RIFLE_AMMO);
        register(SNIPER_AMMO_KEY, SNIPER_AMMO);
        for (WarheadYield yield : WarheadYield.values()) for (PayloadKind kind : PayloadKind.values()) register(key(kind.path(yield)), item(yield, kind));
        register(key("icbm_body"), ICBM_BODY);
        register(key("anti_air_body"), ANTI_AIR_BODY);
        register(key("targeting_chip_tier_1"), TARGETING_CHIP_TIER_1);
        register(key("targeting_chip_tier_2"), TARGETING_CHIP_TIER_2);
        register(key("targeting_chip_tier_3"), TARGETING_CHIP_TIER_3);
        register(key("anti_air_controller_ballistic"), ANTI_AIR_CONTROLLER_BALLISTIC);
        register(key("anti_air_controller_self_destruct"), ANTI_AIR_CONTROLLER_SELF_DESTRUCT);
        register(key("missile_workbench"), MISSILE_WORKBENCH);
        register(key("high_explosive_missile_warhead"), HIGH_EXPLOSIVE_MISSILE_WARHEAD);
        register(key("high_explosive_cluster_missile_warhead"), HIGH_EXPLOSIVE_CLUSTER_MISSILE_WARHEAD);
        register(key("high_capacity_he_missile_warhead"), HIGH_CAPACITY_HE_MISSILE_WARHEAD);
        register(key("high_capacity_he_cluster_missile_warhead"), HIGH_CAPACITY_HE_CLUSTER_MISSILE_WARHEAD);
        register(key("conventional_missile_warhead"), CONVENTIONAL_MISSILE_WARHEAD);
        register(key("conventional_cluster_missile_warhead"), CONVENTIONAL_CLUSTER_MISSILE_WARHEAD);
        register(key("heavy_conventional_missile_warhead"), HEAVY_CONVENTIONAL_MISSILE_WARHEAD);
        register(key("heavy_conventional_cluster_missile_warhead"), HEAVY_CONVENTIONAL_CLUSTER_MISSILE_WARHEAD);
        register(key("tactical_nuclear_missile_warhead"), TACTICAL_NUCLEAR_MISSILE_WARHEAD);
        register(key("tactical_nuclear_cluster_missile_warhead"), TACTICAL_NUCLEAR_CLUSTER_MISSILE_WARHEAD);
        register(key("strategic_nuclear_missile_warhead"), STRATEGIC_NUCLEAR_MISSILE_WARHEAD);
        register(key("strategic_nuclear_cluster_missile_warhead"), STRATEGIC_NUCLEAR_CLUSTER_MISSILE_WARHEAD);
        register(key("heavy_nuclear_missile_warhead"), HEAVY_NUCLEAR_MISSILE_WARHEAD);
        register(key("heavy_nuclear_cluster_missile_warhead"), HEAVY_NUCLEAR_CLUSTER_MISSILE_WARHEAD);
        registered = true;
    }

    public static Item missileWarhead(final WarheadYield yield, final boolean cluster) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("war_mod", yield.getSerializedName() + (cluster ? "_cluster" : "") + "_missile_warhead"));
    }
    public static Item guidanceSupport(final int tier) {
        return switch (tier) {
            case 2 -> GUIDANCE_TIER_2;
            case 3 -> GUIDANCE_TIER_3;
            default -> GUIDANCE_TIER_1;
        };
    }

    public static Item yieldMissile(final WarheadYield yield, final boolean cluster) { return item(yield, cluster ? PayloadKind.CLUSTER_MISSILE : PayloadKind.MISSILE); }
    public static Item artilleryWarhead(final WarheadYield yield, final boolean cluster) { return item(yield, cluster ? PayloadKind.CLUSTER_WARHEAD : PayloadKind.WARHEAD); }
    public static Item timedTnt(final WarheadYield yield, final boolean cluster) { return item(yield, cluster ? PayloadKind.CLUSTER_TNT : PayloadKind.TNT); }
    public static Iterable<Item> yieldItems() { return YIELD_ITEMS.values().stream().flatMap(values -> values.values().stream()).toList(); }
    private static Item item(final WarheadYield yield, final PayloadKind kind) { return YIELD_ITEMS.get(yield).get(kind); }
    private static Map<WarheadYield, Map<PayloadKind, Item>> createYieldItems() {
        Map<WarheadYield, Map<PayloadKind, Item>> result = new EnumMap<>(WarheadYield.class);
        for (WarheadYield yield : WarheadYield.values()) {
            Map<PayloadKind, Item> values = new EnumMap<>(PayloadKind.class);
            for (PayloadKind kind : PayloadKind.values()) {
                ResourceKey<Item> itemKey = key(kind.path(yield));
                ArtilleryPayload payload = new ArtilleryPayload(yield, kind.cluster());
                values.put(kind, switch (kind) {
                    case MISSILE, CLUSTER_MISSILE -> new YieldMissileItem(properties(itemKey, 16), payload);
                    case WARHEAD, CLUSTER_WARHEAD -> new ArtilleryWarheadItem(properties(itemKey, 16), payload);
                    case TNT, CLUSTER_TNT -> new YieldTntBlockItem(
                        ModBlocks.timedTnt(yield, kind.cluster()), properties(itemKey, 16), payload);
                });
            }
            result.put(yield, Map.copyOf(values));
        }
        return Map.copyOf(result);
    }
    private enum PayloadKind {
        MISSILE("missile", false), CLUSTER_MISSILE("cluster_missile", true), WARHEAD("warhead", false), CLUSTER_WARHEAD("cluster_warhead", true), TNT("tnt", false), CLUSTER_TNT("cluster_tnt", true);
        private final String suffix; private final boolean cluster;
        PayloadKind(final String suffix, final boolean cluster) { this.suffix = suffix; this.cluster = cluster; }
        String path(final WarheadYield yield) { return yield.getSerializedName() + "_" + suffix; }
        boolean cluster() { return cluster; }
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

    private static Item.Properties magazineProperties(final ResourceKey<Item> key,
        final int capacity) {
        return new Item.Properties().setId(key).durability(capacity);
    }
}
