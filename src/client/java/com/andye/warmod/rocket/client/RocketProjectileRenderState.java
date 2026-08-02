package com.andye.warmod.rocket.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

public final class RocketProjectileRenderState extends EntityRenderState {
    public Vec3 velocity = Vec3.ZERO;
}
