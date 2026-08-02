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
    private static boolean registered;

    private ModBlockEntities() {
    }

    public static void register() {
        if (registered) return;
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MISSILE_SILO_KEY, MISSILE_SILO);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, RADAR_STATION_KEY, RADAR_STATION);
        registered = true;
    }
}
