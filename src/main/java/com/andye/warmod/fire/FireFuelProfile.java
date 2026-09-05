package com.andye.warmod.fire;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public record FireFuelProfile(boolean flammable, boolean consumable,
    float ignitionThreshold, int burnTicks, float heatRelease,
    float smokeSoot, float emberSusceptibility) {
    public static final FireFuelProfile NONE = new FireFuelProfile(false, false,
        2.4F, 600, 0.35F, 0.12F, 0.05F);
    /** Leaves, plants and fibrous surface fuel: quick ignition, meaningful burn duration. */
    public static final FireFuelProfile HIGH = new FireFuelProfile(true, true,
        0.16F, 320, 0.96F, 0.48F, 1.00F);
    /** Structural wood: slower ignition and a long fuel-driven burn. */
    public static final FireFuelProfile MEDIUM = new FireFuelProfile(true, true,
        0.34F, 1_250, 1.00F, 0.58F, 0.82F);
    /** Organic ground supports a spreading surface burn but must not become a terrain hole. */
    public static final FireFuelProfile LOW = new FireFuelProfile(true, false,
        0.40F, 600, 0.68F, 0.34F, 0.86F);

    public static FireFuelProfile of(final BlockState state) {
        if (state.isAir()) return NONE;
        // Burned ground is still a solid terrain block, but its surface fuel is spent.
        if (state.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT)
            || state.is(net.minecraft.world.level.block.Blocks.DIRT)
            || state.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)) return NONE;
        if (state.is(FireFuelTags.IMMUNE)) return NONE;
        if (state.is(FireFuelTags.HIGH) || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)
            || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) return HIGH;
        if (state.is(FireFuelTags.MEDIUM) || state.is(BlockTags.LOGS)
            || state.is(BlockTags.PLANKS)
            || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
            || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_DOORS)
            || state.is(BlockTags.WOODEN_TRAPDOORS)) return MEDIUM;
        if (state.is(FireFuelTags.LOW) || state.is(BlockTags.DIRT)) return LOW;

        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return fallbackForPath(path);
    }

    static FireFuelProfile fallbackForPath(final String path) {
        if (path.equals("coarse_dirt") || path.equals("dirt")
            || path.equals("dirt_path") || path.startsWith("charred_")) return NONE;
        /* Keep non-consumable ground fuels ahead of the broad plant-name
           fallback. This also preserves the intended classification when a
           development data pack has not bound the fire-fuel tags yet. */
        if (containsAny(path, "grass_block", "moss_block", "podzol", "mycelium", "dirt"))
            return LOW;
        if (containsAny(path, "grass", "fern", "bush", "vine", "bamboo", "sapling",
            "azalea", "cactus", "sugar_cane", "hay_block", "moss", "carpet")) return HIGH;
        if (containsAny(path, "wood", "log", "plank", "bookshelf", "fence", "door",
            "trapdoor", "wooden", "scaffolding")) return MEDIUM;
        return NONE;
    }

    private static boolean containsAny(final String value, final String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
