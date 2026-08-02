package com.andye.warmod.warhead.client.render;

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

/** Explicit 26.2 pipelines for the custom geometry vertex stream. */
public final class WarheadRenderPipelines {
	private static final RenderPipeline EFFECT_TRANSLUCENT_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation("pipeline/war_mod_effect_translucent")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build()
	);
	private static final RenderPipeline FIREBALL_HOT_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
			.withLocation("pipeline/war_mod_fireball_hot")
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build()
	);

	public static final RenderType PROJECTILE = create(
		"war_mod_projectile",
		RenderPipelines.ENTITY_CUTOUT,
		texture("warhead_albedo.png"),
		true,
		true,
		false
	);
	public static final RenderType CONE = effect("war_mod_cone", "vapor_noise.png");
	public static final RenderType VAPOR_BAND = effect("war_mod_vapor_band", "vapor_band.png");
	public static final RenderType PRESSURE_SHELL = effect("war_mod_pressure_shell", "pressure_shell.png");
	public static final RenderType SHOCKWAVE = effect("war_mod_shockwave", "shockwave_strip.png");
	public static final RenderType FIREBALL_HOT = create("war_mod_fireball_hot", FIREBALL_HOT_PIPELINE, texture("fireball_sheet.png"), false, false, true);
	public static final RenderType FIREBALL_COOL = effect("war_mod_fireball_cool", "fireball_sheet.png");
	public static final RenderType SMOKE_LOBE = effect("war_mod_smoke_lobe", "smoke_lobe.png");
	public static final RenderType FIREBALL = FIREBALL_COOL;

	private WarheadRenderPipelines() {
	}

	private static RenderType effect(final String name, final String textureName) {
		return create(name, EFFECT_TRANSLUCENT_PIPELINE, texture(textureName), true, true, true);
	}

	private static RenderType create(
		final String name,
		final RenderPipeline pipeline,
		final Identifier texture,
		final boolean useLightmap,
		final boolean useOverlay,
		final boolean sortOnUpload
	) {
		RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(pipeline)
			.withTexture("Sampler0", texture)
			.setOutline(RenderSetup.OutlineProperty.NONE);
		if (useLightmap) {
			builder.useLightmap();
		}
		if (useOverlay) {
			builder.useOverlay();
		}
		if (sortOnUpload) {
			builder.sortOnUpload();
		}
		return RenderType.create(name, builder.createRenderSetup());
	}

	private static Identifier texture(final String name) {
		return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/effect/" + name);
	}
}