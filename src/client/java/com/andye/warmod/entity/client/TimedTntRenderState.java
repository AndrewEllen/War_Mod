package com.andye.warmod.entity.client;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class TimedTntRenderState extends EntityRenderState {
    public final BlockModelRenderState blockState = new BlockModelRenderState();
    public float fuseRemainingInTicks;
}
