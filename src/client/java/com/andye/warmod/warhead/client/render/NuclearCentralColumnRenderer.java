package com.andye.warmod.warhead.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Stable analytical crater fire, ground skirt and full-height nuclear feed column. */
public final class NuclearCentralColumnRenderer {
    private NuclearCentralColumnRenderer() { }

    public static void renderFire(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final long seed, final WarheadMesh.Lod lod, final boolean hotPass,
        final Quaternionf camera) {
        if (age < 0.0 || age >= 1_360.0) return;
        float scale = Mth.clamp(visualScale, 1.4F, 4.2F);
        float budget = densityMultiplier();
        int baseCount = switch (lod) {
            case NEAR -> 1_520;
            case MEDIUM -> 760;
            case FAR -> 310;
        };
        int count = Math.max(96, Math.round(baseCount * budget));
        float craterRadius = 12.0F + 13.0F * scale;
        float craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
        float columnHeight = 70.0F + 22.0F * scale
            + Mth.sqrt((float) Math.max(0.0, age)) * 2.7F;
        float columnRadius = 4.0F + 2.25F * scale;
        Basis basis = Basis.from(camera);

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x4E55434C45415246L
                ^ index * 0x9E3779B97F4A7C15L);
            boolean craterBase = unit(random, 0) < 0.33F;
            float spawn = unit(random, 1) * (craterBase ? 720.0F : 940.0F);
            float localAge = (float) age - spawn;
            if (localAge < 0.0F) continue;
            float life = craterBase
                ? 520.0F + unit(random, 2) * 470.0F
                : 650.0F + unit(random, 2) * 560.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;
            float lateCooling = Math.max(0.0F, ((float) age - 820.0F) / 620.0F);
            float heat = Mth.clamp(1.08F - progress * 1.02F - lateCooling * 0.58F
                + signed(random, 3) * 0.08F, 0.0F, 1.0F);
            boolean hot = heat >= 0.62F;
            if (hot != hotPass || heat < 0.13F) continue;

            float px;
            float py;
            float pz;
            float particleRadius;
            if (craterBase) {
                float angle = unit(random, 4) * Mth.TWO_PI
                    + localAge * signed(random, 5) * 0.0028F;
                float radialFraction = Mth.sqrt(unit(random, 6));
                float radial = radialFraction * craterRadius * 0.90F;
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                py = Mth.lerp(unit(random, 7), craterFloor + 0.25F, 2.6F)
                    + Mth.sin(localAge * 0.041F + unit(random, 8) * Mth.TWO_PI)
                        * 0.55F;
                particleRadius = Mth.lerp(radialFraction,
                    3.2F + 0.42F * scale, 1.25F + 0.22F * scale)
                    * (0.80F + unit(random, 9) * 0.38F);
            } else {
                float angle = unit(random, 4) * Mth.TWO_PI
                    + localAge * signed(random, 5) * 0.0075F;
                float radialFraction = Mth.sqrt(unit(random, 6));
                float narrowing = Mth.lerp(Mth.clamp((float) age / 1_100.0F,
                    0.0F, 1.0F), 1.0F, 0.56F);
                float radial = radialFraction * columnRadius * narrowing
                    * (0.70F + 0.30F * Mth.sin(progress * Mth.PI));
                float rise = progress * columnHeight;
                float wobble = Mth.sin(localAge * 0.052F
                    + unit(random, 7) * Mth.TWO_PI) * columnRadius * 0.15F;
                px = Mth.cos(angle) * radial
                    + Mth.cos(angle + Mth.HALF_PI) * wobble;
                pz = Mth.sin(angle) * radial
                    + Mth.sin(angle + Mth.HALF_PI) * wobble;
                py = craterFloor + 0.25F + rise;
                particleRadius = Mth.lerp(radialFraction,
                    2.55F + 0.38F * scale, 0.82F + 0.17F * scale)
                    * (0.80F + unit(random, 8) * 0.38F);
            }
            float remaining = Mth.clamp(1.0F - progress, 0.0F, 1.0F);
            float alpha = (hotPass ? 0.94F : 0.84F)
                * smoothstep(Mth.clamp(localAge / 6.0F, 0.0F, 1.0F))
                * (float) Math.pow(remaining, 0.48F);
            Colour colour = fireColour(heat);
            billboard(pose, buffer, px, py, pz, particleRadius,
                unit(random, 10) * Mth.TWO_PI
                    + localAge * signed(random, 11) * 0.007F,
                colour.red, colour.green, colour.blue, alpha, 0xF000F0, basis);
        }
    }

    public static void renderSmoke(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final long seed, final WarheadMesh.Lod lod,
        final Quaternionf camera) {
        if (age < 0.0 || age >= 2_650.0) return;
        float scale = Mth.clamp(visualScale, 1.4F, 4.2F);
        float budget = densityMultiplier();
        int baseCount = switch (lod) {
            case NEAR -> 2_100;
            case MEDIUM -> 1_020;
            case FAR -> 420;
        };
        int count = Math.max(128, Math.round(baseCount * budget));
        float craterRadius = 12.0F + 13.0F * scale;
        float craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
        float columnHeight = 104.0F + 31.0F * scale
            + Mth.sqrt((float) Math.max(0.0, age)) * 3.1F;
        float columnRadius = 5.4F + 2.75F * scale;
        float systemFade = age < 1_900.0 ? 1.0F
            : smoothstep(Mth.clamp((float) ((2_650.0 - age) / 750.0),
                0.0F, 1.0F));
        Basis basis = Basis.from(camera);

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x4E55434C534D4B35L
                ^ index * 0xD1B54A32D192ED03L);
            boolean baseCloud = unit(random, 0) < 0.42F;
            float spawn = unit(random, 1) * (baseCloud ? 1_050.0F : 1_720.0F);
            float localAge = (float) age - spawn;
            if (localAge < 0.0F) continue;
            float life = baseCloud
                ? 850.0F + unit(random, 2) * 980.0F
                : 1_220.0F + unit(random, 2) * 1_180.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;
            float angle = unit(random, 3) * Mth.TWO_PI
                + localAge * signed(random, 4)
                    * (baseCloud ? 0.0032F : 0.0063F);
            float radialFraction = Mth.sqrt(unit(random, 5));
            float px;
            float py;
            float pz;
            float particleRadius;
            if (baseCloud) {
                float targetRadius = craterRadius * (0.30F + unit(random, 6) * 0.86F);
                float radial = targetRadius
                    + Mth.sin(progress * Mth.TWO_PI
                        + unit(random, 7) * Mth.TWO_PI) * craterRadius * 0.07F;
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                float initialHeight = 2.0F + unit(random, 8) * 8.0F;
                float settle = smoothstep(Mth.clamp(localAge / 170.0F, 0.0F, 1.0F));
                float groundHeight = Mth.lerp(unit(random, 9), craterFloor + 0.20F, 1.8F);
                py = Mth.lerp(settle, initialHeight, groundHeight);
                particleRadius = 2.0F + unit(random, 10) * 3.4F;
            } else {
                float narrowing = Mth.lerp(Mth.clamp((float) age / 1_650.0F,
                    0.0F, 1.0F), 1.0F, 0.60F);
                float radial = radialFraction * columnRadius * narrowing;
                float rise = progress * columnHeight;
                float curl = Mth.sin(localAge * 0.044F
                    + unit(random, 6) * Mth.TWO_PI) * columnRadius * 0.17F;
                px = Mth.cos(angle) * radial
                    + Mth.cos(angle + Mth.HALF_PI) * curl;
                pz = Mth.sin(angle) * radial
                    + Mth.sin(angle + Mth.HALF_PI) * curl;
                py = craterFloor + 0.25F + rise;
                particleRadius = Mth.lerp(radialFraction,
                    3.2F + 0.46F * scale, 1.14F + 0.22F * scale)
                    * (0.82F + unit(random, 7) * 0.38F);
            }
            int tone = smokeTone(random, baseCloud, progress);
            float tail = progress < 0.88F ? 1.0F
                : smoothstep(Mth.clamp((1.0F - progress) / 0.12F, 0.0F, 1.0F));
            float alpha = (baseCloud ? 0.84F : 0.94F)
                * smoothstep(Mth.clamp(localAge / 7.0F, 0.0F, 1.0F))
                * tail * systemFade;
            billboard(pose, buffer, px, py, pz, particleRadius,
                unit(random, 11) * Mth.TWO_PI
                    + localAge * signed(random, 12) * 0.004F,
                tone, Math.min(146, tone + 3), Math.min(152, tone + 8),
                alpha, baseCloud ? 0x880088 : 0x900090, basis);
        }
    }

    private static float densityMultiplier() {
        return Mth.clamp((float) Math.sqrt(
            WarheadRenderSettings.particleBudgetMultiplier() / 6.0F), 0.35F, 4.0F);
    }

    private static int smokeTone(final long random, final boolean baseCloud,
        final float progress) {
        int variation = Math.floorMod((int) (random >>> 19), 58);
        int base = baseCloud ? 54 : 38;
        return Mth.clamp(base + variation - (int) (progress * 20.0F), 24, 116);
    }

    private static Colour fireColour(final float heat) {
        if (heat > 0.86F) {
            float t = (heat - 0.86F) / 0.14F;
            return new Colour(255, Mth.lerpInt(t, 214, 255),
                Mth.lerpInt(t, 54, 214));
        }
        if (heat > 0.48F) {
            float t = (heat - 0.48F) / 0.38F;
            return new Colour(255, Mth.lerpInt(t, 78, 214),
                Mth.lerpInt(t, 10, 54));
        }
        float t = heat / 0.48F;
        return new Colour(Mth.lerpInt(t, 82, 255),
            Mth.lerpInt(t, 54, 78), Mth.lerpInt(t, 48, 10));
    }

    private record Colour(int red, int green, int blue) { }

    private record Basis(Vector3f right, Vector3f up, Vector3f normal) {
        private static Basis from(final Quaternionf camera) {
            return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera));
        }
    }

    private static void billboard(final PoseStack.Pose pose,
        final VertexConsumer buffer, final float centerX, final float centerY,
        final float centerZ, final float radius, final float rotation,
        final int red, final int green, final int blue, final float alpha,
        final int light, final Basis basis) {
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

    private static void vertex(final PoseStack.Pose pose,
        final VertexConsumer buffer, final float centerX, final float centerY,
        final float centerZ, final float localX, final float localY,
        final float cosine, final float sine, final float u, final float v,
        final int red, final int green, final int blue, final float alpha,
        final int light, final Basis basis) {
        float rotatedX = localX * cosine - localY * sine;
        float rotatedY = localX * sine + localY * cosine;
        float offsetX = basis.right.x * rotatedX + basis.up.x * rotatedY;
        float offsetY = basis.right.y * rotatedX + basis.up.y * rotatedY;
        float offsetZ = basis.right.z * rotatedX + basis.up.z * rotatedY;
        buffer.addVertex(pose, centerX + offsetX, centerY + offsetY,
                centerZ + offsetZ)
            .setColor(red, green, blue,
                Mth.clamp((int) (alpha * 255.0F), 0, 255))
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
