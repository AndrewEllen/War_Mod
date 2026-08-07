package com.andye.warmod.icbm.client.render;

import com.andye.warmod.WarMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** ICBM render types with dedicated neutral alpha masks. */
public final class IcbmRenderPipelines {
	private static final Identifier ALBEDO = texture("icbm_albedo.png");
	private static final Identifier EXHAUST_CORE_TEXTURE = texture("icbm_exhaust_core.png");
	private static final Identifier EXHAUST_FRINGE_TEXTURE = texture("icbm_exhaust_fringe.png");
	private static final Identifier SMOKE_TEXTURE = texture("icbm_smoke.png");
	private static final Identifier EXHAUST_SHADER = Identifier.fromNamespaceAndPath(
		WarMod.MOD_ID, "core/icbm_emissive_nofog");

	/*
	 * The stock entity shader always applies world fog, including to emissive
	 * fragments. Rocket fire should remain a luminous source at long range, so
	 * the exhaust uses a tiny dedicated shader that keeps depth testing but skips
	 * fog entirely. This mirrors the additive hot-fire treatment used by the
	 * warhead renderer without inheriting its texture.
	 */
	private static final RenderPipeline EXHAUST_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
			.withLocation("pipeline/war_mod_icbm_exhaust_nofog")
			.withVertexShader(EXHAUST_SHADER)
			.withFragmentShader(EXHAUST_SHADER)
			.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build());

	/*
	 * Custom particle vertices do not carry a meaningful entity overlay. The
	 * vanilla entity translucent shader can therefore sample the red hurt
	 * overlay from zero overlay coordinates. Explicitly disabling overlay keeps
	 * the smoke neutral grey while retaining normal world fog and depth testing.
	 */
	private static final RenderPipeline SMOKE_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation("pipeline/war_mod_icbm_smoke")
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build());

	public static final RenderType MISSILE = RenderType.create("war_mod_icbm",
		RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT)
			.withTexture("Sampler0", ALBEDO).useLightmap().useOverlay().createRenderSetup());
	public static final RenderType SPENT_STAGE = RenderType.create("war_mod_icbm_spent",
		RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
			.withTexture("Sampler0", ALBEDO).useLightmap().useOverlay().sortOnUpload().createRenderSetup());
	public static final RenderType EXHAUST_CORE = RenderType.create("war_mod_icbm_exhaust_core",
		RenderSetup.builder(EXHAUST_PIPELINE)
			.withTexture("Sampler0", EXHAUST_CORE_TEXTURE).createRenderSetup());
	public static final RenderType EXHAUST_FRINGE = RenderType.create("war_mod_icbm_exhaust_fringe",
		RenderSetup.builder(EXHAUST_PIPELINE)
			.withTexture("Sampler0", EXHAUST_FRINGE_TEXTURE).createRenderSetup());
	public static final RenderType SMOKE = RenderType.create("war_mod_icbm_smoke",
		RenderSetup.builder(SMOKE_PIPELINE)
			.withTexture("Sampler0", SMOKE_TEXTURE).sortOnUpload().createRenderSetup());

	/** Compatibility alias retained for existing call sites until both exhaust passes are submitted. */
	public static final RenderType EXHAUST = EXHAUST_FRINGE;

	private IcbmRenderPipelines() { }

	private static Identifier texture(final String name) {
		return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/effect/" + name);
	}
}
