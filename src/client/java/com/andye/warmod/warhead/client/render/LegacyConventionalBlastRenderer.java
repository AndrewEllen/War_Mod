package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.TerrainSurfaceCache;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Deterministic analytical renderer retained as a profiling baseline.
 * It intentionally rebuilds every billboard from age and seed every frame.
 */
public final class LegacyConventionalBlastRenderer {
    private static final long FIRE_SEED = 0x535447375F464952L;
    private static final long SMOKE_SEED = 0x535447375F534D4BL;
    private static final long FRONT_SEED = 0x535447375F46524EL;
    private static final long RETURN_SEED = 0x535447375F524554L;
    private static final double HE_FIRE_TOP = 4.75;

    private LegacyConventionalBlastRenderer() { }

    public static void renderFireCore(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderFire(pose, buffer, age, visualScale, seed, lod, camera, FireLayer.CORE);
    }

    public static void renderHot(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderFire(pose, buffer, age, visualScale, seed, lod, camera, FireLayer.HOT);
    }

    public static void renderCooling(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderFire(pose, buffer, age, visualScale, seed, lod, camera, FireLayer.COOL);
    }

    public static void renderSmokeCore(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderSmoke(pose, buffer, age, visualScale, seed, lod, camera, true);
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderSmoke(pose, buffer, age, visualScale, seed, lod, camera, false);
    }

    public static void renderSurfaceFront(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final double physicalRadius, final float visualScale, final long seed,
        final Vec3 impactPosition, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (age < 0.0 || physicalRadius <= 0.0) return;
        float scale = Mth.clamp(visualScale, 0.28F, 1.75F);
        double duration = WarheadVisualMath.airShockwaveDurationTicks(scale);
        if (age >= duration) return;
        int base = switch (lod) {
            case NEAR -> 640;
            case MEDIUM -> 320;
            case FAR -> 120;
        };
        int samples = Math.min(1_500, Math.max(64,
            Math.round(base * (0.72F + (float) Math.pow(scale, 1.30)))));
        float fade = (float) Math.pow(Math.max(0.0, 1.0 - age / duration), 0.58);
        Basis basis = Basis.from(camera);
        for (int index = 0; index < samples; index++) {
            long value = mix(seed ^ FRONT_SEED ^ (long) index * 0x94D049BB133111EBL);
            float angle = (index + unit(value, 0)) / samples * Mth.TWO_PI;
            float trail = unit(value, 1) * (2.2F + 5.0F * scale);
            float radial = (float) Math.max(0.0, physicalRadius - trail);
            float localX = Mth.cos(angle) * radial;
            float localZ = Mth.sin(angle) * radial;
            float height = terrainHeight(impactPosition, localX, localZ,
                0.08F + unit(value, 2) * (0.55F + 1.05F * scale));
            float radius = (0.10F + unit(value, 3) * 0.25F)
                * (0.88F + scale * 0.22F);
            float alpha = fade * (0.18F + 0.38F * (1.0F - unit(value, 4)));
            int tone = 176 + Math.floorMod((int) value, 42);
            billboard(pose, buffer, localX, height, localZ, radius, angle,
                tone, Math.min(224, tone + 4),
                Math.min(230, tone + 9), alpha, 0xB000B0, basis);
        }
    }

    public static void renderNuclearReturnFront(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final double returnRadius,
        final float yieldScale, final long seed, final WarheadMesh.Lod lod,
        final Vec3 impactPosition, final Quaternionf camera) {
        if (returnRadius <= 0.0) return;
        float scale = Mth.clamp(yieldScale, 0.35F, 3.0F);
        int base = switch (lod) {
            case NEAR -> 480;
            case MEDIUM -> 240;
            case FAR -> 90;
        };
        int samples = Math.min(1_200, Math.max(48,
            Math.round(base * (0.70F + (float) Math.sqrt(scale)))));
        Basis basis = Basis.from(camera);
        for (int index = 0; index < samples; index++) {
            long value = mix(seed ^ RETURN_SEED ^ (long) index * 0x9E3779B97F4A7C15L);
            float angle = (index + unit(value, 0)) / samples * Mth.TWO_PI;
            float radial = (float) returnRadius
                + signed(value, 1) * (0.8F + 1.6F * scale);
            float localX = Mth.cos(angle) * radial;
            float localZ = Mth.sin(angle) * radial;
            float height = terrainHeight(impactPosition, localX, localZ,
                0.10F + unit(value, 2) * (0.55F + 0.50F * scale));
            float radius = (0.10F + unit(value, 3) * 0.22F)
                * (0.92F + 0.12F * scale);
            float alpha = 0.16F + unit(value, 4) * 0.22F;
            int tone = 174 + Math.floorMod((int) value, 42);
            billboard(pose, buffer, localX, height, localZ, radius, -angle,
                tone, Math.min(224, tone + 4),
                Math.min(230, tone + 9), alpha, 0xA000A0, basis);
        }
    }

    private static float terrainHeight(final Vec3 impactPosition, final float localX,
        final float localZ, final float offset) {
        if (impactPosition == null) return offset;
        ClientLevel level = Minecraft.getInstance().level;
        TerrainSurfaceCache.SurfaceSample surface = TerrainSurfaceCache.INSTANCE.sample(level,
            impactPosition.x + localX, impactPosition.z + localZ);
        return surface == null ? offset
            : (float) (surface.position().y - impactPosition.y) + Math.max(0.06F, offset);
    }

    private static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera, final FireLayer layer) {
        if (age < 0.0 || age > 126.0) return;
        float scale = Mth.clamp(visualScale, 0.28F, 1.75F);
        int base = switch (lod) {
            case NEAR -> 1_080;
            case MEDIUM -> 560;
            case FAR -> 190;
        };
        int samples = Math.min(lod == WarheadMesh.Lod.NEAR ? 2_900 : 1_420,
            Math.max(96, (int) Math.round(base * (0.72 + Math.pow(scale, 1.48)))));
        if (layer == FireLayer.CORE) samples = Math.max(80, samples / 3);
        Basis basis = Basis.from(camera);
        double craterBase = -(1.8 + 5.6 * scale);
        for (int index = 0; index < samples; index++) {
            long value = mix(seed ^ FIRE_SEED ^ (long) index * 0x9E3779B97F4A7C15L);
            double birth = unit(value, 0) * (5.0 + scale * 4.0);
            double life = 32.0 + unit(value, 1) * (30.0 + scale * 30.0);
            double localAge = age - birth;
            if (localAge < 0.0 || localAge >= life) continue;
            double progress = localAge / life;
            boolean core = unit(value, 2) < 0.30 + 0.12 * scale;
            boolean hot = progress < 0.56 + 0.06 * unit(value, 3);
            if (layer == FireLayer.CORE && !core) continue;
            if (layer == FireLayer.HOT && (!hot || core)) continue;
            if (layer == FireLayer.COOL && (hot || core)) continue;
            float angle = unit(value, 4) * Mth.TWO_PI;
            double sourceRadius = Math.sqrt(unit(value, 5)) * (0.70 + 2.6 * scale)
                * (core ? 0.52 : 1.0);
            double outwardVelocity = (0.070 + unit(value, 6) * 0.19)
                * (0.72 + 0.52 * scale);
            double upwardVelocity = (0.30 + unit(value, 7) * 0.38)
                * (0.68 + 0.32 * scale);
            double radial = sourceRadius + localAge * outwardVelocity
                * (1.0 - 0.58 * smooth(progress));
            double y = craterBase + 1.0 + localAge * upwardVelocity
                - localAge * localAge * (0.0064 + 0.0028 / Math.max(0.35, scale));
            y = Math.min(HE_FIRE_TOP, y);
            float drawRadius = (float) ((0.18 + unit(value, 8) * 0.48)
                * (0.76 + scale * 0.34) * (0.78 + progress * 0.46));
            if (layer == FireLayer.CORE) drawRadius *= 1.18F;
            float alpha = (float) Mth.clamp(
                (layer == FireLayer.CORE ? 0.94 : hot ? 0.80 : 0.58)
                    * Math.pow(1.0 - progress,
                        layer == FireLayer.CORE ? 0.30 : hot ? 0.42 : 0.72),
                0.0, 0.96);
            int red = layer == FireLayer.COOL
                ? Mth.lerpInt((float) progress, 244, 105) : 255;
            int green = layer == FireLayer.CORE
                ? Mth.lerpInt((float) progress, 246, 154)
                : hot ? Mth.lerpInt((float) progress, 224, 112)
                    : Mth.lerpInt((float) progress, 132, 54);
            int blue = layer == FireLayer.CORE
                ? Mth.lerpInt((float) progress, 154, 30)
                : hot ? Mth.lerpInt((float) progress, 80, 18)
                    : Mth.lerpInt((float) progress, 34, 22);
            billboard(pose, buffer, Mth.cos(angle) * (float) radial, (float) y,
                Mth.sin(angle) * (float) radial, drawRadius,
                angle + (float) localAge * 0.024F, red, green, blue, alpha,
                0xF000F0, basis);
        }
    }

    private static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera, final boolean corePass) {
        if (age < 5.0 || age > 360.0) return;
        float scale = Mth.clamp(visualScale, 0.28F, 1.75F);
        int base = switch (lod) {
            case NEAR -> 1_420;
            case MEDIUM -> 720;
            case FAR -> 240;
        };
        int samples = Math.min(lod == WarheadMesh.Lod.NEAR ? 3_600 : 1_700,
            Math.max(120, (int) Math.round(base * (0.82 + Math.pow(scale, 1.55)))));
        if (corePass) samples = Math.max(100, samples / 4);
        Basis basis = Basis.from(camera);
        double craterBase = -(1.8 + 5.6 * scale);
        for (int index = 0; index < samples; index++) {
            long value = mix(seed ^ SMOKE_SEED ^ (long) index * 0xD1B54A32D192ED03L);
            double birth = 5.0 + unit(value, 0) * (40.0 + scale * 24.0);
            double life = 96.0 + unit(value, 1) * (105.0 + scale * 66.0);
            double localAge = age - birth;
            if (localAge < 0.0 || localAge >= life) continue;
            double progress = localAge / life;
            boolean core = unit(value, 2) < 0.20 + 0.10 * scale;
            if (core != corePass) continue;
            float angle = unit(value, 3) * Mth.TWO_PI;
            double radial = Math.sqrt(unit(value, 4)) * (0.9 + 5.0 * scale)
                * (core ? 0.48 : 1.0)
                + localAge * (0.014 + unit(value, 5) * 0.040)
                    * (0.70 + 0.42 * scale);
            double rise = localAge * (0.045 + unit(value, 6) * 0.080)
                * (0.68 + 0.40 * scale);
            double y = craterBase + 1.5 + rise;
            if (age < 48.0) y = Math.min(HE_FIRE_TOP + (age - 5.0) * 0.035, y);
            float drawRadius = (float) ((0.17 + unit(value, 7) * 0.54)
                * (0.78 + scale * 0.34) * (0.72 + progress * 0.76));
            if (corePass) drawRadius *= 1.20F;
            float alpha = (float) Mth.clamp((corePass ? 0.86 : 0.40)
                * Math.pow(1.0 - progress, corePass ? 0.42 : 0.58)
                * smooth(localAge / 7.0), 0.0, corePass ? 0.90 : 0.48);
            int tone = Mth.clamp((corePass ? 32 : 48)
                + (int) (unit(value, 8) * (corePass ? 28.0 : 58.0))
                + (int) (progress * 42.0), 28, 154);
            billboard(pose, buffer, Mth.cos(angle) * (float) radial, (float) y,
                Mth.sin(angle) * (float) radial, drawRadius,
                angle + (float) localAge * 0.007F, tone, Math.min(158, tone + 4),
                Math.min(168, tone + 10), alpha,
                corePass ? 0x900090 : 0xA000A0, basis);
        }
    }

    private enum FireLayer {
        CORE,
        HOT,
        COOL
    }

    private record Basis(Vector3f right, Vector3f up, Vector3f normal) {
        private static Basis from(final Quaternionf camera) {
            return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera),
                new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera));
        }
    }

    private static void billboard(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ, final float radius,
        final float rotation, final int red, final int green, final int blue,
        final float alpha, final int light, final Basis basis) {
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

    private static double smooth(final double value) {
        double t = Mth.clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
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
