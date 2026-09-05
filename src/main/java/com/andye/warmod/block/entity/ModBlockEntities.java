package com.andye.warmod.block.entity;

import com.andye.warmod.WarMod;
import com.andye.warmod.block.ModBlocks;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final ResourceKey<BlockEntityType<?>> ARTILLERY_CANNON_KEY = key("artillery_cannon");
    public static final BlockEntityType<ArtilleryCannonBlockEntity> ARTILLERY_CANNON =
        new BlockEntityType<>(ArtilleryCannonBlockEntity::new, Set.of(ModBlocks.ARTILLERY_CANNON));
    public static final ResourceKey<BlockEntityType<?>> LAUNCH_CONTROLLER_KEY = key("launch_controller");
    public static final BlockEntityType<LaunchControllerBlockEntity> LAUNCH_CONTROLLER =
        new BlockEntityType<>(LaunchControllerBlockEntity::new, Set.of(ModBlocks.LAUNCH_CONTROLLER));
    public static final ResourceKey<BlockEntityType<?>> RADAR_STATION_KEY = key("radar_station");
    public static final BlockEntityType<RadarStationBlockEntity> RADAR_STATION =
        new BlockEntityType<>(RadarStationBlockEntity::new, Set.of(ModBlocks.RADAR_STATION));

    public static final ResourceKey<BlockEntityType<?>> MISSILE_SILO_KEY = key("missile_silo");
    public static final BlockEntityType<MissileSiloBlockEntity> MISSILE_SILO =
        new BlockEntityType<>(MissileSiloBlockEntity::new, Set.of(ModBlocks.MISSILE_SILO));

    public static final ResourceKey<BlockEntityType<?>> PHALANX_TURRET_KEY = key("phalanx_turret");
    public static final BlockEntityType<PhalanxBlockEntity> PHALANX_TURRET =
        new BlockEntityType<>(PhalanxBlockEntity::new, Set.of(ModBlocks.PHALANX_TURRET));

    public static final ResourceKey<BlockEntityType<?>> RADAR_DISPLAY_PANEL_KEY = key("radar_display_panel");
    public static final BlockEntityType<RadarDisplayPanelBlockEntity> RADAR_DISPLAY_PANEL =
        new BlockEntityType<>(RadarDisplayPanelBlockEntity::new, Set.of(ModBlocks.RADAR_DISPLAY_PANEL));

    public static final ResourceKey<BlockEntityType<?>> ITEM_PIPE_KEY = key("item_pipe");
    public static final BlockEntityType<ItemPipeBlockEntity> ITEM_PIPE =
        new BlockEntityType<>(ItemPipeBlockEntity::new, Set.of(ModBlocks.ITEM_PIPE));

    public static final ResourceKey<BlockEntityType<?>> MISSILE_WORKBENCH_KEY = key("missile_workbench");
    public static final BlockEntityType<MissileWorkbenchBlockEntity> MISSILE_WORKBENCH = new BlockEntityType<>(MissileWorkbenchBlockEntity::new, Set.of(ModBlocks.MISSILE_WORKBENCH));
    private static boolean registered;

    private ModBlockEntities() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MISSILE_SILO_KEY, MISSILE_SILO);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ARTILLERY_CANNON_KEY, ARTILLERY_CANNON);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, LAUNCH_CONTROLLER_KEY, LAUNCH_CONTROLLER);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, RADAR_STATION_KEY, RADAR_STATION);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PHALANX_TURRET_KEY, PHALANX_TURRET);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, RADAR_DISPLAY_PANEL_KEY, RADAR_DISPLAY_PANEL);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ITEM_PIPE_KEY, ITEM_PIPE);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MISSILE_WORKBENCH_KEY, MISSILE_WORKBENCH);
        registered = true;
    }

    private static ResourceKey<BlockEntityType<?>> key(final String path) {
        return ResourceKey.create(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WarMod.MOD_ID, path)
        );
    }
}
