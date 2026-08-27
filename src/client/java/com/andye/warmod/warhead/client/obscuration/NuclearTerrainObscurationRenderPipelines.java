package com.andye.warmod.warhead.client.obscuration;

import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Terrain-obscuration state using the compatible translucent dust material. */
final class NuclearTerrainObscurationRenderPipelines {
    static RenderType terrainDust() { return WarheadRenderPipelines.GROUND_DUST; }

    private NuclearTerrainObscurationRenderPipelines() { }
}
