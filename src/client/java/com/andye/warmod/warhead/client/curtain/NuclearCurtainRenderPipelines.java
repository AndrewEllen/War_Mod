package com.andye.warmod.warhead.client.curtain;

import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Dedicated translucent path, independent of every ordinary particle budget. */
final class NuclearCurtainRenderPipelines {
    static RenderType curtain() { return WarheadRenderPipelines.HEAVY_SMOKE; }

    private NuclearCurtainRenderPipelines() { }
}
