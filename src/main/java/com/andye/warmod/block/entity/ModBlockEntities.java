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
    public static final ResourceKey<BlockEntityType<?>> RADAR_STATION_KEY = ResourceKey.create(
        Registries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "radar_station"));
    public static final BlockEntityType<RadarStationBlockEntity> RADAR_STATION = new BlockEntityType<>(
        RadarStationBlockEntity::new, Set.of(ModBlocks.RADAR_STATION));
    public static final ResourceKey<BlockEntityType<?>> MISSILE_SILO_KEY = ResourceKey.create(
        Registries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "missile_silo"));
    public static final BlockEntityType<MissileSiloBlockEntity> MISSILE_SILO = new BlockEntityType<>(
        MissileSiloBlockEntity::new, Set.of(ModBlocks.MISSILE_SILO));
    public static final ResourceKey<BlockEntityType<?>> PHALANX_TURRET_KEY = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "phalanx_turret"));
    public static final BlockEntityType<PhalanxBlockEntity> PHALANX_TURRET = new BlockEntityType<>(PhalanxBlockEntity::new, Set.of(ModBlocks.PHALANX_TURRET));
    public static final ResourceKey<BlockEntityType<?>> RADAR_DISPLAY_PANEL_KEY = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "radar_display_panel"));
    public static final BlockEntityType<RadarDisplayPanelBlockEntity> RADAR_DISPLAY_PANEL = new BlockEntityType<>(RadarDisplayPanelBlockEntity::new, Set.of(ModBlocks.RADAR_DISPLAY_PANEL));
    private static boolean registered;

    private ModBlockEntities() {
    }

    public static void register() {
        if (registered) return;
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MISSILE_SILO_KEY, MISSILE_SILO);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, RADAR_STATION_KEY, RADAR_STATION);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PHALANX_TURRET_KEY, PHALANX_TURRET);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, RADAR_DISPLAY_PANEL_KEY, RADAR_DISPLAY_PANEL);
        registered = true;
    }
}

