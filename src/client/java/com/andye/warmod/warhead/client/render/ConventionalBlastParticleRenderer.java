package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadVisualMath;
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
 * Packed, fixed-capacity client particle field used by conventional impacts and
 * the nuclear return front. Particles are simulated once per client game tick
 * and interpolated while rendering; no Java object is allocated per particle.
 */
public final class ConventionalBlastParticleRenderer {
	private static final int MAX_FIELDS = 24;
	private static final int CAPACITY = 65_536;
	private static final float HE_FIRE_TOP = 4.75F;
	private static final long NUCLEAR_KEY_MASK = 0x6E75636C656172L;
	private static final Map<Long, Field> FIELDS = new LinkedHashMap<>();

	private ConventionalBlastParticleRenderer() { }

	public static void renderFireCore(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (!WarheadRenderSettings.usePackedParticles()) {
			LegacyConventionalBlastRenderer.renderFireCore(pose, buffer, age, visualScale, profile, seed, lod, camera);
			return;
		}
		field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.FIRE_CORE);
	}

	public static void renderHot(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (!WarheadRenderSettings.usePackedParticles()) {
			LegacyConventionalBlastRenderer.renderHot(pose, buffer, age, visualScale, profile, seed, lod, camera);
			return;
		}
		field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.FIRE_HOT);
	}

	public static void renderCooling(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (!WarheadRenderSettings.usePackedParticles()) {
			LegacyConventionalBlastRenderer.renderCooling(pose, buffer, age, visualScale, profile, seed, lod, camera);
			return;
		}
		field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.FIRE_COOLING);
	}

	public static void renderSmokeCore(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (!WarheadRenderSettings.usePackedParticles()) {
			LegacyConventionalBlastRenderer.renderSmokeCore(pose, buffer, age, visualScale, profile, seed, lod, camera);
			return;
		}
		field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.SMOKE_CORE);
	}

	public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (!WarheadRenderSettings.usePackedParticles()) {
			LegacyConventionalBlastRenderer.renderSmoke(pose, buffer, age, visualScale, profile, seed, lod, camera);
			return;
		}
		field(seed, visualScale, false).render(pose, buffer, age, lod, camera, Pass.SMOKE_SOFT);
	}

	public static void renderSurfaceFront(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final double physicalRadius, final float visualScale, final long seed,
		final WarheadMesh.Lod lod, final Quaternionf camera) {
		if (!WarheadRenderSettings.usePackedParticles()) {
			LegacyConventionalBlastRenderer.renderSurfaceFront(pose, buffer, age, physicalRadius, visualScale, seed, lod, camera);
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
			LegacyConventionalBlastRenderer.renderNuclearReturnFront(pose, buffer, age, returnRadius, yieldScale, seed, lod, camera);
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
		return new DebugSnapshot(active, spawned, culled, FIELDS.size(), "packed_soa_custom_geometry");
	}

	private static synchronized Field field(final long key, final float visualScale, final boolean nuclearOnly) {
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

	public record DebugSnapshot(int activeParticles, int spawnedParticlesPerTick, int culledParticles,
		int activeFields, String backend) { }

	private enum Pass {
		FIRE_CORE,
		FIRE_HOT,
		FIRE_COOLING,
		SMOKE_CORE,
		SMOKE_SOFT,
		SURFACE_FRONT,
		RETURN_FRONT
	}

	private static final class Field {
		private static final byte MATERIAL_FIRE = 0;
		private static final byte MATERIAL_DUST = 1;
		private static final byte MATERIAL_FRONT = 2;
		private static final byte MATERIAL_RETURN = 3;
		private static final byte FLAG_CORE = 1;
		private static final byte FLAG_SPOUT = 2;

		private final long seed;
		private final float scale;
		private final boolean nuclearOnly;
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
		private final boolean[] active = new boolean[CAPACITY];
		private int simulatedTick = -1;
		private int nextSlot;
		private int activeCount;
		private int spawnedLastTick;
		private int culledLastRender;
		private int lastSurfaceTick = Integer.MIN_VALUE;
		private int lastReturnTick = Integer.MIN_VALUE;

		private Field(final long seed, final float visualScale, final boolean nuclearOnly) {
			this.seed = seed;
			this.scale = Mth.clamp(visualScale, 0.28F, nuclearOnly ? 3.0F : 1.75F);
			this.nuclearOnly = nuclearOnly;
		}

		private void ensureSimulated(final double age) {
			int target = Math.max(0, (int) Math.floor(age));
			if (target < simulatedTick) reset();
			while (simulatedTick < target) {
				simulatedTick++;
				spawnedLastTick = 0;
				if (!nuclearOnly) emitConventional(simulatedTick);
				update(simulatedTick);
			}
		}

		private void reset() {
			for (int index = 0; index < CAPACITY; index++) active[index] = false;
			simulatedTick = -1;
			nextSlot = 0;
			activeCount = 0;
			spawnedLastTick = 0;
			lastSurfaceTick = Integer.MIN_VALUE;
			lastReturnTick = Integer.MIN_VALUE;
		}

		private void emitConventional(final int tick) {
			float density = 0.78F + (float) Math.pow(scale, 1.62);
			int ignitionEnd = 6;
			int feedEnd = Math.round(28.0F + 18.0F * scale);
			if (tick <= ignitionEnd) {
				int count = Math.round((340.0F + 250.0F * scale) * density);
				for (int index = 0; index < count; index++) spawnFire(tick, index, false, true);
			}
			if (tick <= feedEnd) {
				float feed = 1.0F - tick / (float) Math.max(1, feedEnd);
				int fire = Math.round((115.0F + 155.0F * scale) * density * (0.38F + 0.62F * feed));
				int spout = Math.round((58.0F + 92.0F * scale) * density * (0.45F + 0.55F * feed));
				for (int index = 0; index < fire; index++) spawnFire(tick, index, false, false);
				for (int index = 0; index < spout; index++) spawnFire(tick, index, true, false);
			}
			if (tick <= Math.round(18.0F + 12.0F * scale)) {
				int arcs = Math.round((44.0F + 72.0F * scale) * density);
				int dust = Math.round((34.0F + 64.0F * scale) * density);
				for (int index = 0; index < arcs; index++) spawnArc(tick, index);
				for (int index = 0; index < dust; index++) spawnDust(tick, index);
			}
		}

		private void spawnFire(final int tick, final int ordinal, final boolean spout, final boolean ignition) {
			long random = mix(seed ^ 0x464952455F504143L ^ ((long) tick << 32) ^ ordinal * 0x9E3779B97F4A7C15L
				^ (spout ? 0x53504F5554L : 0L));
			float angle = unit(random, 0) * Mth.TWO_PI;
			float crater = -(1.8F + 5.6F * scale);
			float source = (float) Math.sqrt(unit(random, 1)) * (ignition ? 1.2F + 2.2F * scale : 0.8F + 3.4F * scale);
			float px = Mth.cos(angle) * source;
			float pz = Mth.sin(angle) * source;
			float py = crater + unit(random, 2) * (1.4F + 2.4F * scale);
			float outward = spout ? 0.055F + unit(random, 3) * 0.12F : 0.12F + unit(random, 3) * 0.31F;
			float up = spout ? 0.36F + unit(random, 4) * (0.35F + 0.10F * scale)
				: 0.18F + unit(random, 4) * (0.34F + 0.12F * scale);
			float vx = Mth.cos(angle) * outward + signed(random, 5) * 0.035F;
			float vz = Mth.sin(angle) * outward + signed(random, 6) * 0.035F;
			byte particleFlags = (byte) ((unit(random, 7) < 0.30F ? FLAG_CORE : 0) | (spout ? FLAG_SPOUT : 0));
			float particleRadius = (0.12F + unit(random, 8) * 0.31F) * (0.82F + 0.30F * scale);
			int life = Math.round((spout ? 76.0F : 58.0F) + unit(random, 9) * (44.0F + 30.0F * scale));
			spawn(MATERIAL_FIRE, particleFlags, px, py, pz, vx, up, vz,
				0.88F + unit(random, 10) * 0.22F, particleRadius, life, (int) random);
		}

		private void spawnArc(final int tick, final int ordinal) {
			long random = mix(seed ^ 0x4152435F5041434BL ^ ((long) tick << 33) ^ ordinal * 0xD1B54A32D192ED03L);
			float angle = unit(random, 0) * Mth.TWO_PI;
			float crater = -(1.6F + 5.2F * scale);
			float speed = 0.44F + unit(random, 1) * (0.82F + 0.40F * scale);
			float up = 0.38F + unit(random, 2) * (0.54F + 0.24F * scale);
			float hot = unit(random, 3) < 0.62F ? 0.72F + unit(random, 4) * 0.24F : 0.12F;
			spawn(hot > 0.5F ? MATERIAL_FIRE : MATERIAL_DUST, (byte) 0,
				Mth.cos(angle) * unit(random, 5) * 2.2F * scale, crater + unit(random, 6) * 2.5F,
				Mth.sin(angle) * unit(random, 7) * 2.2F * scale,
				Mth.cos(angle) * speed, up, Mth.sin(angle) * speed,
				hot, (0.09F + unit(random, 8) * 0.20F) * (0.86F + scale * 0.22F),
				Math.round(54.0F + unit(random, 9) * 74.0F), (int) random);
		}

		private void spawnDust(final int tick, final int ordinal) {
			long random = mix(seed ^ 0x445553545F504143L ^ ((long) tick << 31) ^ ordinal * 0x94D049BB133111EBL);
			float angle = unit(random, 0) * Mth.TWO_PI;
			float speed = 0.22F + unit(random, 1) * (0.48F + 0.28F * scale);
			float crater = -(1.2F + 4.8F * scale);
			spawn(MATERIAL_DUST, (byte) 0, Mth.cos(angle) * unit(random, 2) * 3.0F * scale,
				crater + unit(random, 3) * 2.2F, Mth.sin(angle) * unit(random, 4) * 3.0F * scale,
				Mth.cos(angle) * speed, 0.12F + unit(random, 5) * 0.44F, Mth.sin(angle) * speed,
				0.0F, (0.10F + unit(random, 6) * 0.24F) * (0.90F + scale * 0.25F),
				Math.round(45.0F + unit(random, 7) * 68.0F), (int) random);
		}

		private void emitSurfaceFront(final double age, final double physicalRadius, final WarheadMesh.Lod lod) {
			ensureSimulated(age);
			int tick = Math.max(0, (int) Math.floor(age));
			if (tick == lastSurfaceTick || physicalRadius <= 0.0) return;
			lastSurfaceTick = tick;
			double duration = WarheadVisualMath.airShockwaveDurationTicks(scale);
			if (age >= duration) return;
			int base = switch (lod) { case NEAR -> 720; case MEDIUM -> 360; case FAR -> 140; };
			int count = Math.min(1_900, Math.round(base * (0.72F + (float) Math.pow(scale, 1.30))));
			for (int index = 0; index < count; index++) {
				long random = mix(seed ^ 0x46524F4E545F5041L ^ ((long) tick << 32) ^ index * 0xBF58476D1CE4E5B9L);
				float angle = (index + unit(random, 0)) / count * Mth.TWO_PI;
				float trail = unit(random, 1) * (2.2F + 5.0F * scale);
				float radial = (float) Math.max(0.0, physicalRadius - trail);
				float px = Mth.cos(angle) * radial;
				float pz = Mth.sin(angle) * radial;
				float py = 0.08F + unit(random, 2) * (0.45F + 0.9F * scale);
				float tangent = signed(random, 3) * 0.10F;
				float outward = 0.10F + unit(random, 4) * 0.20F;
				float vx = Mth.cos(angle) * outward - Mth.sin(angle) * tangent;
				float vz = Mth.sin(angle) * outward + Mth.cos(angle) * tangent;
				spawn(MATERIAL_FRONT, (byte) 0, px, py, pz, vx, 0.035F + unit(random, 5) * 0.16F, vz,
					unit(random, 6) < 0.08F ? 0.58F : 0.0F,
					(0.10F + unit(random, 7) * 0.24F) * (0.90F + scale * 0.20F),
					Math.round(16.0F + unit(random, 8) * 24.0F), (int) random);
			}
		}

		private void emitReturnFront(final double age, final double returnRadius, final WarheadMesh.Lod lod) {
			ensureSimulated(age);
			int tick = Math.max(0, (int) Math.floor(age));
			if (tick == lastReturnTick || returnRadius <= 0.0) return;
			lastReturnTick = tick;
			int base = switch (lod) { case NEAR -> 560; case MEDIUM -> 280; case FAR -> 110; };
			int count = Math.min(1_500, Math.round(base * (0.70F + (float) Math.sqrt(scale))));
			for (int index = 0; index < count; index++) {
				long random = mix(seed ^ 0x52455455524E5041L ^ ((long) tick << 32) ^ index * 0x9E3779B97F4A7C15L);
				float angle = (index + unit(random, 0)) / count * Mth.TWO_PI;
				float radial = (float) returnRadius + signed(random, 1) * (0.8F + 1.6F * scale);
				float inward = 0.16F + unit(random, 2) * (0.22F + 0.08F * scale);
				spawn(MATERIAL_RETURN, (byte) 0, Mth.cos(angle) * radial,
					0.10F + unit(random, 3) * (0.55F + 0.50F * scale), Mth.sin(angle) * radial,
					-Mth.cos(angle) * inward, 0.025F + unit(random, 4) * 0.09F, -Mth.sin(angle) * inward,
					0.0F, (0.10F + unit(random, 5) * 0.22F) * (0.92F + 0.12F * scale),
					Math.round(22.0F + unit(random, 6) * 32.0F), (int) random);
			}
		}

		private void spawn(final byte particleMaterial, final byte particleFlags,
			final float px, final float py, final float pz, final float vx, final float vy, final float vz,
			final float heat, final float particleRadius, final int particleLifetime, final int randomSeed) {
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
			rotationVelocity[slot] = signed(randomSeed, 1) * 0.045F;
			particleAge[slot] = 0;
			lifetime[slot] = (short) Mth.clamp(particleLifetime, 8, Short.MAX_VALUE);
			particleSeed[slot] = randomSeed;
			material[slot] = particleMaterial;
			flags[slot] = particleFlags;
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

		private void update(final int tick) {
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
				float noise = signed(mix(((long) particleSeed[index] << 32) ^ tick * 0x9E3779B9L), 0);
				float sideways = 0.004F + progress * 0.012F;
				velocityX[index] += noise * sideways;
				velocityZ[index] += signed(particleSeed[index], tick & 7) * sideways;
				switch (material[index]) {
					case MATERIAL_FIRE -> {
						float cooling = (flags[index] & FLAG_CORE) != 0 ? 0.010F : 0.014F;
						temperature[index] = Math.max(0.0F, temperature[index] - cooling * (0.82F + progress * 0.55F));
						if (temperature[index] > 0.28F) {
							velocityY[index] = velocityY[index] * 0.955F + ((flags[index] & FLAG_SPOUT) != 0 ? 0.004F : 0.001F);
							velocityX[index] *= 0.992F;
							velocityZ[index] *= 0.992F;
						} else {
							velocityY[index] = velocityY[index] * 0.94F + 0.018F;
							velocityX[index] *= 0.978F;
							velocityZ[index] *= 0.978F;
						}
						radius[index] *= temperature[index] > 0.25F ? 1.006F : 1.011F;
					}
					case MATERIAL_DUST -> {
						velocityY[index] -= 0.022F;
						velocityX[index] *= 0.986F;
						velocityZ[index] *= 0.986F;
						radius[index] *= 1.010F;
					}
					case MATERIAL_FRONT -> {
						velocityY[index] += 0.002F;
						velocityX[index] *= 0.955F;
						velocityZ[index] *= 0.955F;
						temperature[index] = Math.max(0.0F, temperature[index] - 0.05F);
						radius[index] *= 1.020F;
					}
					case MATERIAL_RETURN -> {
						velocityX[index] *= 1.004F;
						velocityZ[index] *= 1.004F;
						velocityY[index] += 0.0015F;
						radius[index] *= 1.012F;
					}
					default -> { }
				}
				x[index] += velocityX[index];
				y[index] += velocityY[index];
				z[index] += velocityZ[index];
				if (material[index] == MATERIAL_FIRE && temperature[index] > 0.16F && y[index] > HE_FIRE_TOP) {
					y[index] = HE_FIRE_TOP;
					velocityY[index] = Math.min(0.0F, velocityY[index] * -0.12F);
				}
				rotation[index] += rotationVelocity[index];
				particleAge[index] = (short) (age + 1);
			}
		}

		private void render(final PoseStack.Pose pose, final VertexConsumer buffer, final double age,
			final WarheadMesh.Lod lod, final Quaternionf camera, final Pass pass) {
			ensureSimulated(age);
			float partial = (float) Mth.clamp(age - Math.floor(age), 0.0, 1.0);
			Basis basis = Basis.from(camera);
			int stride = switch (lod) { case NEAR -> 1; case MEDIUM -> 2; case FAR -> 5; };
			int culled = 0;
			for (int index = 0; index < CAPACITY; index += stride) {
				if (!active[index] || !matches(index, pass)) continue;
				int life = lifetime[index] & 0xFFFF;
				float progress = (particleAge[index] & 0xFFFF) / (float) Math.max(1, life);
				float alpha = alpha(index, pass, progress);
				if (alpha <= 0.006F) { culled++; continue; }
				float px = Mth.lerp(partial, previousX[index], x[index]);
				float py = Mth.lerp(partial, previousY[index], y[index]);
				float pz = Mth.lerp(partial, previousZ[index], z[index]);
				Colour colour = colour(index, pass);
				float drawRadius = radius[index] * (pass == Pass.SMOKE_CORE ? 1.14F : 1.0F);
				int light = isEmissive(pass) ? 0xF000F0 : pass == Pass.SMOKE_CORE ? 0x900090 : 0xB000B0;
				Uv uv = uv(index, pass);
				billboard(pose, buffer, px, py, pz, drawRadius, rotation[index], colour.red, colour.green,
					colour.blue, alpha, light, basis, uv.u0, uv.u1, uv.v0, uv.v1);
			}
			culledLastRender = culled + activeCount - activeCount / stride;
		}

		private boolean matches(final int index, final Pass pass) {
			byte type = material[index];
			float heat = temperature[index];
			return switch (pass) {
				case FIRE_CORE -> type == MATERIAL_FIRE && heat >= 0.68F && (flags[index] & FLAG_CORE) != 0;
				case FIRE_HOT -> type == MATERIAL_FIRE && heat >= 0.42F && (flags[index] & FLAG_CORE) == 0;
				case FIRE_COOLING -> type == MATERIAL_FIRE && heat >= 0.16F && heat < 0.48F;
				case SMOKE_CORE -> type == MATERIAL_FIRE && heat < 0.24F && (flags[index] & FLAG_CORE) != 0;
				case SMOKE_SOFT -> (type == MATERIAL_FIRE && heat < 0.32F && (flags[index] & FLAG_CORE) == 0)
					|| type == MATERIAL_DUST;
				case SURFACE_FRONT -> type == MATERIAL_FRONT;
				case RETURN_FRONT -> type == MATERIAL_RETURN;
			};
		}

		private float alpha(final int index, final Pass pass, final float progress) {
			float fadeIn = Math.min(1.0F, (particleAge[index] & 0xFFFF) / 3.0F);
			float fadeOut = (float) Math.pow(Math.max(0.0F, 1.0F - progress),
				pass == Pass.SMOKE_CORE ? 0.45 : pass == Pass.SMOKE_SOFT ? 0.72 : 0.55);
			float base = switch (pass) {
				case FIRE_CORE -> 0.96F;
				case FIRE_HOT -> 0.82F;
				case FIRE_COOLING -> 0.60F;
				case SMOKE_CORE -> 0.82F;
				case SMOKE_SOFT -> material[index] == MATERIAL_DUST ? 0.42F : 0.48F;
				case SURFACE_FRONT -> 0.52F;
				case RETURN_FRONT -> 0.38F;
			};
			return Mth.clamp(base * fadeIn * fadeOut, 0.0F, 0.97F);
		}

		private Colour colour(final int index, final Pass pass) {
			if (material[index] == MATERIAL_DUST || material[index] == MATERIAL_FRONT || material[index] == MATERIAL_RETURN) {
				int variation = Math.floorMod(particleSeed[index], 42);
				if (material[index] == MATERIAL_FRONT && temperature[index] > 0.30F) {
					return new Colour(255, 152 + variation / 3, 48);
				}
				int tone = Mth.clamp(174 + variation, 174, 216);
				return new Colour(tone, Math.min(224, tone + 4), Math.min(230, tone + 9));
			}
			float heat = Mth.clamp(temperature[index], 0.0F, 1.0F);
			if (pass == Pass.SMOKE_CORE || pass == Pass.SMOKE_SOFT || heat < 0.18F) {
				int tone = Mth.clamp(35 + (int) ((1.0F - heat) * 58.0F) + Math.floorMod(particleSeed[index], 23), 32, 126);
				return new Colour(tone, Math.min(132, tone + 4), Math.min(140, tone + 9));
			}
			if (heat > 0.82F) {
				float t = (heat - 0.82F) / 0.18F;
				return new Colour(255, Mth.lerpInt(t, 220, 255), Mth.lerpInt(t, 72, 220));
			}
			if (heat > 0.48F) {
				float t = (heat - 0.48F) / 0.34F;
				return new Colour(255, Mth.lerpInt(t, 104, 220), Mth.lerpInt(t, 18, 72));
			}
			float t = heat / 0.48F;
			return new Colour(Mth.lerpInt(t, 112, 255), Mth.lerpInt(t, 45, 104), Mth.lerpInt(t, 34, 18));
		}

		private Uv uv(final int index, final Pass pass) {
			if (pass == Pass.SMOKE_CORE || pass == Pass.SMOKE_SOFT || pass == Pass.SURFACE_FRONT || pass == Pass.RETURN_FRONT) {
				return Uv.FULL;
			}
			int frame = Math.floorMod((particleAge[index] & 0xFFFF) / 2 + particleSeed[index], FireballAtlas.FRAME_COUNT);
			float u0 = frame / (float) FireballAtlas.FRAME_COUNT + 0.5F / FireballAtlas.ATLAS_WIDTH;
			float u1 = (frame + 1) / (float) FireballAtlas.FRAME_COUNT - 0.5F / FireballAtlas.ATLAS_WIDTH;
			float v0 = 0.5F / FireballAtlas.ATLAS_HEIGHT;
			return new Uv(u0, u1, v0, 1.0F - v0);
		}

		private static boolean isEmissive(final Pass pass) {
			return pass == Pass.FIRE_CORE || pass == Pass.FIRE_HOT || pass == Pass.FIRE_COOLING;
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
