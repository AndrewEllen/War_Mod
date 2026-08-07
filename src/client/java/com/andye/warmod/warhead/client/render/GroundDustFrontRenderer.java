package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.TerrainShockfrontNode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Terrain-following dust and explosion artwork emitted from sampled surface blocks. */
public final class GroundDustFrontRenderer {
    private GroundDustFrontRenderer() { }

    public static void render(final PoseStack.Pose pose, final VertexConsumer buffer,
        final List<TerrainShockfrontNode> nodes, final Vec3 impactPosition, final long gameTime,
        final WarheadMesh.Lod lod, final float densityScale, final Quaternionf cameraOrientation) {
        if (nodes == null || nodes.isEmpty()) return;
        /* Overlapping impacts share one expensive ground field per frame. */
        if (!WarheadParticleVisibility.claimWorldClusterOnce(impactPosition,
            WarheadParticleVisibility.CHANNEL_GROUND_DUST, 24.0)) return;

        float budgetScale = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 10.0F),
            0.45F, 3.0F);
        int limit = Math.round((lod == WarheadMesh.Lod.NEAR ? 2_400
            : lod == WarheadMesh.Lod.MEDIUM ? 1_200 : 480)
            * Mth.clamp(densityScale, 0.25F, 3.2F) * budgetScale);
        int count = Math.min(limit, nodes.size());
        Basis basis = Basis.from(cameraOrientation);
        for (int index = 0; index < count; index++) {
            TerrainShockfrontNode node = nodes.get(index);
            long seed = mix(node.surfaceBlock().asLong());
            long start = node.emittedGameTime() == Long.MIN_VALUE
                ? node.readyGameTime() : node.emittedGameTime();
            double age = Math.max(0.0, gameTime - start);
            double motionLifetime = 132.0 + ((seed >>> 8) & 111L);
            double lifetime = motionLifetime * 2.0;
            if (age > lifetime) continue;
            double motionProgress = Mth.clamp(age / motionLifetime, 0.0, 1.0);
            double lifeProgress = Mth.clamp(age / lifetime, 0.0, 1.0);
            double dx = node.position().x - impactPosition.x;
            double dz = node.position().z - impactPosition.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4) continue;

            double settle = smoothstep(Mth.clamp((float) ((age - motionLifetime)
                / Math.max(1.0, motionLifetime * 0.24)), 0.0F, 1.0F));
            double outward = (0.24 + ((seed >>> 18) & 31L) / 36.0) * motionProgress;
            double rise = (0.18 + ((seed >>> 27) & 31L) / 25.0)
                * Math.sin(motionProgress * Math.PI * 0.88) * (1.0 - settle);
            Vec3 base = node.position().subtract(impactPosition)
                .add(dx / length * outward, 0.06 + rise, dz / length * outward);
            float fadeStart = 0.70F + unit(seed, 7) * 0.22F;
            float fade = lifeProgress < fadeStart ? 1.0F
                : smoothstep(Mth.clamp((float) ((1.0 - lifeProgress)
                    / Math.max(0.03, 1.0 - fadeStart)), 0.0F, 1.0F));
            float alpha = (0.58F + unit(seed, 8) * 0.24F) * fade;
            int puffs = lod == WarheadMesh.Lod.NEAR ? 4
                : lod == WarheadMesh.Lod.MEDIUM ? 3 : 2;
            for (int puff = 0; puff < puffs; puff++) {
                long puffSeed = mix(seed + puff * 0x9E3779B97F4A7C15L);
                int selector = Math.floorMod((int) puffSeed, 100);
                int red;
                int green;
                int blue;
                if (selector < 28) {
                    int pale = 206 + Math.floorMod((int) (puffSeed >>> 17), 43);
                    red = pale;
                    green = Math.min(252, pale + 2);
                    blue = Math.min(255, pale + 7);
                } else if (selector < 66) {
                    int neutral = 158 + Math.floorMod((int) (puffSeed >>> 17), 45);
                    red = neutral;
                    green = Math.min(211, neutral + 3);
                    blue = Math.min(219, neutral + 8);
                } else {
                    int earth = 128 + Math.floorMod((int) (puffSeed >>> 17), 48);
                    red = earth;
                    green = Mth.clamp(earth - 13, 105, 172);
                    blue = Mth.clamp(earth - 28, 78, 154);
                }
                /* Fewer billboards, enlarged to preserve the previous visual coverage. */
                float radius = (0.22F + unit(puffSeed, 0) * 0.58F)
                    * (1.06F + (float) motionProgress * 0.74F);
                float rotation = unit(puffSeed, 1) * Mth.TWO_PI;
                Vec3 center = base.add(signed(puffSeed, 2) * radius * 1.35,
                    unit(puffSeed, 3) * radius * 0.75 * (1.0 - settle),
                    signed(puffSeed, 4) * radius * 1.35);
                addBillboard(pose, buffer, center, radius, rotation, red, green, blue,
                    alpha * (0.70F + unit(puffSeed, 5) * 0.28F), basis);
            }
        }
    }

    /**
     * Minecraft explosion artwork reproduced inside War Mod's custom renderer.
     * Several short staggered puffs approximate the old EXPLOSION_EMITTER burst
     * without instantiating vanilla particles or bypassing War Mod culling.
     */
    public static void renderExplosionFlecks(final PoseStack.Pose pose,
        final VertexConsumer buffer, final List<TerrainShockfrontNode> nodes,
        final Vec3 impactPosition, final long gameTime, final WarheadMesh.Lod lod,
        final float densityScale, final Quaternionf cameraOrientation) {
        if (nodes == null || nodes.isEmpty()) return;
        if (!WarheadParticleVisibility.claimWorldClusterOnce(impactPosition,
            WarheadParticleVisibility.CHANNEL_GROUND_EXPLOSION, 24.0)) return;

        float budgetScale = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 10.0F),
            0.45F, 2.6F);
        int limit = Math.round((lod == WarheadMesh.Lod.NEAR ? 1_800
            : lod == WarheadMesh.Lod.MEDIUM ? 900 : 360)
            * Mth.clamp(densityScale, 0.25F, 3.2F) * budgetScale);
        int count = Math.min(limit, nodes.size());
        Basis basis = Basis.from(cameraOrientation);
        int divisor = lod == WarheadMesh.Lod.NEAR ? 2
            : lod == WarheadMesh.Lod.MEDIUM ? 3 : 5;
        int bursts = lod == WarheadMesh.Lod.NEAR ? 3
            : lod == WarheadMesh.Lod.MEDIUM ? 2 : 1;

        for (int index = 0; index < count; index++) {
            TerrainShockfrontNode node = nodes.get(index);
            long seed = mix(node.surfaceBlock().asLong() ^ 0x4558504C4F444537L);
            if (Math.floorMod((int) seed, divisor) != 0) continue;
            long start = node.emittedGameTime() == Long.MIN_VALUE
                ? node.readyGameTime() : node.emittedGameTime();
            double nodeAge = Math.max(0.0, gameTime - start);
            double dx = node.position().x - impactPosition.x;
            double dz = node.position().z - impactPosition.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4) continue;

            for (int burst = 0; burst < bursts; burst++) {
                long burstSeed = mix(seed + burst * 0xD1B54A32D192ED03L);
                double onset = burst * 4.0 + unit(burstSeed, 0) * 2.0;
                double age = nodeAge - onset;
                double lifetime = 18.0 + unit(burstSeed, 1) * 16.0;
                if (age < 0.0 || age > lifetime) continue;
                double progress = age / lifetime;
                int puffs = lod == WarheadMesh.Lod.NEAR ? 2 : 1;
                for (int puff = 0; puff < puffs; puff++) {
                    long puffSeed = mix(burstSeed + puff * 0x94D049BB133111EBL);
                    double outward = (0.12 + unit(puffSeed, 2) * 0.72) * progress;
                    Vec3 center = node.position().subtract(impactPosition).add(
                        dx / length * outward + signed(puffSeed, 3) * 0.52,
                        0.12 + Math.sin(progress * Math.PI)
                            * (0.28 + unit(puffSeed, 4) * 0.88),
                        dz / length * outward + signed(puffSeed, 5) * 0.52);
                    float radius = (0.48F + unit(puffSeed, 6) * 1.18F)
                        * (0.92F + (float) progress * 0.46F);
                    float alpha = (float) (0.98 * Math.pow(1.0 - progress, 0.46));
                    int green = 196 + Math.floorMod((int) (puffSeed >>> 21), 58);
                    int blue = 86 + Math.floorMod((int) (puffSeed >>> 34), 118);
                    addBillboard(pose, buffer, center, radius,
                        unit(puffSeed, 7) * Mth.TWO_PI,
                        255, Math.min(255, green), Math.min(226, blue), alpha, basis);
                }
            }
        }
    }

    private static void addBillboard(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float radius, final float rotation, final int red, final int green,
        final int blue, final float alpha, final Basis basis) {
        if (!WarheadParticleVisibility.visible(pose, (float) center.x, (float) center.y,
            (float) center.z, radius)) return;
        float cosine = Mth.cos(rotation), sine = Mth.sin(rotation);
        float ux = cosine * radius, uy = sine * radius;
        float vx = -sine * radius, vy = cosine * radius;
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        vertex(pose, buffer, center, -ux - vx, -uy - vy, 0.0F, 1.0F,
            red, green, blue, a, basis);
        vertex(pose, buffer, center, -ux + vx, -uy + vy, 0.0F, 0.0F,
            red, green, blue, a, basis);
        vertex(pose, buffer, center, ux + vx, uy + vy, 1.0F, 0.0F,
            red, green, blue, a, basis);
        vertex(pose, buffer, center, ux - vx, uy - vy, 1.0F, 1.0F,
            red, green, blue, a, basis);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final Vec3 center, final float x, final float y, final float u, final float v,
        final int red, final int green, final int blue, final int alpha, final Basis basis) {
        float ox = basis.right.x * x + basis.up.x * y;
        float oy = basis.right.y * x + basis.up.y * y;
        float oz = basis.right.z * x + basis.up.z * y;
        buffer.addVertex(pose, (float) center.x + ox, (float) center.y + oy,
                (float) center.z + oz)
            .setColor(red, green, blue, alpha).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
            .setNormal(pose, basis.normal.x, basis.normal.y, basis.normal.z);
    }

    private record Basis(Vector3f right, Vector3f up, Vector3f normal) {
        private static Basis from(final Quaternionf camera) {
            return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera));
        }
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
