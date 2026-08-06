package com.andye.warmod.warhead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Stable analytical supplement for the nuclear cloud's crater base and central
 * feed. It contains no retained timeline that can rewind, so it cannot snap
 * back to the start when merged cloud membership changes.
 */
public final class NuclearCentralColumnRenderer {
    private NuclearCentralColumnRenderer() { }

    public static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final long seed,
        final WarheadMesh.Lod lod, final boolean hotPass, final Quaternionf camera) {
        if (age < 0.0 || age >= 920.0) return;
        float scale = Mth.clamp(visualScale, 1.4F, 4.2F);
        float budget = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 3.0F),
            0.45F, 1.42F);
        int baseCount = switch (lod) {
            case NEAR -> 980;
            case MEDIUM -> 500;
            case FAR -> 210;
        };
        int count = Math.round(baseCount * budget);
        float craterRadius = 12.0F + 13.0F * scale;
        float craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
        float columnHeight = 28.0F + 13.0F * scale;
        float columnRadius = 3.6F + 2.2F * scale;
        Basis basis = Basis.from(camera);
        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x4E55434C45415246L
                ^ index * 0x9E3779B97F4A7C15L);
            float spawn = unit(random, 0) * 520.0F;
            float localAge = (float) age - spawn;
            if (localAge < 0.0F) continue;
            float life = 310.0F + unit(random, 1) * 390.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;
            float heat = Mth.clamp(1.0F - progress * 1.12F
                - Math.max(0.0F, (float) age - 520.0F) / 520.0F, 0.0F, 1.0F);
            boolean hot = heat >= 0.62F;
            if (hot != hotPass || heat < 0.18F) continue;
            float angle = unit(random, 2) * Mth.TWO_PI
                + localAge * signed(random, 3) * 0.008F;
            float radialFraction = Mth.sqrt(unit(random, 4));
            float narrowing = Mth.lerp(Mth.clamp((float) age / 760.0F, 0.0F, 1.0F),
                1.0F, 0.58F);
            float radial = radialFraction * columnRadius * narrowing
                * (0.72F + 0.28F * Mth.sin(progress * Mth.PI));
            float rise = progress * columnHeight;
            float wobble = Mth.sin(localAge * 0.055F + unit(random, 5) * Mth.TWO_PI)
                * columnRadius * 0.12F;
            float px = Mth.cos(angle) * radial + Mth.cos(angle + Mth.HALF_PI) * wobble;
            float pz = Mth.sin(angle) * radial + Mth.sin(angle + Mth.HALF_PI) * wobble;
            float py = craterFloor + 1.0F + rise;
            float edge = radialFraction;
            float radius = Mth.lerp(edge, 2.15F + 0.34F * scale,
                0.72F + 0.16F * scale) * (0.82F + unit(random, 6) * 0.34F);
            float remaining = Mth.clamp(1.0F - progress, 0.0F, 1.0F);
            float alpha = (hotPass ? 0.92F : 0.76F)
                * smoothstep(Mth.clamp(localAge / 5.0F, 0.0F, 1.0F))
                * (float) Math.pow(remaining, 0.55F);
            Colour colour = fireColour(heat);
            billboard(pose, buffer, px, py, pz, radius,
                unit(random, 7) * Mth.TWO_PI + localAge * signed(random, 8) * 0.009F,
                colour.red, colour.green, colour.blue, alpha, 0xF000F0, basis);
        }
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (age < 0.0 || age >= 2_300.0) return;
        float scale = Mth.clamp(visualScale, 1.4F, 4.2F);
        float budget = Mth.clamp(
            (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 3.0F),
            0.45F, 1.42F);
        int baseCount = switch (lod) {
            case NEAR -> 1_360;
            case MEDIUM -> 680;
            case FAR -> 280;
        };
        int count = Math.round(baseCount * budget);
        float craterRadius = 12.0F + 13.0F * scale;
        float craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
        float columnHeight = 36.0F + 18.0F * scale;
        float columnRadius = 5.0F + 2.6F * scale;
        Basis basis = Basis.from(camera);
        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x4E55434C534D4B34L
                ^ index * 0xD1B54A32D192ED03L);
            boolean baseCloud = unit(random, 0) < 0.34F;
            float spawn = unit(random, 1) * (baseCloud ? 820.0F : 1_260.0F);
            float localAge = (float) age - spawn;
            if (localAge < 0.0F) continue;
            float life = baseCloud
                ? 760.0F + unit(random, 2) * 880.0F
                : 1_080.0F + unit(random, 2) * 1_020.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;
            float angle = unit(random, 3) * Mth.TWO_PI
                + localAge * signed(random, 4) * (baseCloud ? 0.0035F : 0.0065F);
            float radialFraction = Mth.sqrt(unit(random, 5));
            float px;
            float py;
            float pz;
            float particleRadius;
            if (baseCloud) {
                float targetRadius = craterRadius * (0.52F + unit(random, 6) * 0.58F);
                float radial = targetRadius
                    + Mth.sin(progress * Mth.TWO_PI + unit(random, 7) * Mth.TWO_PI)
                        * craterRadius * 0.08F;
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                py = craterFloor * (0.02F + unit(random, 8) * 0.16F)
                    + 0.2F + unit(random, 9) * 4.2F;
                particleRadius = 1.8F + unit(random, 10) * 2.8F;
            } else {
                float narrowing = Mth.lerp(Mth.clamp((float) age / 1_400.0F, 0.0F, 1.0F),
                    1.0F, 0.64F);
                float radial = radialFraction * columnRadius * narrowing;
                float rise = progress * columnHeight;
                float curl = Mth.sin(localAge * 0.043F + unit(random, 6) * Mth.TWO_PI)
                    * columnRadius * 0.16F;
                px = Mth.cos(angle) * radial + Mth.cos(angle + Mth.HALF_PI) * curl;
                pz = Mth.sin(angle) * radial + Mth.sin(angle + Mth.HALF_PI) * curl;
                py = craterFloor + 0.8F + rise;
                particleRadius = Mth.lerp(radialFraction,
                    2.9F + 0.42F * scale, 1.05F + 0.20F * scale)
                    * (0.84F + unit(random, 7) * 0.34F);
            }
            int tone = smokeTone(random, baseCloud, progress);
            /*
             * The central feed remains visually opaque through most of its life;
             * only the final hidden tail fades. This avoids blue sky/water holes.
             */
            float tail = progress < 0.88F ? 1.0F
                : smoothstep(Mth.clamp((1.0F - progress) / 0.12F, 0.0F, 1.0F));
            float alpha = (baseCloud ? 0.80F : 0.91F)
                * smoothstep(Mth.clamp(localAge / 7.0F, 0.0F, 1.0F)) * tail;
            billboard(pose, buffer, px, py, pz, particleRadius,
                unit(random, 11) * Mth.TWO_PI + localAge * signed(random, 12) * 0.004F,
                tone, Math.min(142, tone + 3), Math.min(148, tone + 8),
                alpha, baseCloud ? 0x880088 : 0x900090, basis);
        }
    }

    private static int smokeTone(final long random, final boolean baseCloud,
        final float progress) {
        int variation = Math.floorMod((int) (random >>> 19), 52);
        int base = baseCloud ? 58 : 44;
        return Mth.clamp(base + variation - (int) (progress * 22.0F), 25, 112);
    }

    private static Colour fireColour(final float heat) {
        if (heat > 0.86F) {
            float t = (heat - 0.86F) / 0.14F;
            return new Colour(255, Mth.lerpInt(t, 214, 255), Mth.lerpInt(t, 54, 214));
        }
        if (heat > 0.48F) {
            float t = (heat - 0.48F) / 0.38F;
            return new Colour(255, Mth.lerpInt(t, 78, 214), Mth.lerpInt(t, 10, 54));
        }
        float t = heat / 0.48F;
        return new Colour(Mth.lerpInt(t, 82, 255), Mth.lerpInt(t, 54, 78),
            Mth.lerpInt(t, 48, 10));
    }

    private record Colour(int red, int green, int blue) { }

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
