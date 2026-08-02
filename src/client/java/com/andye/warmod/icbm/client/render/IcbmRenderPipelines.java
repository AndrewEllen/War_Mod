package com.andye.warmod.icbm.client.render;
import com.andye.warmod.WarMod;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
public final class IcbmRenderPipelines {public static final RenderType MISSILE=RenderType.create("war_mod_icbm",RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT).withTexture("Sampler0",Identifier.fromNamespaceAndPath(WarMod.MOD_ID,"textures/effect/icbm_albedo.png")).useLightmap().useOverlay().createRenderSetup());public static final RenderType EXHAUST=WarheadRenderPipelines.FIREBALL_HOT;public static final RenderType SMOKE=WarheadRenderPipelines.HEAVY_SMOKE;private IcbmRenderPipelines(){}}
