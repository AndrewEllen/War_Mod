package com.andye.warmod.radar.station.client;

import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class RadarDishRenderer {
    private static final float MODEL_YAW_OFFSET_DEGREES = 180.0F;
    private static final float ELEVATION_DEGREES = -22.0F;

    private RadarDishRenderer() { }

    public static void submit(final RadarStationRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.translate(0.5, 1.72, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(
            (float)state.sweepAngle + MODEL_YAW_OFFSET_DEGREES));
        poseStack.mulPose(Axis.XP.rotationDegrees(ELEVATION_DEGREES));
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> RadarDishMesh.render(
                pose, buffer, state.lightCoords, state.warningActive));
        poseStack.popPose();
    }
}