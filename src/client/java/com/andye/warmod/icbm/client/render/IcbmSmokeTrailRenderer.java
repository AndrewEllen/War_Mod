package com.andye.warmod.icbm.client.render;

import com.andye.warmod.icbm.client.IcbmTrailSample;
import com.mojang.blaze3d.vertex.*;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Emits independent billboard quads for one missile only; no vertex state is shared across trails. */
public final class IcbmSmokeTrailRenderer {
    private static final double MAX_SEGMENT_DISTANCE = 64.0;
    private IcbmSmokeTrailRenderer() { }
    public static void render(PoseStack.Pose pose, VertexConsumer buffer, List<IcbmTrailSample> samples,
        IcbmLongRangeRenderContext context, Quaternionf camera) {
        var lod = context.lod(); int limit = lod == IcbmLongRangeRenderContext.Lod.NEAR ? 120
            : lod == IcbmLongRangeRenderContext.Lod.MEDIUM ? 70 : lod == IcbmLongRangeRenderContext.Lod.FAR ? 32 : 14;
        int stride = lod == IcbmLongRangeRenderContext.Lod.EXTREME ? 3 : lod == IcbmLongRangeRenderContext.Lod.FAR ? 2 : 1;
        int start = Math.max(0, samples.size() - limit * stride); Vec3 previous = null;
        for (int index = start; index < samples.size(); index += stride) {
            IcbmTrailSample sample = samples.get(index);
            if (!sample.position().isFinite() || !sample.drift().isFinite() || !Double.isFinite(sample.ageTicks())) continue;
            Vec3 actual = sample.position().add(sample.drift().scale(sample.ageTicks()));
            if (!actual.isFinite()) continue;
            if (previous != null) { double distance = previous.distanceTo(actual);
                if (distance <= .001 || distance > MAX_SEGMENT_DISTANCE) { previous = actual; continue; } }
            previous = actual;
            float alpha = (float)Math.max(0, Math.min(.72, 1 - sample.ageTicks() / 140.0));
            float lodScale = lod == IcbmLongRangeRenderContext.Lod.EXTREME ? 1.7F : 1.0F;
            float radius = sample.size() * (float)(1 + sample.ageTicks() * .018) * lodScale
                * (float)context.transform().compression();
            billboard(pose, buffer, context.transform().renderPosition(actual), radius, sample.rotation(), alpha, camera);
        }
    }
    private static void billboard(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, float radius, float rotation,
        float alpha, Quaternionf camera) { float cosine = Mth.cos(rotation), sine = Mth.sin(rotation), ux = cosine * radius,
            uy = sine * radius, vx = -sine * radius, vy = cosine * radius;
        vertex(pose, buffer, center, -ux - vx, -uy - vy, 0, 1, alpha, camera);
        vertex(pose, buffer, center, -ux + vx, -uy + vy, 0, 0, alpha, camera);
        vertex(pose, buffer, center, ux + vx, uy + vy, 1, 0, alpha, camera);
        vertex(pose, buffer, center, ux - vx, uy - vy, 1, 1, alpha, camera);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, float x, float y, float u, float v,
        float alpha, Quaternionf camera) { Vector3f offset = new Vector3f(x, y, 0).rotate(camera), normal = new Vector3f(0, 0, 1).rotate(camera);
        buffer.addVertex(pose, (float)center.x + offset.x, (float)center.y + offset.y, (float)center.z + offset.z)
            .setColor(110, 115, 120, (int)(alpha * 255)).setUv(u, v).setOverlay(0).setLight(0xA000A0)
            .setNormal(pose, normal.x, normal.y, normal.z); }
}