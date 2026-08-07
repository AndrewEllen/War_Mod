package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Deterministic conventional blast built from two interacting fire volumes.
 * The broad crater fire and the narrower internal lift cool into smoke without
 * layer swaps. Ballistic ejecta emit independent smoke puffs instead of drawing
 * a pre-authored ribbon from the origin to the current head position.
 */
public final class ConventionalBlastVisualV5 {
    private static volatile int lastActive;
    private static volatile int lastCulled;

    private ConventionalBlastVisualV5() { }

    public static void renderFireCore(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderFire(pose, buffer, age, visualScale, seed, lod, camera, FirePass.CORE);
    }

    public static void renderHot(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderFire(pose, buffer, age, visualScale, seed, lod, camera, FirePass.HOT);
    }

    public static void renderCooling(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderFire(pose, buffer, age, visualScale, seed, lod, camera, FirePass.COOLING);
    }

    public static void renderSmokeCore(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderSmoke(pose, buffer, age, visualScale, seed, lod, camera, true);
    }

    public static void renderSmoke(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final WarheadClientVisualProfile profile, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        renderSmoke(pose, buffer, age, visualScale, seed, lod, camera, false);
        renderBallisticTrails(pose, buffer, age, visualScale, seed, lod, camera);
    }

    public static DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(lastActive, 0, lastCulled, 0);
    }

    private static void renderFire(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double rawAge, final float rawScale,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera,
        final FirePass pass) {
        if (rawAge < 0.0 || rawAge >= 650.0) return;
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        float smallYieldBoost = 1.0F + Mth.clamp((0.72F - scale) / 0.72F,
            0.0F, 1.0F) * 0.32F;
        float bodyRadius = craterRadius
            * (0.88F + unit(seed, 0) * 0.15F) * smallYieldBoost;
        float craterFloor = -Math.max(1.1F, craterRadius * 0.22F);
        float budget = densityMultiplier();
        int base = switch (lod) {
            case NEAR -> 3_200;
            case MEDIUM -> 1_520;
            case FAR -> 580;
        };
        int count = Math.max(96, Math.round(base * budget));
        Basis basis = Basis.from(camera);
        int active = 0;
        int culled = 0;

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x464952455F563636L
                ^ index * 0x9E3779B97F4A7C15L);
            boolean inner = unit(random, 0) < 0.35F;
            float onset = inner ? 4.0F + unit(random, 1) * 20.0F
                : unit(random, 1) * 10.0F;
            float localAge = age - onset;
            if (localAge < 0.0F) {
                culled++;
                continue;
            }

            float coolingTime = inner
                ? 470.0F + unit(random, 2) * 135.0F
                : 395.0F + unit(random, 2) * 125.0F;
            float temperature = Mth.clamp(1.10F - localAge / coolingTime
                + signed(random, 3) * 0.115F, 0.0F, 1.0F);
            if (!matches(pass, temperature, inner)) {
                culled++;
                continue;
            }

            float contractionStart = inner ? 185.0F : 155.0F;
            float contractionDuration = inner ? 410.0F : 390.0F;
            float contraction = smoothstep(Mth.clamp(
                (localAge - contractionStart) / contractionDuration,
                0.0F, 1.0F));
            float radiusScale = Mth.lerp(contraction,
                inner ? 0.61F : 1.0F,
                inner ? 0.26F : 0.40F);
            float volumeRadius = bodyRadius * radiusScale;
            float rise = Mth.lerp(smoothstep(Mth.clamp(localAge / 360.0F,
                0.0F, 1.0F)), 0.0F, inner ? 7.2F : 3.7F);
            float centerY = craterFloor + bodyRadius * (inner ? 0.70F : 0.48F)
                + rise;

            float angle = unit(random, 4) * Mth.TWO_PI
                + localAge * signed(random, 5) * (inner ? 0.0042F : 0.0022F);
            float radialFraction = Mth.sqrt(unit(random, 6));
            float shellY = signed(random, 7);
            float dome = Mth.sqrt(Math.max(0.0F,
                1.0F - radialFraction * radialFraction));
            float horizontal = radialFraction * volumeRadius
                * (0.80F + unit(random, 8) * 0.36F);
            float verticalRadius = volumeRadius * (inner ? 1.52F : 1.12F)
                * (0.82F + unit(random, 9) * 0.34F);
            float clusterNoise = Mth.sin(unit(random, 10) * Mth.TWO_PI
                + localAge * 0.031F) * volumeRadius * 0.11F;
            float px = Mth.cos(angle) * horizontal
                + Mth.cos(angle + Mth.HALF_PI) * clusterNoise
                + signed(random, 11) * volumeRadius * 0.10F;
            float pz = Mth.sin(angle) * horizontal
                + Mth.sin(angle + Mth.HALF_PI) * clusterNoise
                + signed(random, 12) * volumeRadius * 0.10F;
            float py = centerY + shellY * verticalRadius * dome;
            if (inner) py += Mth.abs(shellY) * bodyRadius * 0.12F;

            float particleSize = Mth.lerp(radialFraction,
                1.65F + scale * 0.76F, 0.55F + scale * 0.26F)
                * (0.76F + unit(random, 13) * 0.52F);
            if (inner) particleSize *= 0.88F;
            float remaining = Mth.clamp(1.0F - localAge / (coolingTime + 110.0F),
                0.0F, 1.0F);
            float alpha = (pass == FirePass.CORE ? 0.99F
                : pass == FirePass.HOT ? 0.95F : 0.87F)
                * smoothstep(Mth.clamp(localAge / 5.0F, 0.0F, 1.0F))
                * (float) Math.pow(remaining, 0.34F);
            Colour colour = fireColour(temperature, random);
            billboard(pose, buffer, px, py, pz, particleSize,
                unit(random, 14) * Mth.TWO_PI
                    + localAge * signed(random, 15) * 0.0055F,
                colour.red, colour.green, colour.blue, alpha, 0xF000F0, basis);
            active++;
        }
        lastActive = active;
        lastCulled = culled;
    }

    private static void renderSmoke(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double rawAge, final float rawScale,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera,
        final boolean corePass) {
        if (rawAge < 22.0 || rawAge >= 720.0) return;
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        float smallYieldBoost = 1.0F + Mth.clamp((0.72F - scale) / 0.72F,
            0.0F, 1.0F) * 0.25F;
        float bodyRadius = craterRadius
            * (0.86F + unit(seed, 0) * 0.14F) * smallYieldBoost;
        float craterFloor = -Math.max(1.1F, craterRadius * 0.22F);
        float budget = densityMultiplier();
        int base = switch (lod) {
            case NEAR -> corePass ? 2_150 : 2_650;
            case MEDIUM -> corePass ? 1_020 : 1_240;
            case FAR -> corePass ? 390 : 470;
        };
        int count = Math.max(96, Math.round(base * budget));
        Basis basis = Basis.from(camera);

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ (corePass
                ? 0x534D4F4B455F4336L : 0x534D4F4B455F5336L)
                ^ index * 0xD1B54A32D192ED03L);
            boolean fromInnerFire = unit(random, 0) < 0.34F;
            float onset = fromInnerFire
                ? 105.0F + unit(random, 1) * 210.0F
                : 65.0F + unit(random, 1) * 235.0F;
            float localAge = age - onset;
            if (localAge < 0.0F) continue;
            float life = 310.0F + unit(random, 2) * 255.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;

            float radialFraction = Mth.sqrt(unit(random, 3));
            boolean central = radialFraction < (corePass ? 0.68F : 0.34F);
            if (central != corePass) continue;
            float clusterAngle = unit(random, 4) * Mth.TWO_PI;
            float clusterDistance = bodyRadius * unit(random, 5)
                * (corePass ? 0.26F : 0.66F);
            float clusterX = Mth.cos(clusterAngle) * clusterDistance;
            float clusterZ = Mth.sin(clusterAngle) * clusterDistance;
            float localAngle = unit(random, 6) * Mth.TWO_PI
                + localAge * signed(random, 7) * 0.0028F;
            float lobeRadius = bodyRadius * (fromInnerFire ? 0.56F : 0.82F)
                * (0.72F + unit(random, 8) * 0.38F);
            float radial = radialFraction * lobeRadius;

            float earlyRise = fromInnerFire
                ? 4.5F + unit(random, 9) * 5.5F
                : 2.0F + unit(random, 9) * 4.5F;
            float coolingGravity = Math.max(0.0F, progress - 0.24F);
            float verticalDrop = coolingGravity * coolingGravity
                * (5.0F + unit(random, 10) * 9.0F);
            float centerY = craterFloor + bodyRadius
                * (fromInnerFire ? 0.78F : 0.48F)
                + earlyRise - verticalDrop;
            float px = clusterX + Mth.cos(localAngle) * radial
                + signed(random, 11) * bodyRadius * 0.10F;
            float pz = clusterZ + Mth.sin(localAngle) * radial
                + signed(random, 12) * bodyRadius * 0.10F;
            float py = centerY + signed(random, 13) * bodyRadius
                * (fromInnerFire ? 0.46F : 0.34F);
            py = Math.max(0.08F, py);

            float particleSize = Mth.lerp(radialFraction,
                1.85F + scale * 0.76F, 0.58F + scale * 0.26F)
                * (0.74F + unit(random, 14) * 0.58F)
                * (1.0F + progress * 0.44F);
            float finalFade = progress < 0.84F ? 1.0F
                : smoothstep(Mth.clamp((1.0F - progress) / 0.16F,
                    0.0F, 1.0F));
            float alpha = (corePass ? 0.95F : 0.86F)
                * smoothstep(Mth.clamp(localAge / 8.0F, 0.0F, 1.0F))
                * finalFade;
            int tone = smokeTone(random, corePass, progress);
            billboard(pose, buffer, px, py, pz, particleSize,
                unit(random, 15) * Mth.TWO_PI
                    + localAge * signed(random, 16) * 0.0032F,
                tone, tone, tone, alpha,
                corePass ? 0x900090 : 0xA000A0, basis);
        }
    }

    private static void renderBallisticTrails(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double rawAge, final float rawScale,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (rawAge < 2.0 || rawAge >= 720.0) return;
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        int streamCount = Mth.clamp(Math.round(4.0F + scale * 6.0F), 5, 15);
        int puffLimit = switch (lod) {
            case NEAR -> 52;
            case MEDIUM -> 30;
            case FAR -> 15;
        };
        float density = Mth.clamp(densityMultiplier(), 0.65F, 2.25F);
        Basis basis = Basis.from(camera);
        final float gravity = 0.052F;

        for (int stream = 0; stream < streamCount; stream++) {
            long random = mix(seed ^ 0x42414C4C49535436L
                ^ stream * 0x94D049BB133111EBL);
            float onset = 1.0F + unit(random, 0) * 26.0F;
            float streamAge = age - onset;
            if (streamAge < 0.0F) continue;
            float angle = unit(random, 1) * Mth.TWO_PI;
            float speed = 0.27F + unit(random, 2)
                * (0.44F + scale * 0.20F);
            float upward = 0.32F + unit(random, 3)
                * (0.48F + scale * 0.14F);
            float startY = 0.65F + unit(random, 4) * Math.min(3.2F,
                craterRadius * 0.28F);
            float drag = 0.020F + unit(random, 5) * 0.027F;
            float hitTime = (upward + Mth.sqrt(upward * upward
                + 2.0F * gravity * startY)) / gravity;
            float emissionEnd = Math.min(hitTime, 44.0F + unit(random, 6) * 28.0F);
            float interval = (1.10F + unit(random, 7) * 1.35F)
                / density;
            int possiblePuffs = Math.min(puffLimit,
                1 + (int) Math.floor(emissionEnd / interval));
            float smokeLife = 265.0F + unit(random, 8) * 235.0F;

            for (int puff = 0; puff < possiblePuffs; puff++) {
                float emissionTime = puff * interval;
                if (emissionTime > streamAge) break;
                float puffAge = streamAge - emissionTime;
                if (puffAge >= smokeLife) continue;
                long puffSeed = mix(random ^ puff * 0x9E3779B97F4A7C15L);

                float launchDistance = dragDistance(speed, drag, emissionTime);
                float launchY = startY + upward * emissionTime
                    - 0.5F * gravity * emissionTime * emissionTime;
                launchY = Math.max(0.08F, launchY);
                float remainingVelocity = speed
                    * (float) Math.exp(-drag * emissionTime);
                float smokeDrift = dragDistance(remainingVelocity * 0.12F,
                    0.055F, puffAge);
                float crossNoise = Mth.sin(emissionTime * 0.42F
                    + unit(puffSeed, 0) * Mth.TWO_PI)
                    * (0.09F + puffAge * 0.0045F);
                float px = Mth.cos(angle) * (launchDistance + smokeDrift)
                    - Mth.sin(angle) * crossNoise
                    + signed(puffSeed, 1) * 0.20F;
                float pz = Mth.sin(angle) * (launchDistance + smokeDrift)
                    + Mth.cos(angle) * crossNoise
                    + signed(puffSeed, 2) * 0.20F;
                float settling = Math.max(0.0F, puffAge - 18.0F);
                float py = launchY + 0.025F * Math.min(puffAge, 18.0F)
                    - settling * (0.018F + unit(puffSeed, 3) * 0.012F);
                py = Math.max(0.08F, py);

                float progress = puffAge / smokeLife;
                float fade = progress < 0.82F ? 1.0F
                    : smoothstep(Mth.clamp((1.0F - progress) / 0.18F,
                        0.0F, 1.0F));
                float particleSize = (0.52F + unit(puffSeed, 4) * 0.92F)
                    * (0.88F + scale * 0.17F)
                    * (1.0F + puffAge * 0.008F);
                int selector = Math.floorMod((int) (puffSeed >>> 9), 100);
                int tone = selector < 9
                    ? 92 + Math.floorMod((int) (puffSeed >>> 19), 42)
                    : selector < 24
                        ? 170 + Math.floorMod((int) (puffSeed >>> 19), 35)
                        : 220 + Math.floorMod((int) (puffSeed >>> 19), 32);
                float alpha = (0.84F + unit(puffSeed, 5) * 0.13F) * fade;
                billboard(pose, buffer, px, py, pz, particleSize,
                    unit(puffSeed, 6) * Mth.TWO_PI
                        + puffAge * signed(puffSeed, 7) * 0.003F,
                    tone, tone, tone, alpha, 0xB000B0, basis);
            }
        }
    }

    private static float dragDistance(final float speed, final float drag,
        final float time) {
        if (time <= 0.0F) return 0.0F;
        return speed * (1.0F - (float) Math.exp(-drag * time)) / drag;
    }

    private static boolean matches(final FirePass pass, final float temperature,
        final boolean inner) {
        return switch (pass) {
            case CORE -> temperature >= 0.72F && !inner;
            case HOT -> temperature >= 0.56F && inner;
            case COOLING -> temperature >= 0.08F && temperature < 0.72F;
        };
    }

    private static Colour fireColour(final float heat, final long random) {
        float noise = signed(random, 17) * 0.07F;
        float temperature = Mth.clamp(heat + noise, 0.0F, 1.0F);
        if (temperature > 0.86F) {
            float t = (temperature - 0.86F) / 0.14F;
            return new Colour(255, Mth.lerpInt(t, 210, 255),
                Mth.lerpInt(t, 56, 225));
        }
        if (temperature > 0.52F) {
            float t = (temperature - 0.52F) / 0.34F;
            return new Colour(255, Mth.lerpInt(t, 102, 210),
                Mth.lerpInt(t, 15, 56));
        }
        if (temperature > 0.22F) {
            float t = (temperature - 0.22F) / 0.30F;
            return new Colour(Mth.lerpInt(t, 184, 255),
                Mth.lerpInt(t, 72, 102), Mth.lerpInt(t, 30, 15));
        }
        float t = temperature / 0.22F;
        return new Colour(Mth.lerpInt(t, 112, 184),
            Mth.lerpInt(t, 102, 72), Mth.lerpInt(t, 96, 30));
    }

    private static int smokeTone(final long random, final boolean core,
        final float progress) {
        float region = 0.5F + 0.5F * Mth.sin(
            (random & 0xFFFFL) * 0.00061F + progress * 9.0F);
        int base = core ? 42 : 74;
        int variation = Math.floorMod((int) (random >>> 21), core ? 62 : 80);
        return Mth.clamp(base + variation + (int) (region * 24.0F), 34, 182);
    }

    private static float densityMultiplier() {
        return Mth.clamp((float) Math.sqrt(
            WarheadRenderSettings.particleBudgetMultiplier() / 6.0F),
            0.35F, 6.0F);
    }

    public record DebugSnapshot(int activeParticles, int spawnedParticlesPerTick,
        int culledParticles, int activeFields) { }

    private enum FirePass { CORE, HOT, COOLING }
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
