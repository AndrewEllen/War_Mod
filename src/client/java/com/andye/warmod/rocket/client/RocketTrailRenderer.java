package com.andye.warmod.rocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class RocketTrailRenderer {
    private RocketTrailRenderer() { }
    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final RocketProjectileRenderState state) {
        quad(pose, buffer, -0.24F, -2.4F, 0, 0.24F, -2.4F, 0, 0.10F, -6.0F, 0, -0.10F, -6.0F, 0);
        quad(pose, buffer, 0, -2.4F, -0.24F, 0, -2.4F, 0.24F, 0, -6.0F, 0.10F, 0, -6.0F, -0.10F);
    }
    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x1, final float y1, final float z1, final float x2, final float y2, final float z2,
        final float x3, final float y3, final float z3, final float x4, final float y4, final float z4) {
        vertex(pose, buffer, x1, y1, z1, 0, 0, 180); vertex(pose, buffer, x2, y2, z2, 1, 0, 180);
        vertex(pose, buffer, x3, y3, z3, 1, 1, 25); vertex(pose, buffer, x4, y4, z4, 0, 1, 25);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final float x, final float y,
        final float z, final float u, final float v, final int alpha) {
        buffer.addVertex(pose, x, y, z).setColor(150, 145, 132, alpha).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0, 1, 0);
    }
}
