package com.andye.warmod.warhead.client.render;

import com.andye.warmod.WarMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Minecraft 26.2 render pipelines for each visual layer. */
public final class WarheadRenderPipelines {
	private static final Identifier SMOKE_LOBE_TEXTURE = Identifier.fromNamespaceAndPath(
		WarMod.MOD_ID,
		"textures/effect/smoke_lobe.png"
	);
	private static final RenderPipeline CONDENSATION_PIPELINE = condensation();
	private static final RenderPipeline PRESSURE_PIPELINE = translucent("pipeline/war_mod_pressure_shell");
	private static final RenderPipeline SHOCKWAVE_PIPELINE = translucent("pipeline/war_mod_shockwave_ribbons");
	private static final RenderPipeline GROUND_DUST_PIPELINE = translucent("pipeline/war_mod_ground_dust");
	private static final RenderPipeline HEAVY_SMOKE_PIPELINE = translucent("pipeline/war_mod_heavy_smoke");
	private static final RenderPipeline COOL_FIRE_PIPELINE = translucent("pipeline/war_mod_cooling_fire");
	private static final RenderPipeline GROUND_RIPPLE_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation("pipeline/war_mod_ground_ripple")
			.withVertexShader(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "core/ground_ripple"))
			.withFragmentShader(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "core/ground_ripple"))
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build()
	);
	private static final RenderPipeline TERRAIN_DEFORMATION_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation("pipeline/war_mod_terrain_deformation")
			.withShaderDefine("NO_OVERLAY")
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
			.withCull(false)
			.build()
	);
	private static final RenderPipeline HOT_FIRE_PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
			.withLocation("pipeline/war_mod_hot_fire")
			.withShaderDefine("ALPHA_CUTOUT", 0.05F)
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build()
	);

	public static final RenderType PROJECTILE = create("war_mod_projectile", RenderPipelines.ENTITY_CUTOUT, texture("warhead_albedo.png"), true, true, false);
	public static final RenderType CONE = create("war_mod_cone", CONDENSATION_PIPELINE, texture("vapor_noise.png"), false, false, true);
	public static final RenderType VAPOR_BAND = create("war_mod_vapor_band", CONDENSATION_PIPELINE, texture("vapor_band.png"), false, false, true);
	public static final RenderType PRESSURE_SHELL = create("war_mod_pressure_shell", PRESSURE_PIPELINE, texture("pressure_shell.png"), true, false, true);
	public static final RenderType TERRAIN_DEFORMATION = create("war_mod_terrain_deformation", TERRAIN_DEFORMATION_PIPELINE, TextureAtlas.LOCATION_BLOCKS, true, false, false);
	public static final RenderType GROUND_RIPPLE = create("war_mod_ground_ripple", GROUND_RIPPLE_PIPELINE, texture("ground_ripple_noise.png"), true, false, true);
	public static final RenderType SHOCKWAVE = create("war_mod_shockwave", SHOCKWAVE_PIPELINE, texture("shockwave_strip.png"), true, false, true);
	public static final RenderType GROUND_DUST = create("war_mod_ground_dust", GROUND_DUST_PIPELINE, SMOKE_LOBE_TEXTURE, true, false, true);
	public static final RenderType HEAVY_SMOKE = create("war_mod_heavy_smoke", HEAVY_SMOKE_PIPELINE, SMOKE_LOBE_TEXTURE, true, false, true);
	public static final RenderType FIREBALL_COOL = create("war_mod_fireball_cool", COOL_FIRE_PIPELINE, texture("fireball_sheet.png"), true, false, true);
	public static final RenderType FIREBALL_HOT = create("war_mod_fireball_hot", HOT_FIRE_PIPELINE, texture("fireball_sheet.png"), false, false, true);
	public static final RenderType SMOKE_LOBE = HEAVY_SMOKE;
	public static final RenderType FIREBALL = FIREBALL_COOL;

	private WarheadRenderPipelines() { }

	private static RenderPipeline condensation() {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
			.withLocation("pipeline/war_mod_condensation")
			.withShaderDefine("ALPHA_CUTOUT", 0.02F)
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build());
	}

	private static RenderPipeline translucent(final String location) {
		return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation(location).withShaderDefine("ALPHA_CUTOUT", 0.02F).withShaderDefine("NO_OVERLAY")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false).build());
	}

	private static RenderType create(final String name, final RenderPipeline pipeline, final Identifier texture,
		final boolean useLightmap, final boolean useOverlay, final boolean sortOnUpload) {
		RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(pipeline).withTexture("Sampler0", texture).setOutline(RenderSetup.OutlineProperty.NONE);
		if (useLightmap) builder.useLightmap();
		if (useOverlay) builder.useOverlay();
		if (sortOnUpload) builder.sortOnUpload();
		return RenderType.create(name, builder.createRenderSetup());
	}

	private static Identifier texture(final String name) {
		return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/effect/" + name);
	}
}