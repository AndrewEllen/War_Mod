package com.andye.warmod.entity.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class TimedTntRenderState extends EntityRenderState {
    public final MovingBlockRenderState movingBlock = new MovingBlockRenderState();
}