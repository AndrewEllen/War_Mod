package com.andye.warmod.block;

import com.andye.warmod.WarMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final ResourceKey<Block> MISSILE_SILO_KEY = ResourceKey.create(Registries.BLOCK,
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "missile_silo"));
    public static final ResourceKey<Block> MISSILE_SILO_GUIDANCE_FRAME_KEY = ResourceKey.create(Registries.BLOCK,
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "missile_silo_guidance_frame"));
    public static final MissileSiloGuidanceFrameBlock MISSILE_SILO_GUIDANCE_FRAME = new MissileSiloGuidanceFrameBlock(BlockBehaviour.Properties.of()
        .setId(MISSILE_SILO_GUIDANCE_FRAME_KEY).strength(6.0F, 18.0F).requiresCorrectToolForDrops()
        .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final ResourceKey<Block> RADAR_STATION_KEY = ResourceKey.create(Registries.BLOCK,
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "radar_station"));
    public static final RadarStationBlock RADAR_STATION = new RadarStationBlock(BlockBehaviour.Properties.of()
        .setId(RADAR_STATION_KEY).strength(8.0F, 24.0F).requiresCorrectToolForDrops()
        .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final MissileSiloBlock MISSILE_SILO = new MissileSiloBlock(BlockBehaviour.Properties.of()
        .setId(MISSILE_SILO_KEY).strength(8.0F, 24.0F).requiresCorrectToolForDrops()
        .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));
    private static boolean registered;

    private ModBlocks() {
    }

    public static void register() {
        if (registered) return;
        Registry.register(BuiltInRegistries.BLOCK, MISSILE_SILO_KEY, MISSILE_SILO);
        Registry.register(BuiltInRegistries.BLOCK, MISSILE_SILO_GUIDANCE_FRAME_KEY, MISSILE_SILO_GUIDANCE_FRAME);
        Registry.register(BuiltInRegistries.BLOCK, RADAR_STATION_KEY, RADAR_STATION);
        registered = true;
    }
}
