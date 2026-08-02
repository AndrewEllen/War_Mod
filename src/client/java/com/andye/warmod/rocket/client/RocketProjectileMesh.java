package com.andye.warmod.rocket.client;

import com.andye.warmod.icbm.client.render.IcbmLongRangeRenderContext;
import com.andye.warmod.icbm.client.render.IcbmMissileMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class RocketProjectileMesh {
    private RocketProjectileMesh() { }
    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer, final int light) {
        IcbmMissileMesh.render(pose, buffer, IcbmLongRangeRenderContext.Lod.NEAR, light);
    }
}
