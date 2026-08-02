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
    public static final DataComponentType<LinkedSilo> LINKED_SILO = DataComponentType
        .<LinkedSilo>builder().persistent(LinkedSilo.CODEC).build();
    private static boolean registered;

    private ModDataComponents() {
    }

    public static void register() {
        if (registered) return;
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("target_coordinates"), TARGET_COORDINATES);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("linked_silo"), LINKED_SILO);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("rocket_launcher_mode"), ROCKET_LAUNCHER_MODE);
        registered = true;
    }

    private static Identifier id(final String path) {
        return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, path);
    }
}
