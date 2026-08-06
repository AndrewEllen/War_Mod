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
 * Conventional blast renderer shaped around a compact rising fireball rather
 * than expanding horizontal discs. Hot material rises through the middle,
 * cools into a tall smoke body and then settles back towards the crater. Fake
 * ejecta streams use ballistic heads and leave pale, coherent smoke trails.
 */
public final class ConventionalBlastVisualV4 {
    private static final int MAX_FIELDS = 4;
    private static final Map<Long, Field> FIELDS = new LinkedHashMap<>(8, 0.75F, true);

    private ConventionalBlastVisualV4() { }

    public static synchronized void clear() {
        FIELDS.clear();
    }

    public static void renderFireCore(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            ConventionalBlastParticleRenderer.renderFireCore(pose, buffer, age, visualScale,
                profile, seed, lod, camera);
            return;
        }
        field(seed, visualScale).render(pose, buffer, age, lod, camera, Pass.FIRE_CORE);
    }

    public static void renderHot(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            ConventionalBlastParticleRenderer.renderHot(pose, buffer, age, visualScale,
                profile, seed, lod, camera);
            return;
        }
        field(seed, visualScale).render(pose, buffer, age, lod, camera, Pass.FIRE_HOT);
    }

    public static void renderCooling(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            ConventionalBlastParticleRenderer.renderCooling(pose, buffer, age, visualScale,
                profile, seed, lod, camera);
            return;
        }
        field(seed, visualScale).render(pose, buffer, age, lod, camera, Pass.FIRE_COOLING);
    }

    public static void renderSmokeCore(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            ConventionalBlastParticleRenderer.renderSmokeCore(pose, buffer, age, visualScale,
                profile, seed, lod, camera);
            return;
        }
        field(seed, visualScale).render(pose, buffer, age, lod, camera, Pass.SMOKE_CORE);
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
        if (!WarheadRenderSettings.usePackedParticles()) {
            ConventionalBlastParticleRenderer.renderSmoke(pose, buffer, age, visualScale,
                profile, seed, lod, camera);
            return;
        }
        field(seed, visualScale).render(pose, buffer, age, lod, camera, Pass.SMOKE_SOFT);
    }

    public static synchronized DebugSnapshot debugSnapshot() {
        int active = 0;
        int spawned = 0;
        int culled = 0;
        for (Field field : FIELDS.values()) {
            active += field.activeCount;
            spawned += field.spawnedLastTick;
            culled += field.culledLastRender;
        }
        return new DebugSnapshot(active, spawned, culled, FIELDS.size());
    }

    private static synchronized Field field(final long seed, final float visualScale) {
        int desiredCapacity = WarheadRenderSettings.conventionalParticleBudget();
        Field existing = FIELDS.get(seed);
        if (existing != null && existing.capacity == desiredCapacity) return existing;
        if (existing != null) FIELDS.remove(seed);
        while (FIELDS.size() >= MAX_FIELDS) {
            Iterator<Long> iterator = FIELDS.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        Field created = new Field(seed, visualScale, desiredCapacity);
        FIELDS.put(seed, created);
        return created;
    }

    public record DebugSnapshot(int activeParticles, int spawnedParticlesPerTick,
        int culledParticles, int activeFields) { }

    private enum Pass {
        FIRE_CORE,
        FIRE_HOT,
        FIRE_COOLING,
        SMOKE_CORE,
        SMOKE_SOFT
    }

    private static final class Field {
        private static final byte FIRE = 0;
        private static final byte SMOKE = 1;
        private static final byte DUST = 2;
        private static final byte TENDRIL = 3;

        private static final byte CORE = 1;
        private static final byte COLUMN = 2;
        private static final byte UNDERSIDE = 4;
        private static final byte LANDED = 8;

        private final long seed;
        private final float scale;
        private final int capacity;
        private final float craterRadius;
        private final float craterFloor;
        private final float smokeTop;
        private final float fireTop;
        private final float bodyRadius;
        private final float leanX;
        private final float leanZ;
        private final float ellipticityX;
        private final float ellipticityZ;
        private final Tendril[] tendrils;

        private final float[] x;
        private final float[] y;
        private final float[] z;
        private final float[] oldX;
        private final float[] oldY;
        private final float[] oldZ;
        private final float[] vx;
        private final float[] vy;
        private final float[] vz;
        private final float[] temperature;
        private final float[] size;
        private final float[] rotation;
        private final float[] spin;
        private final short[] particleAge;
        private final short[] lifetime;
        private final int[] randomSeed;
        private final byte[] material;
        private final byte[] flags;
        private final int[] active;
        private final int[] free;

        private int activeCount;
        private int freeCount;
        private int simulatedTick = -1;
        private int spawnedLastTick;
        private int culledLastRender;

        private Field(final long seed, final float visualScale, final int capacity) {
            this.seed = seed;
            this.scale = Mth.clamp(visualScale, 0.28F, 1.75F);
            this.capacity = capacity;
            this.craterRadius = 2.0F + 13.5F * scale;
            this.craterFloor = -Math.max(1.4F, craterRadius * 0.25F);
            this.bodyRadius = craterRadius * (0.72F + unit(seed, 0) * 0.13F);
            this.smokeTop = 2.0F + craterRadius * (0.43F + unit(seed, 1) * 0.055F);
            this.fireTop = smokeTop + 3.25F + unit(seed, 2) * 0.85F;
            this.leanX = signed(seed, 3) * craterRadius * 0.10F;
            this.leanZ = signed(seed, 4) * craterRadius * 0.10F;
            this.ellipticityX = 0.86F + unit(seed, 5) * 0.30F;
            this.ellipticityZ = 0.86F + unit(seed, 6) * 0.30F;
            this.tendrils = createTendrils(seed, scale);

            x = new float[capacity];
            y = new float[capacity];
            z = new float[capacity];
            oldX = new float[capacity];
            oldY = new float[capacity];
            oldZ = new float[capacity];
            vx = new float[capacity];
            vy = new float[capacity];
            vz = new float[capacity];
            temperature = new float[capacity];
            size = new float[capacity];
            rotation = new float[capacity];
            spin = new float[capacity];
            particleAge = new short[capacity];
            lifetime = new short[capacity];
            randomSeed = new int[capacity];
            material = new byte[capacity];
            flags = new byte[capacity];
            active = new int[capacity];
            free = new int[capacity];
            for (int index = 0; index < capacity; index++) free[index] = capacity - 1 - index;
            freeCount = capacity;
        }

        private static Tendril[] createTendrils(final long seed, final float scale) {
            int count = 11 + Math.round(scale * 8.0F)
                + Math.floorMod((int) mix(seed ^ 0x54454E4452494C34L), 8);
            Tendril[] result = new Tendril[count];
            for (int index = 0; index < count; index++) {
                long random = mix(seed ^ index * 0x9E3779B97F4A7C15L
                    ^ 0x57484954455F4152L);
                result[index] = new Tendril(
                    unit(random, 0) * Mth.TWO_PI,
                    0.42F + unit(random, 1) * (0.62F + 0.20F * scale),
                    0.34F + unit(random, 2) * (0.46F + 0.12F * scale),
                    Math.floorMod((int) (random >>> 11), 15),
                    18 + Math.floorMod((int) (random >>> 27), 17),
                    signed(random, 3) * 0.12F,
                    random
                );
            }
            return result;
        }

        private void ensureSimulated(final double renderedAge) {
            int target = Math.max(0, (int) Math.floor(renderedAge));
            if (target <= simulatedTick) return;
            if (simulatedTick < 0 && target > 80) simulatedTick = target - 40;
            int steps = 0;
            while (simulatedTick < target && steps++ < 48) {
                simulatedTick++;
                spawnedLastTick = 0;
                emit(simulatedTick);
                update(simulatedTick);
            }
            if (simulatedTick < target) simulatedTick = target;
        }

        private void emit(final int tick) {
            float budgetDensity = Mth.clamp(
                (float) Math.sqrt(WarheadRenderSettings.particleBudgetMultiplier() / 3.0F),
                0.45F, 1.42F);
            if (tick <= 4) {
                int count = Math.round((760.0F + 950.0F * scale) * budgetDensity);
                for (int index = 0; index < count; index++) spawnInitialFire(tick, index);
            }

            int fireFeedEnd = Math.round(76.0F + 35.0F * scale);
            if (tick <= fireFeedEnd) {
                float remaining = 1.0F - tick / (float) fireFeedEnd;
                int core = Math.round((80.0F + 118.0F * scale)
                    * (0.34F + remaining * 0.66F) * budgetDensity);
                int column = Math.round((42.0F + 72.0F * scale)
                    * (0.40F + remaining * 0.60F) * budgetDensity);
                for (int index = 0; index < core; index++) spawnCoreFire(tick, index);
                for (int index = 0; index < column; index++) spawnColumnFire(tick, index);
            }

            int smokeFeedEnd = Math.round(145.0F + 45.0F * scale);
            if (tick <= smokeFeedEnd) {
                float remaining = 1.0F - tick / (float) smokeFeedEnd;
                int smoke = Math.round((76.0F + 118.0F * scale)
                    * (0.52F + remaining * 0.48F) * budgetDensity);
                int dust = Math.round((44.0F + 78.0F * scale)
                    * (0.45F + remaining * 0.55F) * budgetDensity);
                for (int index = 0; index < smoke; index++) spawnSmoke(tick, index);
                for (int index = 0; index < dust; index++) spawnDust(tick, index);
            }

            emitTendrilTrails(tick, budgetDensity);
        }

        private void spawnInitialFire(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x494E495449414C34L ^ ((long) tick << 32)
                ^ ordinal * 0xD1B54A32D192ED03L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = Mth.sqrt(unit(random, 1));
            float normalizedY = signed(random, 2);
            float dome = Mth.sqrt(Math.max(0.0F, 1.0F - radialFraction * radialFraction));
            float radial = radialFraction * bodyRadius;
            float px = Mth.cos(angle) * radial * ellipticityX;
            float pz = Mth.sin(angle) * radial * ellipticityZ;
            float py = craterFloor + bodyRadius * (0.42F + 0.48F * dome)
                + normalizedY * bodyRadius * 0.18F;
            float outer = radialFraction;
            float outward = 0.015F + unit(random, 3) * 0.055F;
            spawn(FIRE, CORE, px, py, pz,
                Mth.cos(angle) * outward + signed(random, 4) * 0.018F,
                0.17F + unit(random, 5) * 0.31F,
                Mth.sin(angle) * outward + signed(random, 6) * 0.018F,
                0.88F + unit(random, 7) * 0.12F,
                scaledSize(random, outer, 0.58F, 1.48F),
                190 + Math.floorMod((int) (random >>> 35), 125), (int) random);
        }

        private void spawnCoreFire(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x434F52455F464934L ^ ((long) tick << 31)
                ^ ordinal * 0x94D049BB133111EBL);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = Mth.sqrt(unit(random, 1));
            float radial = radialFraction * bodyRadius * 0.78F;
            float heightFraction = unit(random, 2);
            float px = Mth.cos(angle) * radial * ellipticityX
                + leanX * heightFraction * 0.55F;
            float pz = Mth.sin(angle) * radial * ellipticityZ
                + leanZ * heightFraction * 0.55F;
            float py = craterFloor + 0.5F
                + heightFraction * Math.max(1.0F, smokeTop - craterFloor - 0.2F);
            spawn(FIRE, CORE, px, py, pz,
                -px * 0.0022F + signed(random, 3) * 0.013F,
                0.20F + unit(random, 4) * 0.28F,
                -pz * 0.0022F + signed(random, 5) * 0.013F,
                0.76F + unit(random, 6) * 0.22F,
                scaledSize(random, radialFraction, 0.52F, 1.34F),
                205 + Math.floorMod((int) (random >>> 33), 145), (int) random);
        }

        private void spawnColumnFire(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x434F4C554D4E5F34L ^ ((long) tick << 30)
                ^ ordinal * 0xBF58476D1CE4E5B9L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = Mth.sqrt(unit(random, 1));
            float radial = radialFraction * bodyRadius * 0.22F;
            float heightFraction = unit(random, 2) * 0.40F;
            float px = Mth.cos(angle) * radial + leanX * heightFraction;
            float pz = Mth.sin(angle) * radial + leanZ * heightFraction;
            float py = craterFloor + 0.7F
                + heightFraction * Math.max(1.0F, smokeTop - craterFloor);
            spawn(FIRE, (byte) (CORE | COLUMN), px, py, pz,
                signed(random, 3) * 0.010F,
                0.36F + unit(random, 4) * (0.32F + 0.08F * scale),
                signed(random, 5) * 0.010F,
                0.90F + unit(random, 6) * 0.10F,
                scaledSize(random, radialFraction, 0.50F, 1.22F),
                230 + Math.floorMod((int) (random >>> 31), 155), (int) random);
        }

        private void spawnSmoke(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x534D4F4B455F5634L ^ ((long) tick << 29)
                ^ ordinal * 0x9E3779B97F4A7C15L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = Mth.sqrt(unit(random, 1));
            float radial = radialFraction * bodyRadius * 0.94F;
            boolean underside = unit(random, 2) < 0.20F;
            float heightFraction = unit(random, 3);
            float px = Mth.cos(angle) * radial * ellipticityX
                + leanX * heightFraction * 0.50F;
            float pz = Mth.sin(angle) * radial * ellipticityZ
                + leanZ * heightFraction * 0.50F;
            float py = underside
                ? craterFloor * (0.10F + heightFraction * 0.72F)
                : craterFloor + 0.35F
                    + heightFraction * Math.max(1.0F, smokeTop - craterFloor - 0.4F);
            byte particleFlags = radialFraction < 0.58F ? CORE : 0;
            if (underside) particleFlags |= UNDERSIDE;
            spawn(SMOKE, particleFlags, px, py, pz,
                -px * 0.0010F + signed(random, 4) * 0.020F,
                underside ? -0.020F - unit(random, 5) * 0.055F
                    : 0.075F + unit(random, 5) * 0.17F,
                -pz * 0.0010F + signed(random, 6) * 0.020F,
                0.04F + unit(random, 7) * 0.16F,
                scaledSize(random, radialFraction, 0.68F, 1.68F),
                250 + Math.floorMod((int) (random >>> 35), 185), (int) random);
        }

        private void spawnDust(final int tick, final int ordinal) {
            long random = mix(seed ^ 0x445553545F563434L ^ ((long) tick << 28)
                ^ ordinal * 0xDB4F0B9175AE2165L);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radialFraction = 0.42F + unit(random, 1) * 0.58F;
            float radial = radialFraction * craterRadius;
            float px = Mth.cos(angle) * radial;
            float pz = Mth.sin(angle) * radial;
            spawn(DUST, (byte) 0, px, 0.05F + unit(random, 2) * 1.2F, pz,
                Mth.cos(angle) * (0.025F + unit(random, 3) * 0.070F),
                0.045F + unit(random, 4) * 0.090F,
                Mth.sin(angle) * (0.025F + unit(random, 5) * 0.070F),
                0.0F,
                scaledSize(random, radialFraction, 0.28F, 0.78F),
                210 + Math.floorMod((int) (random >>> 32), 165), (int) random);
        }

        private void emitTendrilTrails(final int tick, final float budgetDensity) {
            final float gravity = 0.052F;
            for (Tendril tendril : tendrils) {
                int localTick = tick - tendril.onset;
                if (localTick < 0 || localTick > tendril.duration) continue;
                float time = localTick + 0.35F;
                float horizontal = tendril.speed * time;
                float px = Mth.cos(tendril.angle) * horizontal;
                float pz = Mth.sin(tendril.angle) * horizontal;
                float py = craterFloor + 1.0F + tendril.upward * time
                    - 0.5F * gravity * time * time;
                if (py < 0.05F && localTick > 2) continue;
                int puffs = Math.max(2, Math.round((3.0F + scale * 1.5F) * budgetDensity));
                for (int puff = 0; puff < puffs; puff++) {
                    long random = mix(tendril.seed ^ ((long) tick << 32)
                        ^ puff * 0x94D049BB133111EBL);
                    float spread = 0.10F + localTick * 0.010F;
                    spawn(TENDRIL, (byte) 0,
                        px + signed(random, 0) * spread,
                        Math.max(0.05F, py + signed(random, 1) * spread),
                        pz + signed(random, 2) * spread,
                        signed(random, 3) * 0.012F
                            - Mth.sin(tendril.angle) * tendril.crossDrift,
                        0.006F + signed(random, 4) * 0.012F,
                        signed(random, 5) * 0.012F
                            + Mth.cos(tendril.angle) * tendril.crossDrift,
                        0.0F,
                        0.36F + unit(random, 6) * 0.66F,
                        105 + Math.floorMod((int) (random >>> 34), 75), (int) random);
                }
            }
        }

        private float scaledSize(final long random, final float outer,
            final float minimum, final float maximum) {
            float coreBias = 1.0F - Mth.clamp(outer, 0.0F, 1.0F);
            float result = Mth.lerp(coreBias, minimum, maximum)
                * (0.82F + unit(random, 11) * 0.36F);
            return result * (0.84F + scale * 0.20F);
        }

        private void spawn(final byte particleMaterial, final byte particleFlags,
            final float px, final float py, final float pz,
            final float velocityX, final float velocityY, final float velocityZ,
            final float heat, final float particleSize, final int particleLifetime,
            final int seedValue) {
            if (freeCount <= 0) return;
            int slot = free[--freeCount];
            active[activeCount++] = slot;
            x[slot] = oldX[slot] = px;
            y[slot] = oldY[slot] = py;
            z[slot] = oldZ[slot] = pz;
            vx[slot] = velocityX;
            vy[slot] = velocityY;
            vz[slot] = velocityZ;
            temperature[slot] = heat;
            size[slot] = particleSize;
            rotation[slot] = unit(seedValue, 0) * Mth.TWO_PI;
            spin[slot] = signed(seedValue, 1) * 0.025F;
            particleAge[slot] = 0;
            lifetime[slot] = (short) Mth.clamp(particleLifetime, 20, Short.MAX_VALUE);
            randomSeed[slot] = seedValue;
            material[slot] = particleMaterial;
            flags[slot] = particleFlags;
            spawnedLastTick++;
        }

        private void remove(final int activePosition) {
            int slot = active[activePosition];
            int last = --activeCount;
            if (activePosition < last) active[activePosition] = active[last];
            free[freeCount++] = slot;
        }

        private void update(final int tick) {
            int activePosition = 0;
            while (activePosition < activeCount) {
                int slot = active[activePosition];
                int age = particleAge[slot] & 0xFFFF;
                int life = lifetime[slot] & 0xFFFF;
                if (age >= life) {
                    remove(activePosition);
                    continue;
                }
                oldX[slot] = x[slot];
                oldY[slot] = y[slot];
                oldZ[slot] = z[slot];
                float progress = age / (float) Math.max(1, life);
                long turbulenceSeed = mix(((long) randomSeed[slot] << 32)
                    ^ tick * 0x9E3779B97F4A7C15L);
                float turbulenceX = signed(turbulenceSeed, 0);
                float turbulenceZ = signed(turbulenceSeed, 1);

                if (material[slot] == FIRE) updateFire(slot, age, life, progress,
                    turbulenceX, turbulenceZ);
                else if (material[slot] == SMOKE) updateSmoke(slot, progress,
                    turbulenceX, turbulenceZ);
                else if (material[slot] == DUST) updateDust(slot, progress,
                    turbulenceX, turbulenceZ);
                else updateTendril(slot, age, life, turbulenceX, turbulenceZ);

                x[slot] += vx[slot];
                y[slot] += vy[slot];
                z[slot] += vz[slot];
                constrain(slot, age);
                rotation[slot] += spin[slot];
                particleAge[slot] = (short) (age + 1);
                activePosition++;
            }
        }

        private void updateFire(final int slot, final int age, final int life,
            final float progress, final float turbulenceX, final float turbulenceZ) {
            boolean column = (flags[slot] & COLUMN) != 0;
            float cooling = column ? 0.0033F : 0.0044F;
            temperature[slot] = Math.max(0.0F,
                temperature[slot] - cooling * (0.72F + progress * 0.86F));
            float heightFraction = Mth.clamp((y[slot] - craterFloor)
                / Math.max(1.0F, fireTop - craterFloor), 0.0F, 1.0F);
            float targetX = leanX * heightFraction;
            float targetZ = leanZ * heightFraction;
            vx[slot] += (targetX - x[slot]) * (column ? 0.0035F : 0.0014F)
                + turbulenceX * (column ? 0.0018F : 0.0030F);
            vz[slot] += (targetZ - z[slot]) * (column ? 0.0035F : 0.0014F)
                + turbulenceZ * (column ? 0.0018F : 0.0030F);
            if (y[slot] < (column ? fireTop : smokeTop + 0.9F)) {
                vy[slot] += column ? 0.0065F : 0.0028F;
            } else {
                vy[slot] -= 0.0042F;
            }
            vx[slot] *= 0.975F;
            vy[slot] *= column ? 0.956F : 0.944F;
            vz[slot] *= 0.975F;
            size[slot] *= temperature[slot] > 0.30F ? 1.0011F : 1.0026F;
            if (temperature[slot] <= 0.055F) {
                material[slot] = SMOKE;
                lifetime[slot] = (short) Math.max(life, Math.min(Short.MAX_VALUE, age + 155));
                vy[slot] *= 0.42F;
                vx[slot] *= 0.70F;
                vz[slot] *= 0.70F;
            }
        }

        private void updateSmoke(final int slot, final float progress,
            final float turbulenceX, final float turbulenceZ) {
            float heightFraction = Mth.clamp((y[slot] - craterFloor)
                / Math.max(1.0F, smokeTop - craterFloor), 0.0F, 1.0F);
            float targetX = leanX * heightFraction * 0.75F;
            float targetZ = leanZ * heightFraction * 0.75F;
            vx[slot] += (targetX - x[slot]) * 0.0009F + turbulenceX * 0.0022F;
            vz[slot] += (targetZ - z[slot]) * 0.0009F + turbulenceZ * 0.0022F;
            if ((flags[slot] & UNDERSIDE) != 0) {
                vy[slot] = vy[slot] * 0.950F - 0.0018F;
            } else if (progress < 0.28F && y[slot] < smokeTop) {
                vy[slot] = vy[slot] * 0.950F + 0.0024F;
            } else {
                vy[slot] = vy[slot] * 0.956F - 0.0034F;
            }
            vx[slot] *= 0.963F;
            vz[slot] *= 0.963F;
            size[slot] *= 1.0028F;
            temperature[slot] = Math.max(0.0F, temperature[slot] - 0.0025F);
        }

        private void updateDust(final int slot, final float progress,
            final float turbulenceX, final float turbulenceZ) {
            vx[slot] += turbulenceX * 0.0018F;
            vz[slot] += turbulenceZ * 0.0018F;
            vy[slot] = vy[slot] * 0.945F + (progress < 0.14F ? 0.0008F : -0.0045F);
            vx[slot] *= 0.954F;
            vz[slot] *= 0.954F;
            size[slot] *= 1.0025F;
        }

        private void updateTendril(final int slot, final int age, final int life,
            final float turbulenceX, final float turbulenceZ) {
            if ((flags[slot] & LANDED) != 0) {
                vx[slot] *= 0.72F;
                vz[slot] *= 0.72F;
                size[slot] *= 1.0030F;
                return;
            }
            vx[slot] = vx[slot] * 0.965F + turbulenceX * 0.0018F;
            vz[slot] = vz[slot] * 0.965F + turbulenceZ * 0.0018F;
            vy[slot] = vy[slot] * 0.950F - 0.0065F;
            size[slot] *= 1.0022F;
            if (y[slot] <= 0.06F && vy[slot] < 0.0F) {
                y[slot] = 0.06F;
                vy[slot] = 0.0F;
                vx[slot] *= 0.24F;
                vz[slot] *= 0.24F;
                flags[slot] |= LANDED;
                lifetime[slot] = (short) Math.min(life, age + 24);
            }
        }

        private void constrain(final int slot, final int age) {
            if (material[slot] == FIRE) {
                float limit = (flags[slot] & COLUMN) != 0 ? fireTop : smokeTop + 1.0F;
                if (y[slot] > limit) {
                    y[slot] = limit;
                    vy[slot] = -Math.abs(vy[slot]) * 0.18F;
                }
                confineHorizontal(slot, bodyRadius * 1.08F);
                return;
            }
            if (material[slot] == SMOKE || material[slot] == DUST) {
                if (y[slot] > smokeTop) {
                    y[slot] = smokeTop;
                    vy[slot] = -Math.abs(vy[slot]) * 0.22F;
                }
                float floor = material[slot] == DUST ? 0.05F
                    : craterFloor * (0.06F + unit(randomSeed[slot], 6) * 0.66F) + 0.08F;
                if (y[slot] < floor) {
                    y[slot] = floor;
                    vy[slot] = 0.0F;
                    vx[slot] *= 0.84F;
                    vz[slot] *= 0.84F;
                }
                confineHorizontal(slot, material[slot] == DUST
                    ? craterRadius + 3.0F : craterRadius + 1.5F);
            }
        }

        private void confineHorizontal(final int slot, final float limit) {
            float radial = Mth.sqrt(x[slot] * x[slot] + z[slot] * z[slot]);
            if (radial <= limit || radial < 1.0E-4F) return;
            float excess = radial - limit;
            float radialX = x[slot] / radial;
            float radialZ = z[slot] / radial;
            vx[slot] -= radialX * Math.min(0.18F, excess * 0.052F);
            vz[slot] -= radialZ * Math.min(0.18F, excess * 0.052F);
            vx[slot] *= 0.83F;
            vz[slot] *= 0.83F;
        }

        private void render(final PoseStack.Pose pose, final VertexConsumer buffer,
            final double renderedAge, final WarheadMesh.Lod lod,
            final Quaternionf camera, final Pass pass) {
            ensureSimulated(renderedAge);
            float partial = (float) Mth.clamp(renderedAge - Math.floor(renderedAge), 0.0, 1.0);
            Basis basis = Basis.from(camera);
            int stride = switch (lod) {
                case NEAR -> 1;
                case MEDIUM -> 2;
                case FAR -> 5;
            };
            int inspected = 0;
            int rejected = 0;
            for (int activePosition = 0; activePosition < activeCount; activePosition += stride) {
                int slot = active[activePosition];
                inspected++;
                if (!matches(slot, pass)) {
                    rejected++;
                    continue;
                }
                int life = lifetime[slot] & 0xFFFF;
                float progress = (particleAge[slot] & 0xFFFF) / (float) Math.max(1, life);
                float alpha = alpha(slot, pass, progress);
                if (alpha <= 0.004F) {
                    rejected++;
                    continue;
                }
                float px = Mth.lerp(partial, oldX[slot], x[slot]);
                float py = Mth.lerp(partial, oldY[slot], y[slot]);
                float pz = Mth.lerp(partial, oldZ[slot], z[slot]);
                Colour colour = colour(slot, pass);
                float drawSize = size[slot];
                if (pass == Pass.SMOKE_CORE) drawSize *= 1.18F;
                if (pass == Pass.SMOKE_SOFT) drawSize *= 1.06F;
                if (material[slot] == TENDRIL) drawSize *= 1.12F;
                int light = pass == Pass.FIRE_CORE || pass == Pass.FIRE_HOT
                    || pass == Pass.FIRE_COOLING ? 0xF000F0
                    : pass == Pass.SMOKE_CORE ? 0x900090 : 0xA000A0;
                billboard(pose, buffer, px, py, pz, drawSize, rotation[slot],
                    colour.red, colour.green, colour.blue, alpha, light, basis);
            }
            culledLastRender = Math.max(0, activeCount - inspected) + rejected;
        }

        private boolean matches(final int slot, final Pass pass) {
            float heat = temperature[slot];
            return switch (pass) {
                case FIRE_CORE -> material[slot] == FIRE && heat >= 0.68F
                    && (flags[slot] & CORE) != 0;
                case FIRE_HOT -> material[slot] == FIRE && heat >= 0.52F
                    && (flags[slot] & CORE) == 0;
                case FIRE_COOLING -> material[slot] == FIRE && heat >= 0.055F && heat < 0.68F;
                case SMOKE_CORE -> material[slot] == SMOKE && (flags[slot] & CORE) != 0;
                case SMOKE_SOFT -> material[slot] == DUST || material[slot] == TENDRIL
                    || (material[slot] == SMOKE && (flags[slot] & CORE) == 0);
            };
        }

        private float alpha(final int slot, final Pass pass, final float progress) {
            float age = particleAge[slot] & 0xFFFF;
            float fadeIn = Mth.clamp(age / 4.0F, 0.0F, 1.0F);
            float remaining = Mth.clamp(1.0F - progress, 0.0F, 1.0F);
            float fadeOut = (float) Math.pow(remaining,
                pass == Pass.SMOKE_CORE ? 0.62F : pass == Pass.SMOKE_SOFT ? 0.72F : 0.58F);
            float base = switch (pass) {
                case FIRE_CORE -> 0.96F;
                case FIRE_HOT -> 0.90F;
                case FIRE_COOLING -> 0.78F;
                case SMOKE_CORE -> 0.86F;
                case SMOKE_SOFT -> material[slot] == TENDRIL ? 0.94F : 0.72F;
            };
            return Mth.clamp(base * fadeIn * fadeOut, 0.0F, 0.98F);
        }

        private Colour colour(final int slot, final Pass pass) {
            if (material[slot] == TENDRIL) {
                int selector = Math.floorMod(randomSeed[slot], 100);
                if (selector < 12) {
                    int dark = 66 + Math.floorMod(randomSeed[slot] >>> 8, 58);
                    return new Colour(dark, Math.min(132, dark + 3), Math.min(138, dark + 8));
                }
                if (selector < 28) {
                    int mid = 156 + Math.floorMod(randomSeed[slot] >>> 8, 40);
                    return new Colour(mid, Math.min(208, mid + 4), Math.min(216, mid + 10));
                }
                int pale = 214 + Math.floorMod(randomSeed[slot] >>> 8, 34);
                return new Colour(pale, Math.min(252, pale + 3), Math.min(255, pale + 8));
            }
            if (material[slot] == DUST) {
                int variation = Math.floorMod(randomSeed[slot], 48);
                int tone = 142 + variation;
                return new Colour(tone, Mth.clamp(tone - 13, 110, 188),
                    Mth.clamp(tone - 28, 88, 168));
            }
            if (material[slot] == SMOKE) {
                float regionNoise = 0.5F + 0.5F * Mth.sin(
                    x[slot] * 0.31F + y[slot] * 0.19F + z[slot] * 0.27F
                        + randomSeed[slot] * 0.00021F);
                int seedNoise = Math.floorMod(randomSeed[slot] >>> 6, 52);
                int base = (flags[slot] & CORE) != 0 ? 52 : 86;
                int tone = Mth.clamp(base + seedNoise + (int) (regionNoise * 34.0F), 42, 174);
                return new Colour(tone, Math.min(180, tone + 3), Math.min(188, tone + 9));
            }
            float heat = Mth.clamp(temperature[slot], 0.0F, 1.0F);
            if (heat > 0.84F) {
                float t = (heat - 0.84F) / 0.16F;
                return new Colour(255, Mth.lerpInt(t, 208, 255), Mth.lerpInt(t, 44, 218));
            }
            if (heat > 0.42F) {
                float t = (heat - 0.42F) / 0.42F;
                return new Colour(255, Mth.lerpInt(t, 76, 208), Mth.lerpInt(t, 10, 44));
            }
            float t = heat / 0.42F;
            return new Colour(Mth.lerpInt(t, 68, 255), Mth.lerpInt(t, 64, 76),
                Mth.lerpInt(t, 68, 10));
        }
    }

    private record Tendril(float angle, float speed, float upward, int onset,
        int duration, float crossDrift, long seed) { }

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
