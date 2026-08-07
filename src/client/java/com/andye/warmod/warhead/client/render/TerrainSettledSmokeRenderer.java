package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.andye.warmod.warhead.client.TerrainShockfrontSpoke;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Ground smoke anchored to sampled terrain rather than an impact-height disc. */
public final class TerrainSettledSmokeRenderer {
    private TerrainSettledSmokeRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final List<TerrainShockfrontSpoke> spokes, final Vec3 impactPosition,
        final double age, final float visualScale, final long visualSeed,
        final WarheadMesh.Lod lod, final boolean nuclear,
        final Quaternionf cameraOrientation) {
        if (spokes == null || spokes.isEmpty() || age < 2.0) return;
        double lifetime = nuclear ? 1_650.0 : 310.0 + visualScale * 65.0;
        if (age >= lifetime) return;

        float budgetScale = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 6.0F),
            0.40F, 4.0F);
        int spokeStride = lod == WarheadMesh.Lod.NEAR ? 1
            : lod == WarheadMesh.Lod.MEDIUM ? 2 : 4;
        double craterRadius = nuclear
            ? 13.0 + visualScale * 13.2
            : 2.5 + visualScale * 13.5;
        double innerRadius = nuclear ? craterRadius * 0.42 : craterRadius * 0.22;
        double outerRadius = nuclear ? craterRadius * 1.22 : craterRadius * 1.04;
        float settleProgress = smoothstep(Mth.clamp((float) (age / (nuclear ? 130.0 : 55.0)),
            0.0F, 1.0F));
        float finalFade = age < lifetime * 0.82 ? 1.0F
            : smoothstep(Mth.clamp((float) ((lifetime - age) / (lifetime * 0.18)),
                0.0F, 1.0F));
        Basis basis = Basis.from(cameraOrientation);
        int rendered = 0;
        int limit = Math.max(128, Math.round((lod == WarheadMesh.Lod.NEAR ? 7_200
            : lod == WarheadMesh.Lod.MEDIUM ? 3_400 : 1_200) * budgetScale));

        for (int spokeIndex = 0; spokeIndex < spokes.size() && rendered < limit;
            spokeIndex += spokeStride) {
            List<TerrainShockfrontNode> nodes = spokes.get(spokeIndex).snapshotNodes();
            for (int nodeIndex = 0; nodeIndex < nodes.size() && rendered < limit; nodeIndex++) {
                TerrainShockfrontNode node = nodes.get(nodeIndex);
                if (!node.valid() || !node.visibleFromImpact()) continue;
                double radius = node.directDistance();
                if (radius < innerRadius || radius > outerRadius) continue;
                long seed = mix(visualSeed ^ node.surfaceBlock().asLong()
                    ^ (nuclear ? 0x4E554B455F424153L : 0x434F4E565F424153L));
                int stacks = nuclear ? 2 + Math.floorMod((int) seed, 4)
                    : 1 + Math.floorMod((int) seed, 3);
                stacks = Math.max(1, Math.round(stacks * Math.min(2.0F, budgetScale)));
                Vec3 base = node.position().subtract(impactPosition);
                for (int stack = 0; stack < stacks && rendered < limit; stack++) {
                    long particleSeed = mix(seed + stack * 0x9E3779B97F4A7C15L);
                    float initialHeight = nuclear
                        ? 3.0F + unit(particleSeed, 0) * 8.0F
                        : 1.2F + unit(particleSeed, 0) * 3.8F;
                    float groundedHeight = 0.10F + stack * (nuclear ? 0.52F : 0.38F);
                    float vertical = Mth.lerp(settleProgress, initialHeight, groundedHeight);
                    float swirl = (1.0F - settleProgress)
                        * (nuclear ? 1.6F : 0.65F);
                    double phase = age * (nuclear ? 0.006 : 0.013)
                        + unit(particleSeed, 1) * Mth.TWO_PI;
                    float px = (float) base.x + Mth.cos((float) phase) * swirl
                        + signed(particleSeed, 2) * 0.42F;
                    float py = (float) base.y + vertical;
                    float pz = (float) base.z + Mth.sin((float) phase) * swirl
                        + signed(particleSeed, 3) * 0.42F;
                    float particleRadius = (nuclear
                        ? 1.15F + unit(particleSeed, 4) * 2.65F
                        : 0.48F + unit(particleSeed, 4) * 1.18F)
                        * (0.88F + visualScale * (nuclear ? 0.08F : 0.15F));
                    int toneBase = nuclear ? 38 : 72;
                    int tone = Mth.clamp(toneBase
                        + Math.floorMod((int) (particleSeed >>> 18), nuclear ? 62 : 86),
                        nuclear ? 28 : 58, nuclear ? 116 : 166);
                    float alpha = (nuclear ? 0.88F : 0.80F)
                        * finalFade * (0.78F + unit(particleSeed, 5) * 0.20F);
                    billboard(pose, buffer, px, py, pz, particleRadius,
                        unit(particleSeed, 6) * Mth.TWO_PI
                            + (float) age * signed(particleSeed, 7) * 0.0028F,
                        tone, Math.min(178, tone + 3), Math.min(184, tone + 8),
                        alpha, nuclear ? 0x880088 : 0xA000A0, basis);
                    rendered++;
                }
            }
        }
    }

    private record Basis(Vector3f right, Vector3f up, Vector3f normal) {
        private static Basis from(final Quaternionf camera) {
            return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera));
        }
    }

    private static void billboard(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ,
        final float radius, final float rotation, final int red, final int green,
        final int blue, final float alpha, final int light, final Basis basis) {
        float cosine = Mth.cos(rotation);
        float sine = Mth.sin(rotation);
        vertex(pose, buffer, centerX, centerY, centerZ, -radius, -radius,
            cosine, sine, 0.0F, 1.0F, red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, -radius, radius,
            cosine, sine, 0.0F, 0.0F, red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, radius, radius,
            cosine, sine, 1.0F, 0.0F, red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, radius, -radius,
            cosine, sine, 1.0F, 1.0F, red, green, blue, alpha, light, basis);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ,
        final float localX, final float localY, final float cosine, final float sine,
        final float u, final float v, final int red, final int green, final int blue,
        final float alpha, final int light, final Basis basis) {
        float rotatedX = localX * cosine - localY * sine;
        float rotatedY = localX * sine + localY * cosine;
        float offsetX = basis.right.x * rotatedX + basis.up.x * rotatedY;
        float offsetY = basis.right.y * rotatedX + basis.up.y * rotatedY;
        float offsetZ = basis.right.z * rotatedX + basis.up.z * rotatedY;
        buffer.addVertex(pose, centerX + offsetX, centerY + offsetY, centerZ + offsetZ)
            .setColor(red, green, blue, Mth.clamp((int) (alpha * 255.0F), 0, 255))
            .setUv(u, v).setOverlay(0).setLight(light)
            .setNormal(pose, basis.normal.x, basis.normal.y, basis.normal.z);
    }

    private static float smoothstep(final float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
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
