package com.andye.warmod.radar.station.client;

import com.andye.warmod.client.model.BlockbenchGameplayMeshes;
import com.andye.warmod.client.model.BlockbenchGameplayMeshes.Model;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Runtime wrapper around the saved radar head and dish groups from Blockbench. */
public final class RadarDishMesh {
    private RadarDishMesh() { }

    public static void renderMount(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final boolean warning) {
        BlockbenchGameplayMeshes.render(pose, buffer, Model.RADAR_YAW,
            RadarStationVisualGeometry.MODEL_SCALE, 0.0F,
            RadarStationVisualGeometry.YAW_PIVOT_MODEL_Y, 0.0F, light);
    }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final boolean warning) {
        BlockbenchGameplayMeshes.render(pose, buffer, Model.RADAR_PITCH,
            RadarStationVisualGeometry.MODEL_SCALE, 0.0F,
            RadarStationVisualGeometry.DISH_PIVOT_MODEL_Y, 0.0F, light);
    }
}
