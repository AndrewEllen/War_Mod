package com.andye.warmod.silo.client;

import com.andye.warmod.antiair.client.render.AntiAirMissileMesh;
import com.andye.warmod.icbm.client.render.IcbmLongRangeRenderContext;
import com.andye.warmod.icbm.client.render.IcbmMissileMesh;
import com.andye.warmod.silo.SiloMissileRole;
import com.andye.warmod.silo.SiloMissileType;
import com.andye.warmod.warhead.WarheadYield;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class MissileSiloMissileMesh {
    private MissileSiloMissileMesh() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final SiloMissileType type, final int light) {
        if (type.role() == SiloMissileRole.INTERCEPTOR) {
            AntiAirMissileMesh.render(pose, buffer, type.antiAirVariant().orElseThrow(), light);
            return;
        }
        WarheadYield yield = type.yield().orElseGet(() ->
            WarheadYield.defaultFor(type.payloadType().orElseThrow()));
        IcbmMissileMesh.render(pose, buffer, yield, type.deliveryMode(),
            IcbmLongRangeRenderContext.Lod.NEAR, light);
    }
}
