package com.andye.warmod.silo.client;

import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class MissileSiloMissileRenderer {
    private MissileSiloMissileRenderer() { }
    public static void submit(final MissileSiloRenderState state, final PoseStack poseStack,
        final SubmitNodeCollector collector) {
        if (!state.visible || state.missileType == null) return;
        poseStack.pushPose();
        poseStack.translate(0.5, 1.40 + state.reloadOffsetY, 0.5);
        poseStack.scale(1.0F, 1.0F, 1.0F);
        collector.submitCustomGeometry(poseStack, BlockbenchModelRenderType.SOLID,
            (pose, buffer) -> MissileSiloMissileMesh.render(pose, buffer, state.missileType, state.lightCoords));
        poseStack.popPose();
    }
}
