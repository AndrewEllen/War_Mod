package com.andye.warmod.fire;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public record FireFuelProfile(boolean flammable, boolean consumable,
    float ignitionThreshold, int burnTicks, float heatRelease,
    float smokeSoot, float emberSusceptibility) {
    public static final FireFuelProfile NONE = new FireFuelProfile(false, false,
        2.4F, 600, 0.35F, 0.12F, 0.05F);
    /** Leaves, plants and dry crown fuel: quick ignition, meaningful burn duration. */
    public static final FireFuelProfile HIGH = new FireFuelProfile(true, true,
        0.42F, 1_050, 0.86F, 0.30F, 1.00F);
    /** Structural wood and wool: slower ignition and a long fuel-driven burn. */
    public static final FireFuelProfile MEDIUM = new FireFuelProfile(true, true,
        0.72F, 3_200, 1.00F, 0.46F, 0.62F);
    /** Organic ground supports a spreading surface burn but must not become a terrain hole. */
    public static final FireFuelProfile LOW = new FireFuelProfile(true, false,
        0.62F, 780, 0.58F, 0.23F, 0.78F);

    public static FireFuelProfile of(final BlockState state) {
        if (state.isAir() || state.is(FireFuelTags.IMMUNE)) return NONE;
        if (state.is(FireFuelTags.HIGH) || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)) return HIGH;
        if (state.is(FireFuelTags.MEDIUM) || state.is(BlockTags.LOGS)
            || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL)
            || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
            || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_DOORS)
            || state.is(BlockTags.WOODEN_TRAPDOORS)) return MEDIUM;
        if (state.is(FireFuelTags.LOW)) return LOW;

        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (containsAny(path, "grass", "fern", "bush", "vine", "bamboo", "sapling",
            "azalea", "cactus", "sugar_cane", "hay_block", "moss")) return HIGH;
        if (containsAny(path, "wood", "log", "plank", "bookshelf", "fence", "door",
            "trapdoor", "wooden", "scaffolding")) return MEDIUM;
        if (containsAny(path, "podzol", "mycelium")) return LOW;
        return NONE;
    }

    private static boolean containsAny(final String value, final String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
