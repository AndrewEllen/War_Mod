package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Persistent packed nuclear cloud. A crater-sized opaque plasma core feeds a
 * mixed fire/smoke stem, a growing toroidal cap, a descending outer curl and a
 * rolling base cloud. Field identity is stable and simulation never rewinds on
 * a one-frame age regression, preventing the visible mushroom from snapping
 * back to its initial column.
 */
public final class NuclearParticleCloudRenderer {
    /* Keep the aggregate allocation near the old six-field budget while allowing
       nine concurrent clouds to retain their simulation state. */
    private static final int CAPACITY = 32_768;
    private static final int LOGICAL_PARTICLES_PER_SIMULATED = 32;
    private static final int MAX_FIELDS = 9;
    private static final int DEPTH_BINS = 12;
    private static final Map<Long, Field> FIELDS = new LinkedHashMap<>(16, 0.75F, true);

    private NuclearParticleCloudRenderer() { }

    public static void renderPlasmaCore(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final List<? extends NuclearCloudSource> sources,
        final Quaternionf camera) {
        if (!valid(profile, age)) return;
        renderCraterPlasma(pose, buffer, age, visualScale, seed, sources, camera);
    }

    public static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final boolean hotPass,
        final List<? extends NuclearCloudSource> sources, final Quaternionf camera) {
        if (!valid(profile, age)) return;
        field(seed, visualScale, sources).render(pose, buffer, age, lod, camera,
            hotPass ? Pass.HOT_FIRE : Pass.COOL_FIRE);
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod,
        final List<? extends NuclearCloudSource> sources, final Quaternionf camera) {
        if (!valid(profile, age)) return;
        field(seed, visualScale, sources).render(pose, buffer, age, lod, camera, Pass.SMOKE);
    }

    public static synchronized DebugSnapshot debugSnapshot() {
        int active = 0;
        int spawned = 0;
        int culled = 0;
        for (Field field : FIELDS.values()) {
            active += field.activeCount * LOGICAL_PARTICLES_PER_SIMULATED;
            spawned += field.spawnedLastTick * LOGICAL_PARTICLES_PER_SIMULATED;
            culled += field.culledLastRender * LOGICAL_PARTICLES_PER_SIMULATED;
        }
        return new DebugSnapshot(active, spawned, culled, FIELDS.size());
    }

    private static boolean valid(final WarheadClientVisualProfile profile, final double age) {
        return profile != null && profile.payloadType() == WarheadPayloadType.NUCLEAR
            && age >= 0.0 && age < profile.totalImpactLifetimeTicks();
    }

    private static synchronized Field field(final long stableSeed, final float scale,
        final List<? extends NuclearCloudSource> sources) {
        Field existing = FIELDS.get(stableSeed);
        if (existing != null) {
            existing.updateSources(sources);
            return existing;
        }
        while (FIELDS.size() >= MAX_FIELDS) {
            Iterator<Long> iterator = FIELDS.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        Field created = new Field(stableSeed, scale, sources);
        FIELDS.put(stableSeed, created);
        return created;
    }

    private static void renderCraterPlasma(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final float visualScale,
        final long seed, final List<? extends NuclearCloudSource> sources,
        final Quaternionf camera) {
        final float end = 520.0F;
        if (age < 0.0 || age >= end) return;
        float scale = Mth.clamp(visualScale, 1.4F, 4.2F);
        float craterRadius = 12.0F + 13.0F * scale;
        float expansion = smoothstep(Mth.clamp((float) age / 24.0F, 0.0F, 1.0F));
        float contraction = age <= 78.0
            ? 1.0F
            : 1.0F - smoothstep(Mth.clamp(((float) age - 78.0F) / (end - 78.0F), 0.0F, 1.0F));
        float envelope = (0.72F + 0.32F * expansion) * contraction;
        float radius = craterRadius * envelope;
        if (radius <= 0.08F) return;
        float craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
        float centerY = craterFloor + radius * 0.78F;
        float heat = Mth.clamp(1.0F - (float) age / 440.0F, 0.0F, 1.0F);
        float fade = smoothstep(Mth.clamp((end - (float) age) / 72.0F, 0.0F, 1.0F));
        int red = 255;
        int green = Mth.lerpInt(heat, 72, 250);
        int blue = Mth.lerpInt(heat, 12, 198);
        int alpha = Mth.clamp((int) ((0.94F + 0.06F * heat) * fade * 255.0F), 0, 255);
        List<? extends NuclearCloudSource> actual = sources == null || sources.isEmpty()
            ? List.of(new NuclearCloudSource.Basic(Vec3.ZERO, age, scale, seed)) : sources;
        for (NuclearCloudSource source : actual) {
            Vec3 offset = source.offset();
            long sourceSeed = seed ^ source.seed();
            renderPlasmaSphere(pose, buffer, (float) offset.x,
                (float) offset.y + centerY, (float) offset.z,
                radius, red, green, blue, alpha, sourceSeed);
            if (age < 280.0) {
                renderPlasmaHalo(pose, buffer, (float) offset.x,
                    (float) offset.y + centerY, (float) offset.z,
                    radius, heat, fade, sourceSeed, camera);
            }
        }
    }

    private static void renderPlasmaSphere(final PoseStack.Pose pose,
        final VertexConsumer buffer, final float centerX, final float centerY,
        final float centerZ, final float radius, final int red, final int green,
        final int blue, final int alpha, final long seed) {
        int latitudeBands = 10;
        int longitudeBands = 20;
        float phase = unit(seed, 0) * Mth.TWO_PI;
        for (int latitude = 0; latitude < latitudeBands; latitude++) {
            float v0 = latitude / (float) latitudeBands;
            float v1 = (latitude + 1) / (float) latitudeBands;
            float phi0 = (v0 - 0.5F) * Mth.PI;
            float phi1 = (v1 - 0.5F) * Mth.PI;
            for (int longitude = 0; longitude < longitudeBands; longitude++) {
                float u0 = longitude / (float) longitudeBands;
                float u1 = (longitude + 1) / (float) longitudeBands;
                float theta0 = u0 * Mth.TWO_PI + phase;
                float theta1 = u1 * Mth.TWO_PI + phase;
                plasmaVertex(pose, buffer, centerX, centerY, centerZ, radius,
                    phi0, theta0, u0, v1, red, green, blue, alpha);
                plasmaVertex(pose, buffer, centerX, centerY, centerZ, radius,
                    phi1, theta0, u0, v0, red, green, blue, alpha);
                plasmaVertex(pose, buffer, centerX, centerY, centerZ, radius,
                    phi1, theta1, u1, v0, red, green, blue, alpha);
                plasmaVertex(pose, buffer, centerX, centerY, centerZ, radius,
                    phi0, theta1, u1, v1, red, green, blue, alpha);
            }
        }
    }

    private static void plasmaVertex(final PoseStack.Pose pose,
        final VertexConsumer buffer, final float centerX, final float centerY,
        final float centerZ, final float radius, final float phi,
        final float theta, final float u, final float v,
        final int red, final int green, final int blue, final int alpha) {
        float cosPhi = Mth.cos(phi);
        float nx = cosPhi * Mth.cos(theta);
        float ny = Mth.sin(phi);
        float nz = cosPhi * Mth.sin(theta);
        buffer.addVertex(pose, centerX + nx * radius, centerY + ny * radius,
                centerZ + nz * radius)
            .setColor(red, green, blue, alpha).setUv(u, v)
            .setOverlay(0).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
    }

    private static void renderPlasmaHalo(final PoseStack.Pose pose,
        final VertexConsumer buffer, final float centerX, final float centerY,
        final float centerZ, final float radius, final float heat,
        final float fade, final long seed, final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (int index = 0; index < 14; index++) {
            long random = mix(seed ^ index * 0x9E3779B97F4A7C15L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float elevation = signed(random, 1) * 0.78F;
            float ring = radius * (0.76F + unit(random, 2) * 0.30F);
            float horizontal = Mth.sqrt(Math.max(0.0F, 1.0F - elevation * elevation));
            float px = centerX + Mth.cos(angle) * horizontal * ring;
            float py = centerY + elevation * ring;
            float pz = centerZ + Mth.sin(angle) * horizontal * ring;
            float size = radius * (0.10F + unit(random, 3) * 0.10F);
            int green = Mth.lerpInt(heat, 92, 244);
            int blue = Mth.lerpInt(heat, 10, 164);
            billboard(pose, buffer, px, py, pz, size,
                unit(random, 4) * Mth.TWO_PI, 255, green, blue,
                0.78F * fade, 0xF000F0, basis);
        }
    }

    public record DebugSnapshot(int activeParticles, int spawnedParticlesPerTick,
        int culledParticles, int activeFields) { }

    private enum Pass { HOT_FIRE, COOL_FIRE, SMOKE }

    private static final class Field {
        private static final byte REGION_FIREBALL = 0;
        private static final byte REGION_STEM = 1;
        private static final byte REGION_CAP = 2;
        private static final byte REGION_OUTER_CURL = 3;
        private static final byte REGION_UNDER_CAP = 4;
        private static final byte REGION_BASE = 5;

        private final long seed;
        private final float scale;
        private List<? extends NuclearCloudSource> sources;
        private final float yield;
        private final float craterRadius;
        private final float craterFloor;
        private final int fireballEmissionEnd;
        private final int hotFeedEnd;
        private final int primaryFeedEnd;
        private final int feedEmissionEnd;
        private final int baseEmissionEnd;

        private final float[] x = new float[CAPACITY];
        private final float[] y = new float[CAPACITY];
        private final float[] z = new float[CAPACITY];
        private final float[] previousX = new float[CAPACITY];
        private final float[] previousY = new float[CAPACITY];
        private final float[] previousZ = new float[CAPACITY];
        private final float[] velocityX = new float[CAPACITY];
        private final float[] velocityY = new float[CAPACITY];
        private final float[] velocityZ = new float[CAPACITY];
        private final float[] temperature = new float[CAPACITY];
        private final float[] radius = new float[CAPACITY];
        private final float[] rotation = new float[CAPACITY];
        private final float[] angularVelocity = new float[CAPACITY];
        private final short[] particleAge = new short[CAPACITY];
        private final short[] lifetime = new short[CAPACITY];
        private final int[] particleSeed = new int[CAPACITY];
        private final byte[] region = new byte[CAPACITY];
        private final int[] activeSlots = new int[CAPACITY];
        private final int[] freeSlots = new int[CAPACITY];
        private final int[] hotBucket = new int[CAPACITY];
        private final int[] coolBucket = new int[CAPACITY];
        private final int[] smokeBucket = new int[CAPACITY];
        private final int[] ordered = new int[CAPACITY];
        private final int[] binCounts = new int[DEPTH_BINS];
        private final int[] binOffsets = new int[DEPTH_BINS];
        private final int[] binWrites = new int[DEPTH_BINS];

        private int simulatedTick = -1;
        private int freeCount;
        private int activeCount;
        private int spawnedLastTick;
        private int culledLastRender;
        private int bucketTick = Integer.MIN_VALUE;
        private WarheadMesh.Lod bucketLod;
        private int hotCount;
        private int coolCount;
        private int smokeCount;

        private Field(final long seed, final float scale,
            final List<? extends NuclearCloudSource> sources) {
            this.seed = seed;
            this.scale = Mth.clamp(scale, 1.4F, 4.2F);
            this.sources = normalizedSources(sources, scale, seed);
            this.yield = Mth.clamp(this.scale / 2.70F, 0.55F, 1.55F);
            this.craterRadius = 12.0F + 13.0F * this.scale;
            this.craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
            this.fireballEmissionEnd = Math.round(190.0F + 120.0F * yield);
            /* The cap now rises quickly, so retain a deliberately light hot feed for
               longer. This keeps the stalk connected without materially increasing
               the packed-field population once the initial cloud is established. */
            /* Keep the same packed population, but keep its existing central feed
               incandescent for the bulk of the impact lifetime.  The earlier
               values cooled the first stem before the mature cap had finished
               forming, which made the remaining smoke read as a separate stalk. */
            this.hotFeedEnd = Math.round(4_500.0F + 420.0F * yield);
            this.primaryFeedEnd = Math.round(4_200.0F + 480.0F * yield);
            this.feedEmissionEnd = Math.round(5_150.0F + 420.0F * yield);
            this.baseEmissionEnd = Math.round(760.0F + 260.0F * yield);
            initialiseSlots();
        }

        private void updateSources(final List<? extends NuclearCloudSource> updated) {
            if (updated != null && !updated.isEmpty()) this.sources = List.copyOf(updated);
        }

        private static List<? extends NuclearCloudSource> normalizedSources(
            final List<? extends NuclearCloudSource> input, final float scale,
            final long seed) {
            return input == null || input.isEmpty()
                ? List.of(new NuclearCloudSource.Basic(Vec3.ZERO, 0.0, scale, seed))
                : List.copyOf(input);
        }

        private void initialiseSlots() {
            for (int index = 0; index < CAPACITY; index++) {
                freeSlots[index] = CAPACITY - 1 - index;
            }
            freeCount = CAPACITY;
            activeCount = 0;
            invalidateBuckets();
        }

        private void ensureSimulated(final double renderedAge) {
            int target = Math.max(0, (int) Math.floor(renderedAge));
            if (target <= simulatedTick) return;
            if (simulatedTick < 0 && target > 44) {
                warmStart(target);
                return;
            }
            int gap = target - simulatedTick;
            if (gap > 96) simulatedTick = target - 24;
            int steps = 0;
            while (simulatedTick < target && steps++ < 32) {
                simulatedTick++;
                spawnedLastTick = 0;
                emit(simulatedTick);
                update(simulatedTick);
            }
            if (simulatedTick < target) simulatedTick = target;
        }

        private void warmStart(final int target) {
            simulatedTick = target;
            spawnedLastTick = 0;
            int population = Mth.clamp(13_000 + target * 15, 13_000, CAPACITY - 512);
            for (int ordinal = 0; ordinal < population; ordinal++) spawnWarm(target, ordinal);
            invalidateBuckets();
        }

        private void spawnWarm(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x5741524D5F4E5533L
                ^ ordinal * 0x9E3779B97F4A7C15L
                ^ (long) tick * 0xD1B54A32D192ED03L);
            float choice = unit(random, 0);
            float capY = capCenterY(tick);
            float capR = capRadius(tick);
            float capD = capDepth(tick);
            byte initialRegion;
            float px;
            float py;
            float pz;
            float vx;
            float vy;
            float vz;
            float heat;
            float particleRadius;
            if (choice < 0.16F) {
                initialRegion = REGION_BASE;
                float angle = unit(random, 1) * Mth.TWO_PI;
                float radial = Mth.sqrt(unit(random, 2)) * craterRadius * 1.18F;
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                py = craterFloor * unit(random, 3) * 0.28F + unit(random, 4) * 3.2F;
                vx = -Mth.sin(angle) * signed(random, 5) * 0.055F;
                vy = signed(random, 6) * 0.025F;
                vz = Mth.cos(angle) * signed(random, 5) * 0.055F;
                heat = 0.02F + unit(random, 7) * 0.18F;
                particleRadius = 1.6F + unit(random, 8) * 2.2F;
            } else if (choice < 0.36F) {
                initialRegion = REGION_STEM;
                float angle = unit(random, 1) * Mth.TWO_PI;
                float radial = Mth.sqrt(unit(random, 2)) * stemRadius(tick);
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                py = craterFloor + unit(random, 3) * Math.max(2.0F, capY - craterFloor);
                vx = -Mth.cos(angle) * radial * 0.004F;
                vy = 0.30F + unit(random, 4) * 0.52F;
                vz = -Mth.sin(angle) * radial * 0.004F;
                heat = unit(random, 5) < 0.58F
                    ? 0.08F + unit(random, 6) * 0.24F
                    : 0.42F + unit(random, 6) * 0.38F;
                particleRadius = 2.05F + unit(random, 7) * 2.35F;
            } else {
                float angle = unit(random, 1) * Mth.TWO_PI;
                float radialFraction = Mth.sqrt(unit(random, 2));
                float radial = radialFraction * capR;
                float shell = signed(random, 3);
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                py = capY + shell * capD * (0.34F + 0.66F * radialFraction);
                if (choice < 0.72F) initialRegion = REGION_CAP;
                else if (shell < -0.24F) initialRegion = REGION_UNDER_CAP;
                else initialRegion = REGION_OUTER_CURL;
                float radialX = radial > 0.001F ? px / radial : 0.0F;
                float radialZ = radial > 0.001F ? pz / radial : 0.0F;
                vx = initialRegion == REGION_CAP ? radialX * 0.16F : -radialX * 0.085F;
                vz = initialRegion == REGION_CAP ? radialZ * 0.16F : -radialZ * 0.085F;
                vy = initialRegion == REGION_OUTER_CURL ? -0.20F : 0.035F;
                heat = initialRegion == REGION_UNDER_CAP
                    ? 0.08F + unit(random, 4) * 0.24F
                    : 0.06F + unit(random, 4) * 0.38F;
                particleRadius = (1.35F + unit(random, 5) * 1.70F)
                    * Mth.lerp(radialFraction, 1.42F, 0.84F);
            }
            int slot = spawn(initialRegion, px, py, pz, vx, vy, vz, heat,
                particleRadius, 1_700 + Math.floorMod((int) random, 2_400), (int) random);
            if (slot >= 0) {
                particleAge[slot] = (short) Math.min(32_000,
                    Math.floorMod((int) (random >>> 32), 760));
            }
        }

        private void invalidateBuckets() {
            bucketTick = Integer.MIN_VALUE;
            bucketLod = null;
            hotCount = 0;
            coolCount = 0;
            smokeCount = 0;
        }

        private void emit(final int tick) {
            if (tick <= 12) {
                int initial = Math.round(310.0F + 205.0F * yield);
                for (int index = 0; index < initial; index++) spawnFireball(tick, index);
            }
            if (tick <= fireballEmissionEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, fireballEmissionEnd);
                int body = Math.round((48.0F + 36.0F * yield)
                    * (0.30F + 0.70F * remaining));
                for (int index = 0; index < body; index++) {
                    spawnFireball(tick, index + 30_000);
                }
            }
            if (tick <= feedEmissionEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, primaryFeedEnd);
                float sustained = Mth.clamp(1.0F - (tick - primaryFeedEnd)
                    / (float) Math.max(1, feedEmissionEnd - primaryFeedEnd), 0.0F, 1.0F);
                float feed = tick <= primaryFeedEnd
                    ? 0.38F + 0.62F * Math.max(0.0F, remaining)
                    : 0.20F + 0.22F * sustained;
                int stem = Math.round((36.0F + 28.0F * yield)
                    * feed);
                for (int index = 0; index < stem; index++) spawnStem(tick, index);
            }
            if (tick <= baseEmissionEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, baseEmissionEnd);
                int base = Math.round((28.0F + 24.0F * yield)
                    * (0.42F + 0.58F * remaining));
                for (int index = 0; index < base; index++) spawnBase(tick, index);
            }
        }

        private NuclearCloudSource source(final long random) {
            return sources.get(Math.floorMod((int) random, sources.size()));
        }

        private void spawnFireball(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x4649524542414C33L ^ ((long) tick << 32)
                ^ ordinal * 0x9E3779B97F4A7C15L);
            NuclearCloudSource source = source(random);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = Mth.sqrt(unit(random, 1));
            float radial = radialFraction * craterRadius * 1.02F;
            float dome = Mth.sqrt(Math.max(0.0F, 1.0F - radialFraction * radialFraction));
            float px = (float) source.offset().x + Mth.cos(angle) * radial;
            float pz = (float) source.offset().z + Mth.sin(angle) * radial;
            /* The old hemispherical launch distribution made a detached, fully formed
               dome before the feed column reached it. Keep the initial fireball dense,
               but deliberately flat: the cap is now built by the rising stem. */
            float py = (float) source.offset().y + craterFloor + 0.8F
                + dome * craterRadius * 0.34F + signed(random, 2) * 0.96F;
            float inward = -0.0034F * radial;
            float coreBias = 1.0F - radialFraction;
            spawn(REGION_FIREBALL, px, py, pz,
                Mth.cos(angle) * inward + signed(random, 3) * 0.018F,
                0.20F + unit(random, 4) * (0.34F + 0.14F * yield),
                Mth.sin(angle) * inward + signed(random, 5) * 0.018F,
                0.86F + unit(random, 6) * 0.14F,
                (1.10F + unit(random, 7) * 1.20F + coreBias * 0.92F)
                    * (0.94F + 0.10F * yield),
                Math.round(1_500.0F + unit(random, 8) * (1_600.0F + 680.0F * yield)),
                (int) random);
        }

        private void spawnStem(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x5354454D5F56334CL ^ ((long) tick << 31)
                ^ ordinal * 0xD1B54A32D192ED03L);
            NuclearCloudSource source = source(random);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = Mth.sqrt(unit(random, 1));
            float radial = radialFraction * craterRadius * 0.22F;
            float px = (float) source.offset().x + Mth.cos(angle) * radial;
            float pz = (float) source.offset().z + Mth.sin(angle) * radial;
            float py = (float) source.offset().y + craterFloor + 1.0F
                + unit(random, 2) * craterRadius * 0.32F;
            float hotRemaining = Mth.clamp(1.0F - tick / (float) Math.max(1, hotFeedEnd),
                0.0F, 1.0F);
            /* The stem has to stay a hot, connected column rather than changing
               abruptly into a smaller smoke-only feed.  Sparse smoke still rolls
               around it, but the central material remains bright until the final
               cooling phase. */
            boolean smoky = tick > hotFeedEnd || unit(random, 3) > 0.82F + hotRemaining * 0.12F;
            float heat = smoky
                ? 0.04F + unit(random, 4) * (0.16F + 0.18F * hotRemaining)
                : 0.78F + unit(random, 4) * (0.22F + 0.08F * hotRemaining);
            spawn(REGION_STEM, px, py, pz,
                -Mth.cos(angle) * radial * 0.006F + signed(random, 5) * 0.014F,
                (smoky ? 0.28F : 0.42F) + unit(random, 6) * (0.40F + 0.16F * yield),
                -Mth.sin(angle) * radial * 0.006F + signed(random, 7) * 0.014F,
                heat,
                (2.05F + unit(random, 8) * 2.35F + (1.0F - radialFraction) * 1.10F)
                    * (0.94F + 0.10F * yield),
                Math.round(4_800.0F + unit(random, 9) * (1_250.0F + 750.0F * yield)),
                (int) random);
        }

        private void spawnBase(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x424153455F56334CL ^ ((long) tick << 29)
                ^ ordinal * 0x94D049BB133111EBL);
            NuclearCloudSource source = source(random);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = Mth.sqrt(unit(random, 1)) * craterRadius * 1.16F;
            float target = craterRadius * (0.62F + unit(random, 2) * 0.48F);
            float tangent = signed(random, 3) * (0.035F + 0.020F * yield);
            float radialDifference = target - radial;
            spawn(REGION_BASE,
                (float) source.offset().x + Mth.cos(angle) * radial,
                (float) source.offset().y + craterFloor * unit(random, 4) * 0.18F
                    + unit(random, 5) * 3.2F,
                (float) source.offset().z + Mth.sin(angle) * radial,
                Mth.cos(angle) * radialDifference * 0.003F - Mth.sin(angle) * tangent,
                signed(random, 6) * 0.022F,
                Mth.sin(angle) * radialDifference * 0.003F + Mth.cos(angle) * tangent,
                0.015F + unit(random, 7) * 0.18F,
                (1.55F + unit(random, 8) * 2.15F) * (0.94F + 0.08F * yield),
                Math.round(1_100.0F + unit(random, 9) * 1_300.0F),
                (int) random);
        }

        private int spawn(final byte initialRegion, final float px, final float py,
            final float pz, final float vx, final float vy, final float vz,
            final float heat, final float particleRadius,
            final int particleLifetime, final int randomSeed) {
            int slot = reserve();
            if (slot < 0) return -1;
            x[slot] = previousX[slot] = px;
            y[slot] = previousY[slot] = py;
            z[slot] = previousZ[slot] = pz;
            velocityX[slot] = vx;
            velocityY[slot] = vy;
            velocityZ[slot] = vz;
            temperature[slot] = heat;
            radius[slot] = particleRadius;
            rotation[slot] = unit(randomSeed, 0) * Mth.TWO_PI;
            angularVelocity[slot] = signed(randomSeed, 1) * 0.016F;
            particleAge[slot] = 0;
            lifetime[slot] = (short) Mth.clamp(particleLifetime, 600, Short.MAX_VALUE);
            particleSeed[slot] = randomSeed;
            region[slot] = initialRegion;
            spawnedLastTick++;
            invalidateBuckets();
            return slot;
        }

        private int reserve() {
            if (freeCount <= 0) return -1;
            int slot = freeSlots[--freeCount];
            activeSlots[activeCount++] = slot;
            return slot;
        }

        private void removeActiveAt(final int activePosition) {
            int removedSlot = activeSlots[activePosition];
            int lastPosition = --activeCount;
            if (activePosition < lastPosition) activeSlots[activePosition] = activeSlots[lastPosition];
            freeSlots[freeCount++] = removedSlot;
        }

        private float capCenterY(final int tick) {
            /* Start attached to the rising feed instead of placing a small detached
               mushroom above it. The first phase is intentionally quicker, so the
               cap and stem read as one evolving cloud rather than a delayed dome. */
            float launch = craterRadius * 0.20F;
            float early = Math.min(tick, 150) * (0.33F + 0.055F * yield);
            float late = Mth.sqrt(Math.max(0.0F, tick - 150.0F))
                * (1.82F + 0.36F * yield);
            return launch + early + late;
        }

        private float capRadius(final int tick) {
            /* A near-zero initial cap prevents the isolated launch dome. Its shorter
               ramp still reaches the mature mushroom sooner than the previous curve. */
            float growth = Mth.sqrt(Mth.clamp(tick / 520.0F, 0.0F, 1.0F));
            float mature = 24.0F + 44.0F * yield;
            return mature * (0.025F + 0.975F * growth);
        }

        private float capDepth(final int tick) {
            return capRadius(tick) * (0.46F + 0.07F * yield);
        }

        private float stemRadius(final int tick) {
            float feed = tick < feedEmissionEnd ? 1.0F
                : Mth.clamp(1.0F - (tick - feedEmissionEnd) / 1_650.0F, 0.26F, 1.0F);
            return (6.2F + 7.4F * yield) * (0.46F + 0.54F * feed);
        }

        private void update(final int tick) {
            float capY = capCenterY(tick);
            float capR = capRadius(tick);
            float capD = capDepth(tick);
            float stemR = stemRadius(tick);
            int activePosition = 0;
            while (activePosition < activeCount) {
                int index = activeSlots[activePosition];
                int age = particleAge[index] & 0xFFFF;
                int life = lifetime[index] & 0xFFFF;
                if (age >= life) {
                    removeActiveAt(activePosition);
                    continue;
                }
                previousX[index] = x[index];
                previousY[index] = y[index];
                previousZ[index] = z[index];
                float progress = age / (float) Math.max(1, life);
                float radial = Mth.sqrt(x[index] * x[index] + z[index] * z[index]);
                float inverseRadial = radial > 0.001F ? 1.0F / radial : 0.0F;
                float radialX = x[index] * inverseRadial;
                float radialZ = z[index] * inverseRadial;
                float tangentX = -radialZ;
                float tangentZ = radialX;
                long turbulenceSeed = mix(((long) particleSeed[index] << 32)
                    ^ tick * 0x9E3779B97F4A7C15L);
                float turbulence = signed(turbulenceSeed, 0);
                float crossTurbulence = signed(turbulenceSeed, 1);

                switch (region[index]) {
                    case REGION_FIREBALL -> {
                        velocityX[index] += -radialX * 0.007F + turbulence * 0.0015F;
                        velocityZ[index] += -radialZ * 0.007F + crossTurbulence * 0.0015F;
                        velocityY[index] += 0.007F + temperature[index] * 0.008F;
                        velocityX[index] *= 0.978F;
                        velocityZ[index] *= 0.978F;
                        if (age > 58 || y[index] > craterRadius * 0.42F) {
                            region[index] = REGION_STEM;
                        }
                    }
                    case REGION_STEM -> {
                        velocityX[index] += -radialX * (0.020F + radial * 0.0012F)
                            + turbulence * 0.0017F;
                        velocityZ[index] += -radialZ * (0.020F + radial * 0.0012F)
                            + crossTurbulence * 0.0017F;
                        velocityY[index] += 0.012F + temperature[index] * 0.013F;
                        if (y[index] >= capY - capD * 0.18F
                            + signed(particleSeed[index], 2) * capD * 0.15F) {
                            region[index] = REGION_CAP;
                            velocityY[index] = Math.max(velocityY[index], 0.09F);
                        }
                    }
                    case REGION_CAP -> {
                        velocityX[index] += radialX * (0.024F + 0.012F * yield)
                            + tangentX * signed(particleSeed[index], 4) * 0.0018F
                            + turbulence * 0.0020F;
                        velocityZ[index] += radialZ * (0.024F + 0.012F * yield)
                            + tangentZ * signed(particleSeed[index], 4) * 0.0018F
                            + crossTurbulence * 0.0020F;
                        velocityY[index] += (capY - y[index]) * 0.0030F;
                        velocityY[index] *= 0.958F;
                        if (radial >= capR * (0.78F + unit(particleSeed[index], 5) * 0.18F)) {
                            region[index] = REGION_OUTER_CURL;
                        }
                    }
                    case REGION_OUTER_CURL -> {
                        velocityX[index] += -radialX * (0.010F + 0.004F * yield)
                            + tangentX * signed(particleSeed[index], 4) * 0.0012F;
                        velocityZ[index] += -radialZ * (0.010F + 0.004F * yield)
                            + tangentZ * signed(particleSeed[index], 4) * 0.0012F;
                        velocityY[index] -= 0.022F + 0.006F * yield;
                        if (y[index] <= capY - capD
                            * (0.68F + unit(particleSeed[index], 6) * 0.22F)) {
                            region[index] = REGION_UNDER_CAP;
                        }
                    }
                    case REGION_UNDER_CAP -> {
                        velocityX[index] += -radialX * (0.029F + 0.011F * yield)
                            + turbulence * 0.0015F;
                        velocityZ[index] += -radialZ * (0.029F + 0.011F * yield)
                            + crossTurbulence * 0.0015F;
                        velocityY[index] += (capY - capD * 0.92F - y[index]) * 0.0017F;
                        if (radial <= stemR * (1.04F + unit(particleSeed[index], 7) * 0.46F)) {
                            region[index] = REGION_STEM;
                            velocityX[index] *= 0.42F;
                            velocityZ[index] *= 0.42F;
                            velocityY[index] = Math.max(velocityY[index],
                                0.28F + temperature[index] * 0.27F);
                            temperature[index] = Math.min(0.68F, temperature[index] + 0.035F);
                        }
                    }
                    case REGION_BASE -> {
                        float targetRadius = craterRadius
                            * (0.66F + unit(particleSeed[index], 3) * 0.46F);
                        float radialError = targetRadius - radial;
                        float roll = signed(particleSeed[index], 4) * (0.012F + 0.006F * yield);
                        velocityX[index] += radialX * radialError * 0.0018F + tangentX * roll
                            + turbulence * 0.0014F;
                        velocityZ[index] += radialZ * radialError * 0.0018F + tangentZ * roll
                            + crossTurbulence * 0.0014F;
                        float targetY = craterFloor * 0.08F
                            + unit(particleSeed[index], 5) * 2.8F;
                        velocityY[index] += (targetY - y[index]) * 0.0022F;
                        if (age > 150 && Math.floorMod(particleSeed[index], 7) == 0
                            && radial < craterRadius * 0.72F) {
                            region[index] = REGION_STEM;
                            velocityY[index] = Math.max(velocityY[index], 0.22F);
                        }
                    }
                    default -> { }
                }

                velocityX[index] += turbulence * (0.0010F + progress * 0.0016F);
                velocityZ[index] += crossTurbulence * (0.0010F + progress * 0.0016F);
                velocityX[index] *= region[index] == REGION_BASE ? 0.975F : 0.987F;
                velocityY[index] *= region[index] == REGION_BASE ? 0.970F : 0.992F;
                velocityZ[index] *= region[index] == REGION_BASE ? 0.975F : 0.987F;
                x[index] += velocityX[index];
                y[index] += velocityY[index];
                z[index] += velocityZ[index];
                rotation[index] += angularVelocity[index];

                float insulation = region[index] == REGION_STEM
                    ? Mth.clamp(1.0F - radial / Math.max(1.0F, stemR * 1.60F), 0.0F, 1.0F)
                    : Mth.clamp(1.0F - radial / Math.max(1.0F, capR * 0.88F), 0.0F, 1.0F);
                float cooling = switch (region[index]) {
                    case REGION_FIREBALL -> 0.00105F;
                    case REGION_STEM -> 0.00058F;
                    case REGION_CAP -> 0.00145F;
                    case REGION_OUTER_CURL -> 0.00410F;
                    case REGION_UNDER_CAP -> 0.00155F;
                    case REGION_BASE -> 0.00125F;
                    default -> 0.0022F;
                };
                cooling *= 1.0F - insulation * 0.46F;
                temperature[index] = Math.max(0.0F,
                    temperature[index] - cooling * (0.80F + progress * 1.35F));
                if (region[index] == REGION_STEM && radial < stemR * 0.62F
                    && y[index] < capY - capD * 0.54F && tick < hotFeedEnd + 620) {
                    temperature[index] = Math.min(0.96F, temperature[index] + 0.00225F);
                }
                /* A turbulent, insulated kernel keeps the lower cap visibly hot
                   while its outer billboards cool into smoke. This is an in-place
                   temperature adjustment, not a second particle system. */
                if ((region[index] == REGION_CAP || region[index] == REGION_UNDER_CAP)
                    && radial < capR * 0.58F && y[index] < capY + capD * 0.28F
                    && tick < hotFeedEnd + 760) {
                    float coreHeat = 0.72F + 0.24F * Mth.clamp(
                        1.0F - tick / (float) Math.max(1, hotFeedEnd + 760), 0.0F, 1.0F);
                    temperature[index] = Math.max(temperature[index], coreHeat);
                }
                radius[index] *= region[index] == REGION_OUTER_CURL ? 1.00036F
                    : region[index] == REGION_BASE ? 1.00018F : 1.00014F;
                particleAge[index] = (short) (age + 1);
                activePosition++;
            }
            invalidateBuckets();
        }

        private void prepareBuckets(final int tick, final WarheadMesh.Lod lod) {
            if (bucketTick == tick && bucketLod == lod) return;
            hotCount = 0;
            coolCount = 0;
            smokeCount = 0;
            int stride = switch (lod) {
                case NEAR -> 2;
                case MEDIUM -> 4;
                case FAR -> 9;
            };
            int inspected = 0;
            int rejected = 0;
            for (int activePosition = 0; activePosition < activeCount; activePosition += stride) {
                int index = activeSlots[activePosition];
                inspected++;
                if (interior(index, tick, lod)) {
                    rejected++;
                    continue;
                }
                float heat = temperature[index];
                if (heat >= 0.70F) hotBucket[hotCount++] = index;
                else if (heat >= 0.28F) coolBucket[coolCount++] = index;
                else smokeBucket[smokeCount++] = index;
            }
            culledLastRender = Math.max(0, activeCount - inspected) + rejected;
            bucketTick = tick;
            bucketLod = lod;
        }

        private void render(final PoseStack.Pose pose, final VertexConsumer buffer,
            final double renderedAge, final WarheadMesh.Lod lod,
            final Quaternionf camera, final Pass pass) {
            ensureSimulated(renderedAge);
            int tick = Math.max(0, (int) Math.floor(renderedAge));
            prepareBuckets(tick, lod);
            float partial = (float) Mth.clamp(renderedAge - Math.floor(renderedAge), 0.0, 1.0);
            Basis basis = Basis.from(camera);
            int[] bucket;
            int count;
            switch (pass) {
                case HOT_FIRE -> { bucket = hotBucket; count = hotCount; }
                case COOL_FIRE -> { bucket = coolBucket; count = coolCount; }
                case SMOKE -> { bucket = smokeBucket; count = smokeCount; }
                default -> throw new IllegalStateException("Unknown nuclear pass " + pass);
            }
            int orderedCount = depthOrder(bucket, count, basis.normal);
            for (int position = 0; position < orderedCount; position++) {
                int index = ordered[position];
                int life = lifetime[index] & 0xFFFF;
                float progress = (particleAge[index] & 0xFFFF) / (float) Math.max(1, life);
                float alpha = alpha(pass, progress, temperature[index]);
                if (alpha <= 0.004F) continue;
                float px = Mth.lerp(partial, previousX[index], x[index]);
                float py = Mth.lerp(partial, previousY[index], y[index]);
                float pz = Mth.lerp(partial, previousZ[index], z[index]);
                /* Deterministic render-space turbulence breaks up the spherical cap
                   without increasing the simulated population or allocating per frame. */
                float noiseAmplitude = switch (region[index]) {
                    case REGION_CAP -> 1.15F;
                    case REGION_OUTER_CURL -> 1.55F;
                    case REGION_UNDER_CAP -> 1.30F;
                    case REGION_STEM -> 0.62F;
                    default -> 0.34F;
                };
                float noiseTime = tick + partial;
                float phase = noiseTime * (0.036F + unit(particleSeed[index], 12) * 0.042F)
                    + unit(particleSeed[index], 13) * Mth.TWO_PI;
                px += Mth.sin(phase) * noiseAmplitude
                    + signed(particleSeed[index], 14) * noiseAmplitude * 0.34F;
                pz += Mth.cos(phase * 1.17F + unit(particleSeed[index], 15) * Mth.PI)
                    * noiseAmplitude + signed(particleSeed[index], 16) * noiseAmplitude * 0.34F;
                py += Mth.sin(phase * 0.71F + unit(particleSeed[index], 17) * Mth.PI)
                    * noiseAmplitude * 0.30F;
                Colour colour = colour(temperature[index], progress, particleSeed[index], pass,
                    region[index]);
                float drawRadius = radius[index] * renderScale(index, tick, pass);
                int light = pass == Pass.SMOKE ? 0x900090 : 0xF000F0;
                billboard(pose, buffer, px, py, pz, drawRadius, rotation[index],
                    colour.red, colour.green, colour.blue, alpha, light, basis);
            }
        }

        private int depthOrder(final int[] bucket, final int count,
            final Vector3f normal) {
            if (count <= 1) {
                if (count == 1) ordered[0] = bucket[0];
                return count;
            }
            float minimum = Float.POSITIVE_INFINITY;
            float maximum = Float.NEGATIVE_INFINITY;
            for (int position = 0; position < count; position++) {
                int index = bucket[position];
                float depth = x[index] * normal.x + y[index] * normal.y + z[index] * normal.z;
                minimum = Math.min(minimum, depth);
                maximum = Math.max(maximum, depth);
            }
            Arrays.fill(binCounts, 0);
            float inverse = maximum > minimum
                ? (DEPTH_BINS - 0.001F) / (maximum - minimum) : 0.0F;
            for (int position = 0; position < count; position++) {
                int index = bucket[position];
                float depth = x[index] * normal.x + y[index] * normal.y + z[index] * normal.z;
                int bin = Mth.clamp((int) ((depth - minimum) * inverse), 0, DEPTH_BINS - 1);
                binCounts[bin]++;
            }
            int cursor = 0;
            for (int bin = DEPTH_BINS - 1; bin >= 0; bin--) {
                binOffsets[bin] = cursor;
                cursor += binCounts[bin];
            }
            System.arraycopy(binOffsets, 0, binWrites, 0, DEPTH_BINS);
            for (int position = 0; position < count; position++) {
                int index = bucket[position];
                float depth = x[index] * normal.x + y[index] * normal.y + z[index] * normal.z;
                int bin = Mth.clamp((int) ((depth - minimum) * inverse), 0, DEPTH_BINS - 1);
                ordered[binWrites[bin]++] = index;
            }
            return count;
        }

        private float renderScale(final int index, final int tick, final Pass pass) {
            float radial = Mth.sqrt(x[index] * x[index] + z[index] * z[index]);
            float density;
            if (region[index] == REGION_FIREBALL) {
                density = Mth.lerp(Mth.clamp(radial / Math.max(1.0F, craterRadius), 0.0F, 1.0F),
                    2.45F, 1.10F);
            } else if (region[index] == REGION_STEM) {
                density = Mth.lerp(Mth.clamp(radial / Math.max(1.0F, stemRadius(tick) * 1.50F),
                    0.0F, 1.0F), 2.30F, 1.02F);
            } else if (region[index] == REGION_BASE) {
                density = 1.30F;
            } else {
                float normalized = Mth.clamp(radial / Math.max(1.0F, capRadius(tick)), 0.0F, 1.0F);
                density = Mth.lerp(normalized, 2.18F, 0.92F);
                if (region[index] == REGION_OUTER_CURL) density *= 0.90F;
                if (region[index] == REGION_UNDER_CAP) density *= 1.24F;
            }
            /* Larger smoke cards cover the intentionally culled interior and remove
               visible holes at no extra billboard count. Lower alpha below balances
               their fill rate on the translucent pass. */
            if (pass == Pass.SMOKE) density *= 2.04F;
            else if (region[index] == REGION_STEM) density *= 1.56F;
            else if (region[index] == REGION_CAP || region[index] == REGION_UNDER_CAP) {
                density *= 1.42F;
            }
            return density;
        }

        private boolean interior(final int index, final int tick,
            final WarheadMesh.Lod lod) {
            float radial = Mth.sqrt(x[index] * x[index] + z[index] * z[index]);
            int keepModulo = lod == WarheadMesh.Lod.NEAR ? 8
                : lod == WarheadMesh.Lod.MEDIUM ? 13 : 21;
            if (region[index] == REGION_FIREBALL && radial < craterRadius * 0.60F) {
                return Math.floorMod(particleSeed[index], keepModulo) != 0;
            }
            if (region[index] == REGION_STEM && radial < stemRadius(tick) * 0.55F) {
                return Math.floorMod(particleSeed[index], keepModulo) != 0;
            }
            if (region[index] == REGION_CAP) {
                float capR = capRadius(tick);
                float capD = capDepth(tick);
                boolean deep = radial < capR * 0.60F
                    && Math.abs(y[index] - capCenterY(tick)) < capD * 0.48F;
                return deep && Math.floorMod(particleSeed[index], keepModulo + 3) != 0;
            }
            if (region[index] == REGION_BASE && radial < craterRadius * 0.48F) {
                return Math.floorMod(particleSeed[index], keepModulo + 4) != 0;
            }
            return false;
        }

        private static float alpha(final Pass pass, final float progress,
            final float heat) {
            float fadeIn = smoothstep(Mth.clamp(progress / 0.018F, 0.0F, 1.0F));
            float remaining = Mth.clamp(1.0F - progress, 0.0F, 1.0F);
            float fadeOut = (float) Math.pow(remaining, pass == Pass.SMOKE ? 0.74F : 0.58F);
            return switch (pass) {
                case HOT_FIRE -> Mth.clamp(0.90F * fadeIn * fadeOut
                    * (0.72F + heat * 0.28F), 0.0F, 0.95F);
                case COOL_FIRE -> Mth.clamp(0.74F * fadeIn * fadeOut, 0.0F, 0.82F);
                case SMOKE -> Mth.clamp(0.86F * fadeIn * fadeOut
                    * (0.86F + (1.0F - heat) * 0.14F), 0.0F, 0.92F);
            };
        }

        private static Colour colour(final float temperature, final float progress,
            final int seed, final Pass pass, final byte particleRegion) {
            float heat = Mth.clamp(temperature, 0.0F, 1.0F);
            if (pass == Pass.SMOKE || heat < 0.28F) {
                int variation = Math.floorMod(seed, 58);
                int ageDarkening = (int) (progress * 42.0F);
                int base = switch (particleRegion) {
                    case REGION_BASE -> 46;
                    case REGION_STEM -> 58;
                    case REGION_UNDER_CAP -> 96;
                    case REGION_OUTER_CURL -> 132;
                    case REGION_CAP -> 154;
                    default -> 78;
                };
                /* Sparse pale billboards create sunlit/ash-grey structure throughout
                   the cap, instead of making the entire mushroom uniformly white. */
                if (particleRegion == REGION_CAP
                    && Math.floorMod(seed >>> 12, 3) == 0) base += 66;
                if (particleRegion == REGION_OUTER_CURL
                    && Math.floorMod(seed >>> 15, 5) == 0) base += 54;
                if (particleRegion == REGION_BASE && progress < 0.34F) base -= 14;
                int tone = Mth.clamp(base - ageDarkening + variation, 20, 244);
                int greyShift = Math.floorMod(seed >>> 8, 13) - 6;
                return new Colour(Mth.clamp(tone + greyShift, 18, 244),
                    Mth.clamp(tone, 18, 244),
                    Mth.clamp(tone - greyShift, 18, 244));
            }
            if (heat > 0.90F) {
                float t = (heat - 0.90F) / 0.10F;
                return new Colour(255, Mth.lerpInt(t, 214, 252),
                    Mth.lerpInt(t, 62, 205));
            }
            if (heat > 0.58F) {
                float t = (heat - 0.58F) / 0.32F;
                return new Colour(255, Mth.lerpInt(t, 92, 214),
                    Mth.lerpInt(t, 12, 62));
            }
            /* The insulated inner stem stays a dark, emissive crimson before it
               becomes orange. It is routed through the existing cool-fire pass. */
            float t = (heat - 0.28F) / 0.30F;
            if (particleRegion == REGION_STEM) {
                return new Colour(Mth.lerpInt(t, 112, 255),
                    Mth.lerpInt(t, 18, 92), Mth.lerpInt(t, 16, 12));
            }
            return new Colour(Mth.lerpInt(t, 108, 255),
                Mth.lerpInt(t, 32, 92), Mth.lerpInt(t, 26, 12));
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
        final int red, final int green, final int blue,
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

    private static void vertex(final PoseStack.Pose pose,
        final VertexConsumer buffer, final float centerX, final float centerY,
        final float centerZ, final float localX, final float localY,
        final float cosine, final float sine, final float u, final float v,
        final int red, final int green, final int blue,
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

    private static float unit(final int value, final int lane) {
        return unit(((long) value << 32) ^ value, lane);
    }

    private static float signed(final int value, final int lane) {
        return unit(value, lane) * 2.0F - 1.0F;
    }
}
