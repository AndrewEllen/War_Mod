package com.andye.warmod.item.component;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ModDataComponents {
    public static final DataComponentType<TargetCoordinates> TARGET_COORDINATES = DataComponentType
        .<TargetCoordinates>builder().persistent(TargetCoordinates.CODEC).build();
    public static final DataComponentType<RocketLauncherMode> ROCKET_LAUNCHER_MODE = DataComponentType
        .<RocketLauncherMode>builder().persistent(RocketLauncherMode.CODEC).build();
    public static final DataComponentType<AntiAirTestVariant> ANTI_AIR_TEST_VARIANT = DataComponentType
        .<AntiAirTestVariant>builder().persistent(AntiAirTestVariant.CODEC).build();
    public static final DataComponentType<LinkedSilo> LINKED_SILO = DataComponentType
        .<LinkedSilo>builder().persistent(LinkedSilo.CODEC).build();
    public static final DataComponentType<LinkedRadarStation> LINKED_RADAR_STATION = DataComponentType
        .<LinkedRadarStation>builder().persistent(LinkedRadarStation.CODEC).build();
    public static final DataComponentType<IcbmTestDeliveryMode> ICBM_TEST_DELIVERY_MODE = DataComponentType
        .<IcbmTestDeliveryMode>builder().persistent(IcbmTestDeliveryMode.CODEC).build();
    public static final DataComponentType<MasterExplosiveConfig> MASTER_EXPLOSIVE_CONFIG = DataComponentType
        .<MasterExplosiveConfig>builder().persistent(MasterExplosiveConfig.CODEC).build();
    private static boolean registered;

    private ModDataComponents() {
    }

    public static void register() {
        if (registered) return;
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("target_coordinates"), TARGET_COORDINATES);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("linked_silo"), LINKED_SILO);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("rocket_launcher_mode"), ROCKET_LAUNCHER_MODE);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("anti_air_test_variant"), ANTI_AIR_TEST_VARIANT);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("linked_radar_station"), LINKED_RADAR_STATION);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("icbm_test_delivery_mode"), ICBM_TEST_DELIVERY_MODE);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("master_explosive_config"), MASTER_EXPLOSIVE_CONFIG);
        registered = true;
    }

    private static Identifier id(final String path) {
        return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, path);
    }
}
