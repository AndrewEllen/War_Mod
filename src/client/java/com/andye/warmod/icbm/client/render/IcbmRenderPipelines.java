package com.andye.warmod.icbm.client.render;
import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
public final class IcbmRenderPipelines {
	private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath(WarMod.MOD_ID,"textures/effect/icbm_albedo.png");
	public static final RenderType MISSILE=RenderType.create("war_mod_icbm",RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT).withTexture("Sampler0",TEXTURE).useLightmap().useOverlay().createRenderSetup());
	public static final RenderType SPENT_STAGE=RenderType.create("war_mod_icbm_spent",RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT).withTexture("Sampler0",TEXTURE).useLightmap().useOverlay().sortOnUpload().createRenderSetup());
	public static final RenderType EXHAUST=WarheadRenderPipelines.NUCLEAR_FLASH;
	public static final RenderType SMOKE=WarheadRenderPipelines.HEAVY_SMOKE;
	private IcbmRenderPipelines(){}
}