package com.andye.warmod.client.model;

import com.andye.warmod.WarMod;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Material-detail pass used by generated meshes whose base colour comes from Blockbench. */
public final class BlockbenchModelRenderType {
    public static final RenderType SOLID = RenderType.create("war_mod_blockbench_models",
        RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT)
            .withTexture("Sampler0", Identifier.fromNamespaceAndPath(WarMod.MOD_ID,
                "textures/blockbench_material_atlas.png"))
            .useLightmap().useOverlay().createRenderSetup());
    public static final RenderType TRANSLUCENT = RenderType.create(
        "war_mod_blockbench_models_translucent",
        RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
            .withTexture("Sampler0", Identifier.fromNamespaceAndPath(WarMod.MOD_ID,
                "textures/blockbench_material_atlas.png"))
            .useLightmap().useOverlay().sortOnUpload().createRenderSetup());

    private BlockbenchModelRenderType() { }
}
