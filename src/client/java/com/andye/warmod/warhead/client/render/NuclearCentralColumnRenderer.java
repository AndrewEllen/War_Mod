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
        if (age < 0.0 || age >= 2_400.0) return;
        float scale = Mth.clamp(visualScale, 1.4F, 4.2F);
        float budget = densityMultiplier();
        int baseCount = switch (lod) {
            case NEAR -> 4_350;
            case MEDIUM -> 2_100;
            case FAR -> 820;
        };
        int count = Math.max(256, Math.round(baseCount * budget));
        float craterRadius = 12.0F + 13.0F * scale;
        float craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
        float columnHeight = columnHeight(age, scale);
        float columnRadius = (4.0F + 2.25F * scale) * 2.45F;
        Basis basis = Basis.from(camera);

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x4E55434C45415246L
                ^ index * 0x9E3779B97F4A7C15L);
            boolean craterBase = unit(random, 0) < 0.38F;
            float spawn = unit(random, 1) * (craterBase ? 1_480.0F : 1_720.0F);
            float localAge = (float) age - spawn;
            if (localAge < 0.0F) continue;
            float life = craterBase
                ? 920.0F + unit(random, 2) * 820.0F
                : 1_020.0F + unit(random, 2) * 1_020.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;
            float lateCooling = Math.max(0.0F, ((float) age - 1_650.0F) / 950.0F);
            float heat = Mth.clamp(1.10F - progress * 0.88F - lateCooling * 0.52F
                + signed(random, 3) * 0.10F, 0.0F, 1.0F);
            boolean hot = heat >= 0.61F;
            if (hot != hotPass || heat < 0.10F) continue;

            float px;
            float py;
            float pz;
            float particleRadius;
            if (craterBase) {
                float angle = unit(random, 4) * Mth.TWO_PI
                    + localAge * signed(random, 5) * 0.0028F;
                float radialFraction = Mth.sqrt(unit(random, 6));
                float radial = radialFraction * craterRadius * 0.96F;
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                py = Mth.lerp(unit(random, 7), craterFloor + 0.18F, 3.4F)
                    + Mth.sin(localAge * 0.041F
                        + unit(random, 8) * Mth.TWO_PI) * 0.70F;
                particleRadius = Mth.lerp(radialFraction,
                    3.8F + 0.50F * scale, 1.35F + 0.24F * scale)
                    * (0.80F + unit(random, 9) * 0.42F);
            } else {
                float angle = unit(random, 4) * Mth.TWO_PI
                    + localAge * signed(random, 5) * 0.0072F;
                float radialFraction = Mth.sqrt(unit(random, 6));
                float narrowing = Mth.lerp(Mth.clamp((float) age / 1_850.0F,
                    0.0F, 1.0F), 1.0F, 0.68F);
                float capBlend = smoothstep(Mth.clamp((progress - 0.72F) / 0.28F,
                    0.0F, 1.0F));
                float radial = radialFraction * columnRadius * narrowing
                    * (0.66F + 0.34F * Mth.sin(progress * Mth.PI));
                radial += capBlend * columnRadius * (0.45F + unit(random, 7) * 1.10F);
                float riseProgress = Math.min(progress, 0.94F);
                float rise = riseProgress * columnHeight;
                float wobble = Mth.sin(localAge * 0.049F
                    + unit(random, 8) * Mth.TWO_PI) * columnRadius * 0.10F;
                px = Mth.cos(angle) * radial
                    + Mth.cos(angle + Mth.HALF_PI) * wobble;
                pz = Mth.sin(angle) * radial
                    + Mth.sin(angle + Mth.HALF_PI) * wobble;
                py = craterFloor + 0.18F + rise
                    - capBlend * unit(random, 9) * 5.0F;
                particleRadius = Mth.lerp(radialFraction,
                    3.1F + 0.44F * scale, 0.94F + 0.19F * scale)
                    * (0.80F + unit(random, 10) * 0.42F)
                    * (1.0F + capBlend * 0.18F);
            }
            float remaining = Mth.clamp(1.0F - progress, 0.0F, 1.0F);
            float alpha = (hotPass ? 0.96F : 0.86F)
                * smoothstep(Mth.clamp(localAge / 6.0F, 0.0F, 1.0F))
                * (float) Math.pow(remaining, 0.40F);
            Colour colour = fireColour(heat);
            billboard(pose, buffer, px, py, pz, particleRadius,
                unit(random, 11) * Mth.TWO_PI
                    + localAge * signed(random, 12) * 0.006F,
                colour.red, colour.green, colour.blue, alpha, 0xF000F0, basis);
        }
    }

    public static void renderSmoke(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final long seed, final WarheadMesh.Lod lod,
        final Quaternionf camera) {
        if (age < 0.0 || age >= 3_300.0) return;
        float scale = Mth.clamp(visualScale, 1.4F, 4.2F);
        float budget = densityMultiplier();
        int baseCount = switch (lod) {
            case NEAR -> 5_900;
            case MEDIUM -> 2_850;
            case FAR -> 1_080;
        };
        int count = Math.max(320, Math.round(baseCount * budget));
        float craterRadius = 12.0F + 13.0F * scale;
        float craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
        float columnHeight = columnHeight(age, scale);
        float columnRadius = (5.4F + 2.75F * scale) * 2.35F;
        float systemFade = age < 2_650.0 ? 1.0F
            : smoothstep(Mth.clamp((float) ((3_300.0 - age) / 650.0),
                0.0F, 1.0F));
        Basis basis = Basis.from(camera);

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x4E55434C534D4B36L
                ^ index * 0xD1B54A32D192ED03L);
            boolean baseCloud = unit(random, 0) < 0.42F;
            float spawn = unit(random, 1) * (baseCloud ? 1_420.0F : 2_120.0F);
            float localAge = (float) age - spawn;
            if (localAge < 0.0F) continue;
            float life = baseCloud
                ? 1_080.0F + unit(random, 2) * 1_160.0F
                : 1_520.0F + unit(random, 2) * 1_420.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;
            float angle = unit(random, 3) * Mth.TWO_PI
                + localAge * signed(random, 4)
                    * (baseCloud ? 0.0030F : 0.0060F);
            float radialFraction = Mth.sqrt(unit(random, 5));
            float px;
            float py;
            float pz;
            float particleRadius;
            if (baseCloud) {
                float targetRadius = craterRadius * (0.24F + unit(random, 6) * 0.96F);
                float radial = targetRadius
                    + Mth.sin(progress * Mth.TWO_PI
                        + unit(random, 7) * Mth.TWO_PI) * craterRadius * 0.075F;
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                float initialHeight = 2.2F + unit(random, 8) * 9.5F;
                float settle = smoothstep(Mth.clamp(localAge / 190.0F,
                    0.0F, 1.0F));
                float groundHeight = Mth.lerp(unit(random, 9),
                    craterFloor + 0.15F, 1.4F);
                py = Mth.lerp(settle, initialHeight, groundHeight);
                particleRadius = 2.2F + unit(random, 10) * 3.9F;
            } else {
                float narrowing = Mth.lerp(Mth.clamp((float) age / 2_150.0F,
                    0.0F, 1.0F), 1.0F, 0.70F);
                float capBlend = smoothstep(Mth.clamp((progress - 0.70F) / 0.30F,
                    0.0F, 1.0F));
                float radial = radialFraction * columnRadius * narrowing;
                radial += capBlend * columnRadius * (0.35F + unit(random, 6) * 0.92F);
                float riseProgress = Math.min(progress, 0.94F);
                float rise = riseProgress * columnHeight;
                float curl = Mth.sin(localAge * 0.043F
                    + unit(random, 7) * Mth.TWO_PI) * columnRadius * 0.11F;
                px = Mth.cos(angle) * radial
                    + Mth.cos(angle + Mth.HALF_PI) * curl;
                pz = Mth.sin(angle) * radial
                    + Mth.sin(angle + Mth.HALF_PI) * curl;
                py = craterFloor + 0.18F + rise
                    - capBlend * unit(random, 8) * 6.0F;
                particleRadius = Mth.lerp(radialFraction,
                    3.8F + 0.54F * scale, 1.22F + 0.24F * scale)
                    * (0.82F + unit(random, 9) * 0.42F)
                    * (1.0F + capBlend * 0.20F);
            }
            int tone = smokeTone(random, baseCloud, progress);
            float tail = progress < 0.90F ? 1.0F
                : smoothstep(Mth.clamp((1.0F - progress) / 0.10F,
                    0.0F, 1.0F));
            float alpha = (baseCloud ? 0.86F : 0.95F)
                * smoothstep(Mth.clamp(localAge / 7.0F, 0.0F, 1.0F))
                * tail * systemFade;
            billboard(pose, buffer, px, py, pz, particleRadius,
                unit(random, 10) * Mth.TWO_PI
                    + localAge * signed(random, 11) * 0.0038F,
                tone, tone, tone, alpha,
                baseCloud ? 0x880088 : 0x900090, basis);
        }
    }

    private static float columnHeight(final double age, final float scale) {
        float growing = 58.0F + 18.0F * scale
            + Mth.sqrt((float) Math.max(0.0, age)) * 1.55F;
        float cap = 142.0F + 17.0F * scale;
        return Math.min(growing, cap);
    }

    private static float densityMultiplier() {
        return Mth.clamp((float) Math.sqrt(
            WarheadRenderSettings.particleBudgetMultiplier() / 6.0F),
            0.35F, 8.0F);
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
        return new Colour(Mth.lerpInt(t, 102, 255),
            Mth.lerpInt(t, 82, 78), Mth.lerpInt(t, 72, 10));
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
