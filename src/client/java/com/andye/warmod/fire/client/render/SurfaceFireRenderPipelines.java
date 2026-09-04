package com.andye.warmod.fire.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Dense surface flames use ordinary opacity, rather than explosion light addition. */
final class SurfaceFireRenderPipelines {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation("pipeline/war_mod_surface_flame")
            .withFragmentShader(Identifier.fromNamespaceAndPath("war_mod", "core/surface_flame"))
            .withShaderDefine("ALPHA_CUTOUT", 0.035F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            // Never write flame masks into the world's depth buffer: doing so
            // punches holes in independently rendered smoke and other effects.
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false).build());
    static final RenderType FLAMES = RenderType.create("war_mod_surface_flames",
        RenderSetup.builder(PIPELINE)
            .withTexture("Sampler0", Identifier.fromNamespaceAndPath(
                "minecraft", "textures/particle/big_smoke_2.png"))
            .sortOnUpload().setOutline(RenderSetup.OutlineProperty.NONE).createRenderSetup());

    private SurfaceFireRenderPipelines() { }
}
