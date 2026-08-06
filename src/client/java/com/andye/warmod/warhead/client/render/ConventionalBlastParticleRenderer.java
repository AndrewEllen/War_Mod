package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Packed conventional explosion field shaped around a low, wide crater blast.
 * The hot body stays close to the impact while independent grey-white tendrils
 * imitate fast ejecta and real debris trails. Dust remains several blocks thick
 * around the fire instead of collapsing into a narrow vertical column.
 */
public final class ConventionalBlastParticleRenderer {
    private static final int MAX_FIELDS = 8;
    private static final int CAPACITY = 65_536;
    private static final float HE_FIRE_TOP = 4.75F;
    private static final long NUCLEAR_KEY_MASK = 0x6E75636C656172L;
    private static final Map<Long, Field> FIELDS = new LinkedHashMap<>();

    private ConventionalBlastParticleRenderer() { }

    public static void renderFireCore(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderFireCore(pose, buffer, age, visualScale, profile,
                seed, lod, camera);
            return;
        }
        field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.FIRE_CORE);
    }

    public static void renderHot(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderHot(pose, buffer, age, visualScale, profile,
                seed, lod, camera);
            return;
        }
        field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.FIRE_HOT);
    }

    public static void renderCooling(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderCooling(pose, buffer, age, visualScale, profile,
                seed, lod, camera);
            return;
        }
        field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.FIRE_COOLING);
    }

    public static void renderSmokeCore(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderSmokeCore(pose, buffer, age, visualScale, profile,
                seed, lod, camera);
            return;
        }
        field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.SMOKE_CORE);
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderSmoke(pose, buffer, age, visualScale, profile,
                seed, lod, camera);
            return;
        }
        field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.SMOKE_SOFT);
    }

    public static void renderSurfaceFront(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final double physicalRadius, final float visualScale, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderSurfaceFront(pose, buffer, age, physicalRadius,
                visualScale, seed, lod, camera);
            return;
        }
        Field field = field(seed, visualScale, false);
        field.emitSurfaceFront(age, physicalRadius, lod);
        field.render(pose, buffer, age, lod, camera, Pass.SURFACE_FRONT);
    }

    public static void renderNuclearReturnFront(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final double returnRadius, final float yieldScale, final long seed,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderNuclearReturnFront(pose, buffer, age, returnRadius,
                yieldScale, seed, lod, camera);
            return;
        }
        Field field = field(seed ^ NUCLEAR_KEY_MASK, yieldScale, true);
        field.emitReturnFront(age, returnRadius, lod);
        field.render(pose, buffer, age, lod, camera, Pass.RETURN_FRONT);
    }

    public static synchronized DebugSnapshot debugSnapshot() {
        if (!WarheadRenderSettings.usePackedParticles()) {
            return new DebugSnapshot(0, 0, 0, 0, "legacy_analytical_custom_geometry");
        }
        int active = 0;
        int spawned = 0;
        int culled = 0;
        for (Field field : FIELDS.values()) {
            active += field.activeCount;
            spawned += field.spawnedLastTick;
            culled += field.culledLastRender;
        }
        return new DebugSnapshot(active, spawned, culled, FIELDS.size(),
            "packed_soa_compact_low_plume");
    }

    private static synchronized Field field(final long key, final float visualScale,
        final boolean nuclearOnly) {
        Field existing = FIELDS.get(key);
        if (existing != null && existing.nuclearOnly == nuclearOnly) return existing;
        while (FIELDS.size() >= MAX_FIELDS) {
            Iterator<Long> iterator = FIELDS.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        Field created = new Field(key, visualScale, nuclearOnly);
        FIELDS.put(key, created);
        return created;
    }

    public record DebugSnapshot(int activeParticles, int spawnedParticlesPerTick,
        int culledParticles, int activeFields, String backend) { }

    private enum Pass {
        FIRE_CORE, FIRE_HOT, FIRE_COOLING, SMOKE_CORE, SMOKE_SOFT, SURFACE_FRONT, RETURN_FRONT
    }

    private static final class Field {
        private static final byte MATERIAL_FIRE = 0;
        private static final byte MATERIAL_SMOKE = 1;
        private static final byte MATERIAL_DUST = 2;
        private static final byte MATERIAL_FRONT = 3;
        private static final byte MATERIAL_RETURN = 4;
        private static final byte FLAG_CORE = 1;
        private static final byte FLAG_SPOUT = 2;
        private static final byte FLAG_TENDRIL = 4;
        private static final byte FLAG_GROUND = 8;

        private final long seed;
        private final float scale;
        private final boolean nuclearOnly;
        private final float craterRadius;
        private final float craterFloor;
        private final float maximumFireY;
        private final float maximumSmokeY;
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
        private final float[] rotationVelocity = new float[CAPACITY];
        private final short[] particleAge = new short[CAPACITY];
        private final short[] lifetime = new short[CAPACITY];
        private final int[] particleSeed = new int[CAPACITY];
        private final byte[] material = new byte[CAPACITY];
        private final byte[] flags = new byte[CAPACITY];
        private final int[] activeSlots = new int[CAPACITY];
        private final int[] freeSlots = new int[CAPACITY];
        private int simulatedTick = -1;
        private int freeCount;
        private int activeCount;
        private int spawnedLastTick;
        private int culledLastRender;
        private int lastSurfaceTick = Integer.MIN_VALUE;
        private int lastReturnTick = Integer.MIN_VALUE;

        private Field(final long seed, final float visualScale, final boolean nuclearOnly) {
            this.seed = seed;
            this.scale = Mth.clamp(visualScale, 0.28F, nuclearOnly ? 3.0F : 1.75F);
            this.nuclearOnly = nuclearOnly;
            this.craterRadius = 2.0F + 15.0F * this.scale;
            this.craterFloor = -Math.max(1.6F, craterRadius * 0.28F);
            this.maximumFireY = HE_FIRE_TOP + Math.max(0.0F, this.scale - 1.0F) * 3.0F;
            this.maximumSmokeY = 6.5F + Math.max(0.0F, this.scale - 0.8F) * 4.0F;
            initialiseSlots();
        }

        private void initialiseSlots() {
            for (int index = 0; index < CAPACITY; index++) freeSlots[index] = CAPACITY - 1 - index;
            freeCount = CAPACITY;
            activeCount = 0;
        }

        private void ensureSimulated(final double age) {
            int target = Math.max(0, (int) Math.floor(age));
            if (target < simulatedTick) reset();
            if (simulatedTick < 0 && target > 80) simulatedTick = target - 24;
            while (simulatedTick < target) {
                simulatedTick++;
                spawnedLastTick = 0;
                if (!nuclearOnly) emitConventional(simulatedTick);
                update(simulatedTick);
            }
        }

        private void reset() {
            initialiseSlots();
            simulatedTick = -1;
            spawnedLastTick = 0;
            lastSurfaceTick = Integer.MIN_VALUE;
            lastReturnTick = Integer.MIN_VALUE;
        }

        private void emitConventional(final int tick) {
            float density = 0.80F + (float) Math.pow(scale, 1.28);
            if (tick <= 6) {
                int count = Math.min(5_400, Math.round((520.0F + 620.0F * scale) * density));
                for (int index = 0; index < count; index++) spawnFireBody(tick, index, true);
            }
            int feedEnd = Math.round(25.0F + 18.0F * scale);
            if (tick <= feedEnd) {
                float feed = 1.0F - tick / (float) Math.max(1, feedEnd);
                int body = Math.round((150.0F + 185.0F * scale) * density
                    * (0.32F + 0.68F * feed));
                int spout = Math.round((46.0F + 58.0F * scale) * density
                    * (0.38F + 0.62F * feed));
                int smoke = Math.round((125.0F + 165.0F * scale) * density);
                int dust = Math.round((150.0F + 220.0F * scale) * density);
                for (int index = 0; index < body; index++) spawnFireBody(tick, index, false);
                for (int index = 0; index < spout; index++) spawnSpout(tick, index);
                for (int index = 0; index < smoke; index++) spawnCraterSmoke(tick, index);
                for (int index = 0; index < dust; index++) spawnDustEnvelope(tick, index);
            }
            emitTendrils(tick);
            int spillEnd = Math.round(58.0F + 38.0F * scale);
            if (tick <= spillEnd) {
                int spill = Math.round((90.0F + 140.0F * scale) * density);
                for (int index = 0; index < spill; index++) spawnGroundSpill(tick, index);
            }
        }

        private void spawnFireBody(final int tick, final int ordinal, final boolean ignition) {
            long random = mix(seed ^ 0x464952455F424F44L ^ ((long) tick << 32)
                ^ ordinal * 0x9E3779B97F4A7C15L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = (float) Math.sqrt(unit(random, 1));
            float radial = radialFraction * craterRadius * (ignition ? 1.10F : 1.03F);
            float dome = (float) Math.sqrt(Math.max(0.0F, 1.0F - radialFraction * radialFraction));
            float px = Mth.cos(angle) * radial;
            float pz = Mth.sin(angle) * radial;
            float py = Math.min(maximumFireY - 0.35F,
                craterFloor + 0.45F + dome * craterRadius * 0.62F + signed(random, 2) * 0.48F);
            float outward = 0.07F + unit(random, 3) * (0.18F + 0.05F * scale);
            float up = 0.08F + unit(random, 4) * (0.14F + 0.05F * scale);
            byte particleFlags = unit(random, 5) < 0.40F ? FLAG_CORE : 0;
            spawn(MATERIAL_FIRE, particleFlags, px, py, pz,
                Mth.cos(angle) * outward + signed(random, 6) * 0.014F, up,
                Mth.sin(angle) * outward + signed(random, 7) * 0.014F,
                0.82F + unit(random, 8) * 0.22F,
                (0.25F + unit(random, 9) * 0.42F) * (0.92F + scale * 0.14F),
                Math.round(52.0F + unit(random, 10) * (52.0F + 18.0F * scale)), (int) random);
        }

        private void spawnSpout(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x53504F55545F5550L ^ ((long) tick << 33)
                ^ ordinal * 0xD1B54A32D192ED03L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = (float) Math.sqrt(unit(random, 1)) * craterRadius * 0.15F;
            float py = Math.min(maximumFireY - 0.5F,
                craterFloor + 0.8F + unit(random, 2) * craterRadius * 0.24F);
            spawn(MATERIAL_FIRE, (byte) (FLAG_CORE | FLAG_SPOUT),
                Mth.cos(angle) * radial, py, Mth.sin(angle) * radial,
                signed(random, 3) * 0.018F,
                0.24F + unit(random, 4) * (0.26F + 0.08F * scale),
                signed(random, 5) * 0.018F,
                0.92F + unit(random, 6) * 0.12F,
                (0.24F + unit(random, 7) * 0.38F) * (0.92F + scale * 0.12F),
                Math.round(58.0F + unit(random, 8) * (50.0F + 18.0F * scale)), (int) random);
        }

        private void spawnCraterSmoke(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x435241544552534DL ^ ((long) tick << 31)
                ^ ordinal * 0x94D049BB133111EBL);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = (float) Math.sqrt(unit(random, 1)) * craterRadius * 0.82F;
            float px = Mth.cos(angle) * radial;
            float pz = Mth.sin(angle) * radial;
            float py = craterFloor + 0.7F + unit(random, 2) * craterRadius * 0.36F;
            float outward = 0.055F + unit(random, 3) * (0.18F + 0.05F * scale);
            spawn(MATERIAL_SMOKE, unit(random, 4) < 0.34F ? FLAG_CORE : 0, px, py, pz,
                Mth.cos(angle) * outward + signed(random, 5) * 0.022F,
                0.12F + unit(random, 6) * (0.25F + 0.08F * scale),
                Mth.sin(angle) * outward + signed(random, 7) * 0.022F,
                0.02F + unit(random, 8) * 0.12F,
                (0.34F + unit(random, 9) * 0.54F) * (0.92F + scale * 0.12F),
                Math.round(100.0F + unit(random, 10) * (90.0F + 28.0F * scale)), (int) random);
        }

        private void spawnDustEnvelope(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x445553545F454E56L ^ ((long) tick << 29)
                ^ ordinal * 0xDB4F0B9175AE2165L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = craterRadius * (0.44F + unit(random, 1) * 0.72F);
            float outward = 0.12F + unit(random, 2) * (0.28F + 0.08F * scale);
            spawn(MATERIAL_DUST, (byte) 0, Mth.cos(angle) * radial,
                0.10F + unit(random, 3) * 2.8F, Mth.sin(angle) * radial,
                Mth.cos(angle) * outward + signed(random, 4) * 0.06F,
                0.06F + unit(random, 5) * 0.20F,
                Mth.sin(angle) * outward + signed(random, 6) * 0.06F,
                0.0F, 0.38F + unit(random, 7) * 0.64F,
                Math.round(82.0F + unit(random, 8) * 84.0F), (int) random);
        }

        private void emitTendrils(final int tick) {
            int streams = Mth.clamp(Math.round(12.0F + 9.0F * scale), 12, 28);
            for (int stream = 0; stream < streams; stream++) {
                long streamSeed = mix(seed ^ 0x54454E4452494C53L
                    ^ stream * 0xBF58476D1CE4E5B9L);
                int onset = Math.floorMod((int) streamSeed, 9);
                int duration = 13 + Math.floorMod((int) (streamSeed >>> 17), 18);
                if (tick < onset || tick > onset + duration) continue;
                int particles = 3 + Math.floorMod((int) (streamSeed >>> 33), 5);
                for (int particle = 0; particle < particles; particle++) {
                    spawnTendril(tick, stream, particle, streams, streamSeed);
                }
            }
        }

        private void spawnTendril(final int tick, final int stream, final int particle,
            final int streamCount, final long streamSeed) {
            long random = mix(streamSeed ^ ((long) tick << 32)
                ^ particle * 0x9E3779B97F4A7C15L);
            float baseAngle = (stream + unit(streamSeed, 0) * 0.72F) / streamCount * Mth.TWO_PI;
            float angle = baseAngle + signed(random, 0) * 0.055F;
            float sourceRadius = craterRadius * (0.12F + unit(streamSeed, 1) * 0.34F);
            float speed = 0.40F + unit(streamSeed, 2) * (0.56F + 0.16F * scale);
            float upward = 0.34F + unit(streamSeed, 3) * (0.48F + 0.14F * scale);
            float spread = 0.018F + 0.014F * scale;
            spawn(MATERIAL_SMOKE, FLAG_TENDRIL,
                Mth.cos(angle) * sourceRadius + signed(random, 1) * craterRadius * 0.025F,
                craterFloor + 1.0F + unit(streamSeed, 4) * craterRadius * 0.30F,
                Mth.sin(angle) * sourceRadius + signed(random, 2) * craterRadius * 0.025F,
                Mth.cos(angle) * speed + signed(random, 3) * spread,
                upward + signed(random, 4) * spread * 1.8F,
                Mth.sin(angle) * speed + signed(random, 5) * spread,
                0.0F, (0.46F + unit(random, 6) * 0.62F) * (0.94F + scale * 0.14F),
                Math.round(72.0F + unit(streamSeed, 5) * 66.0F + scale * 18.0F), (int) random);
        }

        private void spawnGroundSpill(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x47524F554E445350L ^ ((long) tick << 30)
                ^ ordinal * 0xDB4F0B9175AE2165L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = craterRadius * (0.76F + unit(random, 1) * 0.46F);
            float outward = 0.10F + unit(random, 2) * (0.20F + 0.06F * scale);
            spawn(MATERIAL_DUST, FLAG_GROUND, Mth.cos(angle) * radial,
                0.08F + unit(random, 3) * 0.75F, Mth.sin(angle) * radial,
                Mth.cos(angle) * outward - Mth.sin(angle) * signed(random, 4) * 0.055F,
                0.015F + unit(random, 5) * 0.065F,
                Mth.sin(angle) * outward + Mth.cos(angle) * signed(random, 4) * 0.055F,
                0.0F, 0.30F + unit(random, 6) * 0.52F,
                Math.round(86.0F + unit(random, 7) * 96.0F), (int) random);
        }

        private void emitSurfaceFront(final double age, final double physicalRadius,
            final WarheadMesh.Lod lod) {
            ensureSimulated(age);
            int tick = Math.max(0, (int) Math.floor(age));
            if (tick == lastSurfaceTick || physicalRadius <= 0.0) return;
            lastSurfaceTick = tick;
            if (age >= WarheadVisualMath.airShockwaveDurationTicks(scale)) return;
            int base = switch (lod) { case NEAR -> 620; case MEDIUM -> 310; case FAR -> 120; };
            int count = Math.min(1_900,
                Math.round(base * (0.76F + (float) Math.pow(scale, 1.15))));
            for (int index = 0; index < count; index++) {
                long random = mix(seed ^ 0x46524F4E545F5632L ^ ((long) tick << 32)
                    ^ index * 0x9E3779B97F4A7C15L);
                float angle = (index + unit(random, 0)) / count * Mth.TWO_PI;
                float trail = unit(random, 1) * (1.5F + 4.0F * scale);
                float radial = (float) Math.max(0.0, physicalRadius - trail);
                float tangent = signed(random, 2) * 0.09F;
                float outward = 0.08F + unit(random, 3) * 0.16F;
                float heat = unit(random, 4) < 0.11F ? 0.50F + unit(random, 5) * 0.24F : 0.0F;
                spawn(MATERIAL_FRONT, (byte) 0, Mth.cos(angle) * radial,
                    0.05F + unit(random, 6) * (0.45F + 0.48F * scale), Mth.sin(angle) * radial,
                    Mth.cos(angle) * outward - Mth.sin(angle) * tangent,
                    0.025F + unit(random, 7) * 0.10F,
                    Mth.sin(angle) * outward + Mth.cos(angle) * tangent,
                    heat, (0.22F + unit(random, 8) * 0.42F) * (0.94F + scale * 0.08F),
                    Math.round(54.0F + unit(random, 9) * 48.0F), (int) random);
            }
        }

        private void emitReturnFront(final double age, final double returnRadius,
            final WarheadMesh.Lod lod) {
            ensureSimulated(age);
            int tick = Math.max(0, (int) Math.floor(age));
            if (tick == lastReturnTick || returnRadius <= 0.0) return;
            lastReturnTick = tick;
            int base = switch (lod) { case NEAR -> 540; case MEDIUM -> 270; case FAR -> 105; };
            int count = Math.min(1_650, Math.round(base * (0.72F + (float) Math.sqrt(scale))));
            for (int index = 0; index < count; index++) {
                long random = mix(seed ^ 0x52455455524E5632L ^ ((long) tick << 32)
                    ^ index * 0xD1B54A32D192ED03L);
                float angle = (index + unit(random, 0)) / count * Mth.TWO_PI;
                float radial = (float) returnRadius + signed(random, 1) * (0.75F + 1.3F * scale);
                float inward = 0.13F + unit(random, 2) * (0.18F + 0.07F * scale);
                spawn(MATERIAL_RETURN, (byte) 0, Mth.cos(angle) * radial,
                    0.06F + unit(random, 3) * (0.48F + 0.44F * scale), Mth.sin(angle) * radial,
                    -Mth.cos(angle) * inward, 0.018F + unit(random, 4) * 0.06F,
                    -Mth.sin(angle) * inward, 0.0F,
                    (0.18F + unit(random, 5) * 0.36F) * (0.94F + 0.08F * scale),
                    Math.round(52.0F + unit(random, 6) * 54.0F), (int) random);
            }
        }

        private void spawn(final byte particleMaterial, final byte particleFlags,
            final float px, final float py, final float pz, final float vx, final float vy,
            final float vz, final float heat, final float particleRadius,
            final int particleLifetime, final int randomSeed) {
            int slot = reserve();
            if (slot < 0) return;
            x[slot] = previousX[slot] = px;
            y[slot] = previousY[slot] = particleMaterial == MATERIAL_FIRE
                ? Math.min(py, maximumFireY) : py;
            z[slot] = previousZ[slot] = pz;
            velocityX[slot] = vx;
            velocityY[slot] = vy;
            velocityZ[slot] = vz;
            temperature[slot] = heat;
            radius[slot] = particleRadius;
            rotation[slot] = unit(randomSeed, 0) * Mth.TWO_PI;
            rotationVelocity[slot] = signed(randomSeed, 1) * 0.036F;
            particleAge[slot] = 0;
            lifetime[slot] = (short) Mth.clamp(particleLifetime, 8, Short.MAX_VALUE);
            particleSeed[slot] = randomSeed;
            material[slot] = particleMaterial;
            flags[slot] = particleFlags;
            spawnedLastTick++;
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

        private void update(final int tick) {
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
                float turbulence = signed(mix(((long) particleSeed[index] << 32)
                    ^ tick * 0x9E3779B9L), 0);
                switch (material[index]) {
                    case MATERIAL_FIRE -> {
                        float cooling = (flags[index] & FLAG_CORE) != 0 ? 0.010F : 0.014F;
                        temperature[index] = Math.max(0.0F,
                            temperature[index] - cooling * (0.76F + progress * 0.72F));
                        velocityX[index] += turbulence * 0.004F;
                        velocityZ[index] += signed(particleSeed[index], tick & 7) * 0.004F;
                        float lift = (flags[index] & FLAG_SPOUT) != 0 ? 0.0010F : 0.0002F;
                        velocityY[index] = velocityY[index] * 0.925F + lift;
                        velocityX[index] *= 0.988F;
                        velocityZ[index] *= 0.988F;
                        radius[index] *= temperature[index] > 0.22F ? 1.003F : 1.006F;
                        if (temperature[index] < 0.11F) material[index] = MATERIAL_SMOKE;
                    }
                    case MATERIAL_SMOKE -> {
                        velocityX[index] += turbulence * 0.0045F;
                        velocityZ[index] += signed(particleSeed[index], tick & 7) * 0.0045F;
                        if ((flags[index] & FLAG_TENDRIL) != 0) {
                            velocityY[index] -= 0.021F;
                            velocityX[index] *= 0.990F;
                            velocityZ[index] *= 0.990F;
                            radius[index] *= 1.004F;
                        } else {
                            velocityY[index] = velocityY[index] * 0.975F + 0.0025F;
                            velocityX[index] *= 0.978F;
                            velocityZ[index] *= 0.978F;
                            radius[index] *= 1.005F;
                        }
                        temperature[index] = Math.max(0.0F, temperature[index] - 0.004F);
                    }
                    case MATERIAL_DUST -> {
                        velocityX[index] += turbulence * 0.003F;
                        velocityZ[index] += signed(particleSeed[index], tick & 7) * 0.003F;
                        velocityY[index] = velocityY[index] * 0.965F + 0.0012F;
                        velocityX[index] *= (flags[index] & FLAG_GROUND) != 0 ? 0.974F : 0.982F;
                        velocityZ[index] *= (flags[index] & FLAG_GROUND) != 0 ? 0.974F : 0.982F;
                        radius[index] *= 1.0055F;
                    }
                    case MATERIAL_FRONT -> {
                        velocityX[index] *= 0.958F;
                        velocityZ[index] *= 0.958F;
                        velocityY[index] += 0.0008F;
                        temperature[index] = Math.max(0.0F, temperature[index] - 0.040F);
                        radius[index] *= 1.010F;
                    }
                    case MATERIAL_RETURN -> {
                        velocityX[index] *= 1.002F;
                        velocityZ[index] *= 1.002F;
                        velocityY[index] += 0.0008F;
                        radius[index] *= 1.008F;
                    }
                    default -> { }
                }
                x[index] += velocityX[index];
                y[index] += velocityY[index];
                z[index] += velocityZ[index];
                if (material[index] == MATERIAL_FIRE && y[index] > maximumFireY) {
                    y[index] = maximumFireY;
                    velocityY[index] = -Math.abs(velocityY[index]) * 0.05F;
                    spreadOutward(index, 0.042F);
                } else if ((material[index] == MATERIAL_SMOKE || material[index] == MATERIAL_DUST)
                    && (flags[index] & FLAG_TENDRIL) == 0 && y[index] > maximumSmokeY) {
                    y[index] = maximumSmokeY;
                    velocityY[index] = -Math.abs(velocityY[index]) * 0.10F;
                    spreadOutward(index, 0.032F);
                }
                rotation[index] += rotationVelocity[index];
                particleAge[index] = (short) (age + 1);
                activePosition++;
            }
        }

        private void spreadOutward(final int index, final float amount) {
            float horizontal = Mth.sqrt(x[index] * x[index] + z[index] * z[index]);
            if (horizontal > 1.0E-4F) {
                velocityX[index] += x[index] / horizontal * amount;
                velocityZ[index] += z[index] / horizontal * amount;
            }
        }

        private void render(final PoseStack.Pose pose, final VertexConsumer buffer,
            final double age, final WarheadMesh.Lod lod, final Quaternionf camera,
            final Pass pass) {
            ensureSimulated(age);
            float partial = (float) Mth.clamp(age - Math.floor(age), 0.0, 1.0);
            Basis basis = Basis.from(camera);
            int stride = switch (lod) { case NEAR -> 1; case MEDIUM -> 2; case FAR -> 5; };
            int inspected = 0;
            int rejected = 0;
            for (int activePosition = 0; activePosition < activeCount; activePosition += stride) {
                int index = activeSlots[activePosition];
                inspected++;
                if (!matches(index, pass)) {
                    rejected++;
                    continue;
                }
                int life = lifetime[index] & 0xFFFF;
                float progress = (particleAge[index] & 0xFFFF) / (float) Math.max(1, life);
                float alpha = alpha(index, pass, progress);
                if (alpha <= 0.006F) {
                    rejected++;
                    continue;
                }
                float px = Mth.lerp(partial, previousX[index], x[index]);
                float py = Mth.lerp(partial, previousY[index], y[index]);
                float pz = Mth.lerp(partial, previousZ[index], z[index]);
                Colour colour = colour(index);
                float drawRadius = radius[index];
                if (pass == Pass.SMOKE_CORE) drawRadius *= 1.18F;
                if ((flags[index] & FLAG_TENDRIL) != 0) drawRadius *= 1.24F;
                int light = isEmissive(pass) ? 0xF000F0
                    : pass == Pass.SMOKE_CORE ? 0xA000A0 : 0xB000B0;
                billboard(pose, buffer, px, py, pz, drawRadius, rotation[index],
                    colour.red, colour.green, colour.blue, alpha, light, basis);
            }
            culledLastRender = Math.max(0, activeCount - inspected) + rejected;
        }

        private boolean matches(final int index, final Pass pass) {
            byte type = material[index];
            float heat = temperature[index];
            return switch (pass) {
                case FIRE_CORE -> type == MATERIAL_FIRE && heat >= 0.66F
                    && (flags[index] & FLAG_CORE) != 0;
                case FIRE_HOT -> type == MATERIAL_FIRE && heat >= 0.50F
                    && (flags[index] & FLAG_CORE) == 0;
                case FIRE_COOLING -> type == MATERIAL_FIRE && heat >= 0.11F && heat < 0.50F;
                case SMOKE_CORE -> type == MATERIAL_SMOKE
                    && (flags[index] & (FLAG_CORE | FLAG_SPOUT)) != 0;
                case SMOKE_SOFT -> (type == MATERIAL_SMOKE
                    && (flags[index] & (FLAG_CORE | FLAG_SPOUT)) == 0) || type == MATERIAL_DUST;
                case SURFACE_FRONT -> type == MATERIAL_FRONT;
                case RETURN_FRONT -> type == MATERIAL_RETURN;
            };
        }

        private float alpha(final int index, final Pass pass, final float progress) {
            float fadeIn = Math.min(1.0F, (particleAge[index] & 0xFFFF) / 2.5F);
            float fadeOut = (float) Math.pow(Math.max(0.0F, 1.0F - progress),
                pass == Pass.SMOKE_CORE ? 0.52F : pass == Pass.SMOKE_SOFT ? 0.72F : 0.56F);
            float base = switch (pass) {
                case FIRE_CORE -> 0.95F;
                case FIRE_HOT -> 0.85F;
                case FIRE_COOLING -> 0.66F;
                case SMOKE_CORE -> 0.76F;
                case SMOKE_SOFT -> (flags[index] & FLAG_TENDRIL) != 0 ? 0.88F : 0.64F;
                case SURFACE_FRONT -> 0.62F;
                case RETURN_FRONT -> 0.52F;
            };
            return Mth.clamp(base * fadeIn * fadeOut, 0.0F, 0.96F);
        }

        private Colour colour(final int index) {
            if (material[index] == MATERIAL_FRONT) {
                if (temperature[index] > 0.18F) {
                    float heat = Mth.clamp(temperature[index], 0.0F, 1.0F);
                    return new Colour(255, Mth.lerpInt(heat, 148, 236),
                        Mth.lerpInt(heat, 54, 184));
                }
                int tone = 184 + Math.floorMod(particleSeed[index], 31);
                return new Colour(tone, Math.min(220, tone + 3), Math.min(226, tone + 7));
            }
            if (material[index] == MATERIAL_RETURN) {
                int tone = 170 + Math.floorMod(particleSeed[index], 34);
                return new Colour(tone, Math.min(214, tone + 4), Math.min(220, tone + 8));
            }
            if (material[index] == MATERIAL_DUST) {
                int tone = 156 + Math.floorMod(particleSeed[index], 42);
                return new Colour(tone, Mth.clamp(tone - 18, 120, 185),
                    Mth.clamp(tone - 38, 92, 162));
            }
            if (material[index] == MATERIAL_SMOKE) {
                int variation = Math.floorMod(particleSeed[index], 34);
                int base = (flags[index] & FLAG_TENDRIL) != 0 ? 188 : 142;
                int tone = Mth.clamp(base + variation, 142, 226);
                return new Colour(tone, Math.min(232, tone + 3), Math.min(238, tone + 8));
            }
            float heat = Mth.clamp(temperature[index], 0.0F, 1.0F);
            if (heat > 0.84F) {
                float t = (heat - 0.84F) / 0.16F;
                return new Colour(255, Mth.lerpInt(t, 216, 255), Mth.lerpInt(t, 64, 226));
            }
            if (heat > 0.48F) {
                float t = (heat - 0.48F) / 0.36F;
                return new Colour(255, Mth.lerpInt(t, 92, 216), Mth.lerpInt(t, 14, 64));
            }
            float t = heat / 0.48F;
            return new Colour(Mth.lerpInt(t, 76, 255), Mth.lerpInt(t, 72, 92),
                Mth.lerpInt(t, 74, 14));
        }

        private static boolean isEmissive(final Pass pass) {
            return pass == Pass.FIRE_CORE || pass == Pass.FIRE_HOT
                || pass == Pass.FIRE_COOLING;
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
