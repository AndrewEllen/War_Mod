package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Terrain-following particle layer for the inward nuclear return wave. */
public final class TerrainReturnFrontRenderer {
    private static final int MAX_NODES = 2_400;
    private static final List<TerrainShockfrontNode> NODE_BUFFER =
        new ArrayList<>(MAX_NODES);

    private TerrainReturnFrontRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final List<TerrainShockfrontSpoke> spokes, final Vec3 impactPosition,
        final double returnRadius, final float yieldScale, final long visualSeed,
        final WarheadMesh.Lod lod, final Quaternionf cameraOrientation) {
        if (spokes == null || spokes.isEmpty() || impactPosition == null
            || !Double.isFinite(returnRadius) || returnRadius <= 0.0) return;

        float scale = Mth.clamp(yieldScale, 0.35F, 4.2F);
        double halfWidth = 3.0 + Math.sqrt(scale) * 2.2;
        double inner = Math.max(0.0, returnRadius - halfWidth);
        double outer = returnRadius + halfWidth;
        int nodeLimit = switch (lod) {
            case NEAR -> MAX_NODES;
            case MEDIUM -> 1_300;
            case FAR -> 620;
        };

        NODE_BUFFER.clear();
        for (TerrainShockfrontSpoke spoke : spokes) {
            int remaining = nodeLimit - NODE_BUFFER.size();
            if (remaining <= 0) break;
            spoke.appendNodesInDistanceBand(inner, outer, remaining, NODE_BUFFER);
        }
        if (NODE_BUFFER.isEmpty()) return;

        Basis basis = Basis.from(cameraOrientation);
        int puffs = lod == WarheadMesh.Lod.NEAR ? 5
            : lod == WarheadMesh.Lod.MEDIUM ? 3 : 2;
        float sqrtScale = Mth.sqrt(scale);
        for (TerrainShockfrontNode node : NODE_BUFFER) {
            Vec3 relative = node.position().subtract(impactPosition);
            double horizontal = Math.sqrt(relative.x * relative.x + relative.z * relative.z);
            if (horizontal < 1.0E-4) continue;
            double inwardX = -relative.x / horizontal;
            double inwardZ = -relative.z / horizontal;
            long nodeSeed = mix(visualSeed ^ node.surfaceBlock().asLong()
                ^ 0x52455455524E544CL);

            for (int puff = 0; puff < puffs; puff++) {
                long seed = mix(nodeSeed + puff * 0x9E3779B97F4A7C15L);
                float radius = (0.24F + unit(seed, 0) * 0.58F)
                    * (0.92F + sqrtScale * 0.13F);
                double inward = unit(seed, 1) * (0.65 + 0.32 * sqrtScale);
                Vec3 center = relative.add(
                    inwardX * inward + signed(seed, 2) * radius * 1.3,
                    0.08 + unit(seed, 3) * (0.36 + 0.20 * sqrtScale),
                    inwardZ * inward + signed(seed, 4) * radius * 1.3);
                int tone = 170 + Math.floorMod((int) (seed >>> 19), 62);
                float alpha = 0.46F + unit(seed, 5) * 0.30F;
                billboard(pose, buffer, center, radius,
                    unit(seed, 6) * Mth.TWO_PI,
                    tone, Math.min(238, tone + 5), Math.min(246, tone + 11),
                    alpha, basis);
            }
        }
        NODE_BUFFER.clear();
    }

    private static void billboard(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final float radius,
        final float rotation, final int red, final int green, final int blue,
        final float alpha, final Basis basis) {
        float cosine = Mth.cos(rotation);
        float sine = Mth.sin(rotation);
        float ux = cosine * radius;
        float uy = sine * radius;
        float vx = -sine * radius;
        float vy = cosine * radius;
        vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F,
            red, green, blue, alpha, basis);
        vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F,
            red, green, blue, alpha, basis);
        vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F,
            red, green, blue, alpha, basis);
        vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F,
            red, green, blue, alpha, basis);
    }

    private static void vertex(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final float x,
        final float y, final float u, final float v, final int red,
        final int green, final int blue, final float alpha, final Basis basis) {
        float ox = basis.right.x * x + basis.up.x * y;
        float oy = basis.right.y * x + basis.up.y * y;
        float oz = basis.right.z * x + basis.up.z * y;
        buffer.addVertex(pose, (float) center.x + ox,
                (float) center.y + oy, (float) center.z + oz)
            .setColor(red, green, blue,
                Mth.clamp((int) (alpha * 255.0F), 0, 255))
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(0xA000A0)
            .setNormal(pose, basis.normal.x, basis.normal.y, basis.normal.z);
    }

    private record Basis(Vector3f right, Vector3f up, Vector3f normal) {
        private static Basis from(final Quaternionf camera) {
            return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera));
        }
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static float unit(final long value, final int lane) {
        long mixed = mix(value + lane * 0x9E3779B97F4A7C15L);
        return (float) ((mixed >>> 40) * 0x1.0p-24);
    }

    private static float signed(final long value, final int lane) {
        return unit(value, lane) * 2.0F - 1.0F;
    }
}
