package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Deterministic conventional blast built from two interacting fire volumes.
 * The broad crater fire rises and contracts slowly while a smaller internal
 * plume pushes roughly two blocks higher. Both cool continuously into the same
 * smoke material; no layer is replaced or popped out of existence.
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
        renderTendrils(pose, buffer, age, visualScale, seed, lod, camera);
    }

    public static DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(lastActive, 0, lastCulled, 0);
    }

    private static void renderFire(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double rawAge, final float rawScale,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera,
        final FirePass pass) {
        if (rawAge < 0.0 || rawAge >= 390.0) return;
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        float outerRadius = craterRadius * (0.68F + unit(seed, 0) * 0.10F);
        float craterFloor = -Math.max(1.1F, craterRadius * 0.22F);
        float budget = densityMultiplier();
        int base = switch (lod) {
            case NEAR -> 2_350;
            case MEDIUM -> 1_120;
            case FAR -> 430;
        };
        int count = Math.max(64, Math.round(base * budget));
        Basis basis = Basis.from(camera);
        int active = 0;
        int culled = 0;

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x464952455F563535L
                ^ index * 0x9E3779B97F4A7C15L);
            boolean inner = unit(random, 0) < 0.32F;
            float onset = inner ? 3.0F + unit(random, 1) * 18.0F
                : unit(random, 1) * 9.0F;
            float localAge = age - onset;
            if (localAge < 0.0F) {
                culled++;
                continue;
            }
            float coolingTime = inner
                ? 225.0F + unit(random, 2) * 120.0F
                : 185.0F + unit(random, 2) * 105.0F;
            float temperature = Mth.clamp(1.08F - localAge / coolingTime
                + signed(random, 3) * 0.10F, 0.0F, 1.0F);
            if (!matches(pass, temperature, inner)) {
                culled++;
                continue;
            }

            float contractionStart = inner ? 70.0F : 35.0F;
            float contractionDuration = inner ? 245.0F : 225.0F;
            float contraction = smoothstep(Mth.clamp(
                (localAge - contractionStart) / contractionDuration, 0.0F, 1.0F));
            float radiusScale = Mth.lerp(contraction,
                inner ? 0.58F : 1.0F, inner ? 0.18F : 0.26F);
            float volumeRadius = outerRadius * radiusScale;
            float rise = Mth.lerp(smoothstep(Mth.clamp(localAge / 230.0F, 0.0F, 1.0F)),
                0.0F, inner ? 4.9F : 2.7F);
            float centerY = craterFloor + outerRadius * (inner ? 0.72F : 0.48F)
                + rise;
            float lobeNoise = signed(random, 4);
            float angle = unit(random, 5) * Mth.TWO_PI
                + localAge * signed(random, 6) * (inner ? 0.0045F : 0.0024F);
            float radialFraction = Mth.sqrt(unit(random, 7));
            float shellY = signed(random, 8);
            float dome = Mth.sqrt(Math.max(0.0F,
                1.0F - radialFraction * radialFraction));
            float horizontal = radialFraction * volumeRadius
                * (0.82F + unit(random, 9) * 0.30F);
            float verticalRadius = volumeRadius * (inner ? 1.42F : 0.88F)
                * (0.84F + unit(random, 10) * 0.28F);
            float px = Mth.cos(angle) * horizontal
                + lobeNoise * volumeRadius * 0.14F;
            float pz = Mth.sin(angle) * horizontal
                + signed(random, 11) * volumeRadius * 0.14F;
            float py = centerY + shellY * verticalRadius * dome;
            if (inner) {
                py += Mth.abs(shellY) * outerRadius * 0.10F;
            }
            float turbulence = Mth.sin(localAge * 0.047F
                + unit(random, 12) * Mth.TWO_PI);
            px += turbulence * volumeRadius * 0.055F;
            pz += Mth.cos(localAge * 0.041F
                + unit(random, 13) * Mth.TWO_PI) * volumeRadius * 0.055F;

            float edge = radialFraction;
            float particleSize = Mth.lerp(edge,
                1.32F + scale * 0.62F, 0.48F + scale * 0.22F)
                * (0.78F + unit(random, 14) * 0.48F);
            if (inner) particleSize *= 0.86F;
            float remaining = Mth.clamp(1.0F - localAge / (coolingTime + 55.0F),
                0.0F, 1.0F);
            float alpha = (pass == FirePass.CORE ? 0.98F
                : pass == FirePass.HOT ? 0.92F : 0.84F)
                * smoothstep(Mth.clamp(localAge / 5.0F, 0.0F, 1.0F))
                * (float) Math.pow(remaining, 0.42F);
            Colour colour = fireColour(temperature, random);
            billboard(pose, buffer, px, py, pz, particleSize,
                unit(random, 15) * Mth.TWO_PI
                    + localAge * signed(random, 16) * 0.006F,
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
        if (rawAge < 10.0 || rawAge >= 520.0) return;
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        float outerRadius = craterRadius * (0.68F + unit(seed, 0) * 0.10F);
        float craterFloor = -Math.max(1.1F, craterRadius * 0.22F);
        float budget = densityMultiplier();
        int base = switch (lod) {
            case NEAR -> corePass ? 1_720 : 2_100;
            case MEDIUM -> corePass ? 820 : 980;
            case FAR -> corePass ? 310 : 380;
        };
        int count = Math.max(64, Math.round(base * budget));
        Basis basis = Basis.from(camera);

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ (corePass ? 0x534D4F4B455F434FL : 0x534D4F4B455F5346L)
                ^ index * 0xD1B54A32D192ED03L);
            boolean decayedFire = unit(random, 0) < 0.58F;
            boolean inner = unit(random, 1) < 0.30F;
            float onset = decayedFire
                ? (inner ? 72.0F + unit(random, 2) * 105.0F
                    : 58.0F + unit(random, 2) * 115.0F)
                : 14.0F + unit(random, 2) * 110.0F;
            float localAge = age - onset;
            if (localAge < 0.0F) continue;
            float life = 230.0F + unit(random, 3) * 175.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;
            float lobe = unit(random, 4);
            float angle = unit(random, 5) * Mth.TWO_PI
                + localAge * signed(random, 6) * 0.0032F;
            float lobeRadius = outerRadius * (0.20F + lobe * 0.58F);
            float radialFraction = Mth.sqrt(unit(random, 7));
            boolean central = radialFraction < (corePass ? 0.66F : 0.36F);
            if (central != corePass) continue;

            float initialRise = decayedFire ? 2.0F + unit(random, 8) * 5.0F
                : 0.8F + unit(random, 8) * 3.2F;
            float coolingGravity = Math.max(0.0F, progress - 0.28F);
            float verticalDrop = coolingGravity * coolingGravity
                * (3.2F + unit(random, 9) * 5.8F);
            float centerY = craterFloor + outerRadius * (0.45F + lobe * 0.42F)
                + initialRise - verticalDrop;
            float radial = radialFraction * lobeRadius
                * (0.72F + unit(random, 10) * 0.48F);
            float noiseX = signed(random, 11) * outerRadius * 0.18F;
            float noiseZ = signed(random, 12) * outerRadius * 0.18F;
            float px = Mth.cos(angle) * radial + noiseX;
            float pz = Mth.sin(angle) * radial + noiseZ;
            float py = centerY + signed(random, 13) * outerRadius
                * (0.18F + 0.24F * (1.0F - radialFraction));
            py = Math.max(craterFloor * 0.12F, py);

            float particleSize = Mth.lerp(radialFraction,
                1.62F + scale * 0.68F, 0.52F + scale * 0.24F)
                * (0.74F + unit(random, 14) * 0.58F)
                * (1.0F + progress * 0.34F);
            float finalFade = progress < 0.78F ? 1.0F
                : smoothstep(Mth.clamp((1.0F - progress) / 0.22F, 0.0F, 1.0F));
            float alpha = (corePass ? 0.94F : 0.83F)
                * smoothstep(Mth.clamp(localAge / 7.0F, 0.0F, 1.0F))
                * finalFade;
            int tone = smokeTone(random, corePass, progress);
            billboard(pose, buffer, px, py, pz, particleSize,
                unit(random, 15) * Mth.TWO_PI
                    + localAge * signed(random, 16) * 0.0035F,
                tone, Math.min(190, tone + 3), Math.min(198, tone + 9),
                alpha, corePass ? 0x900090 : 0xA000A0, basis);
        }
    }

    private static void renderTendrils(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double rawAge, final float rawScale,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (rawAge < 3.0 || rawAge >= 240.0) return;
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        float budget = densityMultiplier();
        int streamCount = Math.max(8, Math.round((15.0F + scale * 10.0F) * budget));
        int puffBase = lod == WarheadMesh.Lod.NEAR ? 34
            : lod == WarheadMesh.Lod.MEDIUM ? 20 : 10;
        Basis basis = Basis.from(camera);
        final float gravity = 0.052F;

        for (int stream = 0; stream < streamCount; stream++) {
            long random = mix(seed ^ 0x54454E4452494C35L
                ^ stream * 0x94D049BB133111EBL);
            float onset = 2.0F + unit(random, 0) * 24.0F;
            float time = age - onset;
            if (time < 0.0F) continue;
            float angle = unit(random, 1) * Mth.TWO_PI;
            float speed = 0.38F + unit(random, 2) * (0.72F + scale * 0.18F);
            float upward = 0.38F + unit(random, 3) * (0.58F + scale * 0.15F);
            float startY = 0.55F + unit(random, 4) * 2.4F;
            float hitTime = (upward + Mth.sqrt(upward * upward + 2.0F * gravity * startY))
                / gravity;
            float visibleEnd = hitTime + 34.0F + unit(random, 5) * 22.0F;
            if (time > visibleEnd) continue;
            float headTime = Math.min(time, hitTime);
            int puffs = Math.max(5, Math.round(puffBase * budget));
            for (int puff = 0; puff < puffs; puff++) {
                float along = puff / (float) Math.max(1, puffs - 1);
                float sampleTime = headTime * along;
                long puffSeed = mix(random ^ puff * 0x9E3779B97F4A7C15L);
                float horizontal = speed * sampleTime;
                float pathNoise = Mth.sin(sampleTime * 0.31F
                    + unit(puffSeed, 0) * Mth.TWO_PI)
                    * (0.08F + along * 0.28F);
                float px = Mth.cos(angle) * horizontal
                    - Mth.sin(angle) * pathNoise
                    + signed(puffSeed, 1) * 0.14F;
                float pz = Mth.sin(angle) * horizontal
                    + Mth.cos(angle) * pathNoise
                    + signed(puffSeed, 2) * 0.14F;
                float py = startY + upward * sampleTime
                    - 0.5F * gravity * sampleTime * sampleTime
                    + signed(puffSeed, 3) * 0.16F;
                py = Math.max(0.08F, py);
                float ageBehindHead = headTime - sampleTime;
                float alpha = 0.96F * (1.0F - along * 0.24F)
                    * smoothstep(Mth.clamp((visibleEnd - time) / 24.0F, 0.0F, 1.0F));
                float particleSize = (0.34F + unit(puffSeed, 4) * 0.72F)
                    * (0.86F + scale * 0.16F)
                    * (1.0F + ageBehindHead * 0.006F);
                int selector = Math.floorMod((int) (puffSeed >>> 9), 100);
                int tone = selector < 13
                    ? 72 + Math.floorMod((int) (puffSeed >>> 18), 52)
                    : selector < 30
                        ? 156 + Math.floorMod((int) (puffSeed >>> 18), 44)
                        : 218 + Math.floorMod((int) (puffSeed >>> 18), 34);
                billboard(pose, buffer, px, py, pz, particleSize,
                    unit(puffSeed, 5) * Mth.TWO_PI,
                    tone, Math.min(255, tone + 3), Math.min(255, tone + 9),
                    alpha, 0xB000B0, basis);
            }
        }
    }

    private static boolean matches(final FirePass pass, final float temperature,
        final boolean inner) {
        return switch (pass) {
            case CORE -> temperature >= 0.74F && !inner;
            case HOT -> temperature >= 0.58F && inner;
            case COOLING -> temperature >= 0.12F && temperature < 0.74F;
        };
    }

    private static Colour fireColour(final float heat, final long random) {
        float noise = signed(random, 17) * 0.075F;
        float temperature = Mth.clamp(heat + noise, 0.0F, 1.0F);
        if (temperature > 0.84F) {
            float t = (temperature - 0.84F) / 0.16F;
            return new Colour(255, Mth.lerpInt(t, 194, 255),
                Mth.lerpInt(t, 28, 220));
        }
        if (temperature > 0.42F) {
            float t = (temperature - 0.42F) / 0.42F;
            return new Colour(255, Mth.lerpInt(t, 68, 194),
                Mth.lerpInt(t, 7, 28));
        }
        float t = temperature / 0.42F;
        return new Colour(Mth.lerpInt(t, 76, 255),
            Mth.lerpInt(t, 58, 68), Mth.lerpInt(t, 58, 7));
    }

    private static int smokeTone(final long random, final boolean core,
        final float progress) {
        float region = 0.5F + 0.5F * Mth.sin(
            (random & 0xFFFFL) * 0.00061F + progress * 9.0F);
        int base = core ? 43 : 76;
        int variation = Math.floorMod((int) (random >>> 21), core ? 64 : 82);
        return Mth.clamp(base + variation + (int) (region * 22.0F), 36, 184);
    }

    private static float densityMultiplier() {
        return Mth.clamp((float) Math.sqrt(
            WarheadRenderSettings.particleBudgetMultiplier() / 6.0F), 0.35F, 4.0F);
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
