package com.andye.warmod.icbm.client.render;

import com.andye.warmod.WarMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** ICBM render types with neutral smoke and a full-bright additive exhaust. */
public final class IcbmRenderPipelines {
    private static final Identifier ALBEDO = texture("icbm_albedo.png");
    private static final Identifier EXHAUST_CORE_TEXTURE = texture("icbm_exhaust_core.png");
    private static final Identifier EXHAUST_FRINGE_TEXTURE = texture("icbm_exhaust_fringe.png");
    private static final Identifier SMOKE_TEXTURE = texture("icbm_smoke.png");
    private static final boolean IRIS_PRESENT = FabricLoader.getInstance().isModLoaded("iris");

    private static final RenderPipeline CUSTOM_SMOKE_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation("pipeline/war_mod_icbm_smoke")
            .withShaderDefine("ALPHA_CUTOUT", 0.020F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(
                CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build());

    private static final RenderPipeline CUSTOM_EXHAUST_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation("pipeline/war_mod_icbm_exhaust")
            .withShaderDefine("ALPHA_CUTOUT", 0.010F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(
                CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build());

    /*
     * Sodium alone follows exactly the same custom pipelines as the normal
     * renderer. Iris is the only special case because shader packs replace the
     * world-space program contract; it receives entity-compatible geometry.
     */
    private static final RenderPipeline SMOKE_PIPELINE = IRIS_PRESENT
        ? RenderPipelines.ENTITY_TRANSLUCENT : CUSTOM_SMOKE_PIPELINE;
    private static final RenderPipeline EXHAUST_PIPELINE = IRIS_PRESENT
        ? RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE : CUSTOM_EXHAUST_PIPELINE;

    public static final RenderType MISSILE = RenderType.create("war_mod_icbm",
        RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT)
            .withTexture("Sampler0", ALBEDO).useLightmap().useOverlay().createRenderSetup());
    public static final RenderType SPENT_STAGE = RenderType.create("war_mod_icbm_spent",
        RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
            .withTexture("Sampler0", ALBEDO).useLightmap().useOverlay().sortOnUpload().createRenderSetup());
    public static final RenderType EXHAUST_CORE = exhaustType(
        "war_mod_icbm_exhaust_core", EXHAUST_CORE_TEXTURE);
    public static final RenderType EXHAUST_FRINGE = exhaustType(
        "war_mod_icbm_exhaust_fringe", EXHAUST_FRINGE_TEXTURE);
    public static final RenderType SMOKE = smokeType();

    /** Compatibility alias retained for existing call sites. */
    public static final RenderType EXHAUST = EXHAUST_FRINGE;

    private IcbmRenderPipelines() { }

    private static RenderType exhaustType(final String name, final Identifier texture) {
        RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(EXHAUST_PIPELINE)
            .withTexture("Sampler0", texture).sortOnUpload();
        if (IRIS_PRESENT) builder.useLightmap().useOverlay();
        return RenderType.create(name, builder.createRenderSetup());
    }

    private static RenderType smokeType() {
        RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(SMOKE_PIPELINE)
            .withTexture("Sampler0", SMOKE_TEXTURE).useLightmap().sortOnUpload();
        if (IRIS_PRESENT) builder.useOverlay();
        return RenderType.create("war_mod_icbm_smoke", builder.createRenderSetup());
    }

    private static Identifier texture(final String name) {
        return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/effect/" + name);
    }
}
