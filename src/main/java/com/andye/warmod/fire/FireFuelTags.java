package com.andye.warmod.fire;

import com.andye.warmod.WarMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class FireFuelTags {
    public static final TagKey<Block> HIGH = tag("fire_fuel_high");
    public static final TagKey<Block> MEDIUM = tag("fire_fuel_medium");
    public static final TagKey<Block> LOW = tag("fire_fuel_low");
    public static final TagKey<Block> IMMUNE = tag("fire_immune");

    private FireFuelTags() { }

    private static TagKey<Block> tag(final String path) {
        return TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WarMod.MOD_ID, path));
    }
}
