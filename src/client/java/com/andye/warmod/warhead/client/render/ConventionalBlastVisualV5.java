package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Deterministic conventional blast made from a broad crater fire, a narrower
 * rising internal core, irregular smoke lobes and ballistic ejecta puffs.
 * Every non-nuclear smoke particle settles to ground and receives its own fade
 * window so no whole section of the effect disappears in one frame.
 */
public final class ConventionalBlastVisualV5 {
    private static final int MAX_CACHED_FIRE_FRAMES = 24;
    private static final Map<Long, FireFrame> FIRE_FRAMES =
        new LinkedHashMap<>(32, 0.75F, true);
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
        renderTurbulentShroud(pose, buffer, age, visualScale, seed, lod, camera);
        renderBallisticTrails(pose, buffer, age, visualScale, seed, lod, camera);
    }

    public static DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(lastActive, 0, lastCulled, 0);
    }

    static synchronized void clear() {
        FIRE_FRAMES.clear();
        lastActive = 0;
        lastCulled = 0;
    }

    private static void renderFire(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double rawAge, final float rawScale,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera,
        final FirePass requestedPass) {
        if (rawAge < 0.0 || rawAge >= 900.0) return;
        FireFrame frame = fireFrame(rawAge, rawScale, seed, lod);
        Basis basis = Basis.from(camera);
        for (int index = 0; index < frame.size; index++) {
            if (!matches(requestedPass, frame.temperature[index], frame.inner[index])) continue;
            float alpha = switch (requestedPass) {
                case CORE -> 0.99F;
                case HOT -> 0.96F;
                case COOLING -> 0.89F;
            } * frame.alphaFactor[index];
            billboard(pose, buffer, frame.x[index], frame.y[index], frame.z[index],
                frame.radius[index], frame.rotation[index], frame.red[index],
                frame.green[index], frame.blue[index], alpha,
                0xF000F0, basis);
        }
    }

    /**
     * The three fire layers use distinct render types, but their particle
     * distribution is identical. Building it once per submitted frame avoids
     * re-running the expensive deterministic volume math for every layer.
     */
    private static synchronized FireFrame fireFrame(final double rawAge,
        final float rawScale, final long seed, final WarheadMesh.Lod lod) {
        FireFrame existing = FIRE_FRAMES.get(seed);
        if (existing != null && existing.matches(rawAge, rawScale, lod)) return existing;
        FireFrame frame = buildFireFrame(rawAge, rawScale, lod, seed, existing);
        FIRE_FRAMES.put(seed, frame);
        while (FIRE_FRAMES.size() > MAX_CACHED_FIRE_FRAMES) {
            Iterator<Long> iterator = FIRE_FRAMES.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return frame;
    }

    private static FireFrame buildFireFrame(final double rawAge, final float rawScale,
        final WarheadMesh.Lod lod, final long seed, final FireFrame reusable) {
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        float smallYieldBoost = 1.0F + Mth.clamp((0.72F - scale) / 0.72F,
            0.0F, 1.0F) * 0.34F;
        float bodyRadius = craterRadius
            * (1.01F + unit(seed, 0) * 0.16F) * smallYieldBoost;
        float craterFloor = -Math.max(1.1F, craterRadius * 0.22F);
        float budget = densityMultiplier();
        int base = switch (lod) {
            case NEAR -> 3_850;
            case MEDIUM -> 1_850;
            case FAR -> 690;
        };
        int count = Math.max(128, Math.round(base * budget));
        int maximumCount = Math.max(count, Math.round(3_850 * budget));
        FireFrame frame = reusable != null && reusable.capacity() >= maximumCount
            ? reusable : new FireFrame(maximumCount);
        frame.reset(rawAge, rawScale, lod);
        int active = 0;
        int culled = 0;

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x464952455F563737L
                ^ index * 0x9E3779B97F4A7C15L);
            boolean inner = unit(random, 0) < 0.39F;
            float onset = inner ? 3.0F + unit(random, 1) * 22.0F
                : unit(random, 1) * 12.0F;
            float localAge = age - onset;
            if (localAge < 0.0F) {
                culled++;
                continue;
            }

            float coolingTime = inner
                ? 570.0F + unit(random, 2) * 180.0F
                : 485.0F + unit(random, 2) * 165.0F;
            float temperature = Mth.clamp(1.12F - localAge / coolingTime
                + signed(random, 3) * 0.105F, 0.0F, 1.0F);
            if (temperature < 0.08F) {
                culled++;
                continue;
            }

            float contractionStart = inner ? 245.0F : 205.0F;
            float contractionDuration = inner ? 500.0F : 475.0F;
            float contraction = smoothstep(Mth.clamp(
                (localAge - contractionStart) / contractionDuration,
                0.0F, 1.0F));
            float radiusScale = Mth.lerp(contraction,
                inner ? 0.66F : 1.0F,
                inner ? 0.28F : 0.43F);
            float volumeRadius = bodyRadius * radiusScale;
            float rise = Mth.lerp(smoothstep(Mth.clamp(localAge / 430.0F,
                0.0F, 1.0F)), 0.0F, inner ? 8.6F : 4.4F);
            float centerY = craterFloor + bodyRadius * (inner ? 0.72F : 0.49F)
                + rise;

            float angle = unit(random, 4) * Mth.TWO_PI
                + localAge * signed(random, 5) * (inner ? 0.0046F : 0.0025F);
            float radialFraction = Mth.sqrt(unit(random, 6));
            float shellY = signed(random, 7);
            if (buriedFire(random, radialFraction, shellY, inner, lod)) {
                culled++;
                continue;
            }
            float dome = Mth.sqrt(Math.max(0.0F,
                1.0F - radialFraction * radialFraction));
            float horizontal = radialFraction * volumeRadius
                * (0.78F + unit(random, 8) * 0.39F);
            float verticalRadius = volumeRadius * (inner ? 1.62F : 1.20F)
                * (0.80F + unit(random, 9) * 0.38F);
            float clusterNoise = Mth.sin(unit(random, 10) * Mth.TWO_PI
                + localAge * 0.033F) * volumeRadius * 0.12F;
            float px = Mth.cos(angle) * horizontal
                + Mth.cos(angle + Mth.HALF_PI) * clusterNoise
                + signed(random, 11) * volumeRadius * 0.11F;
            float pz = Mth.sin(angle) * horizontal
                + Mth.sin(angle + Mth.HALF_PI) * clusterNoise
                + signed(random, 12) * volumeRadius * 0.11F;
            float py = centerY + shellY * verticalRadius * dome;
            if (inner) py += Mth.abs(shellY) * bodyRadius * 0.14F;

            float particleSize = Mth.lerp(radialFraction,
                1.82F + scale * 0.82F, 0.40F + scale * 0.24F)
                * (0.68F + unit(random, 13) * 0.64F);
            if (inner) particleSize *= 0.91F;
            float remaining = Mth.clamp(1.0F - localAge / (coolingTime + 150.0F),
                0.0F, 1.0F);
            float fadeStart = 0.78F + unit(random, 18) * 0.15F;
            float lifeProgress = localAge / (coolingTime + 150.0F);
            float individualFade = lifeProgress < fadeStart ? 1.0F
                : smoothstep(Mth.clamp((1.0F - lifeProgress)
                    / Math.max(0.04F, 1.0F - fadeStart), 0.0F, 1.0F));
            float alphaFactor = smoothstep(Mth.clamp(localAge / 5.0F, 0.0F, 1.0F))
                * (float) Math.pow(remaining, 0.28F) * individualFade;
            Colour colour = fireColour(temperature, random);
            frame.add(px, py, pz, particleSize,
                unit(random, 14) * Mth.TWO_PI
                    + localAge * signed(random, 15) * 0.0055F,
                colour.red, colour.green, colour.blue, alphaFactor, temperature, inner);
            active++;
        }
        lastActive = active;
        lastCulled = culled;
        return frame;
    }

    private static void renderSmoke(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double rawAge, final float rawScale,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera,
        final boolean corePass) {
        if (rawAge < 6.0 || rawAge >= 1_220.0) return;
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        float smallYieldBoost = 1.0F + Mth.clamp((0.72F - scale) / 0.72F,
            0.0F, 1.0F) * 0.28F;
        float bodyRadius = craterRadius
            * (0.96F + unit(seed, 0) * 0.16F) * smallYieldBoost;
        float craterFloor = -Math.max(1.1F, craterRadius * 0.22F);
        float budget = densityMultiplier();
        int base = switch (lod) {
            case NEAR -> corePass ? 2_850 : 3_450;
            case MEDIUM -> corePass ? 1_360 : 1_620;
            case FAR -> corePass ? 500 : 610;
        };
        int count = Math.max(128, Math.round(base * budget));
        Basis basis = Basis.from(camera);

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ (corePass
                ? 0x534D4F4B455F4337L : 0x534D4F4B455F5337L)
                ^ index * 0xD1B54A32D192ED03L);
            boolean fromInnerFire = unit(random, 0) < 0.37F;
            /* Smoke must overlap the still-burning body instead of arriving only
               after the outer fire has contracted. Staggering is retained so the
               layer grows in rather than appearing as one opaque shell. */
            float onset = fromInnerFire
                ? 24.0F + unit(random, 1) * 170.0F
                : 8.0F + unit(random, 1) * 155.0F;
            float localAge = age - onset;
            if (localAge < 0.0F) continue;
            float life = 650.0F + unit(random, 2) * 410.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;

            float radialFraction = Mth.sqrt(unit(random, 3));
            boolean central = radialFraction < (corePass ? 0.69F : 0.35F);
            if (central != corePass) continue;
            if (buriedSmoke(random, radialFraction, corePass, lod)) continue;
            float clusterAngle = unit(random, 4) * Mth.TWO_PI;
            float clusterDistance = bodyRadius * unit(random, 5)
                * (corePass ? 0.27F : 0.72F);
            float clusterX = Mth.cos(clusterAngle) * clusterDistance;
            float clusterZ = Mth.sin(clusterAngle) * clusterDistance;
            float localAngle = unit(random, 6) * Mth.TWO_PI
                + localAge * signed(random, 7) * 0.0025F;
            float lobeRadius = bodyRadius * (fromInnerFire ? 0.60F : 0.88F)
                * (0.68F + unit(random, 8) * 0.44F);
            float radial = radialFraction * lobeRadius;

            float earlyRise = fromInnerFire
                ? 5.0F + unit(random, 9) * 6.8F
                : 2.2F + unit(random, 9) * 5.2F;
            float airborneY = craterFloor + bodyRadius
                * (fromInnerFire ? 0.82F : 0.50F) + earlyRise
                + signed(random, 13) * bodyRadius
                    * (fromInnerFire ? 0.49F : 0.38F);
            /* Keep the turbulent body airborne through most of its useful life.
               Ground settling is a late transition, not the main motion. */
            float settleStart = 0.38F + unit(random, 10) * 0.20F;
            float settleEnd = 0.82F + unit(random, 11) * 0.13F;
            float settle = smoothstep(Mth.clamp((progress - settleStart)
                / Math.max(0.10F, settleEnd - settleStart), 0.0F, 1.0F));
            float groundY = 0.08F + unit(random, 12) * (corePass ? 0.55F : 0.36F);
            float px = clusterX + Mth.cos(localAngle) * radial
                + signed(random, 14) * bodyRadius * 0.11F;
            float pz = clusterZ + Mth.sin(localAngle) * radial
                + signed(random, 15) * bodyRadius * 0.11F;
            float py = Mth.lerp(settle, airborneY, groundY);

            float particleSize = Mth.lerp(radialFraction,
                2.05F + scale * 0.84F, 0.30F + scale * 0.22F)
                * (0.60F + unit(random, 16) * 0.78F)
                * (1.0F + progress * 0.50F);
            float fadeStart = 0.78F + unit(random, 17) * 0.17F;
            float finalFade = progress < fadeStart ? 1.0F
                : smoothstep(Mth.clamp((1.0F - progress)
                    / Math.max(0.035F, 1.0F - fadeStart), 0.0F, 1.0F));
            float alpha = (corePass ? 0.96F : 0.88F)
                * smoothstep(Mth.clamp(localAge / 9.0F, 0.0F, 1.0F))
                * finalFade;
            int tone = smokeTone(random, corePass, progress);
            billboard(pose, buffer, px, py, pz, particleSize,
                unit(random, 18) * Mth.TWO_PI
                    + localAge * signed(random, 19) * 0.0030F,
                tone, tone, tone, alpha,
                corePass ? 0x900090 : 0xA000A0, basis);
        }
    }

    /**
     * Irregular wall around the fireball. It begins at the same temperature as
     * the core, then cools into varied grey lobes with deliberately uneven
     * elevations and gaps rather than a uniform circling cylinder.
     */
    private static void renderTurbulentShroud(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double rawAge, final float rawScale,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (rawAge < 4.0 || rawAge >= 980.0) return;
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        float bodyRadius = craterRadius * (1.02F + unit(seed, 20) * 0.12F);
        int base = switch (lod) {
            case NEAR -> 1_750;
            case MEDIUM -> 820;
            case FAR -> 300;
        };
        int count = Math.max(72, Math.round(base * densityMultiplier()));
        Basis basis = Basis.from(camera);

        for (int index = 0; index < count; index++) {
            long random = mix(seed ^ 0x5348524F55445F37L
                ^ index * 0x94D049BB133111EBL);
            float onset = unit(random, 0) * 60.0F;
            float localAge = age - onset;
            if (localAge < 0.0F) continue;
            float life = 430.0F + unit(random, 1) * 360.0F;
            if (localAge >= life) continue;
            float progress = localAge / life;
            float angle = unit(random, 2) * Mth.TWO_PI
                + localAge * signed(random, 3) * 0.0044F;
            float sectorWave = 0.5F + 0.5F * Mth.sin(angle * 3.0F
                + unit(random, 4) * Mth.TWO_PI);
            float radial = bodyRadius * (0.68F + unit(random, 5) * 0.31F)
                * (1.0F - progress * 0.22F);
            float heightBand = bodyRadius * (0.20F + sectorWave * 0.78F)
                + signed(random, 6) * bodyRadius * 0.20F;
            float settle = smoothstep(Mth.clamp((progress - 0.62F)
                / (0.24F + unit(random, 7) * 0.10F), 0.0F, 1.0F));
            float py = Mth.lerp(settle,
                -Math.max(1.1F, craterRadius * 0.22F) + heightBand,
                0.08F + unit(random, 8) * 0.42F);
            float px = Mth.cos(angle) * radial
                + signed(random, 9) * bodyRadius * 0.13F;
            float pz = Mth.sin(angle) * radial
                + signed(random, 10) * bodyRadius * 0.13F;
            float heat = Mth.clamp(1.04F - localAge
                / (430.0F + unit(random, 11) * 160.0F)
                + signed(random, 12) * 0.08F, 0.0F, 1.0F);
            Colour colour;
            int light;
            if (heat > 0.17F) {
                colour = fireColour(heat, random);
                light = 0xF000F0;
            } else {
                int tone = smokeTone(random, false, progress);
                colour = new Colour(tone, tone, tone);
                light = 0xA000A0;
            }
            float particleSize = (0.34F + unit(random, 13) * 1.80F)
                * (0.88F + scale * 0.24F) * (1.0F + progress * 0.36F);
            float fadeStart = 0.76F + unit(random, 14) * 0.20F;
            float fade = progress < fadeStart ? 1.0F
                : smoothstep(Mth.clamp((1.0F - progress)
                    / Math.max(0.025F, 1.0F - fadeStart), 0.0F, 1.0F));
            float alpha = (0.72F + unit(random, 15) * 0.24F) * fade;
            billboard(pose, buffer, px, py, pz, particleSize,
                unit(random, 16) * Mth.TWO_PI
                    + localAge * signed(random, 17) * 0.004F,
                colour.red, colour.green, colour.blue, alpha, light, basis);
        }
    }

    private static void renderBallisticTrails(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double rawAge, final float rawScale,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (rawAge < 2.0 || rawAge >= 1_150.0) return;
        float age = (float) rawAge;
        float scale = Mth.clamp(rawScale, 0.28F, 1.75F);
        float craterRadius = 2.0F + 13.5F * scale;
        int streamCount = Mth.clamp(Math.round(1.5F + scale * 5.2F), 3, 12);
        int puffLimit = switch (lod) {
            case NEAR -> 34;
            case MEDIUM -> 21;
            case FAR -> 10;
        };
        float density = Mth.clamp(densityMultiplier(), 0.70F, 2.25F);
        Basis basis = Basis.from(camera);
        final float gravity = 0.052F;

        for (int stream = 0; stream < streamCount; stream++) {
            long random = mix(seed ^ 0x42414C4C49535437L
                ^ stream * 0x94D049BB133111EBL);
            float onset = 1.0F + unit(random, 0) * 30.0F;
            float streamAge = age - onset;
            if (streamAge < 0.0F) continue;
            float angle = unit(random, 1) * Mth.TWO_PI;
            float speed = 0.30F + unit(random, 2)
                * (0.47F + scale * 0.21F);
            float upward = 0.36F + unit(random, 3)
                * (0.52F + scale * 0.15F);
            float startY = 0.65F + unit(random, 4) * Math.min(3.4F,
                craterRadius * 0.30F);
            float drag = 0.022F + unit(random, 5) * 0.030F;
            float hitTime = (upward + Mth.sqrt(upward * upward
                + 2.0F * gravity * startY)) / gravity;
            float emissionEnd = Math.min(hitTime,
                38.0F + unit(random, 6) * (24.0F + scale * 12.0F));
            float interval = (2.35F + unit(random, 7) * 3.10F) / density;
            int possiblePuffs = Math.min(puffLimit,
                1 + (int) Math.floor(emissionEnd / interval));
            float smokeLife = 430.0F + unit(random, 8) * 370.0F;

            for (int puff = 0; puff < possiblePuffs; puff++) {
                long puffSeed = mix(random ^ puff * 0x9E3779B97F4A7C15L);
                float emissionTime = puff * interval
                    + signed(puffSeed, 0) * interval * 0.28F;
                emissionTime = Math.max(0.0F, emissionTime);
                if (emissionTime > streamAge) break;
                float puffAge = streamAge - emissionTime;
                if (puffAge >= smokeLife) continue;

                float launchDistance = dragDistance(speed, drag, emissionTime);
                float launchY = startY + upward * emissionTime
                    - 0.5F * gravity * emissionTime * emissionTime;
                launchY = Math.max(0.08F, launchY);
                float remainingVelocity = speed
                    * (float) Math.exp(-drag * emissionTime);
                float smokeDrift = dragDistance(remainingVelocity * 0.09F,
                    0.060F, Math.min(puffAge, 42.0F));
                float crossNoise = Mth.sin(emissionTime * 0.47F
                    + unit(puffSeed, 1) * Mth.TWO_PI)
                    * (0.16F + puffAge * 0.0060F);
                float px = Mth.cos(angle) * (launchDistance + smokeDrift)
                    - Mth.sin(angle) * crossNoise
                    + signed(puffSeed, 2) * 0.38F;
                float pz = Mth.sin(angle) * (launchDistance + smokeDrift)
                    + Mth.cos(angle) * crossNoise
                    + signed(puffSeed, 3) * 0.38F;
                float progress = puffAge / smokeLife;
                float settleStart = 0.38F + unit(puffSeed, 4) * 0.18F;
                float settleEnd = 0.80F + unit(puffSeed, 5) * 0.14F;
                float settle = smoothstep(Mth.clamp((progress - settleStart)
                    / Math.max(0.12F, settleEnd - settleStart), 0.0F, 1.0F));
                float airborneY = launchY + 0.03F * Math.min(puffAge, 18.0F);
                float py = Mth.lerp(settle, airborneY,
                    0.08F + unit(puffSeed, 6) * 0.32F);

                float fadeStart = 0.76F + unit(puffSeed, 7) * 0.21F;
                float fade = progress < fadeStart ? 1.0F
                    : smoothstep(Mth.clamp((1.0F - progress)
                        / Math.max(0.025F, 1.0F - fadeStart), 0.0F, 1.0F));
                float particleSize = (0.34F + unit(puffSeed, 8) * 1.22F)
                    * (0.90F + scale * 0.18F)
                    * (1.0F + puffAge * 0.0065F);
                int selector = Math.floorMod((int) (puffSeed >>> 9), 100);
                int tone = selector < 8
                    ? 84 + Math.floorMod((int) (puffSeed >>> 19), 48)
                    : selector < 22
                        ? 166 + Math.floorMod((int) (puffSeed >>> 19), 42)
                        : 218 + Math.floorMod((int) (puffSeed >>> 19), 36);
                float alpha = (0.76F + unit(puffSeed, 9) * 0.18F) * fade;
                billboard(pose, buffer, px, py, pz, particleSize,
                    unit(puffSeed, 10) * Mth.TWO_PI
                        + puffAge * signed(puffSeed, 11) * 0.003F,
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

    /**
     * Preserve the visible shell while deterministically thinning billboards
     * buried in the hot volume. Stable seed-based retention avoids temporal
     * sparkle and attacks translucent overdraw without changing simulation or
     * colour calculations.
     */
    private static boolean buriedFire(final long random, final float radial,
        final float vertical, final boolean inner, final WarheadMesh.Lod lod) {
        float radialLimit = inner ? 0.48F : 0.56F;
        if (radial >= radialLimit || Math.abs(vertical) >= 0.62F) return false;
        int keepModulo = switch (lod) {
            case NEAR -> 3;
            case MEDIUM -> 4;
            case FAR -> 6;
        };
        return Math.floorMod((int) (random >>> 32), keepModulo) != 0;
    }

    /** Layer-aware interior rejection for the two smoke volumes. */
    private static boolean buriedSmoke(final long random, final float radial,
        final boolean core, final WarheadMesh.Lod lod) {
        float radialLimit = core ? 0.43F : 0.61F;
        if (radial >= radialLimit) return false;
        int keepModulo = switch (lod) {
            case NEAR -> core ? 3 : 4;
            case MEDIUM -> core ? 4 : 5;
            case FAR -> core ? 6 : 7;
        };
        return Math.floorMod((int) (random >>> 24), keepModulo) != 0;
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
            WarheadRenderSettings.particleBudgetMultiplier() / 10.0F),
            0.35F, 6.0F);
    }

    public record DebugSnapshot(int activeParticles, int spawnedParticlesPerTick,
        int culledParticles, int activeFields) { }

    private enum FirePass { CORE, HOT, COOLING }

    private static final class FireFrame {
        private long ageBits;
        private int scaleBits;
        private WarheadMesh.Lod lod;
        private final float[] x;
        private final float[] y;
        private final float[] z;
        private final float[] radius;
        private final float[] rotation;
        private final float[] alphaFactor;
        private final float[] temperature;
        private final boolean[] inner;
        private final int[] red;
        private final int[] green;
        private final int[] blue;
        private int size;

        private FireFrame(final int capacity) {
            this.x = new float[capacity];
            this.y = new float[capacity];
            this.z = new float[capacity];
            this.radius = new float[capacity];
            this.rotation = new float[capacity];
            this.alphaFactor = new float[capacity];
            this.temperature = new float[capacity];
            this.inner = new boolean[capacity];
            this.red = new int[capacity];
            this.green = new int[capacity];
            this.blue = new int[capacity];
        }

        private int capacity() {
            return x.length;
        }

        private void reset(final double age, final float scale,
            final WarheadMesh.Lod candidateLod) {
            ageBits = Double.doubleToLongBits(age);
            scaleBits = Float.floatToIntBits(scale);
            lod = candidateLod;
            size = 0;
        }

        private boolean matches(final double age, final float scale,
            final WarheadMesh.Lod candidateLod) {
            return ageBits == Double.doubleToLongBits(age)
                && scaleBits == Float.floatToIntBits(scale) && lod == candidateLod;
        }

        private void add(final float px, final float py, final float pz,
            final float particleRadius, final float particleRotation,
            final int particleRed, final int particleGreen, final int particleBlue,
            final float particleAlphaFactor, final float particleTemperature,
            final boolean particleInner) {
            int index = size++;
            x[index] = px;
            y[index] = py;
            z[index] = pz;
            radius[index] = particleRadius;
            rotation[index] = particleRotation;
            alphaFactor[index] = particleAlphaFactor;
            temperature[index] = particleTemperature;
            inner[index] = particleInner;
            red[index] = particleRed;
            green[index] = particleGreen;
            blue[index] = particleBlue;
        }
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
