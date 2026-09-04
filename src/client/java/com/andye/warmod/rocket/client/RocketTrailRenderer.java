package com.andye.warmod.rocket.client;

import com.andye.warmod.rocket.RocketConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Readable exhaust at every LOD; smoke uses a translucent fading pipeline. */
public final class RocketTrailRenderer {
    private RocketTrailRenderer() { }

    public static void renderFlame(final PoseStack.Pose pose, final VertexConsumer buffer,
        final RocketProjectileRenderState state) {
        if (state.ageInTicks > RocketConstants.MOTOR_BURN_TICKS) return;
        float pulse = 0.85F + 0.35F * (0.5F + 0.5F * (float) Math.sin(
            state.ageInTicks * 0.93 + state.visualSeed * 0.0001));
        float base = -(float) state.payloadType.length() / 2.0F;
        float length = switch (state.lod) {
            case NEAR -> 1.45F;
            case MEDIUM -> 1.12F;
            case FAR -> 0.82F;
        } * pulse;
        cross(pose, buffer, base, base - length, 0.16F,
            255, 142, 28, 232);
        cross(pose, buffer, base - 0.02F, base - length * 0.68F, 0.088F,
            255, 246, 196, 255);
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final RocketProjectileRenderState state) {
        if (state.ageInTicks > RocketConstants.MOTOR_BURN_TICKS) return;
        float base = -(float) state.payloadType.length() / 2.0F;
        int samples = switch (state.lod) { case NEAR -> 14; case MEDIUM -> 8; case FAR -> 4; };
        float spacing = switch (state.lod) { case NEAR -> 0.27F; case MEDIUM -> 0.36F; case FAR -> 0.52F; };
        for (int index = 0; index < samples; index++) {
            float y = base - 0.42F - index * spacing;
            float size = 0.14F + index * (state.lod == RocketProjectileRenderState.RocketLod.NEAR
                ? 0.030F : 0.040F);
            double phase = state.visualSeed * 0.00001 + index * 1.7
                + state.ageInTicks * 0.13;
            float drift = 0.028F + index * 0.006F;
            float x = (float) Math.sin(phase) * drift;
            float z = (float) Math.cos(phase * 0.91) * drift;
            int alpha = Math.max(12, 142 - index * (118 / Math.max(1, samples - 1)));
            billboard(pose, buffer, x, y, z, size,
                116 + index, 121 + index, 119 + index, alpha);
        }
    }

    private static void cross(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float y1, final float y2, final float width,
        final int red, final int green, final int blue, final int alpha) {
        quad(pose, buffer, -width, y1, 0, width, y1, 0,
            width * 0.25F, y2, 0, -width * 0.25F, y2, 0,
            red, green, blue, alpha);
        quad(pose, buffer, 0, y1, -width, 0, y1, width,
            0, y2, width * 0.25F, 0, y2, -width * 0.25F,
            red, green, blue, alpha);
    }

    private static void billboard(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z, final float size,
        final int red, final int green, final int blue, final int alpha) {
        quad(pose, buffer, x - size, y - size, z, x + size, y - size, z,
            x + size, y + size, z, x - size, y + size, z,
            red, green, blue, alpha);
        quad(pose, buffer, x, y - size, z - size, x, y - size, z + size,
            x, y + size, z + size, x, y + size, z - size,
            red, green, blue, alpha);
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float ax, final float ay, final float az,
        final float bx, final float by, final float bz,
        final float cx, final float cy, final float cz,
        final float dx, final float dy, final float dz,
        final int red, final int green, final int blue, final int alpha) {
        vertex(pose, buffer, ax, ay, az, 0, 0, red, green, blue, alpha);
        vertex(pose, buffer, bx, by, bz, 1, 0, red, green, blue, alpha);
        vertex(pose, buffer, cx, cy, cz, 1, 1, red, green, blue, alpha);
        vertex(pose, buffer, dx, dy, dz, 0, 1, red, green, blue, alpha);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z, final float u, final float v,
        final int red, final int green, final int blue, final int alpha) {
        buffer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha)
            .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
            .setNormal(pose, 0, 1, 0);
    }
}
