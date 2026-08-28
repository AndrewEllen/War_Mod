package com.andye.warmod.silo.client;

import com.andye.warmod.client.model.BlockbenchModelRenderType;
import com.andye.warmod.silo.SiloMissileRole;
import com.andye.warmod.warhead.client.render.WarheadRenderPipelines;
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
        var renderType = state.missileType.role() == SiloMissileRole.INTERCEPTOR
            ? BlockbenchModelRenderType.SOLID : WarheadRenderPipelines.PROJECTILE;
        collector.submitCustomGeometry(poseStack, renderType,
            (pose, buffer) -> MissileSiloMissileMesh.render(pose, buffer, state.missileType, state.lightCoords));
        poseStack.popPose();
    }
}
