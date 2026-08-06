package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Dense neutral terrain-following dust carried by the visual front. */
public final class GroundDustFrontRenderer {
    private GroundDustFrontRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final List<TerrainShockfrontNode> nodes, final Vec3 impactPosition, final long gameTime,
        final WarheadMesh.Lod lod, final float densityScale, final Quaternionf cameraOrientation) {
        if (nodes == null || nodes.isEmpty()) return;
        int limit = Math.round((lod == WarheadMesh.Lod.NEAR ? 3_600
            : lod == WarheadMesh.Lod.MEDIUM ? 1_800 : 680) * Mth.clamp(densityScale, 0.25F, 3.2F));
        int count = Math.min(limit, nodes.size());
        Basis basis = Basis.from(cameraOrientation);
        for (int index = 0; index < count; index++) {
            TerrainShockfrontNode node = nodes.get(index);
            long seed = mix(node.surfaceBlock().asLong());
            long start = node.emittedGameTime() == Long.MIN_VALUE ? node.readyGameTime() : node.emittedGameTime();
            double age = Math.max(0.0, gameTime - start);
            double lifetime = 20.0 + ((seed >>> 8) & 23L);
            if (age > lifetime) continue;
            double progress = age / lifetime;
            double dx = node.position().x - impactPosition.x;
            double dz = node.position().z - impactPosition.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4) continue;
            double outward = (0.55 + ((seed >>> 18) & 31L) / 16.0) * progress;
            double rise = (0.18 + ((seed >>> 27) & 31L) / 30.0) * Math.sin(progress * Math.PI * 0.82);
            Vec3 base = node.position().subtract(impactPosition)
                .add(dx / length * outward, 0.06 + rise, dz / length * outward);
            float alpha = (float) ((0.42 + ((seed >>> 12) & 7L) * 0.018)
                * Math.pow(1.0 - progress, 0.72));
            int puffs = lod == WarheadMesh.Lod.NEAR ? 5 : lod == WarheadMesh.Lod.MEDIUM ? 3 : 2;
            for (int puff = 0; puff < puffs; puff++) {
                long puffSeed = mix(seed + puff * 0x9E3779B97F4A7C15L);
                boolean warm = puff == 0 && progress < 0.24 && ((puffSeed >>> 44) & 15L) == 0L;
                int tone = 176 + (int) ((puffSeed >>> 35) & 35L);
                int red = warm ? 255 : tone;
                int green = warm ? 204 : Math.min(216, tone + 2);
                int blue = warm ? 132 : Math.min(222, tone + 6);
                float radius = (0.075F + unit(puffSeed, 0) * 0.16F)
                    * (0.88F + (float) progress * 0.42F);
                float rotation = unit(puffSeed, 1) * Mth.TWO_PI;
                Vec3 center = base.add(signed(puffSeed, 2) * radius * 1.25,
                    unit(puffSeed, 3) * radius * 0.72, signed(puffSeed, 4) * radius * 1.25);
                addBillboard(pose, buffer, center, radius, rotation, red, green, blue,
                    alpha * (warm ? 0.76F : 0.68F + unit(puffSeed, 5) * 0.28F), basis);
            }
        }
    }

    private static void addBillboard(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float radius, final float rotation, final int red, final int green,
        final int blue, final float alpha, final Basis basis) {
        float cosine = Mth.cos(rotation), sine = Mth.sin(rotation);
        float ux = cosine * radius, uy = sine * radius;
        float vx = -sine * radius, vy = cosine * radius;
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F, red, green, blue, a, basis);
        vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F, red, green, blue, a, basis);
        vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F, red, green, blue, a, basis);
        vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F, red, green, blue, a, basis);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 center,
        final float x, final float y, final float u, final float v, final int red, final int green,
        final int blue, final int alpha, final Basis basis) {
        float ox = basis.right.x * x + basis.up.x * y;
        float oy = basis.right.y * x + basis.up.y * y;
        float oz = basis.right.z * x + basis.up.z * y;
        buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy, (float) center.z + oz)
            .setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0).setLight(0xB000B0)
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
