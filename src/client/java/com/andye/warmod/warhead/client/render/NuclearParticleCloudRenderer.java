package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Particle-only nuclear fireball and continuously rising toroidal cloud.
 *
 * <p>The fixed packed pool represents a larger logical population. Particle
 * paths are deterministic and persistent: fireball -> stem -> cap -> outside
 * curl -> underside return -> reheated stem. The cap centre keeps rising for
 * the complete multi-minute lifetime and the head gains volume from fed
 * particles rather than ever-increasing billboard size.</p>
 */
public final class NuclearParticleCloudRenderer {
    private static final int CAPACITY = 262_144;
    private static final int LOGICAL_PARTICLES_PER_SIMULATED = 8;
    private static final int MAX_FIELDS = 3;
    private static final Map<Long, Field> FIELDS = new LinkedHashMap<>();

    private NuclearParticleCloudRenderer() { }

    public static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final boolean hotPass,
        final List<NuclearCloudSource> sources, final Quaternionf camera) {
        if (!valid(profile, age)) return;
        field(seed, visualScale, sources).render(pose, buffer, age, lod, camera,
            hotPass ? Pass.HOT_FIRE : Pass.COOL_FIRE);
    }

    public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
        final double age, final float visualScale, final WarheadClientVisualProfile profile,
        final long seed, final WarheadMesh.Lod lod, final List<NuclearCloudSource> sources,
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
        final List<NuclearCloudSource> sources) {
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

    private static long sourceSignature(final List<NuclearCloudSource> sources) {
        long value = 0x4E55434C45415253L;
        if (sources == null) return value;
        for (NuclearCloudSource source : sources) {
            value ^= mix(source.seed() ^ Double.doubleToLongBits(source.offset().x)
                ^ Long.rotateLeft(Double.doubleToLongBits(source.offset().y), 17)
                ^ Long.rotateLeft(Double.doubleToLongBits(source.offset().z), 37));
        }
        return value;
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
        private final List<NuclearCloudSource> sources;
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
        private int simulatedTick = -1;
        private int nextSlot;
        private int activeCount;
        private int spawnedLastTick;
        private int culledLastRender;

        private Field(final long seed, final float scale, final List<NuclearCloudSource> sources,
            final long signature) {
            this.seed = seed;
            this.scale = Mth.clamp(scale, 1.4F, 4.2F);
            this.sources = sources == null || sources.isEmpty()
                ? List.of(new NuclearCloudSource(net.minecraft.world.phys.Vec3.ZERO, 0.0, scale, seed))
                : List.copyOf(sources);
            this.signature = signature;
            this.yield = Mth.clamp(this.scale / 2.70F, 0.55F, 1.55F);
            this.craterRadius = 12.0F + 13.0F * this.scale;
            this.craterFloor = -Math.max(7.0F, craterRadius * 0.30F);
            this.fireballEmissionEnd = Math.round(120.0F + 90.0F * yield);
            this.feedEmissionEnd = Math.round(880.0F + 520.0F * yield);
        }

        private void ensureSimulated(final double age) {
            int target = Math.max(0, (int) Math.floor(age));
            if (target < simulatedTick) reset();
            while (simulatedTick < target) {
                simulatedTick++;
                spawnedLastTick = 0;
                emit(simulatedTick);
                update(simulatedTick);
            }
        }

        private void reset() {
            for (int index = 0; index < CAPACITY; index++) active[index] = false;
            simulatedTick = -1;
            nextSlot = 0;
            activeCount = 0;
            spawnedLastTick = 0;
        }

        private void emit(final int tick) {
            if (tick <= 22) {
                int initial = Math.round(1_200.0F + 780.0F * yield);
                for (int index = 0; index < initial; index++) spawnFireball(tick, index);
            }
            if (tick <= fireballEmissionEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, fireballEmissionEnd);
                int body = Math.round((170.0F + 110.0F * yield) * (0.42F + 0.58F * remaining));
                for (int index = 0; index < body; index++) spawnFireball(tick, index + 30_000);
            }
            if (tick <= feedEmissionEnd) {
                float remaining = 1.0F - tick / (float) Math.max(1, feedEmissionEnd);
                int stem = Math.round((130.0F + 85.0F * yield) * (0.54F + 0.46F * remaining));
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
            float radial = radialFraction * craterRadius * 1.06F;
            float dome = (float) Math.sqrt(Math.max(0.0F, 1.0F - radialFraction * radialFraction));
            float px = (float) source.offset().x + Mth.cos(angle) * radial;
            float pz = (float) source.offset().z + Mth.sin(angle) * radial;
            float py = (float) source.offset().y + craterFloor + 1.0F
                + dome * craterRadius * 0.86F + signed(random, 2) * 1.1F;
            float inward = -0.0018F * radial;
            spawn(REGION_FIREBALL, px, py, pz,
                Mth.cos(angle) * inward + signed(random, 3) * 0.035F,
                0.26F + unit(random, 4) * (0.42F + 0.20F * yield),
                Mth.sin(angle) * inward + signed(random, 5) * 0.035F,
                0.88F + unit(random, 6) * 0.18F,
                (0.11F + unit(random, 7) * 0.23F) * (0.94F + 0.12F * yield),
                Math.round(3_200.0F + unit(random, 8) * (2_200.0F + 1_000.0F * yield)),
                (int) random);
        }

        private void spawnStem(final int tick, final int ordinal) {
            long random = mix(seed ^ signature ^ 0x5354454D5F464545L
                ^ ((long) tick << 31) ^ ordinal * 0xD1B54A32D192ED03L);
            NuclearCloudSource source = source(random);
            float angle = unit(random, 0) * Mth.TWO_PI;
            float radial = (float) Math.sqrt(unit(random, 1)) * craterRadius * 0.24F;
            float px = (float) source.offset().x + Mth.cos(angle) * radial;
            float pz = (float) source.offset().z + Mth.sin(angle) * radial;
            float py = (float) source.offset().y + craterFloor + 1.2F
                + unit(random, 2) * craterRadius * 0.34F;
            spawn(REGION_STEM, px, py, pz,
                -Mth.cos(angle) * radial * 0.004F + signed(random, 3) * 0.024F,
                0.48F + unit(random, 4) * (0.52F + 0.25F * yield),
                -Mth.sin(angle) * radial * 0.004F + signed(random, 5) * 0.024F,
                0.78F + unit(random, 6) * 0.22F,
                (0.10F + unit(random, 7) * 0.21F) * (0.94F + 0.12F * yield),
                Math.round(3_600.0F + unit(random, 8) * (2_600.0F + 1_200.0F * yield)),
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
            angularVelocity[slot] = signed(randomSeed, 1) * 0.032F;
            particleAge[slot] = 0;
            lifetime[slot] = (short) Mth.clamp(particleLifetime, 600, Short.MAX_VALUE);
            particleSeed[slot] = randomSeed;
            region[slot] = initialRegion;
            active[slot] = true;
            activeCount++;
            spawnedLastTick++;
        }

        private int reserve() {
            for (int scan = 0; scan < CAPACITY; scan++) {
                int slot = (nextSlot + scan) % CAPACITY;
                if (!active[slot]) {
                    nextSlot = (slot + 1) % CAPACITY;
                    return slot;
                }
            }
            return -1;
        }

        private float capCenterY(final int tick) {
            float launch = craterRadius * 0.45F;
            float early = Math.min(tick, 160) * (0.28F + 0.06F * yield);
            float continuing = Math.max(0, tick - 160) * (0.095F + 0.035F * yield);
            return launch + early + continuing;
        }

        private float capRadius(final int tick) {
            float populationGrowth = (float) Math.sqrt(Mth.clamp(tick / (float) feedEmissionEnd, 0.0F, 1.0F));
            return (12.0F + 34.0F * yield) * (0.22F + 0.78F * populationGrowth);
        }

        private float capDepth(final int tick) {
            return capRadius(tick) * (0.36F + 0.08F * yield);
        }

        private float stemRadius(final int tick) {
            float feed = tick < feedEmissionEnd
                ? 1.0F : Mth.clamp(1.0F - (tick - feedEmissionEnd) / 2_400.0F, 0.18F, 1.0F);
            return (4.5F + 5.5F * yield) * (0.48F + 0.52F * feed);
        }

        private void update(final int tick) {
            float capY = capCenterY(tick);
            float capR = capRadius(tick);
            float capD = capDepth(tick);
            float stemR = stemRadius(tick);
            for (int index = 0; index < CAPACITY; index++) {
                if (!active[index]) continue;
                int age = particleAge[index] & 0xFFFF;
                int life = lifetime[index] & 0xFFFF;
                if (age >= life) {
                    active[index] = false;
                    activeCount--;
                    continue;
                }
                previousX[index] = x[index];
                previousY[index] = y[index];
                previousZ[index] = z[index];

                float progress = age / (float) Math.max(1, life);
                float radial = (float) Math.sqrt(x[index] * x[index] + z[index] * z[index]);
                float inverseRadial = radial > 0.001F ? 1.0F / radial : 0.0F;
                float radialX = x[index] * inverseRadial;
                float radialZ = z[index] * inverseRadial;
                float tangentX = -radialZ;
                float tangentZ = radialX;
                float turbulence = signed(mix(((long) particleSeed[index] << 32)
                    ^ tick * 0x9E3779B9L), 0);
                float swirl = 0.004F + 0.010F * unit(particleSeed[index], 3);

                switch (region[index]) {
                    case REGION_FIREBALL -> {
                        velocityX[index] *= 0.982F;
                        velocityZ[index] *= 0.982F;
                        velocityY[index] += 0.010F + temperature[index] * 0.010F;
                        if (age > 34 || y[index] > craterRadius * 0.42F) region[index] = REGION_STEM;
                    }
                    case REGION_STEM -> {
                        velocityX[index] += -radialX * (0.016F + radial * 0.0009F)
                            + tangentX * turbulence * swirl;
                        velocityZ[index] += -radialZ * (0.016F + radial * 0.0009F)
                            + tangentZ * turbulence * swirl;
                        velocityY[index] += 0.018F + temperature[index] * 0.015F;
                        if (y[index] >= capY - capD * 0.15F + signed(particleSeed[index], 2) * capD * 0.20F) {
                            region[index] = REGION_CAP;
                        }
                    }
                    case REGION_CAP -> {
                        velocityX[index] += radialX * (0.022F + 0.012F * yield) + tangentX * swirl;
                        velocityZ[index] += radialZ * (0.022F + 0.012F * yield) + tangentZ * swirl;
                        velocityY[index] += (capY - y[index]) * 0.0024F;
                        velocityY[index] *= 0.966F;
                        if (radial >= capR * (0.78F + unit(particleSeed[index], 4) * 0.18F)) {
                            region[index] = REGION_OUTER_CURL;
                        }
                    }
                    case REGION_OUTER_CURL -> {
                        velocityX[index] += -radialX * 0.007F + tangentX * (swirl + 0.004F);
                        velocityZ[index] += -radialZ * 0.007F + tangentZ * (swirl + 0.004F);
                        velocityY[index] -= 0.020F + 0.006F * yield;
                        if (y[index] <= capY - capD * (0.68F + unit(particleSeed[index], 5) * 0.22F)) {
                            region[index] = REGION_UNDER_CAP;
                        }
                    }
                    case REGION_UNDER_CAP -> {
                        velocityX[index] += -radialX * (0.024F + 0.010F * yield) + tangentX * swirl * 0.55F;
                        velocityZ[index] += -radialZ * (0.024F + 0.010F * yield) + tangentZ * swirl * 0.55F;
                        velocityY[index] += (capY - capD - y[index]) * 0.0012F;
                        if (radial <= stemR * (1.15F + unit(particleSeed[index], 6) * 0.62F)) {
                            region[index] = REGION_STEM;
                            velocityY[index] = Math.max(velocityY[index], 0.36F + temperature[index] * 0.34F);
                            temperature[index] = Math.min(0.82F, temperature[index] + 0.08F);
                        }
                    }
                    default -> { }
                }

                velocityX[index] += turbulence * (0.0025F + progress * 0.004F);
                velocityZ[index] += signed(particleSeed[index], tick & 7) * (0.0025F + progress * 0.004F);
                velocityX[index] *= 0.989F;
                velocityY[index] *= 0.994F;
                velocityZ[index] *= 0.989F;
                x[index] += velocityX[index];
                y[index] += velocityY[index];
                z[index] += velocityZ[index];
                rotation[index] += angularVelocity[index];

                float insulation = region[index] == REGION_STEM
                    ? Mth.clamp(1.0F - radial / Math.max(1.0F, stemR * 1.8F), 0.0F, 1.0F)
                    : Mth.clamp(1.0F - radial / Math.max(1.0F, capR), 0.0F, 1.0F);
                float cooling = switch (region[index]) {
                    case REGION_FIREBALL, REGION_STEM -> 0.00055F;
                    case REGION_CAP -> 0.00082F;
                    case REGION_OUTER_CURL -> 0.00135F;
                    case REGION_UNDER_CAP -> 0.00095F;
                    default -> 0.001F;
                };
                cooling *= 1.0F - insulation * 0.68F;
                temperature[index] = Math.max(0.0F, temperature[index] - cooling * (0.72F + progress));
                if (region[index] == REGION_STEM && radial < stemR && y[index] < capY - capD * 0.55F) {
                    temperature[index] = Math.min(0.88F, temperature[index] + 0.0015F);
                }
                radius[index] *= 1.00012F;
                particleAge[index] = (short) (age + 1);
            }
        }

        private void render(final PoseStack.Pose pose, final VertexConsumer buffer, final double age,
            final WarheadMesh.Lod lod, final Quaternionf camera, final Pass pass) {
            ensureSimulated(age);
            int tick = Math.max(0, (int) Math.floor(age));
            float partial = (float) Mth.clamp(age - Math.floor(age), 0.0, 1.0);
            Basis basis = Basis.from(camera);
            int stride = switch (lod) { case NEAR -> 1; case MEDIUM -> 3; case FAR -> 9; };
            int culled = 0;
            for (int index = 0; index < CAPACITY; index += stride) {
                if (!active[index] || !matches(index, pass) || interior(index, tick, lod)) {
                    if (active[index]) culled++;
                    continue;
                }
                int life = lifetime[index] & 0xFFFF;
                float progress = (particleAge[index] & 0xFFFF) / (float) Math.max(1, life);
                float alpha = alpha(pass, progress, temperature[index]);
                if (alpha <= 0.005F) { culled++; continue; }
                float px = Mth.lerp(partial, previousX[index], x[index]);
                float py = Mth.lerp(partial, previousY[index], y[index]);
                float pz = Mth.lerp(partial, previousZ[index], z[index]);
                Colour colour = colour(temperature[index], particleSeed[index], pass);
                int light = pass == Pass.SMOKE ? 0xA000A0 : 0xF000F0;
                billboard(pose, buffer, px, py, pz, radius[index], rotation[index], colour.red, colour.green,
                    colour.blue, alpha, light, basis);
            }
            culledLastRender = culled + activeCount - activeCount / stride;
        }

        private boolean interior(final int index, final int tick, final WarheadMesh.Lod lod) {
            float radial = (float) Math.sqrt(x[index] * x[index] + z[index] * z[index]);
            int keepModulo = lod == WarheadMesh.Lod.NEAR ? 4 : lod == WarheadMesh.Lod.MEDIUM ? 7 : 13;
            if (region[index] == REGION_STEM && radial < stemRadius(tick) * 0.52F) {
                return Math.floorMod(particleSeed[index], keepModulo) != 0;
            }
            if (region[index] == REGION_CAP) {
                float capR = capRadius(tick);
                float capD = capDepth(tick);
                boolean deep = radial < capR * 0.57F
                    && Math.abs(y[index] - capCenterY(tick)) < capD * 0.46F;
                return deep && Math.floorMod(particleSeed[index], keepModulo) != 0;
            }
            return false;
        }

        private boolean matches(final int index, final Pass pass) {
            float heat = temperature[index];
            return switch (pass) {
                case HOT_FIRE -> heat >= 0.58F;
                case COOL_FIRE -> heat >= 0.14F && heat < 0.64F;
                case SMOKE -> heat < 0.28F;
            };
        }

        private static float alpha(final Pass pass, final float progress, final float heat) {
            float fade = (float) Math.pow(Math.max(0.0F, 1.0F - progress),
                pass == Pass.SMOKE ? 0.48 : 0.34);
            return switch (pass) {
                case HOT_FIRE -> Mth.clamp(0.90F * fade * (0.62F + heat * 0.38F), 0.0F, 0.95F);
                case COOL_FIRE -> Mth.clamp(0.66F * fade, 0.0F, 0.78F);
                case SMOKE -> Mth.clamp(0.62F * fade * (0.78F + (1.0F - heat) * 0.22F), 0.0F, 0.72F);
            };
        }

        private static Colour colour(final float temperature, final int seed, final Pass pass) {
            float heat = Mth.clamp(temperature, 0.0F, 1.0F);
            if (pass == Pass.SMOKE || heat < 0.14F) {
                int tone = Mth.clamp(34 + (int) ((1.0F - heat) * 58.0F)
                    + Math.floorMod(seed, 24), 34, 128);
                return new Colour(tone, Math.min(132, tone + 3), Math.min(140, tone + 8));
            }
            if (heat > 0.84F) {
                float t = (heat - 0.84F) / 0.16F;
                return new Colour(255, Mth.lerpInt(t, 218, 255), Mth.lerpInt(t, 70, 232));
            }
            if (heat > 0.48F) {
                float t = (heat - 0.48F) / 0.36F;
                return new Colour(255, Mth.lerpInt(t, 88, 218), Mth.lerpInt(t, 16, 70));
            }
            float t = heat / 0.48F;
            return new Colour(Mth.lerpInt(t, 88, 255), Mth.lerpInt(t, 38, 88), Mth.lerpInt(t, 32, 16));
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
        final float centerX, final float centerY, final float centerZ, final float radius, final float rotation,
        final int red, final int green, final int blue, final float alpha, final int light, final Basis basis) {
        float cosine = Mth.cos(rotation);
        float sine = Mth.sin(rotation);
        vertex(pose, buffer, centerX, centerY, centerZ, -radius, -radius, cosine, sine, 0.0F, 1.0F,
            red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, -radius, radius, cosine, sine, 0.0F, 0.0F,
            red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, radius, radius, cosine, sine, 1.0F, 0.0F,
            red, green, blue, alpha, light, basis);
        vertex(pose, buffer, centerX, centerY, centerZ, radius, -radius, cosine, sine, 1.0F, 1.0F,
            red, green, blue, alpha, light, basis);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
        final float centerX, final float centerY, final float centerZ, final float localX, final float localY,
        final float cosine, final float sine, final float u, final float v, final int red, final int green,
        final int blue, final float alpha, final int light, final Basis basis) {
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
