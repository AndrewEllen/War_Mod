package com.andye.warmod.warhead.client.curtain;

import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Isolated curtain state using the already-compatible translucent dust material. */
final class NuclearCurtainRenderPipelines {
    static RenderType curtain() { return WarheadRenderPipelines.GROUND_DUST; }

    private NuclearCurtainRenderPipelines() { }
}
