package com.andye.warmod.artillery.client.render;

import com.andye.warmod.warhead.client.render.WarheadMesh;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

/** Extracted once so the artillery shell uses the same mesh/effect path as terminal warheads. */
public final class ArtilleryWarheadRenderState extends EntityRenderState {
    public Vec3 velocity = Vec3.ZERO;
    public WarheadMesh.Lod lod = WarheadMesh.Lod.NEAR;
    public long visualSeed;
    public float elapsedTicks;
    public float remainingTicks;
    public float progress;
    public int flightTicks = 1;
}
