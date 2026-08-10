package com.andye.warmod.radar.display.client;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.client.render.IrisShaderState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class RadarDisplayRenderPipelines {
    private static final Identifier PIXEL_TEXTURE =
        Identifier.fromNamespaceAndPath(
            WarMod.MOD_ID,
            "textures/effect/radar_pixel.png"
        );

    private static final RenderPipeline SCREEN_PIPELINE =
        RenderPipelines.register(
            RenderPipeline
                .builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                .withLocation("pipeline/war_mod_radar_display")
                .withShaderDefine("ALPHA_CUTOUT", 0.001F)
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withColorTargetState(
                    new ColorTargetState(BlendFunction.TRANSLUCENT)
                )
                .withDepthStencilState(
                    new DepthStencilState(
                        CompareOp.GREATER_THAN_OR_EQUAL,
                        true
                    )
                )
                .withCull(false)
                .build()
        );

    private static final RenderType NORMAL_SCREEN =
        RenderType.create(
            "war_mod_radar_display",
            RenderSetup
                .builder(SCREEN_PIPELINE)
                .withTexture("Sampler0", PIXEL_TEXTURE)
                /*
                 * Preserve the renderer's explicit depth layering. Depth writes keep a
                 * neighbouring panel background from covering a ring or sweep
                 * segment that crosses the panel seam; upload sorting previously
                 * reordered those translucent quads at different view angles.
                 */
                .createRenderSetup()
        );

    /* Iris shader packs reliably accept the stock entity-translucent contract
       for block-entity custom geometry, while the custom emissive pipeline is
       retained for Sodium and vanilla. */
    private static final RenderType IRIS_SCREEN = RenderType.create(
        "war_mod_radar_display_iris",
        RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
            .withTexture("Sampler0", PIXEL_TEXTURE)
            .useLightmap()
            .useOverlay()
            .sortOnUpload()
            .createRenderSetup()
    );

    private RadarDisplayRenderPipelines() {
    }

    public static RenderType screen() {
        return IrisShaderState.active() ? IRIS_SCREEN : NORMAL_SCREEN;
    }
}
