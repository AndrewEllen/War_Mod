package com.andye.warmod.warhead.client.render;

import com.andye.warmod.WarMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/** Minecraft 26.2 render pipelines for each visual layer. */
public final class WarheadRenderPipelines {
    private static final Identifier PARTICLE_MASK = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/particle/big_smoke_2.png");
    private static final Identifier PLASMA_MASK = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/particle/big_smoke_4.png");
    private static final Identifier EXPLOSION_MASK = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/particle/explosion_0.png");

    /* Stable/PR20 pipelines. These are intentionally unchanged for Sodium and vanilla. */
    private static final RenderPipeline CONDENSATION_PIPELINE = condensation();
    private static final RenderPipeline PRESSURE_PIPELINE =
        translucent("pipeline/war_mod_pressure_shell", false);
    private static final RenderPipeline SHOCKWAVE_PIPELINE =
        translucent("pipeline/war_mod_shockwave_ribbons", false);
    private static final RenderPipeline GROUND_DUST_PIPELINE =
        translucent("pipeline/war_mod_ground_dust", false);
    private static final RenderPipeline HEAVY_SMOKE_PIPELINE =
        smokeCutout("pipeline/war_mod_heavy_smoke", 0.055F);
    private static final RenderPipeline HEAVY_SMOKE_CORE_PIPELINE =
        smokeCutout("pipeline/war_mod_heavy_smoke_core", 0.085F);
    private static final RenderPipeline NUCLEAR_SMOKE_PIPELINE =
        smokeCutout("pipeline/war_mod_nuclear_smoke", 0.045F);
    private static final RenderPipeline COOL_FIRE_PIPELINE =
        emissiveTranslucent("pipeline/war_mod_cooling_fire", false, 0.025F);
    private static final RenderPipeline FIREBALL_CORE_PIPELINE =
        emissiveTranslucent("pipeline/war_mod_fireball_core", true, 0.065F);
    private static final RenderPipeline PLASMA_CORE_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation("pipeline/war_mod_plasma_core")
            .withShaderDefine("ALPHA_CUTOUT", 0.075F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false).build());
    private static final RenderPipeline GROUND_RIPPLE_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation("pipeline/war_mod_ground_ripple")
            .withVertexShader(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "core/ground_ripple"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "core/ground_ripple"))
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false).build());
    private static final RenderPipeline TERRAIN_DEFORMATION_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation("pipeline/war_mod_terrain_deformation")
            .withShaderDefine("NO_OVERLAY")
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false).build());
    private static final RenderPipeline HOT_FIRE_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation("pipeline/war_mod_hot_fire")
            .withShaderDefine("ALPHA_CUTOUT", 0.035F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false).build());

    private static final RenderType NORMAL_CONE = create("war_mod_cone", CONDENSATION_PIPELINE,
        texture("vapor_noise.png"), false, false, true);
    private static final RenderType NORMAL_VAPOR_BAND = create("war_mod_vapor_band", CONDENSATION_PIPELINE,
        texture("vapor_band.png"), false, false, true);
    private static final RenderType NORMAL_PRESSURE_SHELL = create("war_mod_pressure_shell", PRESSURE_PIPELINE,
        texture("pressure_shell.png"), true, false, true);
    private static final RenderType NORMAL_TERRAIN_DEFORMATION = create("war_mod_terrain_deformation",
        TERRAIN_DEFORMATION_PIPELINE, TextureAtlas.LOCATION_BLOCKS, true, false, false);
    private static final RenderType NORMAL_GROUND_RIPPLE = create("war_mod_ground_ripple",
        GROUND_RIPPLE_PIPELINE, texture("ground_ripple_noise.png"), true, false, true);
    private static final RenderType NORMAL_SHOCKWAVE = create("war_mod_shockwave", SHOCKWAVE_PIPELINE,
        texture("shockwave_strip.png"), true, false, true);
    private static final RenderType NORMAL_GROUND_DUST = create("war_mod_ground_dust", GROUND_DUST_PIPELINE,
        PARTICLE_MASK, true, false, true);
    private static final RenderType NORMAL_HEAVY_SMOKE = create("war_mod_heavy_smoke", HEAVY_SMOKE_PIPELINE,
        PARTICLE_MASK, true, false, false);
    private static final RenderType NORMAL_HEAVY_SMOKE_CORE = create("war_mod_heavy_smoke_core",
        HEAVY_SMOKE_CORE_PIPELINE, PARTICLE_MASK, true, false, false);
    private static final RenderType NORMAL_NUCLEAR_SMOKE = create("war_mod_nuclear_smoke",
        NUCLEAR_SMOKE_PIPELINE, PARTICLE_MASK, true, false, false);
    private static final RenderType NORMAL_FIREBALL_CORE = createClamped("war_mod_fireball_core",
        FIREBALL_CORE_PIPELINE, PARTICLE_MASK, false, false);
    private static final RenderType NORMAL_FIREBALL_COOL = createClamped("war_mod_fireball_cool",
        COOL_FIRE_PIPELINE, PARTICLE_MASK, false, true);
    private static final RenderType NORMAL_FIREBALL_HOT = createClamped("war_mod_fireball_hot",
        HOT_FIRE_PIPELINE, PARTICLE_MASK, false, false);
    private static final RenderType NORMAL_PLASMA_CORE = createClamped("war_mod_plasma_core",
        PLASMA_CORE_PIPELINE, PLASMA_MASK, false, false);
    private static final RenderType NORMAL_EXPLOSION_PUFF = createClamped("war_mod_explosion_puff",
        HOT_FIRE_PIPELINE, EXPLOSION_MASK, false, false);
    private static final RenderType NORMAL_NUCLEAR_FLASH = createClamped("war_mod_nuclear_flash",
        HOT_FIRE_PIPELINE, EXPLOSION_MASK, false, false);
    private static final RenderType NORMAL_ICBM_EXHAUST = createClamped("war_mod_icbm_exhaust",
        HOT_FIRE_PIPELINE,
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/particle/warhead_fireball_0.png"),
        false, false);
    private static final RenderType NORMAL_REENTRY_PLASMA = createClamped("war_mod_reentry_plasma",
        HOT_FIRE_PIPELINE,
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/particle/warhead_fireball_0.png"),
        false, false);

    /*
     * Iris-only compatibility types. These effects are submitted as custom world geometry,
     * so their translucent path must use the stock entity/world contract rather than
     * Iris' particle pass. The stable custom pipelines remain untouched when shaders are off.
     */
    private static final RenderType IRIS_CONE = irisParticle("war_mod_cone_iris",
        texture("vapor_noise.png"), true);
    private static final RenderType IRIS_VAPOR_BAND = irisParticle("war_mod_vapor_band_iris",
        texture("vapor_band.png"), true);
    private static final RenderType IRIS_PRESSURE_SHELL = irisParticle("war_mod_pressure_shell_iris",
        texture("pressure_shell.png"), true);
    private static final RenderType IRIS_TERRAIN_DEFORMATION = create("war_mod_terrain_deformation_iris",
        RenderPipelines.ENTITY_CUTOUT, TextureAtlas.LOCATION_BLOCKS, true, true, false);
    private static final RenderType IRIS_GROUND_RIPPLE = create("war_mod_ground_ripple_iris",
        RenderPipelines.ENTITY_TRANSLUCENT, texture("ground_ripple_noise.png"), true, true, true);
    private static final RenderType IRIS_SHOCKWAVE = irisParticle("war_mod_shockwave_iris",
        texture("shockwave_strip.png"), true);
    private static final RenderType IRIS_GROUND_DUST = irisParticle("war_mod_ground_dust_iris",
        PARTICLE_MASK, true);
    private static final RenderType IRIS_HEAVY_SMOKE = irisParticle("war_mod_heavy_smoke_iris",
        PARTICLE_MASK, true);
    private static final RenderType IRIS_HEAVY_SMOKE_CORE = irisParticle("war_mod_heavy_smoke_core_iris",
        PARTICLE_MASK, true);
    private static final RenderType IRIS_NUCLEAR_SMOKE = irisParticle("war_mod_nuclear_smoke_iris",
        PARTICLE_MASK, true);
    private static final RenderType IRIS_FIREBALL_CORE = irisEmissive("war_mod_fireball_core_iris",
        PARTICLE_MASK);
    private static final RenderType IRIS_FIREBALL_HOT = irisEmissive("war_mod_fireball_hot_iris",
        PARTICLE_MASK);
    /* The insulated nuclear stem uses the cool-fire pass for its crimson glow. Keep
       that pass emissive under Iris too, matching the vanilla/Sodium pipeline. */
    private static final RenderType IRIS_FIREBALL_COOL = irisEmissive("war_mod_fireball_cool_iris",
        PARTICLE_MASK);
    private static final RenderType IRIS_PLASMA_CORE = irisEmissive("war_mod_plasma_core_iris",
        PLASMA_MASK);
    private static final RenderType IRIS_EXPLOSION_PUFF = irisEmissive("war_mod_explosion_puff_iris",
        EXPLOSION_MASK);
    private static final RenderType IRIS_NUCLEAR_FLASH = irisEmissive("war_mod_nuclear_flash_iris",
        EXPLOSION_MASK);
    private static final RenderType IRIS_ICBM_EXHAUST = irisEmissive("war_mod_icbm_exhaust_iris",
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/particle/warhead_fireball_0.png"));
    private static final RenderType IRIS_REENTRY_PLASMA = irisEmissive("war_mod_reentry_plasma_iris",
        Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/particle/warhead_fireball_0.png"));

    public static final RenderType PROJECTILE = create("war_mod_projectile",
        RenderPipelines.ENTITY_CUTOUT, texture("warhead_albedo.png"), true, true, false);
    public static RenderType CONE = NORMAL_CONE;
    public static RenderType VAPOR_BAND = NORMAL_VAPOR_BAND;
    public static RenderType PRESSURE_SHELL = NORMAL_PRESSURE_SHELL;
    public static RenderType TERRAIN_DEFORMATION = NORMAL_TERRAIN_DEFORMATION;
    public static RenderType GROUND_RIPPLE = NORMAL_GROUND_RIPPLE;
    public static RenderType SHOCKWAVE = NORMAL_SHOCKWAVE;
    public static RenderType GROUND_DUST = NORMAL_GROUND_DUST;
    public static RenderType HEAVY_SMOKE = NORMAL_HEAVY_SMOKE;
    public static RenderType HEAVY_SMOKE_CORE = NORMAL_HEAVY_SMOKE_CORE;
    public static RenderType NUCLEAR_SMOKE = NORMAL_NUCLEAR_SMOKE;
    public static RenderType FIREBALL_CORE = NORMAL_FIREBALL_CORE;
    public static RenderType FIREBALL_COOL = NORMAL_FIREBALL_COOL;
    public static RenderType FIREBALL_HOT = NORMAL_FIREBALL_HOT;
    public static RenderType PLASMA_CORE = NORMAL_PLASMA_CORE;
    public static RenderType EXPLOSION_PUFF = NORMAL_EXPLOSION_PUFF;
    public static RenderType NUCLEAR_FLASH = NORMAL_NUCLEAR_FLASH;
    public static RenderType ICBM_EXHAUST = NORMAL_ICBM_EXHAUST;
    public static RenderType REENTRY_PLASMA = NORMAL_REENTRY_PLASMA;
    public static RenderType SMOKE_LOBE = HEAVY_SMOKE;
    public static RenderType FIREBALL = FIREBALL_COOL;

    private static boolean irisActive;

    static {
        refreshIrisState();
        ClientTickEvents.END_CLIENT_TICK.register(client -> refreshIrisState());
    }

    private WarheadRenderPipelines() { }

    public static boolean compatibilityRendererActive() { return irisActive; }

    private static void refreshIrisState() {
        boolean active = IrisShaderState.active();
        if (active == irisActive) return;
        irisActive = active;
        CONE = active ? IRIS_CONE : NORMAL_CONE;
        VAPOR_BAND = active ? IRIS_VAPOR_BAND : NORMAL_VAPOR_BAND;
        PRESSURE_SHELL = active ? IRIS_PRESSURE_SHELL : NORMAL_PRESSURE_SHELL;
        TERRAIN_DEFORMATION = active ? IRIS_TERRAIN_DEFORMATION : NORMAL_TERRAIN_DEFORMATION;
        GROUND_RIPPLE = active ? IRIS_GROUND_RIPPLE : NORMAL_GROUND_RIPPLE;
        SHOCKWAVE = active ? IRIS_SHOCKWAVE : NORMAL_SHOCKWAVE;
        GROUND_DUST = active ? IRIS_GROUND_DUST : NORMAL_GROUND_DUST;
        HEAVY_SMOKE = active ? IRIS_HEAVY_SMOKE : NORMAL_HEAVY_SMOKE;
        HEAVY_SMOKE_CORE = active ? IRIS_HEAVY_SMOKE_CORE : NORMAL_HEAVY_SMOKE_CORE;
        NUCLEAR_SMOKE = active ? IRIS_NUCLEAR_SMOKE : NORMAL_NUCLEAR_SMOKE;
        FIREBALL_CORE = active ? IRIS_FIREBALL_CORE : NORMAL_FIREBALL_CORE;
        FIREBALL_HOT = active ? IRIS_FIREBALL_HOT : NORMAL_FIREBALL_HOT;
        FIREBALL_COOL = active ? IRIS_FIREBALL_COOL : NORMAL_FIREBALL_COOL;
        PLASMA_CORE = active ? IRIS_PLASMA_CORE : NORMAL_PLASMA_CORE;
        EXPLOSION_PUFF = active ? IRIS_EXPLOSION_PUFF : NORMAL_EXPLOSION_PUFF;
        NUCLEAR_FLASH = active ? IRIS_NUCLEAR_FLASH : NORMAL_NUCLEAR_FLASH;
        ICBM_EXHAUST = active ? IRIS_ICBM_EXHAUST : NORMAL_ICBM_EXHAUST;
        REENTRY_PLASMA = active ? IRIS_REENTRY_PLASMA : NORMAL_REENTRY_PLASMA;
        SMOKE_LOBE = HEAVY_SMOKE;
        FIREBALL = FIREBALL_COOL;
    }

    private static RenderType irisParticle(final String name, final Identifier texture,
        final boolean sortOnUpload) {
        RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
            .withTexture("Sampler0", texture,
                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            .useLightmap()
            .useOverlay()
            .setOutline(RenderSetup.OutlineProperty.NONE);
        if (sortOnUpload) builder.sortOnUpload();
        return RenderType.create(name, builder.createRenderSetup());
    }

    private static RenderType irisEmissive(final String name, final Identifier texture) {
        return RenderType.create(name, RenderSetup.builder(RenderPipelines.EYES)
            .withTexture("Sampler0", texture,
                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            .setOutline(RenderSetup.OutlineProperty.NONE)
            .createRenderSetup());
    }

    private static RenderType createClamped(final String name, final RenderPipeline pipeline,
        final Identifier texture, final boolean useLightmap, final boolean sortOnUpload) {
        RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(pipeline)
            .withTexture("Sampler0", texture,
                () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            .setOutline(RenderSetup.OutlineProperty.NONE);
        if (useLightmap) builder.useLightmap();
        if (sortOnUpload) builder.sortOnUpload();
        return RenderType.create(name, builder.createRenderSetup());
    }

    private static RenderPipeline condensation() {
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation("pipeline/war_mod_condensation")
            .withShaderDefine("ALPHA_CUTOUT", 0.02F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false).build());
    }

    private static RenderPipeline smokeCutout(final String location,
        final float alphaCutout) {
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(location)
            .withShaderDefine("ALPHA_CUTOUT", alphaCutout)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false).build());
    }

    private static RenderPipeline emissiveTranslucent(final String location,
        final boolean writeDepth, final float alphaCutout) {
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(location)
            .withShaderDefine("ALPHA_CUTOUT", alphaCutout)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, writeDepth))
            .withCull(false).build());
    }

    private static RenderPipeline translucent(final String location, final boolean writeDepth) {
        return translucent(location, writeDepth, 0.02F);
    }

    private static RenderPipeline translucent(final String location, final boolean writeDepth,
        final float alphaCutout) {
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(location)
            .withShaderDefine("ALPHA_CUTOUT", alphaCutout)
            .withShaderDefine("NO_OVERLAY")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, writeDepth))
            .withCull(false).build());
    }

    private static RenderType create(final String name, final RenderPipeline pipeline,
        final Identifier texture, final boolean useLightmap, final boolean useOverlay,
        final boolean sortOnUpload) {
        RenderSetup.RenderSetupBuilder builder = RenderSetup.builder(pipeline)
            .withTexture("Sampler0", texture)
            .setOutline(RenderSetup.OutlineProperty.NONE);
        if (useLightmap) builder.useLightmap();
        if (useOverlay) builder.useOverlay();
        if (sortOnUpload) builder.sortOnUpload();
        return RenderType.create(name, builder.createRenderSetup());
    }

    private static Identifier texture(final String name) {
        return Identifier.fromNamespaceAndPath(WarMod.MOD_ID, "textures/effect/" + name);
    }
}
