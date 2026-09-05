package com.andye.warmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Inert aftermath vegetation; keeps dry-grass support rules without bonemeal regrowth. */
public final class CharredDryGrassBlock extends DryVegetationBlock {
    public static final MapCodec<CharredDryGrassBlock> CODEC = simpleCodec(CharredDryGrassBlock::new);

    public CharredDryGrassBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override
    public MapCodec<CharredDryGrassBlock> codec() { return CODEC; }
}
