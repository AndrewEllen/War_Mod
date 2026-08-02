package com.andye.warmod.silo.client;

import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class MissileSiloMissileRenderer {
    private MissileSiloMissileRenderer() { }
    public static void submit(final MissileSiloRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector) {
        if (!state.visible || state.payloadType == null) return;
        poseStack.pushPose();
        poseStack.translate(0.5, 1.35 + state.reloadOffsetY, 0.5);
        poseStack.scale(0.52F, 0.52F, 0.52F);
        collector.submitCustomGeometry(poseStack, WarheadRenderPipelines.PROJECTILE,
            (pose, buffer) -> MissileSiloMissileMesh.render(pose, buffer, state.payloadType, state.lightCoords));
        poseStack.popPose();
    }
}
