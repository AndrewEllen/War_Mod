package com.andye.warmod.phalanx.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public final class PhalanxRenderState extends BlockEntityRenderState {
    public float yaw, pitch, barrelSpeed, barrelAngle, bloom;
    public int rounds;
    public boolean firing, enabled;
}