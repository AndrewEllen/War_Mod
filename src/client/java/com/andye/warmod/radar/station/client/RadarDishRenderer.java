package com.andye.warmod.radar.station.client;

import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class RadarDishRenderer {
    private RadarDishRenderer() { }
    public static void submit(RadarStationRenderState state, PoseStack stack, SubmitNodeCollector collector) {
        // The mount is seated on the fixed mast; only the reflector turns about its physical axle.
        stack.pushPose(); stack.translate(.5F, RadarStationVisualGeometry.DISH_MOUNT_Y, .5F);
        collector.submitCustomGeometry(stack, WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> RadarDishMesh.renderMount(pose, buffer, state.lightCoords, state.warningActive)); stack.popPose();
        stack.pushPose(); stack.translate(RadarStationVisualGeometry.DISH_PIVOT_X, RadarStationVisualGeometry.DISH_PIVOT_Y,
            RadarStationVisualGeometry.DISH_PIVOT_Z);
        // Sweep around the mast's world-up axis. The shallow elevation is fixed in
        // the array's local frame, so the head no longer appears to tumble.
        stack.mulPose(Axis.YP.rotationDegrees((float)state.sweepAngle
            + RadarStationVisualGeometry.MODEL_YAW_OFFSET_DEGREES));
        stack.mulPose(Axis.XP.rotationDegrees(RadarStationVisualGeometry.DISH_ELEVATION_ANGLE));
        collector.submitCustomGeometry(stack, WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> RadarDishMesh.render(pose, buffer, state.lightCoords, state.warningActive)); stack.popPose();
    }
}
