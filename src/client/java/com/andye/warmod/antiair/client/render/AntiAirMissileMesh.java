package com.andye.warmod.antiair.client.render;

import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes.Model;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Tier-distinct interceptor meshes exported from the saved Blockbench sources. */
public final class AntiAirMissileMesh {
    private static final float MODEL_SCALE = 1.0F / 16.0F;

    private AntiAirMissileMesh() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final AntiAirMissileVariant variant, final int light) {
        Model model = variant == AntiAirMissileVariant.MK_II
            ? Model.ANTI_AIR_MISSILE_MK2 : Model.ANTI_AIR_MISSILE_MK1;
        BlockbenchGameplayMeshes.render(pose, buffer, model, MODEL_SCALE,
            0.0F, 0.0F, 0.0F, light);
    }
}
