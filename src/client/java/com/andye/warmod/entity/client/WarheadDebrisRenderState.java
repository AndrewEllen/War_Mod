package com.andye.warmod.entity.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

public final class WarheadDebrisRenderState extends EntityRenderState {
	public final MovingBlockRenderState movingBlock = new MovingBlockRenderState();
	public Vec3 angularVelocity = Vec3.ZERO;
	public Vec3 velocity = Vec3.ZERO;
	public float debrisAge;
	public float visualScale = 1.0F;
	public int trailColour = 0x8A8178;
	public int trailSeed;
	public boolean onGround;
}