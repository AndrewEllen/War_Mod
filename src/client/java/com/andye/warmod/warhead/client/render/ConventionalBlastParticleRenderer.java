package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Compatibility facade for the active analytical conventional renderer plus
 * bounded ground-front effects. The nuclear return front is evaluated from
 * particle birth time, so every puff rises briefly, falls, lands and fades
 * instead of being frozen at one analytical age above the terrain.
 */
public final class ConventionalBlastParticleRenderer {
    private static final long NUCLEAR_KEY_MASK = 0x6E75636C656172L;

    private ConventionalBlastParticleRenderer() { }

    public static void renderFireCore(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderFireCore(pose, buffer, age,
                visualScale, profile, seed, lod, camera);
            return;
        }
        ConventionalBlastVisualV5.renderFireCore(pose, buffer, age, visualScale,
            profile, seed, lod, camera);
    }

    public static void renderHot(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderHot(pose, buffer, age,
                visualScale, profile, seed, lod, camera);
            return;
        }
        ConventionalBlastVisualV5.renderHot(pose, buffer, age, visualScale,
            profile, seed, lod, camera);
    }

    public static void renderCooling(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderCooling(pose, buffer, age,
                visualScale, profile, seed, lod, camera);
            return;
        }
        ConventionalBlastVisualV5.renderCooling(pose, buffer, age, visualScale,
            profile, seed, lod, camera);
    }

    public static void renderSmokeCore(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderSmokeCore(pose, buffer, age,
                visualScale, profile, seed, lod, camera);
            return;
        }
        ConventionalBlastVisualV5.renderSmokeCore(pose, buffer, age, visualScale,
            profile, seed, lod, camera);
    }

    public static void renderSmoke(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderSmoke(pose, buffer, age,
                visualScale, profile, seed, lod, camera);
            return;
        }
        ConventionalBlastVisualV5.renderSmoke(pose, buffer, age, visualScale,
            profile, seed, lod, camera);
    }

    public static void renderSurfaceFront(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final double physicalRadius,
        final float visualScale, final long seed, final WarheadMesh.Lod lod,
        final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderSurfaceFront(pose, buffer, age,
                physicalRadius, visualScale, seed, lod, camera);
            return;
        }
        renderGroundFront(pose, buffer, age, physicalRadius, visualScale,
            seed, lod, camera, false);
    }

    /** Vanilla explosion-texture flecks carried by the outward pressure front. */
    public static void renderSurfaceExplosionPuffs(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final double physicalRadius,
        final float visualScale, final long seed, final WarheadMesh.Lod lod,
        final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) return;
        renderGroundFront(pose, buffer, age, physicalRadius, visualScale,
            seed ^ 0x4558504C4F53494FL, lod, camera, true);
    }

    public static void renderNuclearReturnFront(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final double returnRadius,
        final float yieldScale, final long seed, final WarheadMesh.Lod lod,
        final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderNuclearReturnFront(pose, buffer,
                age, returnRadius, yieldScale, seed, lod, camera);
            return;
        }
        if (!Double.isFinite(age) || !Double.isFinite(returnRadius)
            || returnRadius <= 0.0 || age < 0.0) return;

        Basis basis = Basis.from(camera);
        float scale = Mth.clamp(yieldScale, 0.35F, 4.2F);
        float sqrtScale = Mth.sqrt(scale);
        int baseCount = switch (lod) {
            case NEAR -> 1_360;
            case MEDIUM -> 680;
            case FAR -> 260;
        };
        int count = Math.min(2_600,
            Math.round(baseCount * (0.72F + sqrtScale * 0.48F)));
        float gravity = 0.0125F;
        long stableSeed = seed ^ NUCLEAR_KEY_MASK;
        float globalAge = (float) age;

        for (int index = 0; index < count; index++) {
            long random = mix(stableSeed
                ^ index * 0x9E3779B97F4A7C15L);
            float spawnAge = unit(random, 0) * 112.0F;
            float localAge = globalAge - spawnAge;
            if (localAge < 0.0F) continue;
            float life = 108.0F + unit(random, 1) * 92.0F;
            if (localAge >= life) continue;

            float angle = (index + unit(random, 2)) / count * Mth.TWO_PI;
            float inwardSpeed = 0.13F + unit(random, 3)
                * (0.20F + 0.055F * sqrtScale);
            float radialJitter = signed(random, 4) * (1.0F + 1.7F * scale);
            float radial = Math.max(0.0F, (float) returnRadius + radialJitter);
            float tangential = signed(random, 5) * localAge * 0.018F;
            float cosine = Mth.cos(angle);
            float sine = Mth.sin(angle);
            float px = cosine * radial - sine * tangential;
            float pz = sine * radial + cosine * tangential;

            float initialY = 0.18F + unit(random, 6)
                * (2.2F + 1.5F * sqrtScale);
            float initialVy = 0.025F + unit(random, 7)
                * (0.075F + 0.018F * scale);
            float py = initialY + initialVy * localAge
                - 0.5F * gravity * localAge * localAge;
            boolean landed = py <= 0.06F;
            py = Math.max(0.06F, py);

            float progress = localAge / life;
            float fadeIn = smoothstep(Mth.clamp(localAge / 5.0F, 0.0F, 1.0F));
            float fadeOut = (float) Math.pow(
                Mth.clamp(1.0F - progress, 0.0F, 1.0F), 0.72F);
            float landingFade = landed
                ? Mth.clamp((life - localAge) / 30.0F, 0.0F, 1.0F)
                : 1.0F;
            float alpha = 0.72F * fadeIn * fadeOut * landingFade;
            if (alpha <= 0.004F) continue;

            int tone = 170 + Math.floorMod((int) (random >>> 24), 66);
            float radius = (0.34F + unit(random, 8) * 0.68F)
                * (0.92F + 0.12F * scale)
                * (1.0F + localAge * 0.005F);
            if (landed) radius *= 1.12F;
            billboard(pose, buffer, px, py, pz, radius,
                unit(random, 9) * Mth.TWO_PI + localAge * signed(random, 10) * 0.018F,
                tone, Math.min(242, tone + 5), Math.min(250, tone + 12),
                alpha, 0xA000A0, basis);
        }
    }

    private static void renderGroundFront(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final double physicalRadius,
        final float visualScale, final long seed, final WarheadMesh.Lod lod,
        final Quaternionf camera, final boolean hotFlecks) {
        if (!Double.isFinite(age) || !Double.isFinite(physicalRadius)
            || physicalRadius <= 0.0 || age < 0.0) return;
        Basis basis = Basis.from(camera);
        float scale = Mth.clamp(visualScale, 0.28F, 1.75F);
        int count = switch (lod) {
            case NEAR -> hotFlecks ? 260 : 720;
            case MEDIUM -> hotFlecks ? 130 : 360;
            case FAR -> hotFlecks ? 52 : 140;
        };
        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ index * 0xD1B54A32D192ED03L
                ^ (long) Math.floor(age) * 0x94D049BB133111EBL);
            float angle = (index + unit(random, 0)) / count * Mth.TWO_PI;
            float trail = unit(random, 1) * (2.0F + 4.2F * scale);
            float radial = Math.max(0.0F, (float) physicalRadius - trail);
            float px = Mth.cos(angle) * radial;
            float pz = Mth.sin(angle) * radial;
            float py = 0.05F + unit(random, 2) * (0.42F + 0.44F * scale);
            float alpha = hotFlecks ? 0.76F : 0.64F;
            float radius = (hotFlecks ? 0.24F : 0.34F)
                + unit(random, 3) * (hotFlecks ? 0.42F : 0.64F);
            int red;
            int green;
            int blue;
            int light;
            if (hotFlecks) {
                red = 255;
                green = 196 + Math.floorMod((int) (random >>> 20), 52);
                blue = 96 + Math.floorMod((int) (random >>> 28), 96);
                light = 0xF000F0;
            } else {
                int tone = 166 + Math.floorMod((int) (random >>> 20), 58);
                red = tone;
                green = Math.min(232, tone + 3);
                blue = Math.min(240, tone + 9);
                light = 0xA000A0;
            }
            billboard(pose, buffer, px, py, pz, radius,
                unit(random, 4) * Mth.TWO_PI, red, green, blue,
                alpha, light, basis);
        }
    }

    public static DebugSnapshot debugSnapshot() {
        ConventionalBlastVisualV5.DebugSnapshot snapshot =
            ConventionalBlastVisualV5.debugSnapshot();
        return new DebugSnapshot(snapshot.activeParticles(),
            snapshot.spawnedParticlesPerTick(), snapshot.culledParticles(),
            snapshot.activeFields(), backendDescription());
    }

    public static String backendDescription() {
        return WarheadRenderSettings.usePackedParticles()
            ? "analytical_v5_ballistic_return_front"
            : "legacy_analytical_custom_geometry";
    }

    public record DebugSnapshot(int activeParticles, int spawnedParticlesPerTick,
        int culledParticles, int activeFields, String backend) { }

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
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
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
