package com.andye.warmod.icbm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

public final class IcbmMissileMesh {
    private IcbmMissileMesh() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final IcbmPayloadAppearance appearance, final IcbmLongRangeRenderContext.Lod detail,
        final int light) {
        render(pose, buffer, appearance, detail, light, 255);
    }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final IcbmPayloadAppearance appearance, final IcbmLongRangeRenderContext.Lod detail,
        final int light, final int alpha) {
        int sides = detail == IcbmLongRangeRenderContext.Lod.NEAR ? 12
            : detail == IcbmLongRangeRenderContext.Lod.MEDIUM ? 8 : 6;
        float radius = IcbmVisualGeometry.BODY_RADIUS;
        float bottom = IcbmVisualGeometry.BODY_BOTTOM;
        float top = IcbmVisualGeometry.BODY_TOP;
        for (int index = 0; index < sides; index++) {
            float angle = Mth.TWO_PI * index / sides;
            float nextAngle = Mth.TWO_PI * (index + 1) / sides;
            float x = radius * Mth.cos(angle);
            float z = radius * Mth.sin(angle);
            float nextX = radius * Mth.cos(nextAngle);
            float nextZ = radius * Mth.sin(nextAngle);
            quad(pose, buffer, x, bottom, z, x, top, z, nextX, top, nextZ,
                nextX, bottom, nextZ, 47, 52, 58, alpha, light,
                Mth.cos(angle), 0, Mth.sin(angle));
            triangle(pose, buffer, x, top, z, 0, IcbmVisualGeometry.NOSE_TIP, 0,
                nextX, top, nextZ, 65, 71, 78, alpha, light);
        }
        renderBand(pose, buffer, appearance, sides, light, alpha);
        if (detail == IcbmLongRangeRenderContext.Lod.EXTREME) return;
        renderNozzle(pose, buffer, sides, light, alpha);
        renderFins(pose, buffer, light, alpha);
    }

    private static void renderBand(final PoseStack.Pose pose, final VertexConsumer buffer,
        final IcbmPayloadAppearance appearance, final int sides, final int light, final int alpha) {
        float radius = IcbmVisualGeometry.BODY_RADIUS + 0.006F;
        float bottom = IcbmVisualGeometry.PAYLOAD_BAND_POSITION - 0.10F;
        float top = IcbmVisualGeometry.PAYLOAD_BAND_POSITION + 0.10F;
        for (int index = 0; index < sides; index++) {
            float angle = Mth.TWO_PI * index / sides;
            float next = Mth.TWO_PI * (index + 1) / sides;
            float x = radius * Mth.cos(angle);
            float z = radius * Mth.sin(angle);
            float nx = radius * Mth.cos(next);
            float nz = radius * Mth.sin(next);
            quad(pose, buffer, x, bottom, z, x, top, z, nx, top, nz, nx, bottom, nz,
                appearance.red(), appearance.green(), appearance.blue(), alpha, light,
                Mth.cos(angle), 0, Mth.sin(angle));
        }
    }

    private static void renderNozzle(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int sides, final int light, final int alpha) {
        float topRadius = IcbmVisualGeometry.BODY_RADIUS * 0.72F;
        float bottomRadius = IcbmVisualGeometry.BODY_RADIUS * 0.52F;
        float top = IcbmVisualGeometry.BODY_BOTTOM;
        float bottom = -IcbmVisualGeometry.TOTAL_VISUAL_HEIGHT * 0.5F;
        for (int index = 0; index < sides; index++) {
            float angle = Mth.TWO_PI * index / sides;
            float next = Mth.TWO_PI * (index + 1) / sides;
            quad(pose, buffer,
                topRadius * Mth.cos(angle), top, topRadius * Mth.sin(angle),
                bottomRadius * Mth.cos(angle), bottom, bottomRadius * Mth.sin(angle),
                bottomRadius * Mth.cos(next), bottom, bottomRadius * Mth.sin(next),
                topRadius * Mth.cos(next), top, topRadius * Mth.sin(next),
                30, 34, 38, alpha, light, Mth.cos(angle), 0, Mth.sin(angle));
        }
    }

    private static void renderFins(final PoseStack.Pose pose, final VertexConsumer buffer,
        final int light, final int alpha) {
        float root = IcbmVisualGeometry.FIN_ROOT_POSITION;
        float radius = IcbmVisualGeometry.BODY_RADIUS;
        for (int fin = 0; fin < 4; fin++) {
            float angle = Mth.TWO_PI * fin / 4;
            float x = Mth.cos(angle);
            float z = Mth.sin(angle);
            quad(pose, buffer,
                x * radius, root - 0.18F, z * radius,
                x * (radius + IcbmVisualGeometry.FIN_SPAN), root - 0.14F,
                z * (radius + IcbmVisualGeometry.FIN_SPAN),
                x * (radius + IcbmVisualGeometry.FIN_SPAN * 0.72F), root + 0.48F,
                z * (radius + IcbmVisualGeometry.FIN_SPAN * 0.72F),
                x * radius, root + 0.35F, z * radius,
                39, 44, 49, alpha, light, x, 0, z);
        }
    }

    private static void triangle(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float ax, final float ay, final float az, final float bx, final float by, final float bz,
        final float cx, final float cy, final float cz, final int red, final int green, final int blue,
        final int alpha, final int light) {
        vertex(pose, buffer, ax, ay, az, 0, 1, red, green, blue, alpha, light, 0, 1, 0);
        vertex(pose, buffer, bx, by, bz, 0.5F, 0, red, green, blue, alpha, light, 0, 1, 0);
        vertex(pose, buffer, cx, cy, cz, 1, 1, red, green, blue, alpha, light, 0, 1, 0);
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x1, final float y1, final float z1, final float x2, final float y2, final float z2,
        final float x3, final float y3, final float z3, final float x4, final float y4, final float z4,
        final int red, final int green, final int blue, final int alpha, final int light,
        final float normalX, final float normalY, final float normalZ) {
        vertex(pose, buffer, x1, y1, z1, 0, 1, red, green, blue, alpha, light, normalX, normalY, normalZ);
        vertex(pose, buffer, x2, y2, z2, 0, 0, red, green, blue, alpha, light, normalX, normalY, normalZ);
        vertex(pose, buffer, x3, y3, z3, 1, 0, red, green, blue, alpha, light, normalX, normalY, normalZ);
        vertex(pose, buffer, x4, y4, z4, 1, 1, red, green, blue, alpha, light, normalX, normalY, normalZ);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z, final float u, final float v,
        final int red, final int green, final int blue, final int alpha, final int light,
        final float normalX, final float normalY, final float normalZ) {
        buffer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose, normalX, normalY, normalZ);
    }
}