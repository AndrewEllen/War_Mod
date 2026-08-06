package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Initial nuclear flash that expands, contracts and fades without popping. */
public final class NuclearFlashRenderer {
    private NuclearFlashRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final Quaternionf camera) {
        if (age < 0.0 || age >= 38.0) return;
        double attack = smoothstep(WarheadVisualMath.clamp(age / 5.0, 0.0, 1.0));
        double release = age <= 5.0 ? 0.0
            : smoothstep(WarheadVisualMath.clamp((age - 5.0) / 33.0, 0.0, 1.0));
        double radius = age <= 5.0
            ? Mth.lerp(attack, 10.0, 146.0)
            : Mth.lerp(release, 146.0, 38.0);
        double remaining = WarheadVisualMath.clamp(1.0 - age / 38.0, 0.0, 1.0);
        double alpha = Math.pow(remaining, 1.18) * (0.82 + 0.18 * attack);
        billboard(pose, buffer, Vec3.ZERO, (float) radius, 0.0, alpha, camera);
        billboard(pose, buffer, Vec3.ZERO, (float) (radius * 0.81), Math.PI * 0.25,
            alpha * 0.86, camera);
        billboard(pose, buffer, Vec3.ZERO, (float) (radius * 0.63), Math.PI * 0.50,
            alpha * 0.70, camera);
    }

    private static void billboard(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final float radius,
        final double rotation, final double alpha, final Quaternionf camera) {
        float cosine = Mth.cos((float) rotation);
        float sine = Mth.sin((float) rotation);
        float ux = cosine * radius;
        float uy = sine * radius;
        float vx = -sine * radius;
        float vy = cosine * radius;
        int packedAlpha = Mth.clamp((int) (alpha * 255.0), 0, 255);
        vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F,
            packedAlpha, camera);
        vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F,
            packedAlpha, camera);
        vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F,
            packedAlpha, camera);
        vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F,
            packedAlpha, camera);
    }

    private static void vertex(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final float x,
        final float y, final float u, final float v, final int alpha,
        final Quaternionf camera) {
        Vector3f offset = new Vector3f(x, y, 0.0F).rotate(camera);
        Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera);
        buffer.addVertex(pose, (float) center.x + offset.x,
                (float) center.y + offset.y, (float) center.z + offset.z)
            .setColor(255, 248, 218, alpha).setUv(u, v).setOverlay(0)
            .setLight(0xF000F0).setNormal(pose, normal.x, normal.y, normal.z);
    }

    private static double smoothstep(final double value) {
        double t = WarheadVisualMath.clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
