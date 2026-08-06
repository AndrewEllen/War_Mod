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
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Packed nuclear fireball and circulating mushroom cloud. The silhouette is an
 * emergent result of a persistent vector field: centre rise, cap expansion,
 * outer downward curl, under-cap return and re-entry into the stem.
 */
public final class NuclearParticleCloudRenderer {
	private static final int CAPACITY = 131_072;
	private static final int MAX_FIELDS = 8;
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
			active += field.activeCount;
			spawned += field.spawnedLastTick;
			culled += field.culledLastRender;
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
		private final long sourceSignature;
		private final int sourceCount;
		private final float[] sourceX;
		private final float[] sourceY;
		private final float[] sourceZ;
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
		private int nextSlot;
		private int activeCount;
		private int simulatedTick = -1;
		private int spawnedLastTick;
		private int culledLastRender;

		private Field(final long seed, final float visualScale,
			final List<NuclearCloudSource> sources, final long sourceSignature) {
			this.seed = seed;
			this.scale = Mth.clamp(visualScale, 0.55F, 5.5F);
			this.sourceSignature = sourceSignature;
			List<NuclearCloudSource> safe = sources == null || sources.isEmpty()
				? List.of(new NuclearCloudSource(Vec3.ZERO, 0.0, visualScale, seed)) : sources;
			this.sourceCount = safe.size();
			this.sourceX = new float[sourceCount];
			this.sourceY = new float[sourceCount];
			this.sourceZ = new float[sourceCount];
			for (int index = 0; index < sourceCount; index++) {
				Vec3 offset = safe.get(index).offset();
				sourceX[index] = (float) offset.x;
				sourceY[index] = (float) offset.y;
				sourceZ[index] = (float) offset.z;
			}
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
			nextSlot = 0;
			activeCount = 0;
			simulatedTick = -1;
		}

		private void emit(final int tick) {
			float yield = Mth.clamp(scale / 2.7F, 0.55F, 1.85F);
			int fireballEnd = Math.round(22.0F + 18.0F * yield);
			int feedEnd = Math.round(225.0F + 155.0F * yield);
			float density = 0.72F + (float) Math.pow(yield, 1.45);
			if (tick <= fireballEnd) {
				int count = Math.min(4_800, Math.round((760.0F + 720.0F * yield) * density));
				for (int index = 0; index < count; index++) spawnFireball(tick, index, yield);
			}
			if (tick <= feedEnd) {
				float feed = 1.0F - tick / (float) Math.max(1, feedEnd);
				int count = Math.min(3_100, Math.round((390.0F + 430.0F * yield) * density
					* (0.34F + 0.66F * feed)));
				for (int index = 0; index < count; index++) spawnStem(tick, index, yield);
			}
		}

		private void spawnFireball(final int tick, final int ordinal, final float yield) {
			long random = mix(seed ^ sourceSignature ^ 0x4649524542414C4CL
				^ ((long) tick << 32) ^ ordinal * 0x9E3779B97F4A7C15L);
			int source = Math.floorMod((int) random, sourceCount);
			float angle = unit(random, 0) * Mth.TWO_PI;
			float vertical = signed(random, 1);
			float radialFraction = (float) Math.sqrt(unit(random, 2));
			float fireballRadius = 17.0F + 23.0F * yield;
			float radial = radialFraction * fireballRadius * (0.30F + 0.70F * Math.min(1.0F, tick / 15.0F));
			float px = sourceX[source] + Mth.cos(angle) * radial;
			float pz = sourceZ[source] + Mth.sin(angle) * radial;
			float craterBase = -(6.0F + 10.0F * yield);
			float py = sourceY[source] + craterBase + fireballRadius * 0.42F
				+ vertical * fireballRadius * 0.48F;
			float outward = 0.18F + unit(random, 3) * (0.58F + 0.32F * yield);
			float vx = Mth.cos(angle) * outward + signed(random, 4) * 0.08F;
			float vz = Mth.sin(angle) * outward + signed(random, 5) * 0.08F;
			float vy = 0.34F + unit(random, 6) * (0.66F + 0.22F * yield) + vertical * 0.12F;
			spawn(REGION_FIREBALL, px, py, pz, vx, vy, vz,
				0.92F + unit(random, 7) * 0.18F,
				(0.15F + unit(random, 8) * 0.34F) * (0.82F + 0.24F * yield),
				Math.round(520.0F + unit(random, 9) * (420.0F + 180.0F * yield)), (int) random);
		}

		private void spawnStem(final int tick, final int ordinal, final float yield) {
			long random = mix(seed ^ sourceSignature ^ 0x5354454D5F464545L
				^ ((long) tick << 32) ^ ordinal * 0xD1B54A32D192ED03L);
			int source = Math.floorMod((int) (random >>> 16), sourceCount);
			float angle = unit(random, 0) * Mth.TWO_PI;
			float sourceRadius = (float) Math.sqrt(unit(random, 1)) * (2.2F + 3.8F * yield);
			float craterBase = -(5.5F + 9.5F * yield);
			float px = sourceX[source] + Mth.cos(angle) * sourceRadius;
			float pz = sourceZ[source] + Mth.sin(angle) * sourceRadius;
			float py = sourceY[source] + craterBase + unit(random, 2) * 4.0F;
			float inwardX = -px * 0.006F;
			float inwardZ = -pz * 0.006F;
			spawn(REGION_STEM, px, py, pz,
				inwardX + signed(random, 3) * 0.05F,
				0.68F + unit(random, 4) * (0.72F + 0.30F * yield),
				inwardZ + signed(random, 5) * 0.05F,
				0.90F + unit(random, 6) * 0.16F,
				(0.13F + unit(random, 7) * 0.30F) * (0.86F + 0.22F * yield),
				Math.round(640.0F + unit(random, 8) * (520.0F + 240.0F * yield)), (int) random);
		}

		private void spawn(final byte initialRegion, final float px, final float py, final float pz,
			final float vx, final float vy, final float vz, final float heat, final float particleRadius,
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
			angularVelocity[slot] = signed(randomSeed, 1) * 0.035F;
			particleAge[slot] = 0;
			lifetime[slot] = (short) Mth.clamp(particleLifetime, 40, Short.MAX_VALUE);
			particleSeed[slot] = randomSeed;
			region[slot] = initialRegion;
			active[slot] = true;
			activeCount++;
			spawnedLastTick++;
		}

		private int reserve() {
			if (activeCount >= CAPACITY) return -1;
			for (int scan = 0; scan < CAPACITY; scan++) {
				int slot = (nextSlot + scan) % CAPACITY;
				if (!active[slot]) {
					nextSlot = (slot + 1) % CAPACITY;
					return slot;
				}
			}
			return -1;
		}

		private void update(final int tick) {
			float yield = Mth.clamp(scale / 2.7F, 0.55F, 1.85F);
			float capHeight = 72.0F + 86.0F * yield;
			float capRadius = 30.0F + 42.0F * yield;
			float stemRadius = 5.0F + 6.5F * yield;
			int returnStart = Math.round(72.0F + 34.0F * yield);
			int returnEnd = returnStart + Math.round(82.0F + 40.0F * yield);
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
				float turbulence = signed(mix(((long) particleSeed[index] << 32) ^ tick * 0x9E3779B9L), 0);
				switch (region[index]) {
					case REGION_FIREBALL -> {
						velocityX[index] *= 0.982F;
						velocityZ[index] *= 0.982F;
						velocityY[index] += 0.012F + temperature[index] * 0.012F;
						if (age > 28 || y[index] > capHeight * 0.42F) region[index] = REGION_STEM;
					}
					case REGION_STEM -> {
						velocityX[index] += -radialX * (0.018F + radial * 0.0008F) + tangentX * turbulence * 0.006F;
						velocityZ[index] += -radialZ * (0.018F + radial * 0.0008F) + tangentZ * turbulence * 0.006F;
						velocityY[index] += 0.020F + temperature[index] * 0.016F;
						if (y[index] >= capHeight + signed(particleSeed[index], 2) * 9.0F) region[index] = REGION_CAP;
					}
					case REGION_CAP -> {
						velocityX[index] += radialX * 0.032F + tangentX * 0.008F;
						velocityZ[index] += radialZ * 0.032F + tangentZ * 0.008F;
						velocityY[index] *= 0.955F;
						velocityY[index] += (capHeight - y[index]) * 0.0018F;
						if (radial >= capRadius * (0.74F + unit(particleSeed[index], 3) * 0.18F)) {
							region[index] = REGION_OUTER_CURL;
						}
					}
					case REGION_OUTER_CURL -> {
						velocityX[index] += -radialX * 0.010F + tangentX * 0.012F;
						velocityZ[index] += -radialZ * 0.010F + tangentZ * 0.012F;
						velocityY[index] -= 0.024F;
						if (y[index] <= capHeight * (0.70F + unit(particleSeed[index], 4) * 0.10F)) {
							region[index] = REGION_UNDER_CAP;
						}
					}
					case REGION_UNDER_CAP -> {
						velocityX[index] += -radialX * 0.030F + tangentX * 0.007F;
						velocityZ[index] += -radialZ * 0.030F + tangentZ * 0.007F;
						velocityY[index] += 0.005F;
						if (radial <= stemRadius * (1.2F + unit(particleSeed[index], 5) * 0.75F)) {
							region[index] = REGION_STEM;
							velocityY[index] = Math.max(velocityY[index], 0.42F + temperature[index] * 0.35F);
						}
					}
					default -> { }
				}
				if (tick >= returnStart && tick <= returnEnd && y[index] < capHeight * 0.58F) {
					float returnProgress = (tick - returnStart) / (float) Math.max(1, returnEnd - returnStart);
					float pull = Mth.sin(returnProgress * Mth.PI) * (0.018F + 0.012F * yield);
					velocityX[index] -= radialX * pull;
					velocityZ[index] -= radialZ * pull;
					if (radial < stemRadius * 3.0F) velocityY[index] += pull * 1.55F;
				}
				velocityX[index] += turbulence * (0.004F + progress * 0.006F);
				velocityZ[index] += signed(particleSeed[index], tick & 7) * (0.004F + progress * 0.006F);
				velocityX[index] *= 0.988F;
				velocityY[index] *= 0.992F;
				velocityZ[index] *= 0.988F;
				x[index] += velocityX[index];
				y[index] += velocityY[index];
				z[index] += velocityZ[index];
				rotation[index] += angularVelocity[index];
				temperature[index] = Math.max(0.0F, temperature[index]
					- (region[index] == REGION_STEM ? 0.0014F : 0.0022F) * (0.72F + progress));
				radius[index] *= temperature[index] > 0.24F ? 1.0025F : 1.0045F;
				particleAge[index] = (short) (age + 1);
			}
		}

		private void render(final PoseStack.Pose pose, final VertexConsumer buffer, final double age,
			final WarheadMesh.Lod lod, final Quaternionf camera, final Pass pass) {
			ensureSimulated(age);
			float partial = (float) Mth.clamp(age - Math.floor(age), 0.0, 1.0);
			Basis basis = Basis.from(camera);
			int stride = switch (lod) { case NEAR -> 1; case MEDIUM -> 2; case FAR -> 6; };
			int culled = 0;
			for (int index = 0; index < CAPACITY; index += stride) {
				if (!active[index] || !matches(index, pass)) continue;
				int life = lifetime[index] & 0xFFFF;
				float progress = (particleAge[index] & 0xFFFF) / (float) Math.max(1, life);
				float alpha = alpha(pass, progress, temperature[index]);
				if (alpha <= 0.005F) { culled++; continue; }
				float px = Mth.lerp(partial, previousX[index], x[index]);
				float py = Mth.lerp(partial, previousY[index], y[index]);
				float pz = Mth.lerp(partial, previousZ[index], z[index]);
				Colour colour = colour(temperature[index], particleSeed[index], pass);
				int light = pass == Pass.SMOKE ? 0xA000A0 : 0xF000F0;
				Uv uv = pass == Pass.SMOKE ? Uv.FULL : fireUv(index);
				billboard(pose, buffer, px, py, pz, radius[index], rotation[index], colour.red, colour.green,
					colour.blue, alpha, light, basis, uv.u0, uv.u1, uv.v0, uv.v1);
			}
			culledLastRender = culled + activeCount - activeCount / stride;
		}

		private boolean matches(final int index, final Pass pass) {
			float heat = temperature[index];
			return switch (pass) {
				case HOT_FIRE -> heat >= 0.56F;
				case COOL_FIRE -> heat >= 0.14F && heat < 0.62F;
				case SMOKE -> heat < 0.26F;
			};
		}

		private static float alpha(final Pass pass, final float progress, final float heat) {
			float fade = (float) Math.pow(Math.max(0.0F, 1.0F - progress), pass == Pass.SMOKE ? 0.52 : 0.36);
			return switch (pass) {
				case HOT_FIRE -> Mth.clamp(0.88F * fade * (0.62F + heat * 0.38F), 0.0F, 0.95F);
				case COOL_FIRE -> Mth.clamp(0.64F * fade, 0.0F, 0.76F);
				case SMOKE -> Mth.clamp(0.54F * fade * (0.82F + (1.0F - heat) * 0.18F), 0.0F, 0.64F);
			};
		}

		private static Colour colour(final float temperature, final int seed, final Pass pass) {
			float heat = Mth.clamp(temperature, 0.0F, 1.0F);
			if (pass == Pass.SMOKE || heat < 0.14F) {
				int tone = Mth.clamp(30 + (int) ((1.0F - heat) * 62.0F) + Math.floorMod(seed, 27), 30, 132);
				return new Colour(tone, Math.min(138, tone + 4), Math.min(148, tone + 11));
			}
			if (heat > 0.84F) {
				float t = (heat - 0.84F) / 0.16F;
				return new Colour(255, Mth.lerpInt(t, 224, 255), Mth.lerpInt(t, 82, 235));
			}
			if (heat > 0.48F) {
				float t = (heat - 0.48F) / 0.36F;
				return new Colour(255, Mth.lerpInt(t, 96, 224), Mth.lerpInt(t, 18, 82));
			}
			float t = heat / 0.48F;
			return new Colour(Mth.lerpInt(t, 105, 255), Mth.lerpInt(t, 42, 96), Mth.lerpInt(t, 31, 18));
		}

		private Uv fireUv(final int index) {
			int frame = Math.floorMod((particleAge[index] & 0xFFFF) / 2 + particleSeed[index], FireballAtlas.FRAME_COUNT);
			float u0 = frame / (float) FireballAtlas.FRAME_COUNT + 0.5F / FireballAtlas.ATLAS_WIDTH;
			float u1 = (frame + 1) / (float) FireballAtlas.FRAME_COUNT - 0.5F / FireballAtlas.ATLAS_WIDTH;
			float v0 = 0.5F / FireballAtlas.ATLAS_HEIGHT;
			return new Uv(u0, u1, v0, 1.0F - v0);
		}
	}

	private record Colour(int red, int green, int blue) { }
	private record Uv(float u0, float u1, float v0, float v1) {
		private static final Uv FULL = new Uv(0.0F, 1.0F, 0.0F, 1.0F);
	}
	private record Basis(Vector3f right, Vector3f up, Vector3f normal) {
		private static Basis from(final Quaternionf camera) {
			return new Basis(new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera),
				new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera),
				new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera));
		}
	}

	private static void billboard(final PoseStack.Pose pose, final VertexConsumer buffer,
		final float centerX, final float centerY, final float centerZ, final float radius, final float rotation,
		final int red, final int green, final int blue, final float alpha, final int light, final Basis basis,
		final float u0, final float u1, final float v0, final float v1) {
		float cosine = Mth.cos(rotation);
		float sine = Mth.sin(rotation);
		vertex(pose, buffer, centerX, centerY, centerZ, -radius, -radius, cosine, sine, u0, v1,
			red, green, blue, alpha, light, basis);
		vertex(pose, buffer, centerX, centerY, centerZ, -radius, radius, cosine, sine, u0, v0,
			red, green, blue, alpha, light, basis);
		vertex(pose, buffer, centerX, centerY, centerZ, radius, radius, cosine, sine, u1, v0,
			red, green, blue, alpha, light, basis);
		vertex(pose, buffer, centerX, centerY, centerZ, radius, -radius, cosine, sine, u1, v1,
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
