package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.TerrainSurfaceCache;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Persistent packed conventional blast field. The effect is intentionally
 * compact: a crater-width hot body, a short central plasma rise, dense smoke
 * and dust that settle back into the crater, and fast weighted ejecta tendrils.
 * Every particle fades continuously and hot particles cool into smoke instead
 * of disappearing at a material boundary.
 */
public final class ConventionalBlastParticleRenderer {
    private static final int MAX_FIELDS = 16;
    private static final int CAPACITY = 131_072;
    /*
     * The nuclear return front is a persistent, packed ring.  Keep it below
     * the backing field capacity so the pressure front never has to compete
     * for whatever slots happen to be released that tick.  Its deliberately
     * short trail is still wider than the visible pressure band.
     */
    private static final int RETURN_ACTIVE_CAP = 110_000;
    private static final float HE_FIRE_TOP = 4.75F;
    private static final long NUCLEAR_KEY_MASK = 0x6E75636C656172L;
    private static final long SURFACE_KEY_MASK = 0x73757266616365L;
    private static final Map<Long, Field> FIELDS = new LinkedHashMap<>(16, 0.75F, true);

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
        final Vec3 impactPosition, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderSurfaceFront(pose, buffer, age, physicalRadius,
                visualScale, seed, impactPosition, lod, camera);
            return;
        }
        /* The moving ground front owns a separate packed field. It must not lose
         * slots to the fireball/cloud simulation at the exact moment a large
         * detonation needs the most terrain dust. */
        Field field = field(seed ^ SURFACE_KEY_MASK, visualScale, true);
        field.emitSurfaceFront(age, physicalRadius, lod);
        field.render(pose, buffer, age, lod, camera, Pass.SURFACE_FRONT, impactPosition);
    }

    /** Vanilla explosion-texture flecks carried by the outward pressure front. */
    public static void renderSurfaceExplosionPuffs(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final double physicalRadius,
        final float visualScale, final long seed, final Vec3 impactPosition,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) return;
        Field field = field(seed ^ SURFACE_KEY_MASK, visualScale, true);
        field.emitSurfaceFront(age, physicalRadius, lod);
        field.render(pose, buffer, age, lod, camera, Pass.EXPLOSION_FRONT, impactPosition);
    }

    public static void renderNuclearReturnFront(final PoseStack.Pose pose,
        final VertexConsumer buffer, final double age, final double returnRadius,
        final float yieldScale, final long seed, final Vec3 impactPosition,
        final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            LegacyConventionalBlastRenderer.renderNuclearReturnFront(pose, buffer, age, returnRadius,
                yieldScale, seed, lod, impactPosition, camera);
            return;
        }
        Field field = field(seed ^ NUCLEAR_KEY_MASK, yieldScale, true);
        field.emitReturnFront(age, returnRadius, lod);
        field.render(pose, buffer, age, lod, camera, Pass.RETURN_FRONT, impactPosition);
    }

    /** Releases all packed arrays whose owning impact has expired. */
    public static synchronized void retainFields(final Set<Long> activeImpactSeeds) {
        if (activeImpactSeeds == null || activeImpactSeeds.isEmpty()) {
            FIELDS.clear();
            return;
        }
        FIELDS.keySet().removeIf(key -> !activeImpactSeeds.contains(key)
            && !activeImpactSeeds.contains(key ^ SURFACE_KEY_MASK)
            && !activeImpactSeeds.contains(key ^ NUCLEAR_KEY_MASK));
    }

    /** Explicit dimension/world lifecycle hook. */
    public static synchronized void clearLevel() { FIELDS.clear(); }

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
            "packed_soa_compact_physical_v3");
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
        FIRE_CORE,
        FIRE_HOT,
        FIRE_COOLING,
        SMOKE_CORE,
        SMOKE_SOFT,
        SURFACE_FRONT,
        EXPLOSION_FRONT,
        RETURN_FRONT
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
        private static final byte FLAG_UNDERSIDE = 16;
        private static final byte FLAG_LANDED = 32;

        private final long seed;
        private final float scale;
        private final boolean nuclearOnly;
        private final float craterRadius;
        private final float craterFloor;
        private final float smokeTop;
        private final float fireBodyTop;
        private final float spoutTop;
        private final float smokeRadiusLimit;
        private final float dustRadiusLimit;

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
        private int activeReturnCount;
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
            this.spoutTop = HE_FIRE_TOP + Math.max(0.0F, this.scale - 0.28F) * 1.48F;
            this.smokeTop = Math.max(1.25F, spoutTop - 3.35F);
            this.fireBodyTop = smokeTop + 1.15F;
            this.smokeRadiusLimit = craterRadius + 3.0F + 1.8F * this.scale;
            this.dustRadiusLimit = craterRadius + 5.0F + 2.6F * this.scale;
            initialiseSlots();
        }

        private void initialiseSlots() {
            for (int index = 0; index < CAPACITY; index++) {
                freeSlots[index] = CAPACITY - 1 - index;
            }
            freeCount = CAPACITY;
            activeCount = 0;
            activeReturnCount = 0;
        }

        private void ensureSimulated(final double renderedAge) {
            int target = Math.max(0, (int) Math.floor(renderedAge));
            if (target < simulatedTick) return;
            if (simulatedTick < 0 && target > 100) simulatedTick = target - 36;
            int steps = 0;
            while (simulatedTick < target && steps++ < 48) {
                simulatedTick++;
                spawnedLastTick = 0;
                if (!nuclearOnly) emitConventional(simulatedTick);
                update(simulatedTick);
            }
            if (simulatedTick < target) simulatedTick = target;
        }

        private void emitConventional(final int tick) {
            float density = 0.92F + (float) Math.pow(scale, 1.18);
            if (tick <= 5) {
                int ignition = Math.min(5_800,
                    Math.round((620.0F + 720.0F * scale) * density));
                for (int index = 0; index < ignition; index++) {
                    spawnFireBody(tick, index, true);
                }
            }

            int fireFeedEnd = Math.round(48.0F + 18.0F * scale);
            if (tick <= fireFeedEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, fireFeedEnd);
                int body = Math.round((120.0F + 175.0F * scale) * density
                    * (0.28F + 0.72F * remaining));
                int spout = Math.round((45.0F + 62.0F * scale) * density
                    * (0.34F + 0.66F * remaining));
                for (int index = 0; index < body; index++) spawnFireBody(tick, index, false);
                for (int index = 0; index < spout; index++) spawnSpout(tick, index);
            }

            int smokeFeedEnd = Math.round(68.0F + 24.0F * scale);
            if (tick <= smokeFeedEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, smokeFeedEnd);
                int smoke = Math.round((145.0F + 190.0F * scale) * density
                    * (0.52F + 0.48F * remaining));
                int dust = Math.round((170.0F + 235.0F * scale) * density
                    * (0.48F + 0.52F * remaining));
                for (int index = 0; index < smoke; index++) spawnCraterSmoke(tick, index);
                for (int index = 0; index < dust; index++) spawnDustEnvelope(tick, index);
            }

            emitTendrils(tick);

            int spillEnd = Math.round(112.0F + 42.0F * scale);
            if (tick <= spillEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, spillEnd);
                int spill = Math.round((82.0F + 126.0F * scale) * density
                    * (0.40F + 0.60F * remaining));
                for (int index = 0; index < spill; index++) spawnGroundSpill(tick, index);
            }
        }

        private void spawnFireBody(final int tick, final int ordinal,
            final boolean ignition) {
            long random = mix(seed ^ 0x464952455F424F44L ^ ((long) tick << 32)
                ^ ordinal * 0x9E3779B97F4A7C15L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = Mth.sqrt(unit(random, 1));
            float radial = radialFraction * craterRadius * (ignition ? 1.08F : 0.98F);
            float dome = Mth.sqrt(Math.max(0.0F, 1.0F - radialFraction * radialFraction));
            float px = Mth.cos(angle) * radial;
            float pz = Mth.sin(angle) * radial;
            float py = Math.min(fireBodyTop - 0.20F,
                craterFloor + 0.40F + dome * craterRadius * 0.60F
                    + signed(random, 2) * 0.42F);
            float outward = 0.045F + unit(random, 3) * (0.105F + 0.035F * scale);
            float upward = 0.16F + unit(random, 4) * (0.22F + 0.06F * scale);
            byte particleFlags = unit(random, 5) < 0.52F ? FLAG_CORE : 0;
            spawn(MATERIAL_FIRE, particleFlags, px, py, pz,
                Mth.cos(angle) * outward + signed(random, 6) * 0.012F,
                upward,
                Mth.sin(angle) * outward + signed(random, 7) * 0.012F,
                0.84F + unit(random, 8) * 0.20F,
                (0.34F + unit(random, 9) * 0.50F) * (0.94F + scale * 0.15F),
                Math.round(138.0F + unit(random, 10) * (88.0F + 24.0F * scale)),
                (int) random);
        }

        private void spawnSpout(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x53504F55545F5550L ^ ((long) tick << 33)
                ^ ordinal * 0xD1B54A32D192ED03L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = Mth.sqrt(unit(random, 1)) * craterRadius * 0.13F;
            float py = craterFloor + 0.70F + unit(random, 2) * craterRadius * 0.22F;
            spawn(MATERIAL_FIRE, (byte) (FLAG_CORE | FLAG_SPOUT),
                Mth.cos(angle) * radial, py, Mth.sin(angle) * radial,
                signed(random, 3) * 0.020F,
                0.42F + unit(random, 4) * (0.34F + 0.08F * scale),
                signed(random, 5) * 0.020F,
                0.94F + unit(random, 6) * 0.10F,
                (0.32F + unit(random, 7) * 0.46F) * (0.96F + scale * 0.14F),
                Math.round(160.0F + unit(random, 8) * (92.0F + 24.0F * scale)),
                (int) random);
        }

        private void spawnCraterSmoke(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x435241544552534DL ^ ((long) tick << 31)
                ^ ordinal * 0x94D049BB133111EBL);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = Mth.sqrt(unit(random, 1)) * craterRadius * 0.90F;
            boolean underside = unit(random, 2) < 0.30F;
            float px = Mth.cos(angle) * radial;
            float pz = Mth.sin(angle) * radial;
            float py = underside
                ? craterFloor * (0.18F + unit(random, 3) * 0.72F)
                : craterFloor + 0.55F + unit(random, 3) * craterRadius * 0.34F;
            float outward = 0.018F + unit(random, 4) * (0.060F + 0.018F * scale);
            byte particleFlags = unit(random, 5) < 0.42F ? FLAG_CORE : 0;
            if (underside) particleFlags |= FLAG_UNDERSIDE;
            spawn(MATERIAL_SMOKE, particleFlags, px, py, pz,
                Mth.cos(angle) * outward + signed(random, 6) * 0.025F,
                underside ? -0.035F - unit(random, 7) * 0.075F
                    : 0.12F + unit(random, 7) * (0.22F + 0.05F * scale),
                Mth.sin(angle) * outward + signed(random, 8) * 0.025F,
                0.04F + unit(random, 9) * 0.16F,
                (0.62F + unit(random, 10) * 0.88F) * (0.94F + scale * 0.15F),
                Math.round(190.0F + unit(random, 11) * (160.0F + 35.0F * scale)),
                (int) random);
        }

        private void spawnDustEnvelope(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x445553545F454E56L ^ ((long) tick << 29)
                ^ ordinal * 0xDB4F0B9175AE2165L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = craterRadius * (0.42F + unit(random, 1) * 0.66F);
            float outward = 0.055F + unit(random, 2) * (0.12F + 0.035F * scale);
            spawn(MATERIAL_DUST, (byte) 0, Mth.cos(angle) * radial,
                0.05F + unit(random, 3) * Math.min(2.4F, smokeTop),
                Mth.sin(angle) * radial,
                Mth.cos(angle) * outward + signed(random, 4) * 0.040F,
                0.065F + unit(random, 5) * 0.16F,
                Mth.sin(angle) * outward + signed(random, 6) * 0.040F,
                0.0F,
                0.56F + unit(random, 7) * 0.92F,
                Math.round(180.0F + unit(random, 8) * 150.0F),
                (int) random);
        }

        private void emitTendrils(final int tick) {
            int streamCount = 9 + Math.floorMod((int) mix(seed ^ 0x54454E4452494C53L),
                11 + Math.max(1, Math.round(scale * 4.0F)));
            for (int stream = 0; stream < streamCount; stream++) {
                long streamSeed = mix(seed ^ 0x54454E4452494C53L
                    ^ stream * 0xBF58476D1CE4E5B9L);
                int onset = Math.floorMod((int) streamSeed, 12);
                int duration = 7 + Math.floorMod((int) (streamSeed >>> 17), 10);
                if (tick < onset || tick > onset + duration) continue;
                int particles = 4 + Math.floorMod((int) (streamSeed >>> 33), 6);
                for (int particle = 0; particle < particles; particle++) {
                    spawnTendril(tick, stream, particle, streamSeed);
                }
            }
        }

        private void spawnTendril(final int tick, final int stream,
            final int particle, final long streamSeed) {
            long random = mix(streamSeed ^ ((long) tick << 32)
                ^ particle * 0x9E3779B97F4A7C15L);
            float angle = unit(streamSeed, 0) * Mth.TWO_PI
                + signed(streamSeed, 1) * 0.34F + signed(random, 0) * 0.085F;
            float sourceRadius = craterRadius * (0.10F + unit(streamSeed, 2) * 0.38F);
            float speed = 0.68F + unit(streamSeed, 3) * (0.62F + 0.17F * scale);
            float upward = 0.38F + unit(streamSeed, 4) * (0.45F + 0.10F * scale);
            float spread = 0.030F + 0.014F * scale;
            spawn(MATERIAL_SMOKE, FLAG_TENDRIL,
                Mth.cos(angle) * sourceRadius + signed(random, 1) * craterRadius * 0.030F,
                craterFloor + 0.9F + unit(streamSeed, 5) * craterRadius * 0.30F,
                Mth.sin(angle) * sourceRadius + signed(random, 2) * craterRadius * 0.030F,
                Mth.cos(angle) * speed + signed(random, 3) * spread,
                upward + signed(random, 4) * spread * 1.8F,
                Mth.sin(angle) * speed + signed(random, 5) * spread,
                0.0F,
                (0.58F + unit(random, 6) * 0.82F) * (0.96F + scale * 0.14F),
                Math.round(76.0F + unit(streamSeed, 6) * 54.0F + scale * 12.0F),
                (int) random);
        }

        private void spawnGroundSpill(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x47524F554E445350L ^ ((long) tick << 30)
                ^ ordinal * 0xDB4F0B9175AE2165L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = craterRadius * (0.72F + unit(random, 1) * 0.34F);
            float outward = 0.030F + unit(random, 2) * (0.075F + 0.018F * scale);
            byte type = unit(random, 3) < 0.60F ? MATERIAL_DUST : MATERIAL_SMOKE;
            spawn(type, FLAG_GROUND, Mth.cos(angle) * radial,
                craterFloor * unit(random, 4) * 0.22F + 0.06F,
                Mth.sin(angle) * radial,
                Mth.cos(angle) * outward - Mth.sin(angle) * signed(random, 5) * 0.040F,
                0.012F + unit(random, 6) * 0.050F,
                Mth.sin(angle) * outward + Mth.cos(angle) * signed(random, 5) * 0.040F,
                0.0F,
                0.48F + unit(random, 7) * 0.78F,
                Math.round(210.0F + unit(random, 8) * 170.0F),
                (int) random);
        }

        private void emitSurfaceFront(final double renderedAge,
            final double physicalRadius, final WarheadMesh.Lod lod) {
            ensureSimulated(renderedAge);
            int tick = Math.max(0, (int) Math.floor(renderedAge));
            if (tick == lastSurfaceTick || physicalRadius <= 0.0) return;
            lastSurfaceTick = tick;
            if (renderedAge >= WarheadVisualMath.airShockwaveDurationTicks(scale)) return;
            int base = switch (lod) {
                case NEAR -> 1_600;
                case MEDIUM -> 1_200;
                case FAR -> 800;
            };
            int count = Math.min(5_000,
                Math.round(base * (0.72F + (float) Math.pow(scale, 1.10))));
            for (int index = 0; index < count; index++) {
                long random = mix(seed ^ 0x46524F4E545F5633L ^ ((long) tick << 32)
                    ^ index * 0x9E3779B97F4A7C15L);
                float angle = (index + unit(random, 0)) / count * Mth.TWO_PI;
                float trail = unit(random, 1) * (1.8F + 4.4F * scale);
                float radial = (float) Math.max(0.0, physicalRadius - trail);
                float tangent = signed(random, 2) * 0.075F;
                float outward = 0.045F + unit(random, 3) * 0.105F;
                float heat = unit(random, 4) < 0.13F
                    ? 0.48F + unit(random, 5) * 0.32F : 0.0F;
                spawn(MATERIAL_FRONT, (byte) 0,
                    Mth.cos(angle) * radial,
                    0.04F + unit(random, 6) * (0.38F + 0.40F * scale),
                    Mth.sin(angle) * radial,
                    Mth.cos(angle) * outward - Mth.sin(angle) * tangent,
                    0.018F + unit(random, 7) * 0.075F,
                    Mth.sin(angle) * outward + Mth.cos(angle) * tangent,
                    heat,
                    (0.30F + unit(random, 8) * 0.54F) * (0.96F + scale * 0.10F),
                    Math.round(92.0F + unit(random, 9) * 82.0F),
                    (int) random);
            }
        }

        private void emitReturnFront(final double renderedAge,
            final double returnRadius, final WarheadMesh.Lod lod) {
            ensureSimulated(renderedAge);
            int tick = Math.max(0, (int) Math.floor(renderedAge));
            if (tick == lastReturnTick || returnRadius <= 0.0) return;
            lastReturnTick = tick;
            int base = switch (lod) {
                case NEAR -> 1_800;
                case MEDIUM -> 1_400;
                case FAR -> 900;
            };
            int count = Math.min(5_200,
                Math.round(base * (0.78F + (float) Math.sqrt(scale))));
            int admitted = Math.min(count,
                Math.max(0, RETURN_ACTIVE_CAP - activeReturnCount));
            if (admitted <= 0) return;
            /*
             * A field near its cap may only accept some of this tick's ring.
             * Spread those admissions over the whole circumference rather
             * than filling the low angular indices first: otherwise released
             * slots make a visibly clockwise arc appear at the end of a nuke.
             */
            int angularOffset = Math.floorMod((int) mix(seed
                ^ 0x52455455524E4F46L ^ ((long) tick << 19)), count);
            for (int emission = 0; emission < admitted; emission++) {
                int index = Math.floorMod((int) (((long) emission * count) / admitted)
                    + angularOffset, count);
                long random = mix(seed ^ 0x52455455524E5633L ^ ((long) tick << 32)
                    ^ index * 0xD1B54A32D192ED03L);
                float angle = (index + unit(random, 0)) / count * Mth.TWO_PI;
                float radial = (float) returnRadius
                    + signed(random, 1) * (0.85F + 1.5F * scale);
                float inward = 0.14F + unit(random, 2) * (0.22F + 0.08F * scale);
                spawn(MATERIAL_RETURN, (byte) 0,
                    Mth.cos(angle) * radial,
                    0.05F + unit(random, 3) * (0.62F + 0.52F * scale),
                    Mth.sin(angle) * radial,
                    -Mth.cos(angle) * inward,
                    0.012F + unit(random, 4) * 0.045F,
                    -Mth.sin(angle) * inward,
                    0.0F,
                    (0.30F + unit(random, 5) * 0.56F) * (0.96F + 0.10F * scale),
                    Math.round(26.0F + unit(random, 6) * 18.0F),
                    (int) random);
            }
        }

        private void spawn(final byte particleMaterial, final byte particleFlags,
            final float px, final float py, final float pz,
            final float vx, final float vy, final float vz,
            final float heat, final float particleRadius,
            final int particleLifetime, final int randomSeed) {
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
            rotationVelocity[slot] = signed(randomSeed, 1) * 0.031F;
            particleAge[slot] = 0;
            lifetime[slot] = (short) Mth.clamp(particleLifetime, 12, Short.MAX_VALUE);
            particleSeed[slot] = randomSeed;
            material[slot] = particleMaterial;
            flags[slot] = particleFlags;
            if (particleMaterial == MATERIAL_RETURN) activeReturnCount++;
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
            if (material[removedSlot] == MATERIAL_RETURN) activeReturnCount--;
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
                long turbulentSeed = mix(((long) particleSeed[index] << 32)
                    ^ tick * 0x9E3779B97F4A7C15L);
                float turbulence = signed(turbulentSeed, 0);
                float crossTurbulence = signed(turbulentSeed, 1);

                switch (material[index]) {
                    case MATERIAL_FIRE -> updateFire(index, age, life, progress,
                        turbulence, crossTurbulence);
                    case MATERIAL_SMOKE -> updateSmoke(index, age, life, progress,
                        turbulence, crossTurbulence);
                    case MATERIAL_DUST -> updateDust(index, progress, turbulence,
                        crossTurbulence);
                    case MATERIAL_FRONT -> {
                        velocityX[index] *= 0.952F;
                        velocityZ[index] *= 0.952F;
                        velocityY[index] = velocityY[index] * 0.94F - 0.0012F;
                        temperature[index] = Math.max(0.0F, temperature[index] - 0.025F);
                        radius[index] *= 1.0065F;
                    }
                    case MATERIAL_RETURN -> {
                        velocityX[index] *= 0.994F;
                        velocityZ[index] *= 0.994F;
                        velocityY[index] = velocityY[index] * 0.94F - 0.0010F;
                        radius[index] *= 1.006F;
                    }
                    default -> { }
                }

                x[index] += velocityX[index];
                y[index] += velocityY[index];
                z[index] += velocityZ[index];
                constrain(index);
                rotation[index] += rotationVelocity[index];
                particleAge[index] = (short) (age + 1);
                activePosition++;
            }
        }

        private void updateFire(final int index, final int age, final int life,
            final float progress, final float turbulence, final float crossTurbulence) {
            boolean spout = (flags[index] & FLAG_SPOUT) != 0;
            boolean core = (flags[index] & FLAG_CORE) != 0;
            float cooling = spout ? 0.0038F : core ? 0.0047F : 0.0062F;
            temperature[index] = Math.max(0.0F,
                temperature[index] - cooling * (0.72F + progress * 0.78F));
            velocityX[index] += turbulence * (spout ? 0.0025F : 0.0038F);
            velocityZ[index] += crossTurbulence * (spout ? 0.0025F : 0.0038F);
            float lift = spout && y[index] < spoutTop - 0.4F ? 0.0065F
                : core && y[index] < fireBodyTop ? 0.0022F : -0.0012F;
            velocityY[index] = velocityY[index] * (spout ? 0.948F : 0.935F) + lift;
            velocityX[index] *= 0.986F;
            velocityZ[index] *= 0.986F;
            radius[index] *= temperature[index] > 0.25F ? 1.0020F : 1.0042F;
            if (temperature[index] <= 0.055F) {
                material[index] = MATERIAL_SMOKE;
                flags[index] = (byte) (flags[index] & (FLAG_CORE | FLAG_SPOUT | FLAG_UNDERSIDE));
                lifetime[index] = (short) Math.max(life, Math.min(Short.MAX_VALUE, age + 105));
                velocityY[index] *= 0.55F;
            }
        }

        private void updateSmoke(final int index, final int age, final int life,
            final float progress, final float turbulence, final float crossTurbulence) {
            if ((flags[index] & FLAG_TENDRIL) != 0) {
                if ((flags[index] & FLAG_LANDED) == 0) {
                    velocityY[index] -= 0.052F;
                    velocityX[index] = velocityX[index] * 0.986F + turbulence * 0.0020F;
                    velocityZ[index] = velocityZ[index] * 0.986F + crossTurbulence * 0.0020F;
                    radius[index] *= 1.0032F;
                    if (y[index] <= 0.06F && velocityY[index] < 0.0F) {
                        y[index] = 0.06F;
                        velocityY[index] = 0.0F;
                        velocityX[index] *= 0.30F;
                        velocityZ[index] *= 0.30F;
                        flags[index] |= FLAG_LANDED;
                        lifetime[index] = (short) Math.min(life, age + 22);
                    }
                } else {
                    velocityX[index] *= 0.74F;
                    velocityZ[index] *= 0.74F;
                    radius[index] *= 1.006F;
                }
                return;
            }

            velocityX[index] += turbulence * 0.0028F;
            velocityZ[index] += crossTurbulence * 0.0028F;
            if ((flags[index] & FLAG_UNDERSIDE) != 0) {
                velocityY[index] = velocityY[index] * 0.956F - 0.0018F;
            } else if (progress < 0.30F && y[index] < smokeTop) {
                velocityY[index] = velocityY[index] * 0.955F + 0.0032F;
            } else {
                velocityY[index] = velocityY[index] * 0.958F - 0.0030F;
            }
            velocityX[index] *= 0.966F;
            velocityZ[index] *= 0.966F;
            radius[index] *= 1.0035F;
            temperature[index] = Math.max(0.0F, temperature[index] - 0.0028F);
        }

        private void updateDust(final int index, final float progress,
            final float turbulence, final float crossTurbulence) {
            velocityX[index] += turbulence * 0.0022F;
            velocityZ[index] += crossTurbulence * 0.0022F;
            velocityY[index] = velocityY[index] * 0.947F
                + (progress < 0.20F ? 0.0012F : -0.0038F);
            velocityX[index] *= (flags[index] & FLAG_GROUND) != 0 ? 0.950F : 0.963F;
            velocityZ[index] *= (flags[index] & FLAG_GROUND) != 0 ? 0.950F : 0.963F;
            radius[index] *= 1.0038F;
        }

        private void constrain(final int index) {
            if (material[index] == MATERIAL_FIRE) {
                float top = (flags[index] & FLAG_SPOUT) != 0 ? spoutTop : fireBodyTop;
                if (y[index] > top) {
                    y[index] = top;
                    velocityY[index] = -Math.abs(velocityY[index]) * 0.16F;
                    spreadOutward(index, (flags[index] & FLAG_SPOUT) != 0 ? 0.014F : 0.025F);
                }
                return;
            }

            if (material[index] == MATERIAL_SMOKE || material[index] == MATERIAL_DUST) {
                if ((flags[index] & FLAG_TENDRIL) == 0 && y[index] > smokeTop) {
                    y[index] = smokeTop;
                    velocityY[index] = -Math.abs(velocityY[index]) * 0.24F;
                }
                float settle = craterFloor * (0.05F + unit(particleSeed[index], 7) * 0.72F)
                    + 0.08F;
                if (y[index] < settle) {
                    y[index] = settle;
                    velocityY[index] = 0.0F;
                    velocityX[index] *= 0.88F;
                    velocityZ[index] *= 0.88F;
                }
                float limit = material[index] == MATERIAL_DUST ? dustRadiusLimit : smokeRadiusLimit;
                confineRadius(index, limit);
            }
        }

        private void confineRadius(final int index, final float limit) {
            float horizontal = Mth.sqrt(x[index] * x[index] + z[index] * z[index]);
            if (horizontal <= limit || horizontal < 1.0E-4F) return;
            float excess = horizontal - limit;
            float radialX = x[index] / horizontal;
            float radialZ = z[index] / horizontal;
            velocityX[index] -= radialX * Math.min(0.14F, excess * 0.035F);
            velocityZ[index] -= radialZ * Math.min(0.14F, excess * 0.035F);
            velocityX[index] *= 0.90F;
            velocityZ[index] *= 0.90F;
        }

        private void spreadOutward(final int index, final float amount) {
            float horizontal = Mth.sqrt(x[index] * x[index] + z[index] * z[index]);
            if (horizontal > 1.0E-4F) {
                velocityX[index] += x[index] / horizontal * amount;
                velocityZ[index] += z[index] / horizontal * amount;
            }
        }

        private void render(final PoseStack.Pose pose, final VertexConsumer buffer,
            final double renderedAge, final WarheadMesh.Lod lod, final Quaternionf camera,
            final Pass pass) {
            render(pose, buffer, renderedAge, lod, camera, pass, null);
        }

        private void render(final PoseStack.Pose pose, final VertexConsumer buffer,
            final double renderedAge, final WarheadMesh.Lod lod, final Quaternionf camera,
            final Pass pass, final Vec3 impactPosition) {
            ensureSimulated(renderedAge);
            float partial = (float) Mth.clamp(renderedAge - Math.floor(renderedAge), 0.0, 1.0);
            Basis basis = Basis.from(camera);
            int stride = switch (lod) {
                case NEAR, MEDIUM -> 1;
                case FAR -> 2;
            };
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
                if (alpha <= 0.004F) {
                    rejected++;
                    continue;
                }
                float px = Mth.lerp(partial, previousX[index], x[index]);
                float py = Mth.lerp(partial, previousY[index], y[index]);
                float pz = Mth.lerp(partial, previousZ[index], z[index]);
                if (impactPosition != null && (pass == Pass.SURFACE_FRONT
                    || pass == Pass.EXPLOSION_FRONT || pass == Pass.RETURN_FRONT)) {
                    ClientLevel level = Minecraft.getInstance().level;
                    TerrainSurfaceCache.SurfaceSample surface = TerrainSurfaceCache.INSTANCE.sample(
                        level, impactPosition.x + px, impactPosition.z + pz);
                    if (surface != null) py = (float) (surface.position().y - impactPosition.y)
                        + Math.max(0.06F, py);
                }
                Colour colour = colour(index, pass);
                float drawRadius = radius[index];
                if (pass == Pass.SMOKE_CORE) drawRadius *= 1.20F;
                if (pass == Pass.SMOKE_SOFT) drawRadius *= 1.08F;
                if ((flags[index] & FLAG_TENDRIL) != 0) drawRadius *= 1.16F;
                int light = isEmissive(pass) ? 0xF000F0
                    : pass == Pass.SMOKE_CORE ? 0x900090 : 0xA000A0;
                billboard(pose, buffer, px, py, pz, drawRadius, rotation[index],
                    colour.red, colour.green, colour.blue, alpha, light, basis);
            }
            culledLastRender = Math.max(0, activeCount - inspected) + rejected;
        }

        private boolean matches(final int index, final Pass pass) {
            byte type = material[index];
            float heat = temperature[index];
            return switch (pass) {
                case FIRE_CORE -> type == MATERIAL_FIRE && heat >= 0.64F
                    && (flags[index] & FLAG_CORE) != 0;
                case FIRE_HOT -> type == MATERIAL_FIRE && heat >= 0.48F
                    && (flags[index] & FLAG_CORE) == 0;
                case FIRE_COOLING -> type == MATERIAL_FIRE && heat >= 0.055F && heat < 0.64F;
                case SMOKE_CORE -> (type == MATERIAL_SMOKE
                    && (flags[index] & (FLAG_CORE | FLAG_SPOUT)) != 0)
                    || (type == MATERIAL_FIRE && heat < 0.14F
                        && (flags[index] & FLAG_CORE) != 0);
                case SMOKE_SOFT -> (type == MATERIAL_SMOKE
                    && (flags[index] & (FLAG_CORE | FLAG_SPOUT)) == 0)
                    || type == MATERIAL_DUST
                    || (type == MATERIAL_FIRE && heat < 0.14F
                        && (flags[index] & FLAG_CORE) == 0);
                case SURFACE_FRONT -> type == MATERIAL_FRONT;
                case EXPLOSION_FRONT -> type == MATERIAL_FRONT && heat > 0.18F
                    && Math.floorMod(particleSeed[index], 3) == 0;
                case RETURN_FRONT -> type == MATERIAL_RETURN;
            };
        }

        private float alpha(final int index, final Pass pass, final float progress) {
            float age = particleAge[index] & 0xFFFF;
            float fadeIn = Mth.clamp(age / 4.0F, 0.0F, 1.0F);
            float remaining = Mth.clamp(1.0F - progress, 0.0F, 1.0F);
            float exponent = switch (pass) {
                case FIRE_CORE, FIRE_HOT, FIRE_COOLING -> 0.72F;
                case SMOKE_CORE -> 0.62F;
                case SMOKE_SOFT -> 0.74F;
                case SURFACE_FRONT, EXPLOSION_FRONT, RETURN_FRONT -> 0.82F;
            };
            float fadeOut = (float) Math.pow(remaining, exponent);
            float base = switch (pass) {
                case FIRE_CORE -> 0.96F;
                case FIRE_HOT -> 0.88F;
                case FIRE_COOLING -> 0.72F;
                case SMOKE_CORE -> 0.82F;
                case SMOKE_SOFT -> (flags[index] & FLAG_TENDRIL) != 0 ? 0.90F : 0.70F;
                case SURFACE_FRONT -> 0.68F;
                case EXPLOSION_FRONT -> 0.78F;
                case RETURN_FRONT -> 0.62F;
            };
            if ((flags[index] & FLAG_LANDED) != 0) base *= 0.84F;
            return Mth.clamp(base * fadeIn * fadeOut, 0.0F, 0.98F);
        }

        private Colour colour(final int index, final Pass pass) {
            if (pass == Pass.EXPLOSION_FRONT) return new Colour(255, 228, 176);
            if (material[index] == MATERIAL_FRONT) {
                if (temperature[index] > 0.18F) {
                    float heat = Mth.clamp(temperature[index], 0.0F, 1.0F);
                    return new Colour(255, Mth.lerpInt(heat, 154, 238),
                        Mth.lerpInt(heat, 62, 190));
                }
                int tone = 178 + Math.floorMod(particleSeed[index], 42);
                return new Colour(tone, Math.min(228, tone + 4), Math.min(234, tone + 9));
            }
            if (material[index] == MATERIAL_RETURN) {
                int tone = 174 + Math.floorMod(particleSeed[index], 46);
                return new Colour(tone, Math.min(228, tone + 5), Math.min(236, tone + 11));
            }
            if (material[index] == MATERIAL_DUST) {
                int variation = Math.floorMod(particleSeed[index], 55);
                int tone = 145 + variation;
                return new Colour(tone, Mth.clamp(tone - 14, 118, 190),
                    Mth.clamp(tone - 30, 92, 172));
            }
            if (material[index] == MATERIAL_SMOKE
                || (material[index] == MATERIAL_FIRE && temperature[index] < 0.14F)) {
                int selector = Math.floorMod(particleSeed[index], 100);
                if ((flags[index] & FLAG_TENDRIL) != 0) {
                    if (selector < 10) {
                        int dark = 76 + Math.floorMod(particleSeed[index] >>> 8, 54);
                        return new Colour(dark, dark + 2, dark + 5);
                    }
                    if (selector < 28) {
                        int mid = 148 + Math.floorMod(particleSeed[index] >>> 8, 50);
                        return new Colour(mid, Math.min(208, mid + 3), Math.min(216, mid + 8));
                    }
                    int pale = 205 + Math.floorMod(particleSeed[index] >>> 8, 40);
                    return new Colour(pale, Math.min(248, pale + 2), Math.min(252, pale + 6));
                }
                float spatial = 0.5F + 0.5F * Mth.sin(
                    x[index] * 0.29F + z[index] * 0.23F + particleSeed[index] * 0.00017F);
                int noise = Math.floorMod(particleSeed[index] >>> 7, 44);
                int base = (flags[index] & FLAG_CORE) != 0 ? 60 : 92;
                int tone = Mth.clamp(base + noise + (int) (spatial * 36.0F), 52, 178);
                return new Colour(tone, Math.min(184, tone + 3), Math.min(190, tone + 8));
            }
            float heat = Mth.clamp(temperature[index], 0.0F, 1.0F);
            if (heat > 0.84F) {
                float t = (heat - 0.84F) / 0.16F;
                return new Colour(255, Mth.lerpInt(t, 214, 255), Mth.lerpInt(t, 54, 220));
            }
            if (heat > 0.42F) {
                float t = (heat - 0.42F) / 0.42F;
                return new Colour(255, Mth.lerpInt(t, 80, 214), Mth.lerpInt(t, 12, 54));
            }
            float t = heat / 0.42F;
            return new Colour(Mth.lerpInt(t, 72, 255), Mth.lerpInt(t, 68, 80),
                Mth.lerpInt(t, 72, 12));
        }

        private static boolean isEmissive(final Pass pass) {
            return pass == Pass.FIRE_CORE || pass == Pass.FIRE_HOT
                || pass == Pass.FIRE_COOLING || pass == Pass.EXPLOSION_FRONT;
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
        final float centerX, final float centerY, final float centerZ,
        final float radius, final float rotation,
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

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ,
        final float localX, final float localY, final float cosine, final float sine,
        final float u, final float v,
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
