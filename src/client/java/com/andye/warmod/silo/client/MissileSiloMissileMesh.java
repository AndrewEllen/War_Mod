package com.andye.warmod.silo.client;

import com.andye.warmod.icbm.client.render.IcbmLongRangeRenderContext;
import com.andye.warmod.icbm.client.render.IcbmMissileMesh;
import com.andye.warmod.icbm.client.render.IcbmPayloadAppearance;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class MissileSiloMissileMesh {
    private MissileSiloMissileMesh() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final WarheadPayloadType payloadType, final int light) {
        IcbmMissileMesh.render(pose, buffer, IcbmPayloadAppearance.from(payloadType),
            IcbmLongRangeRenderContext.Lod.NEAR, light);
    }
}