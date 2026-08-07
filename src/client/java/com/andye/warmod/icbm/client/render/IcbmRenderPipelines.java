package com.andye.warmod.icbm.client.render;

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

/** ICBM render types with neutral smoke and a full-bright long-range exhaust. */
public final class IcbmRenderPipelines {
	private static final Identifier ALBEDO = texture("icbm_albedo.png");
	private static final Identifier EXHAUST_CORE_TEXTURE = texture("icbm_exhaust_core.png");
	private static final Identifier EXHAUST_FRINGE_TEXTURE = texture("icbm_exhaust_fringe.png");
	private static final Identifier SMOKE_TEXTURE = texture("icbm_smoke.png");
	private static final Identifier EXHAUST_SHADER = Identifier.fromNamespaceAndPath(
		WarMod.MOD_ID, "core/icbm_emissive_nofog");
	private static final boolean EXTERNAL_RENDERER =
		WarheadRenderPipelines.compatibilityRendererActive();

	private static final RenderPipeline CUSTOM_SMOKE_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation("pipeline/war_mod_icbm_smoke")
			.withShaderDefine("ALPHA_CUTOUT", 0.025F)
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(
				CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build());

	/*
	 * The exhaust is explicitly fog-independent: emissive rocket fire should
	 * remain a bright orange source at long range rather than being multiplied
	 * down to the environmental fog colour. Depth testing remains enabled so it
	 * is still occluded correctly by nearer geometry.
	 */
	private static final RenderPipeline EXHAUST_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
			.withLocation("pipeline/war_mod_icbm_exhaust_nofog")
			.withVertexShader(EXHAUST_SHADER)
			.withFragmentShader(EXHAUST_SHADER)
			.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
			.withDepthStencilState(new DepthStencilState(
				CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build());

	/*
	 * Sodium/Iris receive Minecraft's particle pipeline for smoke. The previous
	 * entity fallback consumed hurt-overlay state and could tint neutral smoke
	 * red. The native path uses a dedicated NO_OVERLAY pipeline for the same
	 * reason.
	 */
	private static final RenderPipeline SMOKE_PIPELINE = EXTERNAL_RENDERER
		? RenderPipelines.TRANSLUCENT_PARTICLE : CUSTOM_SMOKE_PIPELINE;

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
		return RenderType.create(name, RenderSetup.builder(EXHAUST_PIPELINE)
			.withTexture("Sampler0", texture)
			.createRenderSetup());
	}

	private static RenderType smokeType() {
		RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(SMOKE_PIPELINE)
			.withTexture("Sampler0", SMOKE_TEXTURE).useLightmap().sortOnUpload();
		return RenderType.create("war_mod_icbm_smoke", builder.createRenderSetup());
	}

	private static Identifier texture(final String name) {
		return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/effect/" + name);
	}
}
