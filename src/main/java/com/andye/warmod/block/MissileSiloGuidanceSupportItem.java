package com.andye.warmod.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public final class MissileSiloGuidanceSupportItem extends Item {
    private final int tier;

    public MissileSiloGuidanceSupportItem(final Properties properties, final int tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.FAIL;
        BlockState clicked = level.getBlockState(context.getClickedPos());
        MissileSiloGuidanceFrameStructure.SupportTarget target =
            MissileSiloGuidanceFrameStructure.resolveTarget(level, context.getClickedPos(), clicked,
                context.getClickLocation());
        if (target == null) return InteractionResult.PASS;
        if (!MissileSiloGuidanceFrameStructure.installOrUpgrade(level, target.centre(), target.facing(),
            target.side(), tier, context.getPlayer())) return InteractionResult.FAIL;
        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.SUCCESS_SERVER;
    }
}