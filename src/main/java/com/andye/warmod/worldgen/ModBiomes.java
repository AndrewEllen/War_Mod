package com.andye.warmod.worldgen;

import com.andye.warmod.WarMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** Resource keys for War Mod's data-driven biomes. */
public final class ModBiomes {
    public static final ResourceKey<Biome> NUCLEAR_WASTELAND = ResourceKey.create(
        Registries.BIOME,
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "nuclear_wasteland"));

    private ModBiomes() { }
}
