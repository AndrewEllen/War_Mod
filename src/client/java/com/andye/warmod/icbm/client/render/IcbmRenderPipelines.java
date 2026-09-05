package com.andye.warmod.icbm.client.render;

import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.client.render.IrisShaderState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** ICBM render types with stable smoke and a shader-aware emissive exhaust. */
public final class IcbmRenderPipelines {
	private static final Identifier ALBEDO = texture("icbm_albedo.png");
	private static final Identifier EXHAUST_CORE_TEXTURE = texture("icbm_exhaust_core.png");
	private static final Identifier EXHAUST_FRINGE_TEXTURE = texture("icbm_exhaust_fringe.png");
	private static final Identifier SMOKE_TEXTURE = texture("icbm_smoke.png");
	public static final RenderPipeline LAUNCH_SMOKE_PARTICLE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
			.withLocation("pipeline/war_mod_launch_smoke_particle")
			.withFragmentShader(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "core/launch_smoke_particle"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false).build());

	/* Exact PR20 smoke contract retained whenever an Iris shader pack is not active. */
	private static final RenderPipeline NORMAL_SMOKE_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation("pipeline/war_mod_icbm_smoke")
			.withShaderDefine("ALPHA_CUTOUT", 0.02F)
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false).build());

	private static final RenderType NORMAL_SMOKE = RenderType.create("war_mod_icbm_smoke",
		RenderSetup.builder(NORMAL_SMOKE_PIPELINE)
			.withTexture("Sampler0", SMOKE_TEXTURE).useLightmap().sortOnUpload().createRenderSetup());
	private static final RenderType IRIS_SMOKE = RenderType.create("war_mod_icbm_smoke_iris",
		RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
			.withTexture("Sampler0", SMOKE_TEXTURE).useLightmap().useOverlay().sortOnUpload().createRenderSetup());

	public static final RenderType MISSILE = RenderType.create("war_mod_icbm",
		RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT)
			.withTexture("Sampler0", ALBEDO).useLightmap().useOverlay().createRenderSetup());
	public static final RenderType SPENT_STAGE = RenderType.create("war_mod_icbm_spent",
		RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
			.withTexture("Sampler0", ALBEDO).useLightmap().useOverlay().sortOnUpload().createRenderSetup());

	/* Stock EYES is full-bright but still mixes its RGB into FogColor. The hot
	 * core emits through that haze; it continues to depth-test against terrain. */
	private static final RenderPipeline EXHAUST_GLOW_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
			.withLocation("pipeline/war_mod_icbm_exhaust_glow")
			.withFragmentShader(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "core/icbm_exhaust_glow"))
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false).build());
	public static final RenderType EXHAUST_CORE = RenderType.create("war_mod_icbm_exhaust_core",
		RenderSetup.builder(EXHAUST_GLOW_PIPELINE)
			.withTexture("Sampler0", EXHAUST_CORE_TEXTURE).createRenderSetup());
	public static final RenderType EXHAUST_FRINGE = RenderType.create("war_mod_icbm_exhaust_fringe",
		RenderSetup.builder(RenderPipelines.EYES)
			.withTexture("Sampler0", EXHAUST_FRINGE_TEXTURE).createRenderSetup());
	public static RenderType SMOKE = NORMAL_SMOKE;

	/** Smaller interceptor engines share the fog-visible emission with their own mask. */
	public static final RenderType EXHAUST = RenderType.create("war_mod_interceptor_exhaust_glow",
		RenderSetup.builder(EXHAUST_GLOW_PIPELINE)
			.withTexture("Sampler0", EXHAUST_FRINGE_TEXTURE).createRenderSetup());

	private static boolean irisActive;

	static {
		refreshIrisState();
		ClientTickEvents.END_CLIENT_TICK.register(client -> refreshIrisState());
	}

	private IcbmRenderPipelines() { }

	private static void refreshIrisState() {
		boolean active = IrisShaderState.active();
		if (active == irisActive) return;
		irisActive = active;
		SMOKE = active ? IRIS_SMOKE : NORMAL_SMOKE;
	}

	private static Identifier texture(final String name) {
		return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/effect/" + name);
	}
}
