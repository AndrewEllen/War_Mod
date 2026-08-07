package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.ClientDebrisBatchManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Sparse textured smoke puffs following real launched block trajectories. */
public final class DebrisTrailRendererV7 {
    private DebrisTrailRendererV7() { }

    public static void render(final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final ClientDebrisBatchManager.RenderSample sample,
        final int fallbackColour, final Vec3 cameraPosition,
        final Quaternionf cameraOrientation) {
        if (sample == null || sample.partIndex() != 0
            || sample.trailPositions().size() < 2) return;
        List<Vec3> trail = sample.trailPositions();
        Basis basis = Basis.from(cameraOrientation);
        long baseSeed = mix(sample.batchId().getMostSignificantBits()
            ^ sample.batchId().getLeastSignificantBits()
            ^ sample.pieceIndex() * 0x9E3779B97F4A7C15L);
        int stride = 2 + Math.floorMod((int) baseSeed, 3);
        int phase = Math.floorMod((int) (baseSeed >>> 16), stride);

        for (int point = phase; point < trail.size(); point += stride) {
            long seed = mix(baseSeed ^ point * 0xD1B54A32D192ED03L);
            float head = point / (float) Math.max(1, trail.size() - 1);
            int puffs = unit(seed, 0) < 0.30F ? 2 : 1;
            for (int puff = 0; puff < puffs; puff++) {
                long puffSeed = mix(seed + puff * 0x94D049BB133111EBL);
                float spread = (0.10F + 0.34F * head)
                    * Math.max(0.82F, sample.scale());
                Vec3 center = trail.get(point).subtract(cameraPosition).add(
                    signed(puffSeed, 1) * spread,
                    signed(puffSeed, 2) * spread * 0.72F,
                    signed(puffSeed, 3) * spread);
                float radius = (0.16F + unit(puffSeed, 4) * 0.54F)
                    * Math.max(0.80F, sample.scale())
                    * (0.78F + head * 0.48F);
                int selector = Math.floorMod((int) (puffSeed >>> 11), 100);
                int tone;
                if (selector < 12) {
                    tone = 72 + Math.floorMod((int) (puffSeed >>> 24), 62);
                } else if (selector < 34) {
                    tone = 158 + Math.floorMod((int) (puffSeed >>> 24), 48);
                } else {
                    tone = 210 + Math.floorMod((int) (puffSeed >>> 24), 42);
                }
                if (fallbackColour != 0 && selector < 7) {
                    int base = ((fallbackColour >>> 16 & 255)
                        + (fallbackColour >>> 8 & 255)
                        + (fallbackColour & 255)) / 3;
                    tone = Mth.clamp((tone + base) / 2, 48, 248);
                }
                float alpha = (0.34F + 0.48F * head)
                    * (0.74F + unit(puffSeed, 5) * 0.24F)
                    * (sample.onGround() ? 0.52F : 1.0F);
                billboard(pose, buffer, center, radius,
                    unit(puffSeed, 6) * Mth.TWO_PI,
                    tone, tone, Math.min(255, tone + 5), alpha, basis);
            }
        }
    }

    private static void billboard(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final float radius,
        final float rotation, final int red, final int green, final int blue,
        final float alpha, final Basis basis) {
        float cosine = Mth.cos(rotation);
        float sine = Mth.sin(rotation);
        vertex(pose, buffer, center, -radius, -radius, cosine, sine,
            0.0F, 1.0F, red, green, blue, alpha, basis);
        vertex(pose, buffer, center, -radius, radius, cosine, sine,
            0.0F, 0.0F, red, green, blue, alpha, basis);
        vertex(pose, buffer, center, radius, radius, cosine, sine,
            1.0F, 0.0F, red, green, blue, alpha, basis);
        vertex(pose, buffer, center, radius, -radius, cosine, sine,
            1.0F, 1.0F, red, green, blue, alpha, basis);
    }

    private static void vertex(final PoseStack.Pose pose,
        final VertexConsumer buffer, final Vec3 center, final float localX,
        final float localY, final float cosine, final float sine,
        final float u, final float v, final int red, final int green,
        final int blue, final float alpha, final Basis basis) {
        float rotatedX = localX * cosine - localY * sine;
        float rotatedY = localX * sine + localY * cosine;
        float offsetX = basis.right.x * rotatedX + basis.up.x * rotatedY;
        float offsetY = basis.right.y * rotatedX + basis.up.y * rotatedY;
        float offsetZ = basis.right.z * rotatedX + basis.up.z * rotatedY;
        buffer.addVertex(pose, (float) center.x + offsetX,
                (float) center.y + offsetY, (float) center.z + offsetZ)
            .setColor(red, green, blue,
                Mth.clamp((int) (alpha * 255.0F), 0, 255))
            .setUv(u, v).setOverlay(0).setLight(0xB000B0)
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
