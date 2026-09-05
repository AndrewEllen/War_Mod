package com.andye.warmod.radar.station.client;

import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class RadarDishRenderer {
    private RadarDishRenderer() { }
    public static void submit(RadarStationRenderState state, PoseStack stack, SubmitNodeCollector collector) {
        // The saved hierarchy sweeps the entire head around Y, then applies the
        // dish's authored 18-degree pitch around its own axle.
        stack.pushPose();
        stack.translate(.5F, RadarStationVisualGeometry.YAW_PIVOT_Y, .5F);
        stack.mulPose(Axis.YP.rotationDegrees((float)state.sweepAngle
            + RadarStationVisualGeometry.MODEL_YAW_OFFSET_DEGREES));
        collector.submitCustomGeometry(stack, BlockbenchModelRenderType.SOLID,
            (pose, buffer) -> RadarDishMesh.renderMount(pose, buffer,
                state.lightCoords, state.warningActive));
        stack.translate(0.0F, RadarStationVisualGeometry.DISH_PIVOT_Y
            - RadarStationVisualGeometry.YAW_PIVOT_Y, 0.0F);
        stack.mulPose(Axis.XP.rotationDegrees(RadarStationVisualGeometry.DISH_ELEVATION_ANGLE));
        collector.submitCustomGeometry(stack, BlockbenchModelRenderType.SOLID,
            (pose, buffer) -> RadarDishMesh.render(pose, buffer,
                state.lightCoords, state.warningActive));
        stack.popPose();
    }
}
