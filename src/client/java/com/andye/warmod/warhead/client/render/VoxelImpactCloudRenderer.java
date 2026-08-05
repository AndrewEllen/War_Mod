package com.andye.warmod.warhead.client.render;

import com.andye.warmod.warhead.WarheadPayloadType;
import com.andye.warmod.warhead.WarheadVisualMath;
import com.andye.warmod.warhead.client.WarheadClientVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Surface-voxel impact cloud renderer.
 *
 * <p>Unlike the Stage 4 random cube field, this builds one low-resolution
 * implicit density volume for all nearby impacts, keeps only its exposed
 * surface cells and emits only exposed faces. Interior voxels are never sent
 * to the GPU, so cluster salvos form one cloud instead of several intersecting
 * transparent boxes.</p>
 */
public final class VoxelImpactCloudRenderer {
	private static final int MAX_DENSITY_SOURCES = 16;
	private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
	private static final ThreadLocal<VolumeCache> VOLUME_CACHE = ThreadLocal.withInitial(VolumeCache::new);

	private VoxelImpactCloudRenderer() {
	}

	public record CloudSource(Vec3 offset, double ageTicks, float visualScale, long seed) {
		public CloudSource {
			if (offset == null || !offset.isFinite()) offset = Vec3.ZERO;
		}
	}

	public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod) {
		renderSmoke(pose, buffer, age, visualScale, profile, seed, lod,
			List.of(new CloudSource(Vec3.ZERO, age, visualScale, seed)));
	}

	public static void renderSmoke(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final List<CloudSource> sources) {
		if (profile == null || age < profile.smokeStartTick()
			|| age >= profile.cloudDissipationEndTick()) return;
		boolean nuclear = profile.payloadType() == WarheadPayloadType.NUCLEAR;
		double scale = geometryScale(profile.payloadType(), visualScale);
		double rise = smooth((age - profile.smokeStartTick())
			/ Math.max(1.0, profile.cloudRiseEndTick() - profile.smokeStartTick()));
		double dissipate = WarheadVisualMath.clamp(
			(age - profile.cloudRiseEndTick())
				/ Math.max(1.0, profile.cloudDissipationEndTick() - profile.cloudRiseEndTick()),
			0.0,
			1.0
		);
		double fade = Math.pow(1.0 - dissipate, 0.68);
		double appearance = smooth((age - profile.smokeStartTick()) / (nuclear ? 16.0 : 8.0));
		if (fade <= 0.01) return;

		double sourceExtent = sourceExtent(sources);
		double capWidth = Math.max(8.0, profile.smokeCapWidth() * scale);
		double height = Math.max(10.0, profile.maximumCloudHeight() * scale
			* (0.12 + 0.88 * rise));
		double halfWidth = Math.max(capWidth * (0.38 + 0.30 * rise), sourceExtent + capWidth * 0.24);
		GridSpec requestedGrid = smokeGrid(lod, halfWidth, height, nuclear);
		CacheKey cacheKey = cacheKey(0, age, visualScale, profile, seed, lod, sources);
		GridSnapshot snapshot = VOLUME_CACHE.get().get(cacheKey);
		GridSpec grid;
		boolean[] occupiedCells;
		float[] densityCells;
		if (snapshot == null) {
			grid = requestedGrid;
			Scratch scratch = SCRATCH.get();
			scratch.prepare(grid.count());
			int occupied = 0;
			for (int y = 0; y < grid.yCount; y++) {
				double py = grid.minimumY + (y + 0.5) * grid.cell;
				for (int z = 0; z < grid.zCount; z++) {
					double pz = grid.minimumZ + (z + 0.5) * grid.cell;
					for (int x = 0; x < grid.xCount; x++) {
						double px = grid.minimumX + (x + 0.5) * grid.cell;
						double density = smokeDensity(px, py, pz, age, rise, scale,
							profile, sources, seed, nuclear);
						int index = grid.index(x, y, z);
						boolean solid = density >= 0.43;
						scratch.occupied[index] = solid;
						scratch.density[index] = (float) density;
						if (solid) occupied++;
					}
				}
			}
			if (occupied == 0) return;
			snapshot = GridSnapshot.copyOf(grid, scratch.occupied, scratch.density);
			VOLUME_CACHE.get().put(cacheKey, snapshot);
		} else {
			grid = snapshot.grid;
		}
		occupiedCells = snapshot.occupied;
		densityCells = snapshot.density;

		int maximumCells = lod == WarheadMesh.Lod.NEAR ? 1_700
			: lod == WarheadMesh.Lod.MEDIUM ? 720 : 260;
		int emitted = 0;
		int phase = (int) (mix(seed) & 7L);
		for (int y = 0; y < grid.yCount && emitted < maximumCells; y++) {
			for (int z = 0; z < grid.zCount && emitted < maximumCells; z++) {
				for (int x = 0; x < grid.xCount && emitted < maximumCells; x++) {
					int index = grid.index(x, y, z);
					if (!occupiedCells[index] || ((x + y + z + phase) & grid.sampleMask) != 0) continue;
					int faces = exposedFaces(occupiedCells, grid, x, y, z);
					if (faces == 0) continue;
					double px = grid.minimumX + (x + 0.5) * grid.cell;
					double py = grid.minimumY + (y + 0.5) * grid.cell;
					double pz = grid.minimumZ + (z + 0.5) * grid.cell;
					float density = densityCells[index];
					double heightFraction = WarheadVisualMath.clamp(py / Math.max(1.0, height), 0.0, 1.0);
					long colourSeed = mix(seed ^ ((long) x * 73428767L)
						^ ((long) y * 912931L) ^ ((long) z * 19349663L));
					double variation = unit(colourSeed);
					int base = nuclear ? 24 : 31;
					int warming = age < profile.fireballCoolingEndTick()
						? (int) (Math.max(0.0, 1.0 - heightFraction * 1.65) * (nuclear ? 30 : 20)) : 0;
					int red = Mth.clamp(base + warming + (int) (variation * 34.0)
						+ (int) (dissipate * 28.0), 18, 112);
					int green = Mth.clamp(red + 3, 20, 118);
					int blue = Mth.clamp(red + 8, 24, 128);
					float alpha = (float) Mth.clamp((0.70 + density * 0.24) * fade * appearance, 0.0, 0.94);
					emitExposedCube(pose, buffer, new Vec3(px, py, pz),
						(float) (grid.cell * 1.015), faces, red, green, blue, alpha, 0xA000A0);
					emitted++;
				}
			}
		}
	}

	public static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final boolean hotPass) {
		renderFire(pose, buffer, age, visualScale, profile, seed, lod, hotPass,
			List.of(new CloudSource(Vec3.ZERO, age, visualScale, seed)));
	}

	public static void renderFire(final PoseStack.Pose pose, final VertexConsumer buffer,
		final double age, final float visualScale, final WarheadClientVisualProfile profile,
		final long seed, final WarheadMesh.Lod lod, final boolean hotPass,
		final List<CloudSource> sources) {
		if (profile == null || age < profile.fireballGrowthStartTick()
			|| age >= profile.fireballCoolingEndTick()) return;
		boolean nuclear = profile.payloadType() == WarheadPayloadType.NUCLEAR;
		double scale = geometryScale(profile.payloadType(), visualScale);
		double growth = smooth((age - profile.fireballGrowthStartTick())
			/ Math.max(1.0, profile.fireballGrowthEndTick() - profile.fireballGrowthStartTick()));
		double cooling = WarheadVisualMath.clamp((age - profile.fireballHoldEndTick())
			/ Math.max(1.0, profile.fireballCoolingEndTick() - profile.fireballHoldEndTick()),
			0.0,
			1.0);
		double intensity = hotPass ? 1.0 - Math.pow(cooling, 1.12)
			: smooth(cooling / 0.26) * Math.pow(1.0 - cooling, 0.62);
		if (intensity <= 0.01) return;

		double sourceExtent = sourceExtent(sources);
		double radius = (nuclear ? 25.0 : 10.5) * scale * (0.22 + 0.88 * growth);
		double halfWidth = Math.max(radius * 1.12, sourceExtent + radius * 0.58);
		GridSpec requestedGrid = fireGrid(lod, halfWidth, radius * 2.0, nuclear);
		CacheKey cacheKey = cacheKey(1, age, visualScale, profile, seed, lod, sources);
		GridSnapshot snapshot = VOLUME_CACHE.get().get(cacheKey);
		GridSpec grid;
		boolean[] occupiedCells;
		float[] densityCells;
		if (snapshot == null) {
			grid = requestedGrid;
			Scratch scratch = SCRATCH.get();
			scratch.prepare(grid.count());
			for (int y = 0; y < grid.yCount; y++) {
				double py = grid.minimumY + (y + 0.5) * grid.cell;
				for (int z = 0; z < grid.zCount; z++) {
					double pz = grid.minimumZ + (z + 0.5) * grid.cell;
					for (int x = 0; x < grid.xCount; x++) {
						double px = grid.minimumX + (x + 0.5) * grid.cell;
						double density = fireDensity(px, py, pz, age, radius, sources, seed, nuclear);
						int index = grid.index(x, y, z);
						scratch.occupied[index] = density >= 0.48;
						scratch.density[index] = (float) density;
					}
				}
			}
			snapshot = GridSnapshot.copyOf(grid, scratch.occupied, scratch.density);
			VOLUME_CACHE.get().put(cacheKey, snapshot);
		} else {
			grid = snapshot.grid;
		}
		occupiedCells = snapshot.occupied;
		densityCells = snapshot.density;

		int maximumCells = lod == WarheadMesh.Lod.NEAR ? 900
			: lod == WarheadMesh.Lod.MEDIUM ? 420 : 150;
		int emitted = 0;
		for (int y = 0; y < grid.yCount && emitted < maximumCells; y++) {
			for (int z = 0; z < grid.zCount && emitted < maximumCells; z++) {
				for (int x = 0; x < grid.xCount && emitted < maximumCells; x++) {
					int index = grid.index(x, y, z);
					if (!occupiedCells[index]) continue;
					int faces = exposedFaces(occupiedCells, grid, x, y, z);
					if (faces == 0) continue;
					double px = grid.minimumX + (x + 0.5) * grid.cell;
					double py = grid.minimumY + (y + 0.5) * grid.cell;
					double pz = grid.minimumZ + (z + 0.5) * grid.cell;
					long colourSeed = mix(seed ^ ((long) x << 42) ^ ((long) y << 21) ^ z);
					double variation = unit(colourSeed);
					int red;
					int green;
					int blue;
					if (hotPass) {
						red = 255;
						green = Mth.clamp(145 + (int) (variation * 92.0) - (int) (cooling * 38.0), 112, 238);
						blue = Mth.clamp(18 + (int) (variation * 48.0), 14, 72);
					} else {
						red = Mth.clamp(190 - (int) (cooling * 108.0), 68, 190);
						green = Mth.clamp(82 - (int) (cooling * 44.0), 30, 90);
						blue = Mth.clamp(20 + (int) (variation * 22.0), 16, 46);
					}
					float alpha = (float) Mth.clamp((hotPass ? 0.90 : 0.78)
						* intensity * (0.86 + densityCells[index] * 0.14), 0.0, 0.96);
					emitExposedCube(pose, buffer, new Vec3(px, py, pz),
						(float) (grid.cell * 1.02), faces, red, green, blue, alpha,
						hotPass ? 0xF000F0 : 0xD000D0);
					emitted++;
				}
			}
		}
	}

	private static double smokeDensity(final double x, final double y, final double z,
		final double age, final double rise, final double scale,
		final WarheadClientVisualProfile profile, final List<CloudSource> sources,
		final long seed, final boolean nuclear) {
		if (y < -3.0) return 0.0;
		double height = Math.max(10.0, profile.maximumCloudHeight() * scale
			* (0.12 + 0.88 * rise));
		double capY = height * (0.50 + 0.25 * rise);
		double capRadius = Math.max(4.0, profile.smokeCapWidth() * scale
			* (0.12 + 0.38 * rise));
		double stemHeight = Math.max(5.0, capY * 1.03);
		double yFraction = WarheadVisualMath.clamp(y / stemHeight, 0.0, 1.0);
		double swirl = age * 0.010 + yFraction * 2.7 + (seed & 1023L) * 0.001;
		double axisX = Math.sin(swirl) * profile.smokeStemWidth() * scale * 0.055 * yFraction;
		double axisZ = Math.cos(swirl * 0.91) * profile.smokeStemWidth() * scale * 0.055 * yFraction;
		double radial = Math.hypot(x - axisX, z - axisZ);
		double stemRadius = Math.max(2.0, profile.smokeStemWidth() * scale
			* (0.16 + 0.18 * yFraction + 0.05 * Math.sin(y * 0.13 + swirl)));
		double stem = y <= stemHeight
			? 1.0 - radial / stemRadius - Math.max(0.0, y - stemHeight * 0.92) / (stemHeight * 0.28)
			: -1.0;

		double capRadial = Math.hypot(x, z);
		double capVerticalRadius = Math.max(3.0, height * (0.13 + 0.08 * rise));
		double core = 1.0 - Math.sqrt(
			(capRadial * capRadial) / Math.max(1.0, capRadius * capRadius)
				+ ((y - capY) * (y - capY)) / (capVerticalRadius * capVerticalRadius)
		);
		double torusMajor = capRadius * (0.62 + 0.10 * Math.sin(age * 0.018));
		double torusMinor = Math.max(2.0, capRadius * 0.28);
		double torusDistance = Math.hypot(capRadial - torusMajor, (y - capY) * 1.15);
		double torus = 1.0 - torusDistance / torusMinor;

		double skirtExpansion = Math.min(1.0, Math.max(0.0, age - profile.smokeStartTick())
			/ (nuclear ? 54.0 : 28.0));
		double skirtRadius = profile.smokeCapWidth() * scale * 0.70 * skirtExpansion;
		double skirt = y <= Math.max(3.0, profile.smokeStemWidth() * scale * 0.22)
			? 1.0 - Math.abs(capRadial - skirtRadius * 0.58) / Math.max(2.0, skirtRadius * 0.50)
				- y / Math.max(4.0, profile.smokeStemWidth() * scale * 0.30)
			: -1.0;

		double sourceDensity = -1.0;
		int count = sources == null ? 0 : sources.size();
		int stride = Math.max(1, (int) Math.ceil(count / (double) MAX_DENSITY_SOURCES));
		for (int index = 0; index < count; index += stride) {
			CloudSource source = sources.get(index);
			double convergence = smooth(y / Math.max(4.0, capY));
			double sx = source.offset().x * (1.0 - convergence * 0.92);
			double sz = source.offset().z * (1.0 - convergence * 0.92);
			double sourceRise = Math.min(capY * 0.58, Math.max(0.0, source.ageTicks())
				* (nuclear ? 0.12 : 0.08) * scale);
			double sourceRadius = Math.max(2.0, profile.smokeStemWidth() * scale
				* (0.15 + 0.08 * smooth(source.ageTicks() / 36.0)));
			double dx = x - sx;
			double dy = (y - sourceRise) * 0.82;
			double dz = z - sz;
			double sourceField = 1.0 - Math.sqrt(dx * dx + dy * dy + dz * dz) / sourceRadius;
			sourceDensity = Math.max(sourceDensity, sourceField);
		}

		double noise = spatialNoise(x, y, z, seed);
		double density = Math.max(Math.max(stem, core), Math.max(torus, Math.max(skirt, sourceDensity)));
		return density + (noise - 0.5) * (nuclear ? 0.26 : 0.20);
	}

	private static double fireDensity(final double x, final double y, final double z,
		final double age, final double radius, final List<CloudSource> sources,
		final long seed, final boolean nuclear) {
		double rise = Math.min(radius * 0.34, age * (nuclear ? 0.095 : 0.060));
		double central = 1.0 - Math.sqrt(
			(x * x + z * z) / Math.max(1.0, radius * radius)
				+ ((y - rise) * (y - rise)) / Math.max(1.0, radius * radius * 0.72)
		);
		double sourceDensity = central;
		int count = sources == null ? 0 : sources.size();
		int stride = Math.max(1, (int) Math.ceil(count / (double) MAX_DENSITY_SOURCES));
		for (int index = 0; index < count; index += stride) {
			CloudSource source = sources.get(index);
			double localRadius = radius * (0.46 + 0.14 * smooth(source.ageTicks() / 18.0));
			double dx = x - source.offset().x * 0.72;
			double dy = y - source.offset().y - rise * 0.45;
			double dz = z - source.offset().z * 0.72;
			double value = 1.0 - Math.sqrt(dx * dx + dy * dy + dz * dz)
				/ Math.max(1.0, localRadius);
			sourceDensity = Math.max(sourceDensity, value);
		}
		return sourceDensity + (spatialNoise(x, y, z, seed ^ 0x464952455F5635L) - 0.5) * 0.22;
	}

	private static GridSpec smokeGrid(final WarheadMesh.Lod lod, final double halfWidth,
		final double height, final boolean nuclear) {
		int xz = lod == WarheadMesh.Lod.NEAR ? (nuclear ? 35 : 31)
			: lod == WarheadMesh.Lod.MEDIUM ? 23 : 15;
		int ys = lod == WarheadMesh.Lod.NEAR ? (nuclear ? 47 : 39)
			: lod == WarheadMesh.Lod.MEDIUM ? 31 : 21;
		double cell = Math.max(nuclear ? 1.35 : 0.90,
			Math.max(halfWidth * 2.0 / Math.max(3, xz - 2), height / Math.max(3, ys - 2)));
		return new GridSpec(xz, ys, xz, cell, -xz * cell * 0.5, -cell,
			-xz * cell * 0.5, lod == WarheadMesh.Lod.FAR ? 1 : 0);
	}

	private static GridSpec fireGrid(final WarheadMesh.Lod lod, final double halfWidth,
		final double height, final boolean nuclear) {
		int xz = lod == WarheadMesh.Lod.NEAR ? (nuclear ? 27 : 23)
			: lod == WarheadMesh.Lod.MEDIUM ? 19 : 13;
		int ys = lod == WarheadMesh.Lod.NEAR ? (nuclear ? 29 : 23)
			: lod == WarheadMesh.Lod.MEDIUM ? 19 : 13;
		double cell = Math.max(nuclear ? 1.15 : 0.72,
			Math.max(halfWidth * 2.0 / Math.max(3, xz - 2), height / Math.max(3, ys - 2)));
		return new GridSpec(xz, ys, xz, cell, -xz * cell * 0.5, -ys * cell * 0.30,
			-xz * cell * 0.5, 0);
	}

	private static int exposedFaces(final boolean[] occupied, final GridSpec grid,
		final int x, final int y, final int z) {
		int faces = 0;
		if (x == 0 || !occupied[grid.index(x - 1, y, z)]) faces |= 1;
		if (x + 1 == grid.xCount || !occupied[grid.index(x + 1, y, z)]) faces |= 2;
		if (y == 0 || !occupied[grid.index(x, y - 1, z)]) faces |= 4;
		if (y + 1 == grid.yCount || !occupied[grid.index(x, y + 1, z)]) faces |= 8;
		if (z == 0 || !occupied[grid.index(x, y, z - 1)]) faces |= 16;
		if (z + 1 == grid.zCount || !occupied[grid.index(x, y, z + 1)]) faces |= 32;
		return faces;
	}

	private static void emitExposedCube(final PoseStack.Pose pose, final VertexConsumer buffer,
		final Vec3 center, final float size, final int faces, final int red,
		final int green, final int blue, final float alpha, final int light) {
		float h = size * 0.5F;
		int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		if ((faces & 1) != 0) faceX(pose, buffer, center, -h, -h, h, red, green, blue, a, light, -1.0F);
		if ((faces & 2) != 0) faceX(pose, buffer, center, h, -h, h, red, green, blue, a, light, 1.0F);
		if ((faces & 4) != 0) faceY(pose, buffer, center, -h, -h, h, red, green, blue, a, light, -1.0F);
		if ((faces & 8) != 0) faceY(pose, buffer, center, h, -h, h, red, green, blue, a, light, 1.0F);
		if ((faces & 16) != 0) faceZ(pose, buffer, center, -h, -h, h, red, green, blue, a, light, -1.0F);
		if ((faces & 32) != 0) faceZ(pose, buffer, center, h, -h, h, red, green, blue, a, light, 1.0F);
	}

	private static void faceX(final PoseStack.Pose pose, final VertexConsumer b, final Vec3 c,
		final float x, final float low, final float high, final int r, final int g,
		final int bl, final int a, final int light, final float normal) {
		vertex(pose, b, c, x, low, low, 0, 1, r, g, bl, a, light, normal, 0, 0);
		vertex(pose, b, c, x, high, low, 0, 0, r, g, bl, a, light, normal, 0, 0);
		vertex(pose, b, c, x, high, high, 1, 0, r, g, bl, a, light, normal, 0, 0);
		vertex(pose, b, c, x, low, high, 1, 1, r, g, bl, a, light, normal, 0, 0);
	}

	private static void faceY(final PoseStack.Pose pose, final VertexConsumer b, final Vec3 c,
		final float y, final float low, final float high, final int r, final int g,
		final int bl, final int a, final int light, final float normal) {
		vertex(pose, b, c, low, y, low, 0, 1, r, g, bl, a, light, 0, normal, 0);
		vertex(pose, b, c, low, y, high, 0, 0, r, g, bl, a, light, 0, normal, 0);
		vertex(pose, b, c, high, y, high, 1, 0, r, g, bl, a, light, 0, normal, 0);
		vertex(pose, b, c, high, y, low, 1, 1, r, g, bl, a, light, 0, normal, 0);
	}

	private static void faceZ(final PoseStack.Pose pose, final VertexConsumer b, final Vec3 c,
		final float z, final float low, final float high, final int r, final int g,
		final int bl, final int a, final int light, final float normal) {
		vertex(pose, b, c, low, low, z, 0, 1, r, g, bl, a, light, 0, 0, normal);
		vertex(pose, b, c, low, high, z, 0, 0, r, g, bl, a, light, 0, 0, normal);
		vertex(pose, b, c, high, high, z, 1, 0, r, g, bl, a, light, 0, 0, normal);
		vertex(pose, b, c, high, low, z, 1, 1, r, g, bl, a, light, 0, 0, normal);
	}

	private static void vertex(final PoseStack.Pose pose, final VertexConsumer buffer,
		final Vec3 center, final float x, final float y, final float z,
		final float u, final float v, final int red, final int green, final int blue,
		final int alpha, final int light, final float nx, final float ny, final float nz) {
		buffer.addVertex(pose, (float) center.x + x, (float) center.y + y, (float) center.z + z)
			.setColor(red, green, blue, alpha)
			.setUv(u, v)
			.setOverlay(0)
			.setLight(light)
			.setNormal(pose, nx, ny, nz);
	}

	private static CacheKey cacheKey(final int kind, final double age, final float visualScale,
		final WarheadClientVisualProfile profile, final long seed, final WarheadMesh.Lod lod,
		final List<CloudSource> sources) {
		return new CacheKey(
			kind,
			Math.max(0, (int) Math.floor(age)),
			Float.floatToIntBits(visualScale),
			profile.hashCode(),
			seed,
			lod.ordinal(),
			sourceSignature(sources)
		);
	}

	private static long sourceSignature(final List<CloudSource> sources) {
		if (sources == null || sources.isEmpty()) return 0L;
		long signature = sources.size() * 0x9E3779B97F4A7C15L;
		int stride = Math.max(1, (int) Math.ceil(sources.size() / (double) MAX_DENSITY_SOURCES));
		for (int index = 0; index < sources.size(); index += stride) {
			CloudSource source = sources.get(index);
			long value = source.seed()
				^ Double.doubleToLongBits(source.offset().x)
				^ Long.rotateLeft(Double.doubleToLongBits(source.offset().y), 17)
				^ Long.rotateLeft(Double.doubleToLongBits(source.offset().z), 33)
				^ ((long) Math.floor(source.ageTicks()) << 32)
				^ Float.floatToIntBits(source.visualScale());
			signature ^= mix(value + index * 0xD1B54A32D192ED03L);
		}
		return signature;
	}

	private static double geometryScale(final WarheadPayloadType type, final float visualScale) {
		return type == WarheadPayloadType.NUCLEAR
			? Mth.clamp(visualScale / 3.0F, 0.48F, 1.90F)
			: Mth.clamp(visualScale, 0.30F, 1.95F);
	}

	private static double sourceExtent(final List<CloudSource> sources) {
		double extent = 0.0;
		if (sources == null) return extent;
		for (CloudSource source : sources) {
			extent = Math.max(extent, Math.hypot(source.offset().x, source.offset().z));
		}
		return extent;
	}

	private static double spatialNoise(final double x, final double y, final double z, final long seed) {
		long ix = Mth.floor(x * 0.29);
		long iy = Mth.floor(y * 0.29);
		long iz = Mth.floor(z * 0.29);
		return unit(mix(seed ^ ix * 0x632BE59BD9B4E019L
			^ iy * 0x9E3779B97F4A7C15L ^ iz * 0xD1B54A32D192ED03L));
	}

	private static double smooth(final double value) {
		double t = WarheadVisualMath.clamp(value, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	private static double unit(final long value) {
		return (mix(value) >>> 11) * 0x1.0p-53;
	}

	private static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	private record CacheKey(int kind, int ageTick, int scaleBits, int profileHash,
		long seed, int lod, long sourceSignature) {
	}

	private static final class GridSnapshot {
		private final GridSpec grid;
		private final boolean[] occupied;
		private final float[] density;

		private GridSnapshot(final GridSpec grid, final boolean[] occupied, final float[] density) {
			this.grid = grid;
			this.occupied = occupied;
			this.density = density;
		}

		private static GridSnapshot copyOf(final GridSpec grid, final boolean[] occupied,
			final float[] density) {
			int count = grid.count();
			return new GridSnapshot(grid, java.util.Arrays.copyOf(occupied, count),
				java.util.Arrays.copyOf(density, count));
		}
	}

	private static final class VolumeCache extends LinkedHashMap<CacheKey, GridSnapshot> {
		private static final int MAX_ENTRIES = 24;

		private VolumeCache() {
			super(32, 0.75F, true);
		}

		@Override
		protected boolean removeEldestEntry(final Map.Entry<CacheKey, GridSnapshot> eldest) {
			return size() > MAX_ENTRIES;
		}
	}

	private static final class Scratch {
		private boolean[] occupied = new boolean[0];
		private float[] density = new float[0];

		private void prepare(final int size) {
			if (occupied.length < size) {
				occupied = new boolean[size];
				density = new float[size];
			} else {
				java.util.Arrays.fill(occupied, 0, size, false);
				java.util.Arrays.fill(density, 0, size, 0.0F);
			}
		}
	}

	private static final class GridSpec {
		private final int xCount;
		private final int yCount;
		private final int zCount;
		private final double cell;
		private final double minimumX;
		private final double minimumY;
		private final double minimumZ;
		private final int sampleMask;

		private GridSpec(final int xCount, final int yCount, final int zCount,
			final double cell, final double minimumX, final double minimumY,
			final double minimumZ, final int sampleMask) {
			this.xCount = xCount;
			this.yCount = yCount;
			this.zCount = zCount;
			this.cell = cell;
			this.minimumX = minimumX;
			this.minimumY = minimumY;
			this.minimumZ = minimumZ;
			this.sampleMask = sampleMask;
		}

		private int count() {
			return xCount * yCount * zCount;
		}

		private int index(final int x, final int y, final int z) {
			return (y * zCount + z) * xCount + x;
		}
	}
}
