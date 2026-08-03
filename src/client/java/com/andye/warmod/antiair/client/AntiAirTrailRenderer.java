package com.andye.warmod.antiair.client;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** World-space smoke samples rendered as independent camera-facing billboards. */
public final class AntiAirTrailRenderer {
    private AntiAirTrailRenderer() { }
    public static void render(PoseStack.Pose pose, VertexConsumer buffer, List<AntiAirTrailSampler.Sample> samples,
        Vec3 origin, Quaternionf camera) {
        for (AntiAirTrailSampler.Sample sample : samples) {
            Vec3 centre = sample.position().subtract(origin);
            if (!centre.isFinite()) continue;
            float radius = sample.width() * (1.1F + sample.age() * 2.2F);
            float alpha = sample.alpha() * .72F;
            billboard(pose, buffer, centre, radius, alpha, camera);
        }
    }
    private static void billboard(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, float radius, float alpha,
        Quaternionf camera) {
        vertex(pose, buffer, centre, -radius, -radius, 0, 1, alpha, camera);
        vertex(pose, buffer, centre, -radius, radius, 0, 0, alpha, camera);
        vertex(pose, buffer, centre, radius, radius, 1, 0, alpha, camera);
        vertex(pose, buffer, centre, radius, -radius, 1, 1, alpha, camera);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, float x, float y, float u,
        float v, float alpha, Quaternionf camera) {
        Vector3f offset = new Vector3f(x, y, 0).rotate(camera);
        Vector3f normal = new Vector3f(0, 0, 1).rotate(camera);
        buffer.addVertex(pose, (float)centre.x + offset.x, (float)centre.y + offset.y, (float)centre.z + offset.z)
            .setColor(185, 193, 188, (int)(Math.max(0, Math.min(1, alpha)) * 255)).setUv(u, v)
            .setOverlay(0).setLight(0xA000A0).setNormal(pose, normal.x, normal.y, normal.z);
    }
}