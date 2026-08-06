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
 * Dense particle-only nuclear cloud with a shrinking crater plasma mesh.
 *
 * <p>The visible population is deliberately much smaller than the logical
 * population. Large inner particles and a depth-writing plasma sphere provide
 * the dense volume, while the packed simulation concentrates on the moving
 * outer surface of the fireball, stem and toroidal cap. Material buckets are
 * exclusive, so hot fire no longer double-renders over cooling fire or smoke.</p>
 */
public final class NuclearParticleCloudRenderer {
    private static final int CAPACITY = 49_152;
    private static final int LOGICAL_PARTICLES_PER_SIMULATED = 32;
    private static final int MAX_FIELDS = 2;
    private static final int DEPTH_BINS = 12;
    private static final Map<Long, Field> FIELDS = new LinkedHashMap<>();

    private NuclearParticleCloudRenderer() { }

    public static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final boolean hotPass,
        final List<? extends NuclearCloudSource> sources, final Quaternionf camera) {
        if (!valid(profile, age)) return;
        if (hotPass) renderPlasmaCore(pose, buffer, age, visualScale, seed, sources, camera);
        field(seed, visualScale, sources).render(pose, buffer, age, lod, camera,
            hotPass ? Pass.HOT_FIRE : Pass.COOL_FIRE);
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final List<? extends NuclearCloudSource> sources,
        final Quaternionf camera) {
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

    private static synchronized Field field(final long seed, final float scale,
        final List<? extends NuclearCloudSource> sources) {
        long signature = sourceSignature(sources);
        long key = seed ^ Long.rotateLeft(signature, 21);
        Field existing = FIELDS.get(key);
        if (existing != null) return existing;
        while (FIELDS.size() >= MAX_FIELDS) {
            Iterator<Long> iterator = FIELDS.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        Field created = new Field(seed, scale, sources, signature);
        FIELDS.put(key, created);
        return created;
    }

    private static long sourceSignature(final List<? extends NuclearCloudSource> sources) {
        long value = 0x4E55434C45415253L;
        if (sources == null) return value;
        for (NuclearCloudSource source : sources) {
            value ^= mix(source.seed() ^ Double.doubleToLongBits(source.offset().x)
                ^ Long.rotateLeft(Double.doubleToLongBits(source.offset().y), 17)
                ^ Long.rotateLeft(Double.doubleToLongBits(source.offset().z), 37));
        }
        return value;
    }

    private static void renderPlasmaCore(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final long seed,
        final List<? extends NuclearCloudSource> sources, final Quaternionf camera) {
        if (age > 360.0) return;
        float scale = Mth.clamp(visualScale, 1.4F, 4.2F);
        float craterRadius = 12.0F + 13.0F * scale;
        float life = Mth.clamp((float) (1.0 - age / 360.0), 0.0F, 1.0F);
        float shrink = 0.12F + 0.88F * (float) Math.pow(life, 0.42);
        float radius = craterRadius * 0.76F * shrink;
        if (radius < 0.7F) return;
        float centerY = -Math.max(7.0F, craterRadius * 0.30F) + radius * 0.72F;
        float heat = Mth.clamp((float) (1.0 - age / 300.0), 0.0F, 1.0F);
        int red = 255;
        int green = Mth.lerpInt(heat, 82, 250);
        int blue = Mth.lerpInt(heat, 18, 205);
        int alpha = Mth.clamp((int) ((0.72F + heat * 0.24F) * 255.0F), 0, 255);
        List<? extends NuclearCloudSource> actualSources = sources == null || sources.isEmpty()
            ? List.of(new NuclearCloudSource.Basic(Vec3.ZERO, age, scale, seed)) : sources;
        for (NuclearCloudSource source : actualSources) {
            Vec3 offset = source.offset();
            renderPlasmaSphere(pose, buffer, (float) offset.x, (float) offset.y + centerY,
                (float) offset.z, radius, red, green, blue, alpha, seed ^ source.seed());
            renderPlasmaHalo(pose, buffer, (float) offset.x, (float) offset.y + centerY,
                (float) offset.z, radius, heat, seed ^ source.seed(), camera);
        }
    }

    private static void renderPlasmaSphere(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ, final float radius,
        final int red, final int green, final int blue, final int alpha, final long seed) {
        int latitudeBands = 7;
        int longitudeBands = 14;
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

    private static void plasmaVertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ, final float radius,
        final float phi, final float theta, final float u, final float v,
        final int red, final int green, final int blue, final int alpha) {
        float cosPhi = Mth.cos(phi);
        float nx = cosPhi * Mth.cos(theta);
        float ny = Mth.sin(phi);
        float nz = cosPhi * Mth.sin(theta);
        buffer.addVertex(pose, centerX + nx * radius, centerY + ny * radius, centerZ + nz * radius)
            .setColor(red, green, blue, alpha).setUv(u, v).setOverlay(0).setLight(0xF000F0)
            .setNormal(pose, nx, ny, nz);
    }

    private static void renderPlasmaHalo(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ, final float radius,
        final float heat, final long seed, final Quaternionf camera) {
        Basis basis = Basis.from(camera);
        for (int index = 0; index < 18; index++) {
            long random = mix(seed ^ index * 0x9E3779B97F4A7C15L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float elevation = signed(random, 1) * 0.72F;
            float ring = radius * (0.72F + unit(random, 2) * 0.32F);
            float horizontal = Mth.sqrt(Math.max(0.0F, 1.0F - elevation * elevation));
            float px = centerX + Mth.cos(angle) * horizontal * ring;
            float py = centerY + elevation * ring;
            float pz = centerZ + Mth.sin(angle) * horizontal * ring;
            float size = radius * (0.13F + unit(random, 3) * 0.11F);
            int green = Mth.lerpInt(heat, 98, 242);
            int blue = Mth.lerpInt(heat, 12, 154);
            billboard(pose, buffer, px, py, pz, size, unit(random, 4) * Mth.TWO_PI,
                255, green, blue, 0.76F, 0xF000F0, basis);
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

        private final long seed;
        private final float scale;
        private final List<? extends NuclearCloudSource> sources;
        private final long signature;
        private final float yield;
        private final float craterRadius;
        private final float craterFloor;
        private final int fireballEmissionEnd;
        private final int feedEmissionEnd;
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
        private final boolean[] active = new boolean[CAPACITY];
        private final int[] activeSlots = new int[CAPACITY];
        private final int[] freeSlots = new int[CAPACITY];
        private final int[] hotBucket = new int[CAPACITY];
        private final int[] coolBucket = new int[CAPACITY];
        private final int[] smokeBucket = new int[CAPACITY];
        private final int[] ordered = new int[CAPACITY];
        private final int[] binCounts = new int[DEPTH_BINS];
        private final int[] binOffsets = new int[DEPTH_BINS];

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

        private Field(final long seed, final float scale, final List<? extends NuclearCloudSource> sources,
            final long signature) {
            this.seed = seed;
            this.scale = Mth.clamp(scale, 1.4F, 4.2F);
            this.sources = sources == null || sources.isEmpty()
                ? List.of(new NuclearCloudSource.Basic(Vec3.ZERO, 0.0, scale, seed))
                : List.copyOf(sources);
            this.signature = signature;
            this.yield = Mth.clamp(this.scale / 2.70F, 0.55F, 1.55F);
            this.craterRadius = 12.0F + 13.0F * this.scale;
            this.craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
            this.fireballEmissionEnd = Math.round(165.0F + 105.0F * yield);
            this.feedEmissionEnd = Math.round(1_260.0F + 720.0F * yield);
            initialiseSlots();
        }

        private void initialiseSlots() {
            Arrays.fill(active, false);
            for (int index = 0; index < CAPACITY; index++) freeSlots[index] = CAPACITY - 1 - index;
            freeCount = CAPACITY;
            activeCount = 0;
            invalidateBuckets();
        }

        private void ensureSimulated(final double age) {
            int target = Math.max(0, (int) Math.floor(age));
            if (target < simulatedTick) reset();
            if (simulatedTick < 0 && target > 40) {
                warmStart(target);
                return;
            }
            if (target - simulatedTick > 80) {
                reset();
                warmStart(target);
                return;
            }
            while (simulatedTick < target) {
                simulatedTick++;
                spawnedLastTick = 0;
                emit(simulatedTick);
                update(simulatedTick);
            }
        }

        private void warmStart(final int target) {
            simulatedTick = target;
            spawnedLastTick = 0;
            int population = Mth.clamp(12_000 + target * 18, 12_000, CAPACITY - 512);
            for (int ordinal = 0; ordinal < population; ordinal++) spawnWarm(target, ordinal);
            invalidateBuckets();
        }

        private void spawnWarm(final int tick, final int ordinal) {
            long random = mix(seed ^ signature ^ 0x5741524D5F4E5543L
                ^ ordinal * 0x9E3779B97F4A7C15L ^ (long) tick * 0xD1B54A32D192ED03L);
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
            if (choice < 0.22F) {
                initialRegion = REGION_STEM;
                float angle = unit(random, 1) * Mth.TWO_PI;
                float radial = (float) Math.sqrt(unit(random, 2)) * stemRadius(tick);
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                py = craterFloor + unit(random, 3) * Math.max(2.0F, capY - craterFloor);
                vx = -Mth.cos(angle) * radial * 0.004F;
                vy = 0.46F + unit(random, 4) * 0.48F;
                vz = -Mth.sin(angle) * radial * 0.004F;
                heat = 0.44F + unit(random, 5) * 0.36F;
                particleRadius = 1.35F + unit(random, 6) * 1.45F;
            } else {
                float angle = unit(random, 1) * Mth.TWO_PI;
                float radialFraction = (float) Math.sqrt(unit(random, 2));
                float radial = radialFraction * capR;
                float shell = signed(random, 3);
                px = Mth.cos(angle) * radial;
                pz = Mth.sin(angle) * radial;
                py = capY + shell * capD * (0.38F + 0.62F * radialFraction);
                if (choice < 0.68F) initialRegion = REGION_CAP;
                else if (shell < -0.25F) initialRegion = REGION_UNDER_CAP;
                else initialRegion = REGION_OUTER_CURL;
                float radialX = radial > 0.001F ? px / radial : 0.0F;
                float radialZ = radial > 0.001F ? pz / radial : 0.0F;
                vx = initialRegion == REGION_CAP ? radialX * 0.18F : -radialX * 0.10F;
                vz = initialRegion == REGION_CAP ? radialZ * 0.18F : -radialZ * 0.10F;
                vy = initialRegion == REGION_OUTER_CURL ? -0.24F : 0.04F;
                heat = initialRegion == REGION_UNDER_CAP ? 0.18F + unit(random, 4) * 0.30F
                    : 0.10F + unit(random, 4) * 0.42F;
                particleRadius = (1.05F + unit(random, 5) * 1.30F)
                    * Mth.lerp(radialFraction, 1.48F, 0.82F);
            }
            spawn(initialRegion, px, py, pz, vx, vy, vz, heat, particleRadius,
                1_500 + Math.floorMod((int) random, 2_000), (int) random);
            particleAge[activeSlots[activeCount - 1]] = (short) Math.min(32_000,
                Math.floorMod((int) (random >>> 32), 720));
        }

        private void reset() {
            initialiseSlots();
            simulatedTick = -1;
            spawnedLastTick = 0;
        }

        private void invalidateBuckets() {
            bucketTick = Integer.MIN_VALUE;
            bucketLod = null;
            hotCount = 0;
            coolCount = 0;
            smokeCount = 0;
        }

        private void emit(final int tick) {
            if (tick <= 10) {
                int initial = Math.round(285.0F + 190.0F * yield);
                for (int index = 0; index < initial; index++) spawnFireball(tick, index);
            }
            if (tick <= fireballEmissionEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, fireballEmissionEnd);
                int body = Math.round((46.0F + 34.0F * yield) * (0.34F + 0.66F * remaining));
                for (int index = 0; index < body; index++) spawnFireball(tick, index + 30_000);
            }
            if (tick <= feedEmissionEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, feedEmissionEnd);
                int stem = Math.round((34.0F + 28.0F * yield) * (0.44F + 0.56F * remaining));
                for (int index = 0; index < stem; index++) spawnStem(tick, index);
            }
        }

        private NuclearCloudSource source(final long random) {
            return sources.get(Math.floorMod((int) random, sources.size()));
        }

        private void spawnFireball(final int tick, final int ordinal) {
            long random = mix(seed ^ signature ^ 0x4649524542414C4CL
                ^ ((long) tick << 32) ^ ordinal * 0x9E3779B97F4A7C15L);
            NuclearCloudSource source = source(random);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = (float) Math.sqrt(unit(random, 1));
            float radial = radialFraction * craterRadius * 0.92F;
            float dome = (float) Math.sqrt(Math.max(0.0F, 1.0F - radialFraction * radialFraction));
            float px = (float) source.offset().x + Mth.cos(angle) * radial;
            float pz = (float) source.offset().z + Mth.sin(angle) * radial;
            float py = (float) source.offset().y + craterFloor + 1.0F
                + dome * craterRadius * 0.74F + signed(random, 2) * 0.72F;
            float inward = -0.0036F * radial;
            float coreBias = 1.0F - radialFraction;
            spawn(REGION_FIREBALL, px, py, pz,
                Mth.cos(angle) * inward + signed(random, 3) * 0.018F,
                0.22F + unit(random, 4) * (0.36F + 0.14F * yield),
                Mth.sin(angle) * inward + signed(random, 5) * 0.018F,
                0.86F + unit(random, 6) * 0.14F,
                (1.00F + unit(random, 7) * 1.05F + coreBias * 0.85F) * (0.94F + 0.10F * yield),
                Math.round(1_350.0F + unit(random, 8) * (1_450.0F + 620.0F * yield)),
                (int) random);
        }

        private void spawnStem(final int tick, final int ordinal) {
            long random = mix(seed ^ signature ^ 0x5354454D5F464545L
                ^ ((long) tick << 31) ^ ordinal * 0xD1B54A32D192ED03L);
            NuclearCloudSource source = source(random);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = (float) Math.sqrt(unit(random, 1));
            float radial = radialFraction * craterRadius * 0.20F;
            float px = (float) source.offset().x + Mth.cos(angle) * radial;
            float pz = (float) source.offset().z + Mth.sin(angle) * radial;
            float py = (float) source.offset().y + craterFloor + 1.2F
                + unit(random, 2) * craterRadius * 0.30F;
            spawn(REGION_STEM, px, py, pz,
                -Mth.cos(angle) * radial * 0.006F + signed(random, 3) * 0.012F,
                0.44F + unit(random, 4) * (0.46F + 0.18F * yield),
                -Mth.sin(angle) * radial * 0.006F + signed(random, 5) * 0.012F,
                0.72F + unit(random, 6) * 0.24F,
                (1.05F + unit(random, 7) * 1.12F + (1.0F - radialFraction) * 0.72F)
                    * (0.94F + 0.10F * yield),
                Math.round(1_650.0F + unit(random, 8) * (1_850.0F + 760.0F * yield)),
                (int) random);
        }

        private void spawn(final byte initialRegion, final float px, final float py, final float pz,
            final float vx, final float vy, final float vz, final float heat,
            final float particleRadius, final int particleLifetime, final int randomSeed) {
            int slot = reserve();
            if (slot < 0) return;
            x[slot] = previousX[slot] = px;
            y[slot] = previousY[slot] = py;
            z[slot] = previousZ[slot] = pz;
            velocityX[slot] = vx;
            velocityY[slot] = vy;
            velocityZ[slot] = vz;
            temperature[slot] = heat;
            radius[slot] = particleRadius;
            rotation[slot] = unit(randomSeed, 0) * Mth.TWO_PI;
            angularVelocity[slot] = signed(randomSeed, 1) * 0.018F;
            particleAge[slot] = 0;
            lifetime[slot] = (short) Mth.clamp(particleLifetime, 600, Short.MAX_VALUE);
            particleSeed[slot] = randomSeed;
            region[slot] = initialRegion;
            active[slot] = true;
            spawnedLastTick++;
            invalidateBuckets();
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
            active[removedSlot] = false;
            freeSlots[freeCount++] = removedSlot;
        }

        private float capCenterY(final int tick) {
            float launch = craterRadius * 0.42F;
            float early = Math.min(tick, 180) * (0.25F + 0.050F * yield);
            float continuing = Math.max(0, tick - 180) * (0.078F + 0.026F * yield);
            return launch + early + continuing;
        }

        private float capRadius(final int tick) {
            float growth = (float) Math.sqrt(Mth.clamp(tick / (float) feedEmissionEnd, 0.0F, 1.0F));
            return (17.0F + 42.0F * yield) * (0.18F + 0.82F * growth);
        }

        private float capDepth(final int tick) {
            return capRadius(tick) * (0.42F + 0.08F * yield);
        }

        private float stemRadius(final int tick) {
            float feed = tick < feedEmissionEnd
                ? 1.0F : Mth.clamp(1.0F - (tick - feedEmissionEnd) / 1_850.0F, 0.14F, 1.0F);
            return (5.8F + 6.8F * yield) * (0.42F + 0.58F * feed);
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
                long turbulenceSeed = mix(((long) particleSeed[index] << 32)
                    ^ tick * 0x9E3779B97F4A7C15L);
                float turbulence = signed(turbulenceSeed, 0);
                float crossTurbulence = signed(turbulenceSeed, 1);

                switch (region[index]) {
                    case REGION_FIREBALL -> {
                        velocityX[index] += -radialX * 0.008F + turbulence * 0.0015F;
                        velocityZ[index] += -radialZ * 0.008F + crossTurbulence * 0.0015F;
                        velocityY[index] += 0.008F + temperature[index] * 0.008F;
                        velocityX[index] *= 0.977F;
                        velocityZ[index] *= 0.977F;
                        if (age > 46 || y[index] > craterRadius * 0.40F) region[index] = REGION_STEM;
                    }
                    case REGION_STEM -> {
                        velocityX[index] += -radialX * (0.022F + radial * 0.0013F)
                            + turbulence * 0.0018F;
                        velocityZ[index] += -radialZ * (0.022F + radial * 0.0013F)
                            + crossTurbulence * 0.0018F;
                        velocityY[index] += 0.018F + temperature[index] * 0.014F;
                        if (y[index] >= capY - capD * 0.16F
                            + signed(particleSeed[index], 2) * capD * 0.16F) {
                            region[index] = REGION_CAP;
                            velocityY[index] = Math.max(velocityY[index], 0.10F);
                        }
                    }
                    case REGION_CAP -> {
                        velocityX[index] += radialX * (0.026F + 0.014F * yield)
                            + turbulence * 0.0022F;
                        velocityZ[index] += radialZ * (0.026F + 0.014F * yield)
                            + crossTurbulence * 0.0022F;
                        velocityY[index] += (capY - y[index]) * 0.0032F;
                        velocityY[index] *= 0.956F;
                        if (radial >= capR * (0.77F + unit(particleSeed[index], 4) * 0.19F)) {
                            region[index] = REGION_OUTER_CURL;
                        }
                    }
                    case REGION_OUTER_CURL -> {
                        velocityX[index] += -radialX * (0.011F + 0.004F * yield);
                        velocityZ[index] += -radialZ * (0.011F + 0.004F * yield);
                        velocityY[index] -= 0.025F + 0.008F * yield;
                        if (y[index] <= capY - capD * (0.68F + unit(particleSeed[index], 5) * 0.23F)) {
                            region[index] = REGION_UNDER_CAP;
                        }
                    }
                    case REGION_UNDER_CAP -> {
                        velocityX[index] += -radialX * (0.032F + 0.013F * yield)
                            + turbulence * 0.0016F;
                        velocityZ[index] += -radialZ * (0.032F + 0.013F * yield)
                            + crossTurbulence * 0.0016F;
                        velocityY[index] += (capY - capD * 0.94F - y[index]) * 0.0018F;
                        if (radial <= stemR * (1.02F + unit(particleSeed[index], 6) * 0.44F)) {
                            region[index] = REGION_STEM;
                            velocityX[index] *= 0.40F;
                            velocityZ[index] *= 0.40F;
                            velocityY[index] = Math.max(velocityY[index], 0.34F + temperature[index] * 0.30F);
                            temperature[index] = Math.min(0.72F, temperature[index] + 0.045F);
                        }
                    }
                    default -> { }
                }

                velocityX[index] += turbulence * (0.0011F + progress * 0.0018F);
                velocityZ[index] += crossTurbulence * (0.0011F + progress * 0.0018F);
                velocityX[index] *= 0.987F;
                velocityY[index] *= 0.992F;
                velocityZ[index] *= 0.987F;
                x[index] += velocityX[index];
                y[index] += velocityY[index];
                z[index] += velocityZ[index];
                rotation[index] += angularVelocity[index];

                float insulation = region[index] == REGION_STEM
                    ? Mth.clamp(1.0F - radial / Math.max(1.0F, stemR * 1.60F), 0.0F, 1.0F)
                    : Mth.clamp(1.0F - radial / Math.max(1.0F, capR * 0.86F), 0.0F, 1.0F);
                float cooling = switch (region[index]) {
                    case REGION_FIREBALL -> 0.00115F;
                    case REGION_STEM -> 0.00130F;
                    case REGION_CAP -> 0.00220F;
                    case REGION_OUTER_CURL -> 0.00425F;
                    case REGION_UNDER_CAP -> 0.00285F;
                    default -> 0.0022F;
                };
                cooling *= 1.0F - insulation * 0.48F;
                temperature[index] = Math.max(0.0F,
                    temperature[index] - cooling * (0.80F + progress * 1.45F));
                if (region[index] == REGION_STEM && radial < stemR * 0.66F
                    && y[index] < capY - capD * 0.52F) {
                    temperature[index] = Math.min(0.72F, temperature[index] + 0.0008F);
                }
                radius[index] *= region[index] == REGION_OUTER_CURL ? 1.00042F : 1.00018F;
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
            int stride = switch (lod) { case NEAR -> 2; case MEDIUM -> 4; case FAR -> 9; };
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

        private void render(final PoseStack.Pose pose, final VertexConsumer buffer, final double age,
            final WarheadMesh.Lod lod, final Quaternionf camera, final Pass pass) {
            ensureSimulated(age);
            int tick = Math.max(0, (int) Math.floor(age));
            prepareBuckets(tick, lod);
            float partial = (float) Mth.clamp(age - Math.floor(age), 0.0, 1.0);
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
                if (alpha <= 0.006F) continue;
                float px = Mth.lerp(partial, previousX[index], x[index]);
                float py = Mth.lerp(partial, previousY[index], y[index]);
                float pz = Mth.lerp(partial, previousZ[index], z[index]);
                Colour colour = colour(temperature[index], progress, particleSeed[index], pass);
                float drawRadius = radius[index] * renderScale(index, tick, pass);
                int light = pass == Pass.SMOKE ? 0x900090 : 0xF000F0;
                billboard(pose, buffer, px, py, pz, drawRadius, rotation[index], colour.red,
                    colour.green, colour.blue, alpha, light, basis);
            }
        }

        private int depthOrder(final int[] bucket, final int count, final Vector3f normal) {
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
            float inverse = maximum > minimum ? (DEPTH_BINS - 0.001F) / (maximum - minimum) : 0.0F;
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
            int[] write = Arrays.copyOf(binOffsets, DEPTH_BINS);
            for (int position = 0; position < count; position++) {
                int index = bucket[position];
                float depth = x[index] * normal.x + y[index] * normal.y + z[index] * normal.z;
                int bin = Mth.clamp((int) ((depth - minimum) * inverse), 0, DEPTH_BINS - 1);
                ordered[write[bin]++] = index;
            }
            return count;
        }

        private float renderScale(final int index, final int tick, final Pass pass) {
            float radial = Mth.sqrt(x[index] * x[index] + z[index] * z[index]);
            float density;
            if (region[index] == REGION_FIREBALL) {
                density = Mth.lerp(Mth.clamp(radial / Math.max(1.0F, craterRadius), 0.0F, 1.0F),
                    2.35F, 1.06F);
            } else if (region[index] == REGION_STEM) {
                density = Mth.lerp(Mth.clamp(radial / Math.max(1.0F, stemRadius(tick) * 1.45F),
                    0.0F, 1.0F), 2.20F, 0.98F);
            } else {
                float normalized = Mth.clamp(radial / Math.max(1.0F, capRadius(tick)), 0.0F, 1.0F);
                density = Mth.lerp(normalized, 2.05F, 0.88F);
                if (region[index] == REGION_OUTER_CURL) density *= 0.86F;
                if (region[index] == REGION_UNDER_CAP) density *= 1.18F;
            }
            if (pass == Pass.SMOKE) density *= 1.34F;
            return density;
        }

        private boolean interior(final int index, final int tick, final WarheadMesh.Lod lod) {
            float radial = Mth.sqrt(x[index] * x[index] + z[index] * z[index]);
            int keepModulo = lod == WarheadMesh.Lod.NEAR ? 7 : lod == WarheadMesh.Lod.MEDIUM ? 11 : 18;
            if (region[index] == REGION_FIREBALL && radial < craterRadius * 0.58F) {
                return Math.floorMod(particleSeed[index], keepModulo) != 0;
            }
            if (region[index] == REGION_STEM && radial < stemRadius(tick) * 0.56F) {
                return Math.floorMod(particleSeed[index], keepModulo) != 0;
            }
            if (region[index] == REGION_CAP) {
                float capR = capRadius(tick);
                float capD = capDepth(tick);
                boolean deep = radial < capR * 0.58F
                    && Math.abs(y[index] - capCenterY(tick)) < capD * 0.46F;
                return deep && Math.floorMod(particleSeed[index], keepModulo + 2) != 0;
            }
            return false;
        }

        private static float alpha(final Pass pass, final float progress, final float heat) {
            float lateFade = progress < 0.32F ? 1.0F
                : 1.0F - Mth.clamp((progress - 0.32F) / 0.68F, 0.0F, 1.0F);
            lateFade = (float) Math.pow(lateFade, pass == Pass.SMOKE ? 0.70F : 0.48F);
            return switch (pass) {
                case HOT_FIRE -> Mth.clamp(0.88F * lateFade * (0.70F + heat * 0.30F), 0.0F, 0.94F);
                case COOL_FIRE -> Mth.clamp(0.72F * lateFade, 0.0F, 0.80F);
                case SMOKE -> Mth.clamp(0.82F * lateFade * (0.86F + (1.0F - heat) * 0.14F),
                    0.0F, 0.88F);
            };
        }

        private static Colour colour(final float temperature, final float progress,
            final int seed, final Pass pass) {
            float heat = Mth.clamp(temperature, 0.0F, 1.0F);
            if (pass == Pass.SMOKE || heat < 0.28F) {
                int variation = Math.floorMod(seed, 26);
                int ageDarkening = (int) (progress * 48.0F);
                int tone = Mth.clamp(84 - ageDarkening + variation, 28, 108);
                return new Colour(tone, Math.min(114, tone + 3), Math.min(120, tone + 7));
            }
            if (heat > 0.88F) {
                float t = (heat - 0.88F) / 0.12F;
                return new Colour(255, Mth.lerpInt(t, 214, 252), Mth.lerpInt(t, 68, 210));
            }
            if (heat > 0.62F) {
                float t = (heat - 0.62F) / 0.26F;
                return new Colour(255, Mth.lerpInt(t, 102, 214), Mth.lerpInt(t, 14, 68));
            }
            float t = (heat - 0.28F) / 0.34F;
            return new Colour(Mth.lerpInt(t, 118, 255), Mth.lerpInt(t, 36, 102),
                Mth.lerpInt(t, 28, 14));
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

    private static void billboard(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ, final float radius,
        final float rotation, final int red, final int green, final int blue,
        final float alpha, final int light, final Basis basis) {
        float cosine = Mth.cos(rotation);
        float sine = Mth.sin(rotation);
        vertex(pose, buffer, centerX, centerY, centerZ, -radius, -radius, cosine, sine,
            0.0F, 1.0F, red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, -radius, radius, cosine, sine,
            0.0F, 0.0F, red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, radius, radius, cosine, sine,
            1.0F, 0.0F, red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, radius, -radius, cosine, sine,
            1.0F, 1.0F, red, green, blue, alpha, light, basis);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ, final float localX,
        final float localY, final float cosine, final float sine, final float u,
        final float v, final int red, final int green, final int blue, final float alpha,
        final int light, final Basis basis) {
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
