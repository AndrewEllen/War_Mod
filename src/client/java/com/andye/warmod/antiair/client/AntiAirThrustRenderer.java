package com.andye.warmod.antiair.client;

import com.andye.warmod.antiair.AntiAirFlightPhase;
import com.andye.warmod.antiair.AntiAirMissileVariant;
import com.andye.warmod.antiair.client.render.AntiAirMissileMesh;
import com.andye.warmod.icbm.client.render.IcbmLongRangeRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;

public final class AntiAirThrustRenderer {
    private AntiAirThrustRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final AntiAirMissileVariant variant, final AntiAirFlightPhase phase,
        final long seed, final double time, final IcbmLongRangeRenderContext.Lod lod) {
        float scale = phase == AntiAirFlightPhase.IGNITION ? 0.55F
            : phase == AntiAirFlightPhase.INTERCEPT ? 0.58F : 1.0F;
        float flicker = (float) (0.92 + 0.08 * Math.sin(time * 2.7 + (seed & 1023) * 0.031));
        float nozzle = AntiAirMissileMesh.nozzleY(variant);
        plume(pose, buffer, nozzle, 0.11F, 1.12F * scale * flicker, 255, 255, 242, 255);
        plume(pose, buffer, nozzle - 0.02F, 0.20F, 2.10F * scale * flicker, 255, 142, 36, 212);
        if (lod == IcbmLongRangeRenderContext.Lod.NEAR
            || lod == IcbmLongRangeRenderContext.Lod.MEDIUM) {
            plume(pose, buffer, nozzle - 0.04F, 0.18F, 3.10F * scale * flicker,
                225, 215, 180, 105);
        }
    }

    private static void plume(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float top, final float radius, final float length,
        final int red, final int green, final int blue, final int alpha) {
        for (int quad = 0; quad < 2; quad++) {
            float angle = quad * Mth.HALF_PI, cosine = Mth.cos(angle), sine = Mth.sin(angle);
            vertex(pose, buffer, -cosine * radius, top, -sine * radius, 0, 0, red, green, blue, alpha);
            vertex(pose, buffer, cosine * radius, top, sine * radius, 1, 0, red, green, blue, alpha);
            vertex(pose, buffer, cosine * 0.012F, top - length, sine * 0.012F, 1, 1, red, green, blue, 0);
            vertex(pose, buffer, -cosine * 0.012F, top - length, -sine * 0.012F, 0, 1, red, green, blue, 0);
        }
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float x, final float y, final float z, final float u, final float v,
        final int red, final int green, final int blue, final int alpha) {
        buffer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha).setUv(u, v)
            .setOverlay(0).setLight(0xF000F0).setNormal(pose, 0, 1, 0);
    }
}
