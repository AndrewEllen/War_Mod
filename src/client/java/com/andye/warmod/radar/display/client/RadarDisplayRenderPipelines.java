package com.andye.warmod.radar.display.client;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
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

    private static final boolean EXTERNAL_RENDERER =
        WarheadRenderPipelines.compatibilityRendererActive();

    private static final RenderPipeline CUSTOM_SCREEN_PIPELINE =
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

    private static final RenderPipeline SCREEN_PIPELINE = EXTERNAL_RENDERER
        ? RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE
        : CUSTOM_SCREEN_PIPELINE;

    public static final RenderType SCREEN = createScreen();

    private RadarDisplayRenderPipelines() {
    }

    private static RenderType createScreen() {
        RenderSetup.RenderSetupBuilder builder = RenderSetup
            .builder(SCREEN_PIPELINE)
            .withTexture("Sampler0", PIXEL_TEXTURE);
        if (EXTERNAL_RENDERER) {
            builder.useOverlay();
        }
        return RenderType.create(
            "war_mod_radar_display",
            builder.createRenderSetup()
        );
    }
}
