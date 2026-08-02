package com.andye.warmod.silo.client;

import com.andye.warmod.icbm.client.render.IcbmLongRangeRenderContext;
import com.andye.warmod.icbm.client.render.IcbmMissileMesh;
import com.andye.warmod.warhead.WarheadPayloadType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

public final class MissileSiloMissileMesh {
    private MissileSiloMissileMesh() { }
    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final WarheadPayloadType payloadType, final int light) {
        IcbmMissileMesh.render(pose, buffer, IcbmLongRangeRenderContext.Lod.NEAR, light);
        ring(pose, buffer, payloadType == WarheadPayloadType.NUCLEAR ? 212 : 145,
            payloadType == WarheadPayloadType.NUCLEAR ? 179 : 116, payloadType == WarheadPayloadType.NUCLEAR ? 35 : 60, light);
        // The existing carrier mesh supplies the physical body. Payload identity is carried by the render state;
        // the original item textures and silo lighting make conventional/nuclear loads distinct at close range.
    }
    private static void ring(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int red, final int green, final int blue, final int light) {
        int sides = 12;
        float radius = 0.535F, bottom = 0.15F, top = 0.55F;
        for (int index = 0; index < sides; index++) {
            float angle = Mth.TWO_PI * index / sides;
            float next = Mth.TWO_PI * (index + 1) / sides;
            float x = radius * Mth.cos(angle), z = radius * Mth.sin(angle);
            float nx = radius * Mth.cos(next), nz = radius * Mth.sin(next);
            vertex(pose, buffer, x, bottom, z, 0, 1, red, green, blue, light, x, z);
            vertex(pose, buffer, x, top, z, 0, 0, red, green, blue, light, x, z);
            vertex(pose, buffer, nx, top, nz, 1, 0, red, green, blue, light, nx, nz);
            vertex(pose, buffer, nx, bottom, nz, 1, 1, red, green, blue, light, nx, nz);
        }
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final float x,
        final float y, final float z, final float u, final float v, final int red, final int green,
        final int blue, final int light, final float normalX, final float normalZ) {
        buffer.addVertex(pose, x, y, z).setColor(red, green, blue, 255).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, normalX, 0, normalZ);
    }
}
